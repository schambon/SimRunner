package org.schambon.loadsimrunner.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.bson.Document;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;

import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;

public class BucketTimeSeriesRunner extends TimeSeriesRunner {

    int bucketSize = 100;
    String countField = "count";

    public BucketTimeSeriesRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);

        this.bucketSize = params.getInteger("bucketSize", 100);
        String _countField = params.getString("countField");
        if (_countField != null) this.countField = _countField;
    }

    @Override
    protected Callable<Void> addOneMeasure(Document doc) {

        return () -> {
            var _s = System.currentTimeMillis();

            mongoColl.updateOne(
                new Document(metaField, doc.remove(metaField)).append(countField, new Document("$lt", bucketSize)),
                new Document()
                    .append("$push", new Document("records", doc))
                    .append("$set", new Document("maxDate", doc.get(timeField)))
                    .append("$inc", new Document(countField, 1))
                    .append("$setOnInsert", new Document("minDate", doc.get(timeField))),
                new UpdateOptions().upsert(true)
            );

            reporter.reportOp(name, 1, System.currentTimeMillis() - _s);
            return null;
        };
    }

    @Override
    protected Callable<Void> addManyMeasures(final ArrayList<Document> docs) {
        return () -> {
            var _s = System.currentTimeMillis();

            List<WriteModel<Document>> ops = new ArrayList<>(docs.size());
            for (var d : docs) {
                UpdateOneModel<Document> op = new UpdateOneModel<>(
                    new Document(metaField, d.remove(metaField)).append(countField, new Document("$lt", bucketSize)),
                    new Document()
                        .append("$push", new Document("records", d))
                        .append("$set", new Document("maxDate", d.get(timeField)))
                        .append("$inc", new Document(countField, 1))
                        .append("$setOnInsert", new Document("minDate", d.get(timeField))),
                    new UpdateOptions().upsert(true));

                ops.add(op);
            }

            mongoColl.bulkWrite(ops);

            reporter.reportOp(name, docs.size(), System.currentTimeMillis() - _s);
            return null;
        };
    }

}
