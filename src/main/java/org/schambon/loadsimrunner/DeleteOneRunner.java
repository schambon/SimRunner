package org.schambon.loadsimrunner;

import com.mongodb.client.result.DeleteResult;

import org.bson.Document;

public class DeleteOneRunner extends AbstractDeleteRunner {

    public DeleteOneRunner(WorkloadConfiguration workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected DeleteResult doDelete(Document filter) {
        return mongoColl.deleteOne(filter);
    }
    
}
