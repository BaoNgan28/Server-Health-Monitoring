package vn.edu.hutech.group10.monitoring.monitor.config;

import java.nio.file.Path;

/**
 * Settings of the monitoring client, already normalised to milliseconds.
 *
 * <p>Production and demo profiles differ only in the numbers held here, never in
 * the code that uses them, so a demo with ten-second buckets exercises exactly
 * the same aggregation path as a production run with five-minute buckets.
 */
public class MonitorConfig {

    private final int udpPort;
    private final long heartbeatTimeoutMs;
    private final long heartbeatCheckIntervalMs;
    private final long timestampToleranceMs;

    private final Path databasePath;

    private final long rawRetentionMs;
    private final long aggregationBucketMs;
    private final long aggregateRetentionMs;
    private final long aggregationIntervalMs;

    private final int chartWindowSamples;
    private final int tcpTimeoutMs;
    private final String profileName;

    public MonitorConfig(int udpPort, long heartbeatTimeoutMs, long heartbeatCheckIntervalMs,
                         long timestampToleranceMs, Path databasePath,
                         long rawRetentionMs, long aggregationBucketMs, long aggregateRetentionMs,
                         long aggregationIntervalMs, int chartWindowSamples, int tcpTimeoutMs,
                         String profileName) {
        this.udpPort = udpPort;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
        this.heartbeatCheckIntervalMs = heartbeatCheckIntervalMs;
        this.timestampToleranceMs = timestampToleranceMs;
        this.databasePath = databasePath;
        this.rawRetentionMs = rawRetentionMs;
        this.aggregationBucketMs = aggregationBucketMs;
        this.aggregateRetentionMs = aggregateRetentionMs;
        this.aggregationIntervalMs = aggregationIntervalMs;
        this.chartWindowSamples = chartWindowSamples;
        this.tcpTimeoutMs = tcpTimeoutMs;
        this.profileName = profileName;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public long getHeartbeatTimeoutMs() {
        return heartbeatTimeoutMs;
    }

    public long getHeartbeatCheckIntervalMs() {
        return heartbeatCheckIntervalMs;
    }

    public long getTimestampToleranceMs() {
        return timestampToleranceMs;
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    /** How long a raw sample is kept before it is folded into a bucket. */
    public long getRawRetentionMs() {
        return rawRetentionMs;
    }

    /** Width of one aggregation bucket. */
    public long getAggregationBucketMs() {
        return aggregationBucketMs;
    }

    /** How long aggregated buckets are kept. */
    public long getAggregateRetentionMs() {
        return aggregateRetentionMs;
    }

    /** How often the aggregation job runs. */
    public long getAggregationIntervalMs() {
        return aggregationIntervalMs;
    }

    public int getChartWindowSamples() {
        return chartWindowSamples;
    }

    public int getTcpTimeoutMs() {
        return tcpTimeoutMs;
    }

    /** {@code production} or {@code demo}; shown in the window title. */
    public String getProfileName() {
        return profileName;
    }

    @Override
    public String toString() {
        return "MonitorConfig{profile=" + profileName
                + ", udpPort=" + udpPort
                + ", heartbeatTimeoutMs=" + heartbeatTimeoutMs
                + ", database=" + databasePath
                + ", rawRetentionMs=" + rawRetentionMs
                + ", bucketMs=" + aggregationBucketMs
                + ", aggregateRetentionMs=" + aggregateRetentionMs + "}";
    }
}
