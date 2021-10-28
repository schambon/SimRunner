package org.schambon.loadsimrunner;

import com.mongodb.client.model.ReplaceOptions;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplaceWithNewRunner extends AbstractRunner {

    private static Logger LOGGER = LoggerFactory.getLogger(ReplaceWithNewRunner.class);

    public ReplaceWithNewRunner(WorkloadConfiguration workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);
    }

    @Override
    protected long doRun() {
        var filter = (Document) params.get("filter");
        filter = template.generate(filter);

        var replace = template.generate(template.template); // TODO this smells
        replace.remove("_id");

        var options = new ReplaceOptions().upsert(params.getBoolean("upsert", false));
        
        var start = System.currentTimeMillis();
        var replaceResults = mongoColl.replaceOne(filter, replace, options);
        var duration = System.currentTimeMillis() - start;

        reporter.reportOp(name, replaceResults.getModifiedCount() + (replaceResults.getUpsertedId() != null ? 1 : 0), duration);
        return duration;
    }
    
}
