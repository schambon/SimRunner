package org.schambon.loadsimrunner.runner;

import java.util.ArrayList;
import java.util.List;

import com.mongodb.client.model.InsertManyOptions;

import org.bson.Document;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;
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

        var refreshVariables = ("operation".equals(variablesScope)) ? true : false;

        for (int i = 0; i < batch; i++) {
            if(refreshVariables){
                template.setVariables(variables);
            }

            docs.add(template.generate());
            
            if(refreshVariables){
                template.clearVariables();
            }
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Generated batch of {} in {}ms", batch, System.currentTimeMillis()-s);
        }
        long start = System.currentTimeMillis();
        mongoColl.insertMany(docs, new InsertManyOptions().ordered(params.getBoolean("ordered", false)));
        long duration = System.currentTimeMillis() - start;
        reporter.reportOp(name, batch, duration);

        return duration;
    }
}
