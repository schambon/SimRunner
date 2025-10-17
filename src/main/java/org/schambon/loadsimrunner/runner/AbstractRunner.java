package org.schambon.loadsimrunner.runner;

import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.schambon.loadsimrunner.TemplateManager;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRunner implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRunner.class);

    protected TemplateManager template;
    protected MongoCollection<Document> mongoColl;
    protected int pace;
    protected int batch;
    protected Reporter reporter;
    protected String name;
    protected Document params;
    protected Document variables; // may be null!
    protected long stopAfter;
    protected long stopAfterDuration;
    protected long startAfterDuration;
    protected String variablesScope;

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
        this.stopAfterDuration = workloadConfiguration.getStopAfterDuration();
        this.startAfterDuration = workloadConfiguration.getStartAfterDuration();
        this.variablesScope = workloadConfiguration.getVariablesScope();
    }

    @Override
    public void run() {
        var keepGoing = true;
        long totalDuration = 0;

        if(this.startAfterDuration >0){
            try {
                Thread.sleep(this.startAfterDuration);
            } catch (InterruptedException e) {
                LOGGER.error(String.format("Workload %s: Error caught in execution", name), e);
            }
        }

        while (keepGoing) {
            long duration = 0;
            try {
                ((WorkloadThread) Thread.currentThread()).setContextValue("iteration", Long.valueOf(counter));
                template.setVariables(variables);
                duration = doRun();
                totalDuration += duration;
                counter++;

                LOGGER.debug("Counter: {}, stopAfter: {}", counter, stopAfter);
                LOGGER.debug("Duration: {}, stopAfterDuration: {}", totalDuration, stopAfterDuration);
                if (stopAfter > 0 && counter >= stopAfter) {
                    LOGGER.info("Workload {} stopping.", name);
                    keepGoing = false;
                }
                if (stopAfterDuration > 0 && totalDuration >= stopAfterDuration) {
                    LOGGER.info("Workload {} stopping.", name);
                    keepGoing = false;
                }
            } catch (Exception e) {
                LOGGER.error(String.format("Workload %s: Error caught in execution", name), e);
            } finally {
                template.clearVariables();
            } 

            if (pace != 0) {
                long wait = Math.max(0, pace - duration);
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                }
            }
        }
        
    }
    
    protected abstract long doRun();
}
