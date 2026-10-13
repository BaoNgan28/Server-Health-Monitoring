package vn.edu.hutech.group10.monitoring.monitor.service;

import vn.edu.hutech.group10.monitoring.common.util.TimeUtil;
import vn.edu.hutech.group10.monitoring.monitor.config.MonitorConfig;
import vn.edu.hutech.group10.monitoring.monitor.repository.MetricRepository;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the database from growing without bound by folding old raw samples into
 * fixed-width buckets and dropping buckets that are past their retention.
 *
 * <p>Raw samples arrive once a second per agent, which is far more detail than
 * anyone needs beyond the last hour. After the raw retention window each bucket
 * is replaced by one row carrying the sample count, the average and the peak of
 * every metric - the peak matters, because an average alone would hide the spike
 * that caused an incident.
 *
 * <p>The cutoff is aligned down to a bucket boundary, so a bucket is only ever
 * summarised once it is complete; a half-finished bucket keeps its raw rows
 * until the next run. Writing the summary and deleting the raw rows happen in
 * one transaction, so detail is never dropped without its summary existing.
 *
 * <p>The production profile and the demo profile differ only in the numbers in
 * {@link MonitorConfig}: the same code runs with five-minute buckets and with
 * ten-second ones.
 */
public class AggregationService {

    private static final Logger LOG = Logger.getLogger(AggregationService.class.getName());

    private final MetricRepository metricRepository;
    private final MonitorConfig config;
    private final MonitorEventBus eventBus;
    private final LongSupplier clock;

    private volatile ScheduledFuture<?> task;
    private volatile long lastRunAt;
    private volatile long totalBucketsWritten;

    public AggregationService(MetricRepository metricRepository, MonitorConfig config,
                              MonitorEventBus eventBus) {
        this(metricRepository, config, eventBus, System::currentTimeMillis);
    }

    public AggregationService(MetricRepository metricRepository, MonitorConfig config,
                              MonitorEventBus eventBus, LongSupplier clock) {
        this.metricRepository = metricRepository;
        this.config = config;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    /** Starts the periodic job on {@code executor}. */
    public void start(ScheduledExecutorService executor) {
        long interval = config.getAggregationIntervalMs();
        task = executor.scheduleAtFixedRate(this::safeRun, interval, interval, TimeUnit.MILLISECONDS);
        LOG.info("Aggregation scheduled every " + interval + " ms"
                + " (raw kept " + config.getRawRetentionMs() + " ms, bucket "
                + config.getAggregationBucketMs() + " ms, aggregates kept "
                + config.getAggregateRetentionMs() + " ms)");
    }

    private void safeRun() {
        try {
            runOnce(clock.getAsLong());
        } catch (RuntimeException e) {
            // Never let this escape: the executor would cancel the job.
            LOG.log(Level.WARNING, "Aggregation run failed", e);
        }
    }

    /**
     * Runs one aggregation pass against the clock value {@code nowMs}. Exposed so
     * the tests can drive it with fabricated timestamps instead of waiting an hour.
     *
     * @return how many buckets were written
     */
    public int runOnce(long nowMs) {
        long bucketMs = config.getAggregationBucketMs();

        // Align down, so a bucket that is still filling keeps its raw rows.
        long rawCutoff = TimeUtil.bucketStart(nowMs - config.getRawRetentionMs(), bucketMs);
        int buckets = metricRepository.aggregateAndPrune(rawCutoff, bucketMs);

        long aggregateCutoff = nowMs - config.getAggregateRetentionMs();
        int pruned = metricRepository.deleteAggregatesBefore(aggregateCutoff);

        lastRunAt = nowMs;
        totalBucketsWritten += buckets;

        if (buckets > 0 || pruned > 0) {
            LOG.info("Aggregation wrote " + buckets + " bucket(s) and pruned "
                    + pruned + " expired bucket(s)");
        }
        eventBus.fireAggregationCompleted(buckets, pruned);
        return buckets;
    }

    public long getLastRunAt() {
        return lastRunAt;
    }

    public long getTotalBucketsWritten() {
        return totalBucketsWritten;
    }

    public void stop() {
        ScheduledFuture<?> current = task;
        if (current != null) {
            current.cancel(false);
            task = null;
            LOG.info("Aggregation stopped");
        }
    }
}
