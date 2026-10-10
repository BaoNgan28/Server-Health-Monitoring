package vn.edu.hutech.group10.monitoring.monitor.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.group10.monitoring.monitor.TestSupport;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricAggregate;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricSample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricRepositoryTest {

    private static final long BUCKET_MS = 10_000L;

    private DatabaseManager database;
    private MetricRepository metrics;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        database = new DatabaseManager(tempDir.resolve("monitoring.db"));
        database.initialize();
        new AgentRepository(database).insert(TestSupport.agent());
        new AgentRepository(database).insert(TestSupport.agent("AGENT-02"));
        metrics = new MetricRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName("A stored sample comes back with every field intact")
    void storesAndReadsBackASample() {
        MetricSample sample = new MetricSample(TestSupport.AGENT_ID, 1_700_000_000_000L,
                23.5d, 61.25d, 42, 3600L, 12_345.5d, 6_789.25d);
        metrics.insertRaw(sample);

        List<MetricSample> found = metrics.findRaw(TestSupport.AGENT_ID, 0L, Long.MAX_VALUE);

        assertEquals(1, found.size());
        MetricSample stored = found.get(0);
        assertAll(
                () -> assertEquals(1_700_000_000_000L, stored.getTimestamp()),
                () -> assertEquals(23.5d, stored.getCpuUsage(), 0.0001d),
                () -> assertEquals(61.25d, stored.getMemoryUsage(), 0.0001d),
                () -> assertEquals(42, stored.getConnectionCount()),
                () -> assertEquals(3600L, stored.getUptimeSeconds()),
                () -> assertEquals(12_345.5d, stored.getNetworkInBytesPerSecond(), 0.0001d),
                () -> assertEquals(6_789.25d, stored.getNetworkOutBytesPerSecond(), 0.0001d));
    }

    @Test
    @DisplayName("A range query returns only that agent and only that window, oldest first")
    void filtersByAgentAndRange() {
        metrics.insertRawBatch(List.of(
                TestSupport.sample(1000L, 1.0d, 1.0d),
                TestSupport.sample(2000L, 2.0d, 2.0d),
                TestSupport.sample(3000L, 3.0d, 3.0d),
                TestSupport.sample("AGENT-02", 2000L, 9.0d, 9.0d)));

        List<MetricSample> window = metrics.findRaw(TestSupport.AGENT_ID, 1500L, 3000L);

        assertAll(
                () -> assertEquals(2, window.size()),
                () -> assertEquals(2000L, window.get(0).getTimestamp()),
                () -> assertEquals(3000L, window.get(1).getTimestamp()),
                () -> assertEquals(4L, metrics.countRaw()),
                () -> assertEquals(3L, metrics.countRaw(TestSupport.AGENT_ID)));
    }

    @Test
    @DisplayName("Aggregation computes the count, the average and the peak of each bucket")
    void aggregatesCorrectly() {
        long bucketStart = 100_000L;
        // Three samples inside one ten-second bucket.
        metrics.insertRawBatch(List.of(
                TestSupport.sample(bucketStart + 1000L, 10.0d, 40.0d),
                TestSupport.sample(bucketStart + 3000L, 20.0d, 50.0d),
                TestSupport.sample(bucketStart + 5000L, 60.0d, 60.0d)));

        int buckets = metrics.aggregateAndPrune(bucketStart + BUCKET_MS, BUCKET_MS);

        assertEquals(1, buckets);
        List<MetricAggregate> aggregates =
                metrics.findAggregates(TestSupport.AGENT_ID, 0L, Long.MAX_VALUE);
        assertEquals(1, aggregates.size());

        MetricAggregate aggregate = aggregates.get(0);
        assertAll(
                () -> assertEquals(bucketStart, aggregate.getBucketStart()),
                () -> assertEquals(3, aggregate.getSampleCount()),
                () -> assertEquals(30.0d, aggregate.getAvgCpu(), 0.0001d),
                () -> assertEquals(60.0d, aggregate.getMaxCpu(), 0.0001d,
                        "the peak must survive, not just the average"),
                () -> assertEquals(50.0d, aggregate.getAvgMemory(), 0.0001d),
                () -> assertEquals(60.0d, aggregate.getMaxMemory(), 0.0001d),
                () -> assertEquals(42.0d, aggregate.getAvgConnections(), 0.0001d),
                () -> assertEquals(10_000.0d, aggregate.getAvgNetworkInBps(), 0.0001d),
                () -> assertEquals(10_000.0d, aggregate.getMaxNetworkInBps(), 0.0001d),
                () -> assertEquals(5_000.0d, aggregate.getAvgNetworkOutBps(), 0.0001d));
    }

    @Test
    @DisplayName("Samples are grouped into the bucket they belong to, per agent")
    void separatesBucketsAndAgents() {
        long base = 200_000L;
        metrics.insertRawBatch(List.of(
                TestSupport.sample(base + 1000L, 10.0d, 10.0d),
                TestSupport.sample(base + 2000L, 20.0d, 20.0d),
                TestSupport.sample(base + BUCKET_MS + 1000L, 30.0d, 30.0d),
                TestSupport.sample("AGENT-02", base + 1000L, 90.0d, 90.0d)));

        metrics.aggregateAndPrune(base + 3 * BUCKET_MS, BUCKET_MS);

        List<MetricAggregate> first =
                metrics.findAggregates(TestSupport.AGENT_ID, 0L, Long.MAX_VALUE);
        List<MetricAggregate> second = metrics.findAggregates("AGENT-02", 0L, Long.MAX_VALUE);

        assertAll(
                () -> assertEquals(2, first.size(), "two distinct buckets for AGENT-01"),
                () -> assertEquals(2, first.get(0).getSampleCount()),
                () -> assertEquals(1, first.get(1).getSampleCount()),
                () -> assertEquals(1, second.size(), "AGENT-02 is aggregated separately"),
                () -> assertEquals(90.0d, second.get(0).getAvgCpu(), 0.0001d));
    }

    @Test
    @DisplayName("Aggregated raw rows are deleted, and newer ones are left alone")
    void prunesOnlyWhatWasAggregated() {
        long cutoff = 100_000L;
        metrics.insertRawBatch(List.of(
                TestSupport.sample(cutoff - 5000L, 10.0d, 10.0d),
                TestSupport.sample(cutoff - 1000L, 20.0d, 20.0d),
                TestSupport.sample(cutoff + 1000L, 30.0d, 30.0d)));

        metrics.aggregateAndPrune(cutoff, BUCKET_MS);

        List<MetricSample> remaining = metrics.findRaw(TestSupport.AGENT_ID, 0L, Long.MAX_VALUE);
        assertAll(
                () -> assertEquals(1, remaining.size(), "the sample after the cutoff must survive"),
                () -> assertEquals(cutoff + 1000L, remaining.get(0).getTimestamp()),
                () -> assertTrue(metrics.countAggregates() > 0, "a summary must exist"));
    }

    @Test
    @DisplayName("Running aggregation again over the same window writes no duplicate bucket")
    void aggregationIsIdempotent() {
        long bucketStart = 300_000L;
        metrics.insertRawBatch(List.of(
                TestSupport.sample(bucketStart + 1000L, 10.0d, 10.0d),
                TestSupport.sample(bucketStart + 2000L, 20.0d, 20.0d)));

        metrics.aggregateAndPrune(bucketStart + BUCKET_MS, BUCKET_MS);
        long afterFirst = metrics.countAggregates();

        // The same rows arrive again - a restart replaying an old window.
        metrics.insertRawBatch(List.of(
                TestSupport.sample(bucketStart + 3000L, 90.0d, 90.0d)));
        metrics.aggregateAndPrune(bucketStart + BUCKET_MS, BUCKET_MS);

        assertEquals(afterFirst, metrics.countAggregates(),
                "the unique index on (agent_id, bucket_start) must prevent a second row");
    }

    @Test
    @DisplayName("Expired buckets are dropped and recent ones are kept")
    void prunesExpiredAggregates() {
        long base = 1_000_000L;
        List<MetricSample> samples = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            samples.add(TestSupport.sample(base + i * BUCKET_MS + 500L, 10.0d, 10.0d));
        }
        metrics.insertRawBatch(samples);
        metrics.aggregateAndPrune(base + 10 * BUCKET_MS, BUCKET_MS);
        assertEquals(5L, metrics.countAggregates());

        int pruned = metrics.deleteAggregatesBefore(base + 3 * BUCKET_MS);

        assertAll(
                () -> assertEquals(3, pruned),
                () -> assertEquals(2L, metrics.countAggregates()));
    }

    @Test
    @DisplayName("An empty aggregation run is harmless")
    void aggregationWithNoDataDoesNothing() {
        assertEquals(0, metrics.aggregateAndPrune(System.currentTimeMillis(), BUCKET_MS));
        assertEquals(0L, metrics.countAggregates());
    }

    @Test
    @DisplayName("The newest raw timestamp of an agent is reported")
    void reportsLatestTimestamp() {
        metrics.insertRawBatch(List.of(
                TestSupport.sample(1000L, 1.0d, 1.0d),
                TestSupport.sample(9000L, 2.0d, 2.0d),
                TestSupport.sample(5000L, 3.0d, 3.0d)));

        assertEquals(9000L, metrics.latestRawTimestamp(TestSupport.AGENT_ID));
        assertEquals(0L, metrics.latestRawTimestamp("AGENT-02"));
    }
}
