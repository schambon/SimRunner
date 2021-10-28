package org.schambon.loadsimrunner;

import org.bson.Document;

public class FindRunner extends AbstractRunner {
    
    public FindRunner(WorkloadConfiguration workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected long doRun() {
        Document filter = (Document) params.get("filter");
        filter = template.generate(filter);

        var limit = params.getInteger("limit", -1);
        var cursor = mongoColl.find(filter)
            .sort((Document) params.get("sort"))
            .projection((Document) params.get("project"));
        if (limit != -1) {
            cursor = cursor.limit(limit);
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
