package org.schambon.loadsimrunner;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InsertRunner extends AbstractRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InsertRunner.class);

    public InsertRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
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
        var s = System.currentTimeMillis();
        List<Document> docs = new ArrayList<>(batch);
        for (int i = 0; i < batch; i++) {
            docs.add(template.generate());
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Generated batch of {} in {}ms", batch, System.currentTimeMillis()-s);
        }
        long start = System.currentTimeMillis();
        mongoColl.insertMany(docs);
        long duration = System.currentTimeMillis() - start;
        reporter.reportOp(name, batch, duration);

        return duration;
    }
}
