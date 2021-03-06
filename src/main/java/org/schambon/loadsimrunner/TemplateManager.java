package org.schambon.loadsimrunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import static com.mongodb.client.model.Filters.*;

import org.bson.Document;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class TemplateManager {

    private static final int DEFAULT_NUMBER_TO_PRELOAD = 1000000;

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateManager.class);

    private String database;
    private String collection;
    private boolean drop = false;

    private Document template;
    private Document createOptions;
    private Document variables;
    private Document dictionariesConfig;
    private Document shardingConfig;

    private Set<RememberField> fieldsToRemember = new HashSet<>();
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

        var rememberFields = (List<Object>) config.get("remember");
        if (rememberFields == null) {
            rememberFields = Collections.emptyList();
        }
        var remember = parseRememberFields(rememberFields);
        for (var rfield: remember) {
            this.fieldsToRemember.add(rfield);
            this.remembrances.put(rfield.name, Collections.synchronizedList(new ArrayList<>()));
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

        this.shardingConfig = (Document) config.get("sharding");
    }
    

    private static class RememberField {
        String field;
        boolean preload ;
        List<String> compound;
        String name;
        int number;

        public RememberField(String field, List<String> compound, String name, boolean preload, int number) {
            this.field = field;
            this.preload = preload;
            if (compound == null) {
                this.compound = Collections.emptyList();
            } else {
                this.compound = compound;
            }
            if (name == null) {
                this.name = field;
            } else {
                this.name = name;
            }
            this.number = number;
        }

        // compound trumps field, basically
        public boolean isSimple() {
            return compound.isEmpty();
        }
    }

    private List<RememberField> parseRememberFields(List<Object> input) {
        return input.stream().map(i -> {
            if (i instanceof Document) {
                var doc = (Document) i;
                return new RememberField(doc.getString("field"), doc.getList("compound", String.class), doc.getString("name"), doc.getBoolean("preload", true), doc.getInteger("number", DEFAULT_NUMBER_TO_PRELOAD));
            } else {
                return new RememberField((String)i, null, null, true, DEFAULT_NUMBER_TO_PRELOAD);
            }
        }).collect(Collectors.toList());
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
            // if we are sharded, do a delete all instead of a drop
            if (client.getDatabase("config").getCollection("collections").find(eq("_id", database + "." + collection)).first() != null) {
                reporter.reportInit(String.format("Collection %s.%s is sharded. Deleting all records instead of dropping (https://docs.mongodb.com/manual/reference/method/db.collection.drop)", database, collection));
                mongoColl.deleteMany(new Document());
                reporter.reportInit(String.format("Deleted all records from collection %s.%s", database, collection));
            } else {
                mongoColl.drop();
                reporter.reportInit(String.format("Dropped collection %s.%s", database, collection));
            }
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

        _preloadRememberedFields();

        for (Document indexDef: indexes) {
            mongoColl.createIndex(indexDef);
        }
        reporter.reportInit(String.format("\tCreated %d indexes", indexes.size()));

        _initializeDictionaries();
        reporter.reportInit(String.format("\tLoaded %d dictionaries", dictionaries.size()));

        _initializeSharding(client);

        generators.put(template, _compile(template));
    }

    private void _preloadRememberedFields() {
        for (var rfield: fieldsToRemember) {
            if (!rfield.preload) {
                reporter.reportInit(String.format("\tSkip preloading existing keys for field: %s", rfield.name));
                continue;
            }

            List<Object> values = remembrances.get(rfield.name);

            var pipeline = new ArrayList<Document>();

            if (rfield.isSimple()) {
                // load by field
                pipeline.add(new Document("$group", new Document("_id", String.format("$%s", rfield.field))));
            } else {
                var compoundKey = new Document();
                for (var key: rfield.compound) {
                    compoundKey.append(key.replace('.', '_'), String.format("$%s", key));
                }

                pipeline.add(new Document("$group",
                    new Document("_id", compoundKey)
                ));
            }

            pipeline.add( new Document("$limit", rfield.number));


            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Preload pipeline is {}",pipeline);
            }

            for (var result : mongoColl.aggregate(pipeline).allowDiskUse(true)) {
                values.add(result.get("_id"));
            }
            reporter.reportInit(String.format("\tLoaded %d existing keys for field: %s", values.size(), rfield.name));

            if (LOGGER.isDebugEnabled()) {
                for (var v: values) {
                    LOGGER.debug("-- {}", v.toString());
                }
            }
        }
    }

    private void _extractRememberedFields(Document doc) {
        for (var rfield: fieldsToRemember) {
            Object value;
            if (rfield.isSimple()) {
                if (rfield.field.contains(".")) {
                    value = Util.subdescend(doc, Arrays.asList(rfield.field.split("\\.")));
                } else {
                    value = doc.get(rfield.field);
                }

            } else {
                var cmp = new Document();
                for (var key: rfield.compound) {
                    cmp.append(key.replace('.', '_'), Util.subdescend(doc, Arrays.asList(key.split("\\."))));
                }
                value = cmp;
            }

            remembrances.get(rfield.name).add(value);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Extracted value: {} for remembered field {}", value, rfield.name);
            }
        }
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
    
    private void _initializeSharding(MongoClient client) {
        if (shardingConfig == null) {
            return;
        }

        var admindb = client.getDatabase("admin");
        var namespace = String.format("%s.%s", database, collection);

        // check if we are on a mongos
        var isMaster = admindb.runCommand(new Document("isMaster", 1));
        if (! "isdbgrid".equals(isMaster.getString("msg"))) {
            reporter.reportInit("Connection is not to a mongos; ignoring sharding configuration");
            return;
        }

        if (client.getDatabase("config").getCollection("collections").find(eq("_id", namespace)).first() != null) {
            reporter.reportInit("Collection is already sharded; skipping sharding configuration");
            return;
        }

        try {
            admindb.runCommand(new Document("enableSharding", database));
        } catch (MongoException e) {

            // can happen if the db is already enabled for sharding
            if (!e.getMessage().contains("already enabled")) {
                LOGGER.error("Cannot enable sharding", e);
                throw e;
            }
        }

        Document key = (Document)shardingConfig.get("key");
        if (key == null) {
            reporter.reportInit("No shard key found, skipping sharding configuration");
            return;
        }
        
        admindb.runCommand(new Document("shardCollection", namespace).append("key", key));
        reporter.reportInit(String.format("Sharded collection %s with key %s", namespace, key.toJson()));

        if (shardingConfig.get("presplit") != null) {
            List<Document> splitPoints = shardingConfig.getList("presplit", Document.class);

            // stop the balancer
            var balancerStatus = admindb.runCommand(new Document("balancerStatus", 1)).getString("mode");
            admindb.runCommand(new Document("balancerStop", 1));

            reporter.reportInit(String.format("Presplitting collection %s", namespace));
            // 1st pass - split chunks
            for (var point: splitPoints) {
                admindb.runCommand(new Document("split", namespace)
                    .append("middle", point.getEmbedded(List.of("point"), Document.class))
                );
            }

            // 2nd pass - move chunks
            for (var point: splitPoints) {
                admindb.runCommand(new Document("moveChunk", namespace)
                    .append("find", point.getEmbedded(List.of("point"), Document.class))
                    .append("to", point.getString("shard"))
                );
            }

            // restart the balancer if it was running before
            if (balancerStatus.equals("full")) {
                admindb.runCommand(new Document("balancerStart", 1));
            }

            reporter.reportInit(String.format("Collection %s pre-splitted", namespace));
        }
    }

    public List<Object> getRemindedValues(String key) {
        return remembrances.get(key);
    }

    public Document generate() {
        try {
            localVariables.set(generate(variables));

            var doc = generate(template);

            //for (RememberField field : fieldsToRemember) {
            //    remembrances.get(field.field).add(doc.get(field.field));
            //}
            _extractRememberedFields(doc);

            return doc;
        } finally {
            localVariables.remove();
        }
    }

    public void setVariables(Document variables) {
        if (variables != null) {
            localVariables.set(generate(variables)); 
        }
    }

    public Document getLocalVariables() {
        return localVariables.get();
    }

    public void clearVariables() {
        localVariables.remove();
    }
    
    public List<Document> generate(List<Document> from) {
        return from.stream().map(this::generate).collect(Collectors.toList());
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
            } else if (val.startsWith("#")) {
                return _hashGenerator(val.substring(1));
            } else if (val.startsWith("##")) { // this is for compatibility
                return () -> localVariables.get().get(val.substring(2));
            } /*else if (val.startsWith("#")) {
                return new RemindingGenerator(remembrances.get(val.substring(1)));
            }*/
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

    private Generator _hashGenerator(String key) {
        return () -> {
            var keys = Arrays.asList(key.split("\\."));

            var head = keys.get(0); // at least we are always assured it exists
            var tail = keys.subList(1, keys.size()); // may be empty

            // 1. dereference head
            Object resolved;
            // first check variables - note we may be defining variables so they don't exist yet!
            if (localVariables.get() != null && localVariables.get().containsKey(head)) {
                resolved = localVariables.get().get(head);
            } else if (remembrances.containsKey(head)) {
                var values = remembrances.get(head);
                if (values.size() == 0) {
                    resolved = null;
                } else {
                    resolved = values.get(ThreadLocalRandom.current().nextInt(values.size()));
                }
            } else if (dictionaries.containsKey(head)) {
                var values = dictionaries.get(head);
                if (values.size() == 0) {
                    resolved = null;
                } else {
                    resolved = values.get(ThreadLocalRandom.current().nextInt(values.size()));
                }
            } else {
                LOGGER.debug("Hash key not resolved: {}", head);
                return null;
            }

            // 2. descend
            if (resolved instanceof Document) {
                return Util.subdescend((Document) resolved, tail);
            } else {
                LOGGER.debug("Hash key not a document, but there is a descent: {}", head);
                return resolved;
            }
        };
    }

    private Generator _valueGenerator(String operator, DocumentGenerator params) {
        switch (operator) {
            case "%objectid": return ValueGenerators.objectId();
            case "%bool":
            case "%boolean":
             return ValueGenerators.bool();

            // numbers
            case "%integer":
            case "%number":
                return ValueGenerators.integer(params);
            case "%natural": return ValueGenerators.natural(params);
            case "%long": return ValueGenerators.longValue(params);
            case "%double": return ValueGenerators.doubleValue(params);
            case "%decimal": return ValueGenerators.decimal(params);
            case "%sequence": return ValueGenerators.sequence();
            case "%gaussian": return ValueGenerators.gaussian(params);
            case "%product": return ValueGenerators.product(params);
            case "%sum": return ValueGenerators.sum(params);
            case "%abs": return ValueGenerators.abs(params);

            // strings
            case "%stringConcat": return ValueGenerators.stringConcat(params);

            // dates
            case "%now": return ValueGenerators.now();
            case "%date": return ValueGenerators.date(params);
            case "%time": return ValueGenerators.time(params);
            case "%plusDate": return ValueGenerators.plusDate(params);
            case "%ceilDate": return ValueGenerators.ceilDate(params);
            case "%floorDate": return ValueGenerators.floorDate(params);

            case "%binary": return ValueGenerators.binary(params);
            case "%uuidString": return ValueGenerators.uuidString();
            case "%uuidBinary": return ValueGenerators.uuidBinary();
            case "%array": return ValueGenerators.array(params);
            case "%keyValueMap": return ValueGenerators.keyValueMap(params);

            // dictionary
            case "%dictionary": return ValueGenerators.dictionary(params, dictionaries);
            case "%dictionaryConcat": return ValueGenerators.dictionaryConcat(params, dictionaries);

            // geo
            case "%longlat": return ValueGenerators.longlat(params);
            case "%coordLine": return ValueGenerators.coordLine(params);

            case "%stringTemplate": return ValueGenerators.stringTemplate(params);
            case "%custom": return ValueGenerators.custom(params);

            // path descent
            case "%descend": return ValueGenerators.descend(params);

            // faker
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
