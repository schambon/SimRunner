package org.schambon.loadsimrunner.runner;

import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.schambon.loadsimrunner.TemplateManager;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.LoggerFactory;

public abstract class AbstractRunner implements Runnable {

    protected TemplateManager template;
    protected MongoCollection<Document> mongoColl;
    protected int pace;
    protected int batch;
    protected Reporter reporter;
    protected String name;
    protected Document params;
    protected Document variables; // may be null!
    protected long stopAfter;

    protected long counter = 0;

    public AbstractRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        this.template = workloadConfiguration.getTemplateConfig();
        this.name = workloadConfiguration.getName();

        var collection = template.getCollection();

        if (workloadConfiguration.getReadPreference() != null) {
            collection = collection.withReadPreference(workloadConfiguration.getReadPreference());
        }
        if (workloadConfiguration.getReadConcern() != null) {
            collection = collection.withReadConcern(workloadConfiguration.getReadConcern());
        }
        if (workloadConfiguration.getWriteConcern() != null) {
            collection = collection.withWriteConcern(workloadConfiguration.getWriteConcern());
        }
        this.mongoColl = collection;

        this.pace = workloadConfiguration.getPace();
        this.batch = workloadConfiguration.getBatch();
        this.reporter = reporter;
        this.params = workloadConfiguration.getParams();
        this.variables = workloadConfiguration.getVariables();
        this.stopAfter = workloadConfiguration.getStopAfter();
    }

    @Override
    public void run() {
        var keepGoing = true;
        while (keepGoing) {
            try {
                ((WorkloadThread) Thread.currentThread()).setContextValue("iteration", Long.valueOf(counter));
                template.setVariables(variables);
                long duration = doRun();
                counter++;

                LoggerFactory.getLogger(getClass()).debug("Counter: {}, stopAfter: {}", counter, stopAfter);
                if (stopAfter > 0 && counter >= stopAfter) {
                    LoggerFactory.getLogger(getClass()).info("Workload {} stopping.", name);
                    keepGoing = false;
                }
                if (pace != 0) {
                    long wait = Math.max(0, pace - duration);
                    Thread.sleep(wait);
                }
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).error(String.format("Workload %s: Error caught in execution", name), e);
            } finally {
                template.clearVariables();
            } 
        }
        
    }
    
    protected abstract long doRun();
}
