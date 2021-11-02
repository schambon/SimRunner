package org.schambon.loadsimrunner;

import java.util.List;
import java.util.stream.Collectors;

import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractUpdateRunner extends AbstractRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractUpdateRunner.class);

    public AbstractUpdateRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected long doRun() {
        var filter = (Document) params.get("filter");
        filter = template.generate(filter);

        var options = new UpdateOptions().upsert(params.getBoolean("upsert", false));

        // TODO handle arrayfilters, hint, etc.
        var update = params.get("update");
        if (update instanceof Document) {
            update = template.generate((Document) update);
        } else if (update instanceof List) {
            update = ((List<Document>) update).stream().map(template::generate).collect(Collectors.toList());
        } else {
            LOGGER.error("Invalid update definition");
            return 0;
        }
        
        var start = System.currentTimeMillis();
        var updateResults = 
            update instanceof Document ? 
                doUpdate(filter, (Document) update, options) :
                doUpdate(filter, (List<Document>) update, options);

        var duration = System.currentTimeMillis() - start;

        reporter.reportOp(name, updateResults.getMatchedCount() + (updateResults.getUpsertedId() != null ? 1 : 0), duration);
        return duration;
    }

    abstract protected UpdateResult doUpdate(Document filter, Document update, UpdateOptions options);
    abstract protected UpdateResult doUpdate(Document filter, List<Document> update, UpdateOptions options);
    
}
