package org.schambon.loadsimrunner.runner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.NotImplementedException;
import org.bson.Document;
import org.schambon.loadsimrunner.WorkloadManager;
import org.schambon.loadsimrunner.errors.InvalidConfigException;
import org.schambon.loadsimrunner.report.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.InsertManyOptions;

public class TimeSeriesRunner extends AbstractRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeSeriesRunner.class);

    Instant currentTime = null;
    ExecutorService exec = null;

    Document timeConfig;
    Document metaConfig;

    public TimeSeriesRunner(WorkloadManager workloadConfiguration, Reporter reporter) {
        super(workloadConfiguration, reporter);

        this.timeConfig = (Document) params.get("time");
        if (timeConfig.containsKey("start")) {
            currentTime =  ((Date) timeConfig.get("start")).toInstant();
        }

        this.metaConfig = (Document) params.get("meta");

        var workers = params.getInteger("workers", 1);
        exec = Executors.newFixedThreadPool(workers);
    }

    @Override
    protected long doRun() {
        LOGGER.debug("Timeseries runner {} waking up", name);

        Instant base = null;
        if (timeConfig.containsKey("value")) {
            var value = template.generateExpression(timeConfig.get("value"));
            if (value instanceof Date) {
                base = ((Date)value).toInstant();
            } else if (value instanceof Long) {
                base = Instant.ofEpochMilli((Long)value);
            } else {
                LOGGER.info("Time value resolved to {}, ignoring", value.getClass().getName());
            }
        }
        if (base == null) {
            if (currentTime != null) {
                long step;
                var stepC = timeConfig.get("step");
                if (stepC instanceof Number) {
                    step = ((Number)stepC).longValue();
                } else {
                    LOGGER.info("Step resolved to {}, defaulting to 1000ms", stepC);
                    step = 1000l;
                }

                currentTime = currentTime.plus(step, ChronoUnit.MILLIS);
                base = currentTime;
            } else {
                LOGGER.error("Invalid timeseries configuration, no start/step and no value. Aborting");
                throw new InvalidConfigException("Invalid timeseries configuration, no start/step and no value");
            }
        } 

        if (timeConfig.containsKey("stop")) {
            var stop = ((Date)timeConfig.get("stop")).toInstant();

            if (base.isAfter(stop)) {
                LOGGER.info("TimeSeriesRunner {} has run beyond stop date", name);
                return 0;
            }
        }

        // now we have a base
        
        var allSeries = template.dictionary(metaConfig.getString("dictionary"));
        if (allSeries == null) {
            LOGGER.error("Series dictionary {} not found", metaConfig.getString("dictionary"));
            throw new InvalidConfigException("Series dictionary");
        }

        List<?> series;
        var generateOption = metaConfig.get("generate");
        if (generateOption == null || "all".equals(generateOption)) {
            series = allSeries;
        } else if (isDocKey("random", generateOption)) {
            var rnd = template.generateExpression(((Document)generateOption).get("random"));
            if (!(rnd instanceof Integer)) {
                throw new InvalidConfigException("generate: {random: xxx} should evaluate to an integer");
            }
            series = randomSubList(allSeries, (int) rnd);
        } else {
            throw new NotImplementedException("Timeseries generation option {} not supported", generateOption.toString());
        }

        List<Callable<Void>> tasks = new ArrayList<>();

        if (batch <= 0) {
            for (var metaVal: series) {
                var doc = getDoc(base, metaVal);

                tasks.add(() -> {
                    var _s = System.currentTimeMillis();
                    mongoColl.insertOne(doc);
                    reporter.reportOp(name, 1, System.currentTimeMillis() - _s);
                    return null;
                });
            }
        } else {
            var docs = new ArrayList<Document>(batch);
            for (var metaVal: series) {
                docs.add(getDoc(base, metaVal));
                if (docs.size() == batch) {
                    final var _docs = new ArrayList<>(docs); // make a final copy
                    final var _batch = batch;
                    tasks.add(() -> {
                        var _s = System.currentTimeMillis();
                        mongoColl.insertMany(_docs, new InsertManyOptions().ordered(false));
                        reporter.reportOp(name, _batch, System.currentTimeMillis() - _s);
                        return null;
                    });
                    docs.clear();
                }
            }

            if (docs.size() > 0) {
                final var _docs = new ArrayList<>(docs);
                tasks.add(() -> {
                var _s = System.currentTimeMillis();
                mongoColl.insertMany(_docs, new InsertManyOptions().ordered(false));
                reporter.reportOp(name, _docs.size(), System.currentTimeMillis() - _s);
                return null;
            });
            }
            
        }


        try {
            var _s = System.currentTimeMillis();
            List<Future<Void>> futures = exec.invokeAll(tasks);
            for (var f : futures) {
                f.get();
            }
            return System.currentTimeMillis() - _s;
        } catch (InterruptedException|ExecutionException e) {
            LOGGER.error("Interrupted", e);
            throw new RuntimeException(e);
        }

    }

    private Document getDoc(Instant base, Object metaVal) {
        Instant ts;
        if (timeConfig.containsKey("jitter") && timeConfig.get("jitter") instanceof Number) {
            long maxJitter = ((Number) timeConfig.get("jitter")).longValue();
            long jitter = ThreadLocalRandom.current().nextLong(maxJitter);
            long flip = ThreadLocalRandom.current().nextInt(2);
            if (flip == 0) {
                ts = base.plus(jitter, ChronoUnit.MILLIS);
            } else {
                ts = base.minus(jitter, ChronoUnit.MILLIS);
            }
        } else {
            ts = base;
        }

        var doc = template.generate();
        doc.append(metaConfig.getString("metaField"), metaVal);
        doc.append(timeConfig.getString("timeField"), ts);
        return doc;
    }
    
    static boolean isDocKey(String key, Object test) {
        return test instanceof Document && ((Document)test).containsKey(key);
    }

    static List<?> randomSubList(List<?> source, int number) {
        var result = new ArrayList<>(number);

        var indices = new ArrayList<Integer>();

        var rnd = ThreadLocalRandom.current();

        while (result.size() < number) {
            var i = rnd.nextInt(source.size());
            while (indices.contains(i)) {
                i = rnd.nextInt(source.size());
            }
            result.add(source.get(i));
        }

        return result;
    }
}
