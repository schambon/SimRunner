package org.schambon.loadsimrunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bson.Document;
import org.schambon.loadsimrunner.errors.InvalidConfigException;
import org.slf4j.LoggerFactory;

public class SimRunner {
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
    Map<String, TemplateManager> templates = new TreeMap<>();
    List<WorkloadManager> workloads = new ArrayList<>();

    Reporter reporter = new Reporter();

    /////////// Implementation //////////

    private SimRunner(Document config) {
        this.config = config;
    }

    public void start() {
        validateConfig();
        for (var entry: templates.entrySet()) {
            entry.getValue().initialize(client);
        }

        reporter.start(); // start the clock
        for (var workload: workloads) {
            workload.initAndStart(client, templates, reporter);
        }

        while(true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LoggerFactory.getLogger(SimRunner.class).warn("Interrupted", e);
            }
            reporter.printReport();
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
        this.client = MongoClients.create(config.getString("connectionString"));
        Document commandResult = client.getDatabase("admin").runCommand(new Document("isMaster", 1));
        if (!commandResult.getBoolean("ismaster")) {
            throw new InvalidConfigException("Must connect to master");
        }

        if (config.get("templates") == null || ! (config.get("templates") instanceof List)) {
            throw new InvalidConfigException("Missing or invalid templates section");
        }
        for (var templateConfig: (List<Document>) config.get("templates")) {
            var name = templateConfig.getString("name");
            templates.put(name, new TemplateManager(templateConfig, reporter));
        }

        if (config.get("workloads") == null || !(config.get("workloads") instanceof List)) {
            throw new InvalidConfigException("Missing or invalid workloads section");
        }
        for (var workloadConfig: (List<Document>) config.get("workloads")) {
            workloads.add(new WorkloadManager(workloadConfig));
        }
    }
}