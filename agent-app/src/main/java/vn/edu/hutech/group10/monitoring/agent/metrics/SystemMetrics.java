package vn.edu.hutech.group10.monitoring.agent.metrics;

/** One immutable sample of the six metrics the agent reports. */
public final class SystemMetrics {

    private final long timestamp;
    private final double cpuUsage;
    private final double memoryUsage;
    private final int connectionCount;
    private final long uptimeSeconds;
    private final double networkInBytesPerSecond;
    private final double networkOutBytesPerSecond;

    public SystemMetrics(long timestamp, double cpuUsage, double memoryUsage, int connectionCount,
                         long uptimeSeconds, double networkInBytesPerSecond,
                         double networkOutBytesPerSecond) {
        this.timestamp = timestamp;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.connectionCount = connectionCount;
        this.uptimeSeconds = uptimeSeconds;
        this.networkInBytesPerSecond = networkInBytesPerSecond;
        this.networkOutBytesPerSecond = networkOutBytesPerSecond;
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

    public int getConnectionCount() {
        return connectionCount;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public double getNetworkInBytesPerSecond() {
        return networkInBytesPerSecond;
    }

    public double getNetworkOutBytesPerSecond() {
        return networkOutBytesPerSecond;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "cpu=%.1f%% mem=%.1f%% conn=%d uptime=%ds in=%.0fB/s out=%.0fB/s",
                cpuUsage, memoryUsage, connectionCount, uptimeSeconds,
                networkInBytesPerSecond, networkOutBytesPerSecond);
    }
}
