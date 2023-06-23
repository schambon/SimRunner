package org.schambon.loadsimrunner.runner;

import java.util.ArrayList;
import java.util.List;

import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.schambon.loadsimrunner.WorkloadManager;
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
        if (batch == 0) {
            return singleUpdate();
        } else {
            return bulkUpdate();
        }
    }

    private long singleUpdate() {
        var filter = (Document) params.get("filter");
        filter = template.generate(filter);

        var options = new UpdateOptions().upsert(params.getBoolean("upsert", false));

        // TODO handle arrayfilters, hint, etc.
        var update = params.get("update");
        if (update instanceof Document) {
            update = template.generate((Document) update);
        } else if (update instanceof List) {
            update = template.generate((List<Document>) update);
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



    private long bulkUpdate() {

        List operations = new ArrayList<>(batch);

        var options = new UpdateOptions().upsert(params.getBoolean("upsert", false));
        var filter = (Document) params.get("filter");
        var update = params.get("update");

        for (int i = 0; i < batch; i++) {
            var _f = template.generate(filter);
            UpdateOneModel<Document> model;
            if (update instanceof Document) {
                model = new UpdateOneModel<>(_f, template.generate((Document)update), options);
            } else if (update instanceof List) {
                model = new UpdateOneModel<>(_f, template.generate((List<Document>)update), options);
            } else {
                LOGGER.error("Invalid update definition");
                return 0;
            }
            operations.add(model);
        }
        var start = System.currentTimeMillis();
        var bulkWriteResult = mongoColl.bulkWrite(operations, new BulkWriteOptions().ordered(params.getBoolean("ordered", false)));
        long duration = System.currentTimeMillis() - start;
        //LOGGER.debug("Modified {}, upserted {}", bulkWriteResult.getModifiedCount(), bulkWriteResult.getUpserts().size());
        reporter.reportOp(name, batch, duration);
        return duration;
    }

    abstract protected UpdateResult doUpdate(Document filter, Document update, UpdateOptions options);
    abstract protected UpdateResult doUpdate(Document filter, List<Document> update, UpdateOptions options);
 
    abstract protected WriteModel<Document> updateModel(Document filter, Document update, UpdateOptions options);
    abstract protected WriteModel<Document> updateModel(Document filter, List<Document> update, UpdateOptions options);
   
}
