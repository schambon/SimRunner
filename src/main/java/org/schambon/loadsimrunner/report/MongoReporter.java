package org.schambon.loadsimrunner.report;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.bson.Document;
import org.schambon.loadsimrunner.errors.InvalidConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;

public class MongoReporter implements ReporterCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoReporter.class);
    
    boolean initialized = false;

    MongoCollection<Document> collection;
    String runIdentifier;

    public MongoReporter(Document config) {
        if (config == null) {
            initialized = false;
            // nothing to initialize, let it go
        } else {

            var enabled = config.getBoolean("enabled", false);

            if (enabled) {
                var connstring = config.getString("connectionString");
                var database = config.getString("database");
                var collectionName = config.getString("collection");
                var drop = config.getBoolean("drop", false);
                var runtimeSuffix = config.getBoolean("runtimeSuffix", false);
                runIdentifier = config.getString("runIdentifier");

                if (connstring == null || database == null || collectionName == null) {
                    LOGGER.error("connectionString, database and collection are mandatory parameters for mongoReporter");
                    System.exit(1);
                }

                if (connstring.startsWith("$")) {
                    var envVariable = connstring.substring(1);
                    var envConnectionString = System.getenv(envVariable);
                    if (envConnectionString == null) {
                        throw new InvalidConfigException("Can not resolve connection string from environment variable: [" + envConnectionString + "]");
                    }
                    connstring = envConnectionString;
                }

                if (runtimeSuffix) {
                    var formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneId.of("Z"));
                    collectionName = collectionName + "_" + formatter.format(Instant.now());
                }

                var exportClient = MongoClients.create(connstring);
                var db = exportClient.getDatabase(database);
                collection = db.getCollection(collectionName);

                if (drop) {
                    collection.drop();
                    LOGGER.info("Dropped collection {}.{} for result export", database, collectionName);
                }

                var exists = false;
                for(var name: db.listCollectionNames()) {
                    if (name.equals(collectionName)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    db.createCollection(collectionName, new CreateCollectionOptions().timeSeriesOptions(
                        new TimeSeriesOptions("time").metaField("test").granularity(TimeSeriesGranularity.SECONDS)
                    ));
                }

                initialized = true;
            }
        }
    }

    @Override
    public void report(Report report) {
        if (initialized) {
            for (var task : report.getReport().keySet()) {
               
                var meta = new Document("task", task);
                if (runIdentifier != null) {
                    meta.append("runIdentifier", runIdentifier);
                }
                collection.insertOne(
                    new Document("time", report.getTime())
                        .append("test", meta)
                        .append("measures", report.getReport().get(task))
                );
            }
        }
    }
}
