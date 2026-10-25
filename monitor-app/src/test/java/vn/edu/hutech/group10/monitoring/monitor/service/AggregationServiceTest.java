package vn.edu.hutech.group10.monitoring.monitor.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.group10.monitoring.monitor.TestSupport;
import vn.edu.hutech.group10.monitoring.monitor.config.MonitorConfig;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricAggregate;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricSample;
import vn.edu.hutech.group10.monitoring.monitor.repository.AgentRepository;
import vn.edu.hutech.group10.monitoring.monitor.repository.DatabaseManager;
import vn.edu.hutech.group10.monitoring.monitor.repository.MetricRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aggregation is driven with fabricated timestamps, so an hour of retention is
 * tested in milliseconds rather than by waiting.
 */
class AggregationServiceTest {

    private static final long RAW_RETENTION_MS = 60_000L;
    private static final long BUCKET_MS = 10_000L;
    private static final long AGGREGATE_RETENTION_MS = 300_000L;

    /** A round number of buckets, so the boundaries are easy to reason about. */
    private static final long BASE = 1_700_000_000_000L / BUCKET_MS * BUCKET_MS;

    private DatabaseManager database;
    private MetricRepository metrics;
    private AggregationService aggregation;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        database = new DatabaseManager(tempDir.resolve("monitoring.db"));
        database.initialize();
        new AgentRepository(database).insert(TestSupport.agent());

        metrics = new MetricRepository(database);
        MonitorConfig config = TestSupport.config(tempDir.resolve("monitoring.db"),
                RAW_RETENTION_MS, BUCKET_MS, AGGREGATE_RETENTION_MS);
        aggregation = new AggregationService(metrics, config, new MonitorEventBus(), () -> BASE);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    /** One sample per second from {@code from} for {@code seconds}. */
    private void insertSeries(long from, int seconds, double cpu) {
        List<MetricSample> samples = new ArrayList<>();
        for (int i = 0; i < seconds; i++) {
            samples.add(TestSupport.sample(from + i * 1000L, cpu, 50.0d));
        }
        metrics.insertRawBatch(samples);
    }

    @Test
    @DisplayName("Samples older than the retention window are folded into buckets")
    void aggregatesOldSamples() {
        // Two minutes of samples ending now; the first minute is past retention.
        insertSeries(BASE - 120_000L, 120, 40.0d);

        int buckets = aggregation.runOnce(BASE);

        assertAll(
                () -> assertTrue(buckets > 0, "some buckets must have been written"),
                () -> assertTrue(metrics.countAggregates() > 0),
                () -> assertTrue(metrics.countRaw() > 0, "recent detail must survive"));
    }

    @Test
    @DisplayName("Only samples older than the retention window are removed")
    void keepsRecentRawSamples() {
        insertSeries(BASE - 120_000L, 120, 40.0d);

        aggregation.runOnce(BASE);

        List<MetricSample> remaining = metrics.findRaw(TestSupport.AGENT_ID, 0L, Long.MAX_VALUE);
        assertTrue(remaining.stream().allMatch(sample -> sample.getTimestamp() >= BASE - RAW_RETENTION_MS),
                "no sample older than the retention window may remain");
        assertTrue(remaining.size() >= 55 && remaining.size() <= 65,
                "roughly the last minute should remain, got " + remaining.size());
    }

    @Test
    @DisplayName("A bucket that is still filling keeps its raw rows")
    void doesNotAggregateAPartialBucket() {
        // The cutoff lands in the middle of a bucket; that bucket must be left alone.
        long cutoff = BASE - RAW_RETENTION_MS;
        insertSeries(cutoff - 5000L, 10, 30.0d);

        aggregation.runOnce(BASE);

        List<MetricAggregate> aggregates =
                metrics.findAggregates(TestSupport.AGENT_ID, 0L, Long.MAX_VALUE);
        for (MetricAggregate aggregate : aggregates) {
            assertTrue(aggregate.getBucketStart() + BUCKET_MS <= cutoff,
                    "bucket " + aggregate.getBucketStart() + " was not complete at the cutoff");
        }
    }

    @Test
    @DisplayName("Averages and peaks are preserved by the fold")
    void preservesAveragesAndPeaks() {
        long bucketStart = BASE - 120_000L;
        metrics.insertRawBatch(List.of(
                TestSupport.sample(bucketStart + 1000L, 10.0d, 20.0d),
                TestSupport.sample(bucketStart + 3000L, 30.0d, 40.0d),
                TestSupport.sample(bucketStart + 5000L, 80.0d, 60.0d)));

        aggregation.runOnce(BASE);

        MetricAggregate aggregate =
                metrics.findAggregates(TestSupport.AGENT_ID, bucketStart, bucketStart).get(0);
        assertAll(
                () -> assertEquals(3, aggregate.getSampleCount()),
                () -> assertEquals(40.0d, aggregate.getAvgCpu(), 0.0001d),
                () -> assertEquals(80.0d, aggregate.getMaxCpu(), 0.0001d),
                () -> assertEquals(40.0d, aggregate.getAvgMemory(), 0.0001d),
                () -> assertEquals(60.0d, aggregate.getMaxMemory(), 0.0001d));
    }

    @Test
    @DisplayName("Buckets past the aggregate retention are deleted")
    void prunesExpiredAggregates() {
        // One old bucket, well past the aggregate retention, and one recent bucket.
        long ancient = BASE - AGGREGATE_RETENTION_MS - 60_000L;
        metrics.insertRawBatch(List.of(
                TestSupport.sample(ancient + 1000L, 10.0d, 10.0d),
                TestSupport.sample(BASE - 120_000L + 1000L, 20.0d, 20.0d)));

        aggregation.runOnce(BASE);

        List<MetricAggregate> remaining =
                metrics.findAggregates(TestSupport.AGENT_ID, 0L, Long.MAX_VALUE);
        assertAll(
                () -> assertEquals(1, remaining.size(), "the expired bucket must be gone"),
                () -> assertTrue(remaining.get(0).getBucketStart()
                        >= BASE - AGGREGATE_RETENTION_MS));
    }

    @Test
    @DisplayName("Running the job twice does not duplicate a bucket")
    void repeatedRunsAreIdempotent() {
        insertSeries(BASE - 120_000L, 120, 40.0d);

        aggregation.runOnce(BASE);
        long afterFirst = metrics.countAggregates();
        aggregation.runOnce(BASE);

        assertEquals(afterFirst, metrics.countAggregates());
    }

    @Test
    @DisplayName("A run with nothing to do is harmless and is still counted")
    void emptyRunIsSafe() {
        assertEquals(0, aggregation.runOnce(BASE));
        assertEquals(BASE, aggregation.getLastRunAt());
        assertEquals(0L, aggregation.getTotalBucketsWritten());
    }

    @Test
    @DisplayName("The same code produces five-minute buckets when configured that way")
    void productionTimingUsesTheSameCode(@TempDir Path tempDir) {
        long fiveMinutes = 5L * 60_000L;
        long oneHour = 60L * 60_000L;
        MonitorConfig production = TestSupport.config(tempDir.resolve("monitoring.db"),
                oneHour, fiveMinutes, 30L * 86_400_000L);
        AggregationService productionAggregation =
                new AggregationService(metrics, production, new MonitorEventBus(), () -> BASE);

        // Ninety minutes ago, so it is well past the one-hour raw retention.
        long start = (BASE - 90L * 60_000L) / fiveMinutes * fiveMinutes;
        metrics.insertRawBatch(List.of(
                TestSupport.sample(start + 1000L, 10.0d, 10.0d),
                TestSupport.sample(start + 120_000L, 30.0d, 30.0d)));

        assertEquals(1, productionAggregation.runOnce(BASE));

        MetricAggregate aggregate =
                metrics.findAggregates(TestSupport.AGENT_ID, 0L, Long.MAX_VALUE).get(0);
        assertAll(
                () -> assertEquals(start, aggregate.getBucketStart(),
                        "both samples belong to the same five-minute bucket"),
                () -> assertEquals(2, aggregate.getSampleCount()),
                () -> assertEquals(20.0d, aggregate.getAvgCpu(), 0.0001d));
    }
}
