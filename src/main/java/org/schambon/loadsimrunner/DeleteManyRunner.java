package org.schambon.loadsimrunner;

import com.mongodb.client.result.DeleteResult;

import org.bson.Document;

public class DeleteManyRunner extends AbstractDeleteRunner {

    public DeleteManyRunner(WorkloadConfiguration workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected DeleteResult doDelete(Document filter) {
        return mongoColl.deleteMany(filter);
    }

}
