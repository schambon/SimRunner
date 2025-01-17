package org.schambon.loadsimrunner.report;

import static java.lang.System.currentTimeMillis;

import java.time.Duration;
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

import com.google.common.collect.TreeMultiset;
import com.google.common.math.Stats;

public class Reporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Reporter.class);

    private volatile Map<String, StatsHolder> stats = null;
    private long startTime = 0;
    private TreeMap<Instant, Report> reports = new TreeMap<>();
    private List<Integer> percentiles;

    public Reporter(List<Integer> reportPercentiles) {
        this.percentiles = reportPercentiles;
    }

    public void start() {
        stats = new TreeMap<>();
        startTime = System.currentTimeMillis();
    }

    public void reportInit(String message) {
        LOGGER.info(String.format("INIT: %s", message));
    }

    public void computeReport(List<? extends ReporterCallback> callbacks) {
        LOGGER.debug("Scheduling report compute");
        asyncExecutor.submit(() -> {
            try {
                LOGGER.debug("Running report compute");
                var oldStats = stats;
                long now = System.currentTimeMillis();
                long interval = now - startTime;
                startTime = now;
        
                stats = new TreeMap<>();
        
                Document reportDoc = new Document();
                for (var workload: oldStats.keySet()) {
                    Document computedStats = oldStats.get(workload).compute(interval, percentiles);
                    if (computedStats != null) {
                        reportDoc.append(workload, computedStats);
                    }
                }
        
                Instant reportInstant = Instant.ofEpochMilli(now);
                Report report = new Report(reportInstant, reportDoc);
                synchronized(this) {
                    reports.put(reportInstant, report);
                    // evict reports older than one hour
                    var oneHourAgo = reportInstant.minus(Duration.ofHours(1));
                    reports.headMap(oneHourAgo).clear();
                }
        
                LOGGER.info(report.toString());

                for (var cb : callbacks) {
                    cb.report(report);
                }

            } catch (Throwable t) {
                LOGGER.error("Error while computing report", t);
            }
        });
    }

    public synchronized void reportOp(String name, long i, long duration) {
        //LOGGER.debug("Reported {} {} {}", name, i, duration);
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

    public synchronized Collection<Report> getReportsSince(Instant start) {
        return reports.tailMap(start, false).values();
    }
    
    // a specific thread for logging durations
    static ExecutorService asyncExecutor = Executors.newFixedThreadPool(1);

    private static class StatsHolder {

        AtomicLong numops = new AtomicLong(0);
        // List<Long> durationsBatch = new ArrayList<>();
        TreeMultiset<Long> durationsBatch = TreeMultiset.create();
        List<Long> numbers = new ArrayList<>();

        // Compute some statistics
        // interval is the overall duration
        public Document compute(long interval, List<Integer> percentiles) {

            var __startCompute = currentTimeMillis();

            List<Long> durations = new ArrayList<>();
            durations.addAll(durationsBatch);

            List<Document> computedPercentiles = new ArrayList<>(percentiles.size());
            for (int _p : percentiles) {
                double p = (double)_p/100d;

                var index = (int)Math.ceil(p * (double)durations.size());
                if (index >= durations.size()) {
                    index = Math.max(0, durations.size() - 1);
                }
                long pctVal = durations.get(index);
                computedPercentiles.add(new Document("p", _p).append("value", pctVal));
            }

            // var ninetyFifthIndex = (int)Math.ceil(.95d * (double)durations.size());
            // if (ninetyFifthIndex >= durations.size()) {
            //     ninetyFifthIndex = Math.max(0, durations.size()-1);
            // }
            // long ninetyFifth = durations.get(ninetyFifthIndex);
            // long fiftieth = durations.size() > 1 ? durations.get(durations.size()/2) : 0;

            Stats batchStats = Stats.of(durations);
            var meanBatch = batchStats.mean();
            var util = 100. * batchStats.sum() / (double) interval;
            var numberStats = Stats.of(numbers);

            long totalOps = (long) (numberStats.count() /  ((double)interval/1000.d));
            // TODO fix this properly - there are times when the number goes through the roof, either because of an overflow or because `interval` is too small.
            if (totalOps > 1e10) {
                LOGGER.warn("Computed very large ops number {}. Count is {}, interval is {}", totalOps, numberStats.count(), interval);
                return null;
            }

            Document wlReport = new Document();

            wlReport.append("ops", totalOps);
            wlReport.append("records", (long) (numberStats.sum() / (double) (interval/1000)));
            wlReport.append("total ops", numberStats.count());
            wlReport.append("total records", (long)numberStats.sum());
            wlReport.append("mean duration", meanBatch);
            wlReport.append("percentiles", computedPercentiles);
            wlReport.append("mean batch size", numberStats.mean());
            wlReport.append("min batch size", numberStats.min());
            wlReport.append("max batch size", numberStats.max());
            wlReport.append("client util", util);
            wlReport.append("report compute time", currentTimeMillis() - __startCompute);

            return wlReport;

        }

        public void addOp(long number, long duration) {
            numops.incrementAndGet();
            Reporter.asyncExecutor.submit(() ->  {durationsBatch.add(duration); numbers.add(number);});
        }
    }
}
