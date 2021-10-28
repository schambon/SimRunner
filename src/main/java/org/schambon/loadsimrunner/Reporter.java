package org.schambon.loadsimrunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.math.Quantiles.percentiles;
import com.google.common.math.Stats;

public class Reporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Reporter.class);

    private volatile Map<String, StatsHolder> stats = null;
    private long startTime = 0;

    public void start() {
        stats = new TreeMap<>();
        startTime = System.currentTimeMillis();
    }

    public void reportInit(String message) {
        LOGGER.info(String.format("INIT: %s", message));
    }

    public void printReport() {
        LOGGER.info("Periodic report");
        var oldStats = stats;
        long now = System.currentTimeMillis();
        long interval = now - startTime;
        startTime = now;

        stats = new TreeMap<>();

        for (var workload: oldStats.keySet()) {
            LOGGER.info("{}:\n{}", workload, oldStats.get(workload).compute(interval));
        }
    }

    public void reportOp(String name, long i, long duration) {
        StatsHolder h = stats.get(name);
        if (h == null) {
            h = new StatsHolder();
            stats.put(name, h);
        }
        h.addOp(i, duration);
    }
    
    // a specific thread for logging durations
    static ExecutorService asyncExecutor = Executors.newFixedThreadPool(1);

    private static class StatsHolder {

        AtomicLong numops = new AtomicLong(0);
        List<Long> durationsBatch = new ArrayList<>();
        List<Long> numbers = new ArrayList<>();

        // Compute some statistics
        // interval is the overall duration
        public String compute(long interval) {

            var percentilesBatch = percentiles().indexes(50,95).compute(durationsBatch);
            var meanBatch = Stats.of(durationsBatch).mean();
            var sumBatch = Stats.of(durationsBatch).sum();
            var numberStats = Stats.of(numbers);

            return String.format("%d ops per second\n%f ms mean duration\n%f ms median\n%f ms 95th percentile\n(sum of batch durations): %f\n%f / %f / %f Batch size avg / mean / max",
                (long) (Math.round((double) numops.get()) / (double) (interval/1000)), 
                meanBatch,
                percentilesBatch.get(50),
                percentilesBatch.get(95),
                sumBatch,
                numberStats.mean(),
                numberStats.min(),
                numberStats.max());
        }

        public void addOp(long number, long duration) {
            numops.addAndGet(number);
            Reporter.asyncExecutor.submit(() ->  {durationsBatch.add(duration); numbers.add(number);});
        }
    }
}
