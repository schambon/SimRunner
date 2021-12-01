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

    public AbstractRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        this.template = workloadConfiguration.getTemplateConfig();
        this.name = workloadConfiguration.getName();
        this.mongoColl = template.getCollection();
        this.pace = workloadConfiguration.getPace();
        this.batch = workloadConfiguration.getBatch();
        this.reporter = reporter;
        this.params = workloadConfiguration.getParams();
        this.variables = workloadConfiguration.getVariables();
    }

    @Override
    public void run() {
        while (true) {
            try {
                template.setVariables(variables);
                long duration = doRun();
                if (pace != 0) {
                    long wait = Math.max(0, pace - duration);
                    Thread.sleep(wait);
                }
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).error("Error caught in execution", e);
            } finally {
                template.clearVariables();
            } 
        }
        
    }
    
    protected abstract long doRun();
}
