package org.schambon.loadsimrunner;

import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.slf4j.LoggerFactory;

public abstract class AbstractRunner implements Runnable {

    protected TemplateManager template;
    protected MongoCollection<Document> mongoColl;
    protected int pace;
    protected int batch;
    protected Reporter reporter;
    protected String name;
    protected Document params;

    public AbstractRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        this.template = workloadConfiguration.getTemplateConfig();
        this.name = workloadConfiguration.getName();
        this.mongoColl = template.getCollection();
        this.pace = workloadConfiguration.getPace();
        this.batch = workloadConfiguration.getBatch();
        this.reporter = reporter;
        this.params = workloadConfiguration.getParams();
    }

    @Override
    public void run() {
        while (true) {
            long duration = doRun();

            if (pace != 0) {
                long wait = Math.max(0, pace - duration);
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    LoggerFactory.getLogger(getClass()).error("Oops", e);
                }
            }
        }
        
    }
    
    protected abstract long doRun();
}
