package vn.edu.hutech.group10.monitoring.monitor.model;

/**
 * One point on a history chart.
 *
 * <p>Raw samples and aggregated buckets are both mapped onto this shape, so the
 * history panel plots a one-hour range and a thirty-day range with the same code
 * and never has to know which table the numbers came from.
 */
public class HistoryPoint {

    private final long timestamp;
    private final double cpuUsage;
    private final double memoryUsage;
    private final double connectionCount;
    private final double networkInBytesPerSecond;
    private final double networkOutBytesPerSecond;
    private final boolean aggregated;

    public HistoryPoint(long timestamp, double cpuUsage, double memoryUsage, double connectionCount,
                        double networkInBytesPerSecond, double networkOutBytesPerSecond,
                        boolean aggregated) {
        this.timestamp = timestamp;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.connectionCount = connectionCount;
        this.networkInBytesPerSecond = networkInBytesPerSecond;
        this.networkOutBytesPerSecond = networkOutBytesPerSecond;
        this.aggregated = aggregated;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public double getConnectionCount() {
        return connectionCount;
    }

    public double getNetworkInBytesPerSecond() {
        return networkInBytesPerSecond;
    }

    public double getNetworkOutBytesPerSecond() {
        return networkOutBytesPerSecond;
    }

    /** True when this point is a bucket average rather than a single sample. */
    public boolean isAggregated() {
        return aggregated;
    }
}
