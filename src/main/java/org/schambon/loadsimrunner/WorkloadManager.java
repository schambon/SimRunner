package org.schambon.loadsimrunner;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;

import org.bson.Document;
import org.schambon.loadsimrunner.errors.InvalidConfigException;
import org.schambon.loadsimrunner.report.Reporter;
import org.schambon.loadsimrunner.runner.AggregationRunner;
import org.schambon.loadsimrunner.runner.CustomRunner;
import org.schambon.loadsimrunner.runner.DeleteManyRunner;
import org.schambon.loadsimrunner.runner.DeleteOneRunner;
import org.schambon.loadsimrunner.runner.FindRunner;
import org.schambon.loadsimrunner.runner.InsertRunner;
import org.schambon.loadsimrunner.runner.ReplaceOneRunner;
import org.schambon.loadsimrunner.runner.ReplaceWithNewRunner;
import org.schambon.loadsimrunner.runner.TimeSeriesRunner;
import org.schambon.loadsimrunner.runner.UpdateManyRunner;
import org.schambon.loadsimrunner.runner.UpdateOneRunner;
import org.schambon.loadsimrunner.runner.WorkloadThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkloadManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkloadManager.class);

    String name;
    String op; 
    Document params = null;
    Document variables = null;
    int threads;
    int batch;
    int pace;
    ReadPreference readPreference;
    ReadConcern readConcern;
    WriteConcern writeConcern;
    long stopAfter = -1;

    Reporter reporter;

    private TemplateManager templateConfig;

    private MongoClient client;

    public static List<WorkloadManager> newInstances(Document config, Map<String, List<TemplateManager>> templatesByBaseName) {
        var templateBaseName = config.getString("template");
        var templates = templatesByBaseName.get(templateBaseName);
        return templates.stream().map( template -> new WorkloadManager(config, template)).collect(Collectors.toList());
    }

    private WorkloadManager(Document config, TemplateManager templateConfig) {
        var _name = config.getString("name");
        if (templateConfig.getInstance() == -1) {
            this.name = _name;
        } else {
            this.name = String.format("%s_%d", _name, templateConfig.getInstance());
        }

        this.templateConfig = templateConfig;
        this.op = config.getString("op");
        if (config.containsKey("params")) {
            this.params = (Document) config.get("params");
        } else {
            this.params = new Document();
        }
        this.variables = (Document) config.get("variables");
        this.threads = config.getInteger("threads", 1);
        this.batch =  config.getInteger("batch", 0);
        this.pace = config.getInteger("pace", 0);

        var _stopAfter = config.get("stopAfter");
        if (_stopAfter != null) {
            this.stopAfter = ((Number)_stopAfter).longValue();
        }
        LOGGER.debug("Workload {} stopping after {} (config: {})", name, stopAfter, _stopAfter);

        String readPref = config.getString("readPreference");
        if (readPref != null) {
            try {
                readPreference = ReadPreference.valueOf(readPref);
            } catch (IllegalArgumentException e) {
                throw new InvalidConfigException(String.format("%s is not a legal read preference", readPref));
            }
        }

        String readC = config.getString("readConcern");
        if (readC != null) {
            switch(readC.toLowerCase()) {
                case "local":
                readConcern = ReadConcern.LOCAL;
                break;
                case "majority":
                readConcern = ReadConcern.MAJORITY;
                break;
                case "available":
                readConcern = ReadConcern.AVAILABLE;
                break;
                case "linearizable":
                readConcern = ReadConcern.LINEARIZABLE;
                break;
                case "snapshot":
                readConcern = ReadConcern.SNAPSHOT;
                break;
                default:
                throw new InvalidConfigException(String.format("%s is not a legal read concern level", readC));
            }
        }

        String wc = config.getString("writeConcern");
        if (wc != null) {
            writeConcern = WriteConcern.valueOf(wc);
            if (writeConcern == null) {
                throw new InvalidConfigException(String.format("%s is not a valid write concern", wc));
            }
        }


        if (this.batch > 0) {
            if (! ("insert".equals(op) || "updateOne".equals(op) || "updateMany".equals(op) || "replaceWithNew".equals(op) || "timeseries".equals(op) )) {
                throw new InvalidConfigException("Op must be insert, update(One|Many), replaceWithNew, or timeseries for batch/bulk work");
            }
        }
    }

    public void initAndStart(MongoClient client, Reporter reporter) {
        this.client = client;
        this.reporter = reporter;

        LOGGER.info("Starting workload {}", name);

        for (var i = 0; i < threads; i++) {
            Thread thread = new WorkloadThread(name, i, getRunnable());
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
            case "timeseries": return new TimeSeriesRunner(this, reporter);
            case "custom": return new CustomRunner(this, reporter);
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

    public MongoClient getMongoClient() {
        return client;
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

    public Document getVariables() {
        return variables;
    }

    public ReadPreference getReadPreference() {
        return readPreference;
    }

    public ReadConcern getReadConcern() {
        return readConcern;
    }

    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    public long getStopAfter() {
        return stopAfter;
    }
}
