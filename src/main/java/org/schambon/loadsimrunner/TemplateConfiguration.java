package org.schambon.loadsimrunner;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.github.javafaker.Faker;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class TemplateConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateConfiguration.class);

    String name;
    String database;
    String collection;
    boolean drop = false;

    Document template;

    private Set<String> fieldsToRemember = new TreeSet<>();
    // using a synchronized list for remembrances because a set is too slow to get a random element out of
    // private Map<String, ConcurrentSkipListSet<Object>> remembrances = new TreeMap<>();
    private Map<String, List<Object>> remembrances = new TreeMap<>();
    private List<Document> indexes;

    private MongoCollection<Document> mongoColl = null;

    private Faker faker = new Faker();

    public TemplateConfiguration(Document config) {
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

    public void initialize(MongoClient client, Reporter reporter) {
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
    }

    public Document generate() {
        var doc = generate(template);

        for (String field : fieldsToRemember) {
            remembrances.get(field).add(doc.get(field));
        }
        return doc;
    }

    public Document generate(Document from) {
        Document doc = _generate(from);
        // if (LOGGER.isDebugEnabled()) {
        //     LOGGER.debug("Generated: {}", doc.toJson());
        // }
        return doc;
    }

    private Object _randomRememberedField(String field) {
        var values = remembrances.get(field);
        int itemNum = ThreadLocalRandom.current().nextInt(values.size());

        return values.get(itemNum);     
    }

    private Document _generate(Document current) {
        Document doc = new Document();

        for (var entry: current.entrySet()) {
            doc.append(entry.getKey(), _traverseValue(entry.getValue()));
        }
 
        return doc;
    }

    private Object _traverseValue(Object value) {
        if (value instanceof String) {
            String val = (String) value;
            if (val.startsWith("%")) {
                return _generateValue(val, new Document());
            } else if (val.startsWith("#")) {
                return _randomRememberedField(val.substring(1));
            }
            else return value;
        } else if (value instanceof Document) {
            var subdoc = (Document) value;
            // is it a template?
            if (_isExpression(subdoc)) {
                var x = subdoc.entrySet().iterator().next();
                return _generateValue((String) x.getKey(), (Document) x.getValue());
            } else {
                return _generate(subdoc);
            }
        } else if (value instanceof List) {
            return ((List) value).stream().map(this::_traverseValue).collect(Collectors.toList());
        } else {
            return value;
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

    private Object _generateValue(String operator, Document params) {
        switch (operator) {
            case "%objectid": return new ObjectId();
            case "%integer":
            case "%number":
                return faker.number().numberBetween(params.getInteger("min", Integer.MIN_VALUE), params.getInteger("max", Integer.MAX_VALUE));
            case "%natural": return faker.number().numberBetween(params.getInteger("min", 0), params.getInteger("max", Integer.MAX_VALUE));
            case "%now": return Instant.now();
            case "%date": return _date(params);
            case "%binary": return _binary(params);
            default: return _autoFaker(operator);
        }
    }

    private Object _autoFaker(String operator) {
        String[] split = operator.substring(1).split("\\.");
        
        Object object = faker;
        try {
            for (String s : split) {
                var method = object.getClass().getMethod(s);
                object = method.invoke(object);
            }
            return object;
        } catch (NoSuchMethodException e) {
            LOGGER.warn("Cannot map operator {}", operator);
        } catch (InvocationTargetException | IllegalAccessException e) {
            LOGGER.warn("Cannot call faker method", e);
        }
        
        return operator;
    }

    private Date _date(Document params) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_INSTANT;
        Date from;

        Object min = params.get("min");
        if (min instanceof String) {
            String minString = (String) min;
            from = Date.from(Instant.from(dateTimeFormatter.parse(minString)));
        } else if (min instanceof Date) {
            from = (Date) min;
        } else {
            from = Date.from(Instant.ofEpochMilli(0));
        }

        Date to;
        Object max = params.get("max");
        if (max instanceof String) {
            String maxString = (String) max;
            to = Date.from(Instant.from(dateTimeFormatter.parse(maxString)));
        } else if (max instanceof Date) {
            to = (Date) max;
        } else  {
            to = Date.from(Instant.now().plus(3650, ChronoUnit.DAYS));
        }

        return faker.date().between(from, to);
    }

    private byte[] _binary(Document params) {
        var size = params.getInteger("size", 512);
        var bytes = new byte[size];

        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }

}
