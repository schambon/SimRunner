package org.schambon.loadsimrunner;

import java.security.InvalidParameterException;
import java.util.List;

import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.schambon.loadsimrunner.report.Reporter;

public class ReplaceOneRunner extends AbstractUpdateRunner {

    public ReplaceOneRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected UpdateResult doUpdate(Document filter, Document update, UpdateOptions options) {
        var replaceOptions = new ReplaceOptions().upsert(options.isUpsert());
        update.remove("_id");
        return mongoColl.replaceOne(filter, update, replaceOptions);
    }

    @Override
    protected UpdateResult doUpdate(Document filter, List<Document> update, UpdateOptions options) {
        throw new InvalidParameterException("Cannot replace with expression");
    }

    
}
