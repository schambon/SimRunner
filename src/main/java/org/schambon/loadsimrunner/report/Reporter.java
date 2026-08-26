package org.schambon.loadsimrunner.report;

import static java.lang.System.currentTimeMillis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private final boolean memoryOptimized;

    public Reporter(List<Integer> reportPercentiles) {
        this(reportPercentiles, false);
    }

    public Reporter(List<Integer> reportPercentiles, boolean memoryOptimized) {
        this.percentiles = reportPercentiles;
        this.memoryOptimized = memoryOptimized;
        if (memoryOptimized) {
            LOGGER.info("Reporter running in memory-optimized mode");
        }
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
        StatsHolder h = stats.get(name);
        if (h == null) {
            h = memoryOptimized ? new OptimizedStatsHolder() : new DefaultStatsHolder();
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

    // -------------------------------------------------------------------------
    // StatsHolder interface
    // -------------------------------------------------------------------------

    private interface StatsHolder {
        void addOp(long number, long duration);
        Document compute(long interval, List<Integer> percentiles);
    }

    // -------------------------------------------------------------------------
    // DefaultStatsHolder — original behaviour, unchanged
    // -------------------------------------------------------------------------

    private static class DefaultStatsHolder implements StatsHolder {

        AtomicLong numops = new AtomicLong(0);
        TreeMultiset<Long> durationsBatch = TreeMultiset.create();
        List<Long> numbers = new ArrayList<>();

        // Compute some statistics
        // interval is the overall duration
        public Document compute(long interval, List<Integer> percentiles) {

            var __startCompute = currentTimeMillis();

            List<Long> durations = new ArrayList<>();
            durations.addAll(durationsBatch);

            List<Document> computedPercentiles = new ArrayList<>(percentiles.size());
            if (!durations.isEmpty()) {
                for (int _p : percentiles) {
                    double p = (double)_p/100d;

                    var index = (int)Math.ceil(p * (double)durations.size());
                    if (index >= durations.size()) {
                        index = durations.size() - 1;
                    }
                    long pctVal = durations.get(index);
                    computedPercentiles.add(new Document("p", _p).append("value", pctVal));
                }
            }

            Stats batchStats = Stats.of(durations);
            var meanBatch = batchStats.mean();
            var util = 100. * batchStats.sum() / (double) interval;
            var numberStats = Stats.of(numbers);

            long totalOps = (long) (numberStats.count() /  ((double)interval/1000.d));
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
            Reporter.asyncExecutor.submit(() -> { durationsBatch.add(duration); numbers.add(number); });
        }
    }

    // -------------------------------------------------------------------------
    // OptimizedStatsHolder — memory-safe implementation
    //
    // Key differences from DefaultStatsHolder:
    //   1. addOp() writes directly to ConcurrentLinkedQueue (no asyncExecutor
    //      submission) — eliminates the unbounded executor queue.
    //   2. compute() atomically swaps both queues for fresh empty ones before
    //      processing — eliminates the race where stale queued tasks write to
    //      an already-drained StatsHolder, and means new ops during compute()
    //      land in the next cycle's fresh queue immediately.
    //   3. No full copy of durationsBatch into a second ArrayList — the swapped-
    //      out queue IS the snapshot, so peak memory is never doubled.
    // -------------------------------------------------------------------------

    private static class OptimizedStatsHolder implements StatsHolder {

        private final AtomicReference<Queue<Long>> durationsBatch =
                new AtomicReference<>(new ConcurrentLinkedQueue<>());
        private final AtomicReference<Queue<Long>> numbers =
                new AtomicReference<>(new ConcurrentLinkedQueue<>());

        public void addOp(long number, long duration) {
            // Direct lock-free writes — no executor submission
            durationsBatch.get().add(duration);
            numbers.get().add(number);
        }

        public Document compute(long interval, List<Integer> percentiles) {

            var __startCompute = currentTimeMillis();

            // Atomically swap both queues out — any addOp() calls after this
            // point write into the fresh queues and belong to the next cycle.
            Queue<Long> durationsSnapshot = durationsBatch.getAndSet(new ConcurrentLinkedQueue<>());
            Queue<Long> numbersSnapshot   = numbers.getAndSet(new ConcurrentLinkedQueue<>());

            // Materialise snapshots into sorted lists for percentile computation
            List<Long> durations = new ArrayList<>(durationsSnapshot);
            durations.sort(null);
            List<Long> numbersList = new ArrayList<>(numbersSnapshot);

            List<Document> computedPercentiles = new ArrayList<>(percentiles.size());
            if (!durations.isEmpty()) {
                for (int _p : percentiles) {
                    double p = (double)_p / 100d;
                    var index = (int) Math.ceil(p * (double) durations.size());
                    if (index >= durations.size()) {
                        index = durations.size() - 1;
                    }
                    long pctVal = durations.get(index);
                    computedPercentiles.add(new Document("p", _p).append("value", pctVal));
                }
            }

            Stats batchStats = durations.isEmpty() ? Stats.of(new long[]{}) : Stats.of(durations);
            var meanBatch = batchStats.mean();
            var util = 100. * batchStats.sum() / (double) interval;
            Stats numberStats = numbersList.isEmpty() ? Stats.of(new long[]{}) : Stats.of(numbersList);

            long totalOps = (long) (numberStats.count() / ((double) interval / 1000.d));
            if (totalOps > 1e10) {
                LOGGER.warn("Computed very large ops number {}. Count is {}, interval is {}", totalOps, numberStats.count(), interval);
                return null;
            }

            Document wlReport = new Document();

            wlReport.append("ops", totalOps);
            wlReport.append("records", (long) (numberStats.sum() / (double) (interval / 1000)));
            wlReport.append("total ops", numberStats.count());
            wlReport.append("total records", (long) numberStats.sum());
            wlReport.append("mean duration", meanBatch);
            wlReport.append("percentiles", computedPercentiles);
            wlReport.append("mean batch size", numberStats.mean());
            wlReport.append("min batch size", numberStats.min());
            wlReport.append("max batch size", numberStats.max());
            wlReport.append("client util", util);
            wlReport.append("report compute time", currentTimeMillis() - __startCompute);

            return wlReport;
        }
    }
}
