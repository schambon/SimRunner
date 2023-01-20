package org.schambon.loadsimrunner.runner;

import java.util.concurrent.ThreadLocalRandom;

import org.bson.Document;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindRunner extends AbstractRunner {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(FindRunner.class);

    public FindRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected long doRun() {
        Document filter = (Document) params.get("filter");
        filter = template.generate(filter);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Find filter: {}", filter);
        }

        var limit = params.getInteger("limit", -1);
        var skip = params.getBoolean("skip", false);
        var cursor = mongoColl.find(filter)
            .sort((Document) params.get("sort"))
            .projection((Document) params.get("project"));
        if (limit != -1) {
            cursor = cursor.limit(limit);
            if (skip) {
                cursor = cursor.skip(ThreadLocalRandom.current().nextInt(10) * limit);
            }
        }

        int count = 0;
        var start = System.currentTimeMillis();
        var iterator = cursor.iterator();

        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        var duration = System.currentTimeMillis() - start;

        reporter.reportOp(name, count, duration);
        return duration;
    }
    
 
}
