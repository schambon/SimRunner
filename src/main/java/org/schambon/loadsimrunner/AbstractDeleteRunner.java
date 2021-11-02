package org.schambon.loadsimrunner;

import com.mongodb.client.result.DeleteResult;

import org.bson.Document;
import org.schambon.loadsimrunner.report.Reporter;

public abstract class AbstractDeleteRunner extends AbstractRunner {

    public AbstractDeleteRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }
    
    @Override
    protected long doRun() {
        var filter = (Document) params.get("filter");
        filter = template.generate(filter);

        var start = System.currentTimeMillis();
        var deleteResult = doDelete(filter);
        var duration = System.currentTimeMillis() - start;

        reporter.reportOp(name, deleteResult.getDeletedCount(), duration);
        return duration;
    }

    abstract protected DeleteResult doDelete(Document filter);
    
}
