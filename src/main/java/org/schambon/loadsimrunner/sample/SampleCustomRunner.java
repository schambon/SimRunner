package org.schambon.loadsimrunner.sample;

import static com.mongodb.client.model.Filters.eq;
import static java.lang.System.currentTimeMillis;

import com.mongodb.client.MongoClient;

import org.bson.Document;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;
import org.schambon.loadsimrunner.runner.AbstractRunner;

public class SampleCustomRunner extends AbstractRunner {

    private MongoClient mongoClient;

    public SampleCustomRunner(WorkloadManager workload, Reporter reporter) {
        super(workload, reporter);
        this.mongoClient = workload.getMongoClient();
    }

    @Override
    protected long doRun() {
        var start = currentTimeMillis();
        var count = 0;
        for (var doc : mongoColl.find(eq("first", "John"))) {
            count++;
        }

        // transaction sample
        var tempDB = mongoClient.getDatabase("temp");
        var coll1 = tempDB.getCollection("coll1");
        var coll2 = tempDB.getCollection("coll2");
        try (var session = mongoClient.startSession()) {
            session.withTransaction(() -> {
                var doc = new Document("hello", "world");
                coll1.insertOne(session, doc);
                coll2.insertOne(session, new Document("greeting", doc.getObjectId("_id")));
                return doc.getObjectId("_id");
            });
        }
        reporter.reportOp(name, count, currentTimeMillis() - start);
        return 0;
    }
    

}
