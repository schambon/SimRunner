package org.schambon.loadsimrunner.runner;

import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;

public class ReplaceWithNewRunner extends AbstractRunner {

    public ReplaceWithNewRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected long doRun() {
        if (batch == 0) {
            return singleReplace();
        } else {
            return bulkReplace();
        }
    }

    private long singleReplace() {
        var filter = (Document) params.get("filter");
        filter = template.generate(filter);

        var replace = template.generate();
        replace.remove("_id");

        var options = new ReplaceOptions().upsert(params.getBoolean("upsert", false));
        
        var start = System.currentTimeMillis();
        var replaceResults = mongoColl.replaceOne(filter, replace, options);
        var duration = System.currentTimeMillis() - start;

        reporter.reportOp(name, replaceResults.getModifiedCount() + (replaceResults.getUpsertedId() != null ? 1 : 0), duration);
        return duration;
    }
    
    private long bulkReplace() {
        List<ReplaceOneModel<Document>> operations = new ArrayList<>(batch);

        var options = new ReplaceOptions().upsert(params.getBoolean("upsert", false));
        var filter = (Document) params.get("filter");

        for (int i = 0; i < batch; i++) {
            var _f = template.generate(filter);
            ReplaceOneModel<Document> model;

            var replace = template.generate();
            replace.remove("_id");

            model = new ReplaceOneModel<Document>(_f, replace, options);

            operations.add(model);
        }

        var start = System.currentTimeMillis();
        var bulkWriteResult = mongoColl.bulkWrite(operations, new BulkWriteOptions().ordered(params.getBoolean("ordered", false)));
        var duration = System.currentTimeMillis() - start;

        reporter.reportOp(name, bulkWriteResult.getModifiedCount() + bulkWriteResult.getUpserts().size(), duration);
        return duration;
    }

}
