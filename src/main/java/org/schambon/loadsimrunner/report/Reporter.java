package org.schambon.loadsimrunner.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.math.Quantiles.percentiles;
import com.google.common.math.Stats;

public class Reporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Reporter.class);

    private volatile Map<String, StatsHolder> stats = null;
    private long startTime = 0;
    private TreeMap<Instant, Report> reports = new TreeMap<>();

    public void start() {
        stats = new TreeMap<>();
        startTime = System.currentTimeMillis();
    }

    public void reportInit(String message) {
        LOGGER.info(String.format("INIT: %s", message));
    }

    public void computeReport() {
        var oldStats = stats;
        long now = System.currentTimeMillis();
        long interval = now - startTime;
        startTime = now;

        stats = new TreeMap<>();

        Document reportDoc = new Document();
        for (var workload: oldStats.keySet()) {
            reportDoc.append(workload, oldStats.get(workload).compute(interval));
        }

        Instant reportInstant = Instant.ofEpochMilli(now);
        Report report = new Report(reportInstant, reportDoc);
        reports.put(reportInstant, report);

        LOGGER.info(report.toString());
    }

    public void reportOp(String name, long i, long duration) {
        LOGGER.debug("Reported {} {} {}", name, i, duration);
        StatsHolder h = stats.get(name);
        if (h == null) {
            h = new StatsHolder();
            stats.put(name, h);
        }
        h.addOp(i, duration);
    }

    public Collection<Report> getAllReports() {
        return reports.values();
    }

    public Collection<Report> getReportsSince(Instant start) {
        return reports.tailMap(start, false).values();
    }
    
    // a specific thread for logging durations
    static ExecutorService asyncExecutor = Executors.newFixedThreadPool(1);

    private static class StatsHolder {

        AtomicLong numops = new AtomicLong(0);
        List<Long> durationsBatch = new ArrayList<>();
        List<Long> numbers = new ArrayList<>();

        // Compute some statistics
        // interval is the overall duration
        public Document compute(long interval) {

            var percentilesBatch = percentiles().indexes(50,95).compute(durationsBatch);
            Stats batchStats = Stats.of(durationsBatch);
            var meanBatch = batchStats.mean();
            var util = 100. * batchStats.sum() / (double) interval;
            var numberStats = Stats.of(numbers);

            Document wlReport = new Document();
            wlReport.append("ops", (long) (numberStats.count() / (double) (interval/1000)));
            wlReport.append("records", (long) (numberStats.sum() / (double) (interval/1000)));
            wlReport.append("mean duration", meanBatch);
            wlReport.append("median duration", percentilesBatch.get(50));
            wlReport.append("95th percentile", percentilesBatch.get(95));
            wlReport.append("mean batch size", numberStats.mean());
            wlReport.append("min batch size", numberStats.min());
            wlReport.append("max batch size", numberStats.max());
            wlReport.append("client util", util);

            return wlReport;
        }

        public void addOp(long number, long duration) {
            numops.incrementAndGet();
            Reporter.asyncExecutor.submit(() ->  {durationsBatch.add(duration); numbers.add(number);});
        }
    }
}
