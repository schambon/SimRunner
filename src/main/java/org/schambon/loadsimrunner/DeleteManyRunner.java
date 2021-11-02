package org.schambon.loadsimrunner;

import com.mongodb.client.result.DeleteResult;

import org.bson.Document;
import org.schambon.loadsimrunner.report.Reporter;

public class DeleteManyRunner extends AbstractDeleteRunner {

    public DeleteManyRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected DeleteResult doDelete(Document filter) {
        return mongoColl.deleteMany(filter);
    }

}
