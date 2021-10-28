package org.schambon.loadsimrunner;

import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;

public class ReplaceOneRunner extends AbstractUpdateRunner {

    public ReplaceOneRunner(WorkloadConfiguration workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected UpdateResult doUpdate(Document filter, Document update, UpdateOptions options) {
        var replaceOptions = new ReplaceOptions().upsert(options.isUpsert());
        update.remove("_id");
        return mongoColl.replaceOne(filter, update, replaceOptions);
    }

    
}
