package org.schambon.loadsimrunner.runner;

import com.mongodb.client.result.DeleteResult;

import org.bson.Document;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;

public class DeleteOneRunner extends AbstractDeleteRunner {

    public DeleteOneRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected DeleteResult doDelete(Document filter) {
        return mongoColl.deleteOne(filter);
    }
    
}
