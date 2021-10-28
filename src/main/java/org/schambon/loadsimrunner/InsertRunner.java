package org.schambon.loadsimrunner;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

public class InsertRunner extends AbstractRunner {

    public InsertRunner(WorkloadConfiguration workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected long doRun() {
        return batch == 0 ? insertOne() : insertBatch();
    }
    private long insertOne() {
        Document doc = template.generate();
        long start = System.currentTimeMillis();
        mongoColl.insertOne(doc);
        long duration = System.currentTimeMillis() - start;
        reporter.reportOp(name, 1, duration);

        return duration;
    }

    private long insertBatch() {
        List<Document> docs = new ArrayList<>(batch);
        for (int i = 0; i < batch; i++) {
            docs.add(template.generate());
        }
        long start = System.currentTimeMillis();
        mongoColl.insertMany(docs);
        long duration = System.currentTimeMillis() - start;
        reporter.reportOp(name, batch, duration);

        return duration;
    }
}
