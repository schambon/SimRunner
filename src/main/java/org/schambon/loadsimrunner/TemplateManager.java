package org.schambon.loadsimrunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class TemplateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateManager.class);

    String name;
    String database;
    String collection;
    boolean drop = false;

    Document template;

    private Set<String> fieldsToRemember = new TreeSet<>();
    // using a synchronized list for remembrances because a set is too slow to get a random element out of
    private Map<String, List<Object>> remembrances = new TreeMap<>();
    private List<Document> indexes;

    private MongoCollection<Document> mongoColl = null;

    private Reporter reporter;

    // compiled generators cache
    private Map<Document, DocumentGenerator> generators = new HashMap<>();

    public TemplateManager(Document config, Reporter reporter) {
        this.reporter = reporter;
        this.name = config.getString("name");
        this.database = config.getString("database");
        this.collection = config.getString("collection");
        this.drop = config.getBoolean("drop", false);

        this.template = (Document) config.get("template");

        var remember = (List<String>) config.get("remember");
        for (var field: remember) {
            this.fieldsToRemember.add(field);
            this.remembrances.put(field, Collections.synchronizedList(new ArrayList<>()));
        }

        if (config.containsKey("indexes")) {
            this.indexes = config.getList("indexes", Document.class);
        } else {
            this.indexes = Collections.emptyList();
        }
    }
    
    public MongoCollection<Document> getCollection() {
        return this.mongoColl;
    }

    public void initialize(MongoClient client) {
        reporter.reportInit(String.format("Initializing collection %s.%s", database, collection));
        mongoColl = client.getDatabase(database).getCollection(collection);
        if (drop) {
            mongoColl.drop();
            reporter.reportInit(String.format("Dropped collection %s.%s", database, collection));
        }

        for (var field: fieldsToRemember) {
            List<Object> values = remembrances.get(field);

            for (var result : mongoColl.aggregate(Arrays.asList(
                new Document("$group", new Document("_id", String.format("$%s", field)))
            ))) {
                values.add(result.get("_id"));
            }
            reporter.reportInit(String.format("\tLoaded %d existing keys for field: %s", values.size(), field));
        }

        for (Document indexDef: indexes) {
            mongoColl.createIndex(indexDef);
        }
        reporter.reportInit(String.format("\tCreated %d indexes", indexes.size()));

        generators.put(template, _compile(template));
    }

    public Document generate() {
        var doc = generate(template);

        for (String field : fieldsToRemember) {
            remembrances.get(field).add(doc.get(field));
        }

        return doc;
    }

    public Document generate(Document from) {
        DocumentGenerator gen = generators.get(from);
        if (gen == null) {
            gen = _compile(from);
            generators.put(from, gen);
        }
        Document doc = gen.generateDocument();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Generated: {}", doc.toJson());
        }
        return doc;
    }



    /*
     * Compile a template into an internal "generator" structure
     */
    private DocumentGenerator _compile(Document current) {
        var generator = new DocumentGenerator();

        for (var entry: current.entrySet()) {
            generator.addSubgenerator(entry.getKey(), _traverseCompileValue(entry.getValue()));
        }

        return generator;
    }

    private Generator _traverseCompileValue(Object value) {
        if (value instanceof String) {
            String val = (String) value;
            if (val.startsWith("%")) {
                return _valueGenerator(val, new Document());
            } else if (val.startsWith("#")) {
                return new RemindingGenerator(remembrances.get(val.substring(1)));
            }
            else return ValueGenerators.constant(value);
        } else if (value instanceof Document) {
            var subdoc = (Document) value;
            // is it a template?
            if (_isExpression(subdoc)) {
                var x = subdoc.entrySet().iterator().next();
                return _valueGenerator((String) x.getKey(), (Document) x.getValue());
            } else {
                return _compile(subdoc);
            }
        } else if (value instanceof List<?>) {
            return new ListGenerator(
                ((List<Object>) value).stream().map(this::_traverseCompileValue).collect(Collectors.toList())
            );
        } else {
            return ValueGenerators.constant(value);
        }
    }

    private Generator _valueGenerator(String operator, Document params) {
        switch (operator) {
            case "%objectid": return ValueGenerators.objectId();
            case "%integer":
            case "%number":
                return ValueGenerators.integer(params.getInteger("min", Integer.MIN_VALUE), params.getInteger("max", Integer.MAX_VALUE));
            case "%natural": return ValueGenerators.integer(params.getInteger("min", 0), params.getInteger("max", Integer.MAX_VALUE));
            case "%sequence": return ValueGenerators.sequence();
            case "%now": return ValueGenerators.now();
            case "%date": return ValueGenerators.date(params);
            case "%binary": return ValueGenerators.binary(params);
            case "%uuidString": return ValueGenerators.uuidString();
            case "%uuidBinary": return ValueGenerators.uuidBinary();
            case "%array": 
                var of = params.get("of");
                if (of == null || ! (of instanceof Document)) {
                    LOGGER.warn("Parameter 'of' of array operator is invalid, ignoring");
                    of = new Document();
                }
                return ValueGenerators.array(params, _compile((Document)of));
            default: return ValueGenerators.autoFaker(operator);
        }
    }

    private boolean _isExpression(Document doc) {
        if (doc.size() == 1) {
            Object key = doc.keySet().iterator().next();
            if (key instanceof String && ((String) key).startsWith("%") ) {
                return true;
            } else return false;
        } else return false;
    }

}
