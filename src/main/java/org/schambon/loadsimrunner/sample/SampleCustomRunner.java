package org.schambon.loadsimrunner.sample;

import static com.mongodb.client.model.Filters.eq;
import static java.lang.System.currentTimeMillis;

import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;
import org.schambon.loadsimrunner.runner.AbstractRunner;

public class SampleCustomRunner extends AbstractRunner {

    public SampleCustomRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected long doRun() {
        var start = currentTimeMillis();
        var count = 0;
        for (var doc : mongoColl.find(eq("first", "John"))) {
            count++;
        }
        reporter.reportOp(name, count, currentTimeMillis() - start);
        return 0;
    }
    

}
