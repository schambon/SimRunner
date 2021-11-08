package org.schambon.loadsimrunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;

import org.bson.Document;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class TemplateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateManager.class);

    private String database;
    private String collection;
    private boolean drop = false;

    private Document template;
    private Document createOptions;
    private Document variables;
    private Document dictionariesConfig;

    private Set<String> fieldsToRemember = new TreeSet<>();
    // using a synchronized list for remembrances because a set is too slow to get a random element out of
    private Map<String, List<Object>> remembrances = new TreeMap<>();
    private List<Document> indexes;

    private MongoCollection<Document> mongoColl = null;

    private Reporter reporter;

    // compiled generators cache
    private Map<Document, DocumentGenerator> generators = new HashMap<>();

    // thread local container for variables
    private static ThreadLocal<Document> localVariables = new ThreadLocal<>();

    // dictionaries
    private Map<String, List<? extends Object>> dictionaries = new TreeMap<>();

    public TemplateManager(Document config, Reporter reporter) {
        this.reporter = reporter;
        this.database = config.getString("database");
        this.collection = config.getString("collection");
        this.drop = config.getBoolean("drop", false);

        this.template = (Document) config.get("template");
        this.variables = (Document) config.get("variables");
        if (this.variables == null) {
            this.variables = new Document();
        }

        var remember = (List<String>) config.get("remember");
        if (remember == null) {
            remember = Collections.emptyList();
        }
        for (var field: remember) {
            this.fieldsToRemember.add(field);
            this.remembrances.put(field, Collections.synchronizedList(new ArrayList<>()));
        }

        if (config.containsKey("indexes")) {
            this.indexes = config.getList("indexes", Document.class);
        } else {
            this.indexes = Collections.emptyList();
        }

        if (config.containsKey("dictionaries")) {
            this.dictionariesConfig = (Document) config.get("dictionaries");
        } else {
            this.dictionariesConfig = new Document();
        }

        if (config.containsKey("createOptions")) {
            this.createOptions = (Document) config.get("createOptions");
        } else {
            this.createOptions = new Document();
        }
    }
    
    public MongoCollection<Document> getCollection() {
        return this.mongoColl;
    }

    public void initialize(MongoClient client) {
        reporter.reportInit(String.format("Initializing collection %s.%s", database, collection));


        var db = client.getDatabase(database);
        var found = false;
        for (var name : db.listCollectionNames()) {
            if (name.equals(collection)) {
                found = true;
                break;
            }
        }
        
        this.mongoColl = db.getCollection(collection);

        if (drop) {
            mongoColl.drop();
            reporter.reportInit(String.format("Dropped collection %s.%s", database, collection));
        }

        if (! found) {
            var options = new CreateCollectionOptions();
            options.capped(createOptions.getBoolean("capped", false));
            if (createOptions.containsKey("timeseries")) {
                var timeseries = (Document) createOptions.get("timeseries");
                if (createOptions.containsKey("expireAfterSeconds")) options.expireAfter(createOptions.getLong("expireAfterSeconds"), TimeUnit.SECONDS);
                var tsOptions = new TimeSeriesOptions(timeseries.getString("timeField"));
                if (timeseries.containsKey("granularity")) tsOptions.granularity(TimeSeriesGranularity.valueOf(timeseries.getString("granularity")));
                if (timeseries.containsKey("metaField")) tsOptions.metaField(timeseries.getString("metaField"));
                options.timeSeriesOptions(tsOptions);
            }
            if (createOptions.containsKey("size")) options.sizeInBytes(createOptions.getLong("size"));
            if (createOptions.containsKey("validator")) {
                ValidationOptions validationOptions = new ValidationOptions().validator((Document) createOptions.get("validator"));
                if (createOptions.containsKey("validationLevel")) validationOptions.validationLevel(ValidationLevel.valueOf(createOptions.getString("validationLevel")));
                if (createOptions.containsKey("validationAction")) validationOptions.validationAction(ValidationAction.valueOf(createOptions.getString("validationAction")));
                options.validationOptions(validationOptions);
            }

            db.createCollection(collection, options);
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

        _initializeDictionaries();
        reporter.reportInit(String.format("\tLoaded %d dictionaries", dictionaries.size()));

        generators.put(template, _compile(template));
    }

    private void _initializeDictionaries() {
        for (var entry: dictionariesConfig.entrySet()) {
            if (entry.getValue() instanceof List<?>) {
                dictionaries.put(entry.getKey(), (List<Object>)entry.getValue());
            } else if (entry.getValue() instanceof Document) {
                dictionaries.put(entry.getKey(), _loadDictionary((Document)entry.getValue()));
            } else {
                LOGGER.warn("Invalid dictionary config: {}", entry.getKey());
            }
        }
    }

    private List<? extends Object> _loadDictionary(Document config) {

        String type = config.getString("type");
        switch (type) {
            case "json": return _loadJSONDictionary(config);
            case "text": return _loadTextDictionary(config);
            default:
                LOGGER.warn("Cannot read dictionary of type: {}", type);
                return Collections.emptyList();
        }
    }

    private List<Object> _loadJSONDictionary(Document config) {
        try {
            var doc = Document.parse(Files.readString(Path.of(config.getString("file"))));
            return doc.getList("data", Object.class);
        } catch (IOException e) {
            LOGGER.error("Cannot read file", e);
            return Collections.emptyList();
        }
    }

    private List<String> _loadTextDictionary(Document config) {
        try {
            return Files.readAllLines(Path.of(config.getString("file")));
        } catch (IOException e) {
            LOGGER.error("Cannot read file", e);
            return Collections.emptyList();
        }
    }

    public Document generate() {
        try {
            localVariables.set(generate(variables));

            var doc = generate(template);

            for (String field : fieldsToRemember) {
                remembrances.get(field).add(doc.get(field));
            }

            return doc;
        } finally {
            localVariables.remove();
        }
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
                return _valueGenerator(val, new DocumentGenerator());
            } else if (val.startsWith("##")) {
                return () -> localVariables.get().get(val.substring(2));
            } else if (val.startsWith("#")) {
                return new RemindingGenerator(remembrances.get(val.substring(1)));
            }
            else return ValueGenerators.constant(value);
        } else if (value instanceof Document) {
            var subdoc = (Document) value;
            // is it a template?
            if (_isExpression(subdoc)) {
                var x = subdoc.entrySet().iterator().next();

                return _valueGenerator((String) x.getKey(), _compile((Document) x.getValue()));
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

    private Generator _valueGenerator(String operator, DocumentGenerator params) {
        switch (operator) {
            case "%objectid": return ValueGenerators.objectId();
            case "%integer":
            case "%number":
                return ValueGenerators.integer(params);
            case "%natural": return ValueGenerators.natural(params);
            case "%long": return ValueGenerators.longValue(params);
            case "%double": return ValueGenerators.doubleValue(params);
            case "%decimal": return ValueGenerators.decimal(params);
            case "%sequence": return ValueGenerators.sequence();
            case "%gaussian": return ValueGenerators.gaussian(params);
            case "%now": return ValueGenerators.now();
            case "%date": return ValueGenerators.date(params);
            case "%plusDate": return ValueGenerators.plusDate(params);
            case "%binary": return ValueGenerators.binary(params);
            case "%uuidString": return ValueGenerators.uuidString();
            case "%uuidBinary": return ValueGenerators.uuidBinary();
            case "%array": return ValueGenerators.array(params);
            case "%dictionary": return ValueGenerators.dictionary(params, dictionaries);
            case "%dictionaryConcat": return ValueGenerators.dictionaryConcat(params, dictionaries);
            case "%longlat": return ValueGenerators.longlat(params);
            case "%stringTemplate": return ValueGenerators.stringTemplate(params);
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
