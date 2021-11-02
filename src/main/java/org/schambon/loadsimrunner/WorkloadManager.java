package org.schambon.loadsimrunner;

import java.util.Map;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.schambon.loadsimrunner.errors.InvalidConfigException;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkloadManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkloadManager.class);

    String name;
    String template;
    String op; 
    Document params = null;
    int threads;
    int batch;
    int pace;

    MongoCollection<Document> collection;

    Reporter reporter;

    private TemplateManager templateConfig;

    public WorkloadManager(Document config) {
        this.name = config.getString("name");
        this.template = config.getString("template");
        this.op = config.getString("op");
        if (config.containsKey("params")) {
            this.params = (Document) config.get("params");
        } else {
            this.params = new Document();
        }
        this.threads = config.getInteger("threads", 1);
        this.batch =  config.getInteger("batch", 0);
        this.pace = config.getInteger("pace", 0);

        if (this.batch > 0) {
            if (! "insert".equals(op)) {
                throw new InvalidConfigException("Op must be insert for batch work");
            }
        }
    }

    public void initAndStart(MongoClient client, Map<String, TemplateManager> templates, Reporter reporter) {
        this.collection = templates.get(template).getCollection();
        this.templateConfig = templates.get(template);
        this.reporter = reporter;

        for (var i = 0; i < threads; i++) {
            Thread thread = new Thread(getRunnable());
            thread.start();
        }
    }

    private Runnable getRunnable() {
        switch (op) {
            case "insert": return new InsertRunner(this, reporter);
            case "find": return new FindRunner(this, reporter);
            case "updateOne": return new UpdateOneRunner(this, reporter);
            case "updateMany": return new UpdateManyRunner(this, reporter);
            case "deleteOne": return new DeleteOneRunner(this, reporter);
            case "deleteMany": return new DeleteManyRunner(this, reporter);
            case "replaceOne": return new ReplaceOneRunner(this, reporter);
            case "replaceWithNew": return new ReplaceWithNewRunner(this, reporter);
            case "aggregate": return new AggregationRunner(this, reporter);
            default:
                LOGGER.warn("Not implemented (yet?)");
                return new Runnable() {

                    @Override
                    public void run() {
                        LOGGER.info("No-op task");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                    }
                    
                };
        }
    }

    public TemplateManager getTemplateConfig() {
        return templateConfig;
    }

    public int getPace() {
        return pace;
    }

    public int getBatch() {
        return batch;
    }

    public String getName() {
        return name;
    }

    public Document getParams() {
        return params;
    }
}
