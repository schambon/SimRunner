package org.schambon.loadsimrunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.schambon.loadsimrunner.errors.InvalidConfigException;
import org.schambon.loadsimrunner.http.HttpServer;
import org.schambon.loadsimrunner.report.MongoReporter;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimRunner.class);

    public static void main(String[] args) throws ParseException, IOException {
        var options = new Options();

        var parser = new DefaultParser();
        var line = parser.parse(options, args);

        var list = line.getArgList();
        if (list.size() < 1) {
            System.err.println("Usage: SimRunner <config file>");
            System.exit(1);
        }

        String configString = Files.readString(Path.of(list.get(0)));

        var config = Document.parse(configString);

        new SimRunner(config).start();
    }

    //////////// Fields /////////////
    Document config;
    MongoClient client;
    Map<String, List<TemplateManager>> templatesByBaseName = new HashMap<>();
    List<WorkloadManager> workloads = new ArrayList<>();

    Reporter reporter = new Reporter();
    HttpServer httpServer = null;

    int reportInterval = 1000;


    /////////// Implementation //////////

    private SimRunner(Document config) {
        this.config = config;
    }

    public void start() {
        validateConfig();
        for (var templates : templatesByBaseName.values()) {
            for (var template : templates) {
                template.initialize(client);
            }
        }

        var mongoReporter = new MongoReporter((Document) config.get("mongoReporter"));
        var reporterCallbacks = Collections.singletonList(mongoReporter);

        reporter.start(); // start the clock
        for (var workload: workloads) {
            workload.initAndStart(client, reporter);
        }

        if (httpServer != null) {
            try {
                httpServer.start();
            } catch (Exception e) {
                LOGGER.error("Cannot start HTTP server", e);
            }
           
        }
        
        while(true) {
            LOGGER.debug("Reporter waking up");
            try {
                Thread.sleep((long) reportInterval);
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted", e);
            }
            reporter.computeReport(reporterCallbacks);
        }
    }

    private void validateConfig() {
        try {
            if (config.getString("connectionString") == null) {
                throw new InvalidConfigException("Connection String not present");
            }
        } catch (ClassCastException t) {
            throw new InvalidConfigException("Invalid Connection String");
        }

        this.client = MongoClients.create(MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(config.getString("connectionString")))
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .build());

        Document commandResult = client.getDatabase("admin").runCommand(new Document("isMaster", 1));
        if (!commandResult.getBoolean("ismaster")) {
            throw new InvalidConfigException("Must connect to master");
        }

        if (config.get("templates") == null || ! (config.get("templates") instanceof List)) {
            throw new InvalidConfigException("Missing or invalid templates section");
        }
        for (var templateConfig: (List<Document>) config.get("templates")) {
            for (var template : TemplateManager.newInstances(templateConfig, reporter)) {
                var basename = template.getBaseName();
                if (templatesByBaseName.get(basename) == null) {
                    templatesByBaseName.put(basename, new LinkedList<TemplateManager>());
                }
                templatesByBaseName.get(basename).add(template);
            }
        }

        if (config.get("workloads") == null || !(config.get("workloads") instanceof List)) {
            throw new InvalidConfigException("Missing or invalid workloads section");
        }
        for (var workloadConfig: (List<Document>) config.get("workloads")) {
            if ((! workloadConfig.containsKey("disabled")) || (!(workloadConfig.getBoolean("disabled", false))))
                workloads.addAll(WorkloadManager.newInstances(workloadConfig, templatesByBaseName));
        }

        if (config.get("http") != null || (config.get("http") instanceof Document)) {
            this.httpServer = new HttpServer((Document) config.get("http"), reporter);
        }

        reportInterval = config.getInteger("reportInterval", 1000);
    }
}