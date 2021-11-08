package org.schambon.loadsimrunner.runner;

import java.util.stream.Collectors;

import org.bson.Document;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;

public class AggregationRunner extends AbstractRunner {

    public AggregationRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected long doRun() {
        var pipeline = params.getList("pipeline", Document.class).stream()
            .map(template::generate)
            .collect(Collectors.toList());

        var start = System.currentTimeMillis();
        var i = 0;
        var iterator = mongoColl.aggregate(pipeline).iterator();
        while (iterator.hasNext()) {
            iterator.next();
            i++;
        }
        var duration = System.currentTimeMillis() - start;

        reporter.reportOp(name, i, duration);
        return duration;
        
    }
    
}
