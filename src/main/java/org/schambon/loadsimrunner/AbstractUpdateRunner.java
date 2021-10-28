package org.schambon.loadsimrunner;

import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;

public abstract class AbstractUpdateRunner extends AbstractRunner {

    public AbstractUpdateRunner(WorkloadConfiguration workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected long doRun() {
        var filter = (Document) params.get("filter");
        filter = template.generate(filter);

        // TODO handle expressive updates, arrayfilters, hint, etc.
        var update = (Document) params.get("update");
        update = template.generate(update);

        var options = new UpdateOptions().upsert(params.getBoolean("upsert", false));

        var start = System.currentTimeMillis();
        var updateResults = doUpdate(filter, update, options);
        var duration = System.currentTimeMillis() - start;

        reporter.reportOp(name, updateResults.getMatchedCount() + (updateResults.getUpsertedId() != null ? 1 : 0), duration);
        return duration;
    }

    abstract protected UpdateResult doUpdate(Document filter, Document update, UpdateOptions options);
    
}
