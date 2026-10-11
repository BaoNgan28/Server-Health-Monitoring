package vn.edu.hutech.group10.monitoring.monitor.model;

/** One aggregation bucket, as stored in {@code metric_5m}. */
public class MetricAggregate {

    private final String agentId;
    private final long bucketStart;
    private final int sampleCount;

    private final double avgCpu;
    private final double maxCpu;
    private final double avgMemory;
    private final double maxMemory;
    private final double avgConnections;
    private final double avgNetworkInBps;
    private final double maxNetworkInBps;
    private final double avgNetworkOutBps;
    private final double maxNetworkOutBps;

    public MetricAggregate(String agentId, long bucketStart, int sampleCount,
                           double avgCpu, double maxCpu, double avgMemory, double maxMemory,
                           double avgConnections, double avgNetworkInBps, double maxNetworkInBps,
                           double avgNetworkOutBps, double maxNetworkOutBps) {
        this.agentId = agentId;
        this.bucketStart = bucketStart;
        this.sampleCount = sampleCount;
        this.avgCpu = avgCpu;
        this.maxCpu = maxCpu;
        this.avgMemory = avgMemory;
        this.maxMemory = maxMemory;
        this.avgConnections = avgConnections;
        this.avgNetworkInBps = avgNetworkInBps;
        this.maxNetworkInBps = maxNetworkInBps;
        this.avgNetworkOutBps = avgNetworkOutBps;
        this.maxNetworkOutBps = maxNetworkOutBps;
    }

    public String getAgentId() {
        return agentId;
    }

    /** Epoch millis of the first moment covered by this bucket. */
    public long getBucketStart() {
        return bucketStart;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public double getAvgCpu() {
        return avgCpu;
    }

    public double getMaxCpu() {
        return maxCpu;
    }

    public double getAvgMemory() {
        return avgMemory;
    }

    public double getMaxMemory() {
        return maxMemory;
    }

    public double getAvgConnections() {
        return avgConnections;
    }

    public double getAvgNetworkInBps() {
        return avgNetworkInBps;
    }

    public double getMaxNetworkInBps() {
        return maxNetworkInBps;
    }

    public double getAvgNetworkOutBps() {
        return avgNetworkOutBps;
    }

    public double getMaxNetworkOutBps() {
        return maxNetworkOutBps;
    }

    @Override
    public String toString() {
        return "MetricAggregate{agentId=" + agentId + ", bucketStart=" + bucketStart
                + ", samples=" + sampleCount + ", avgCpu=" + avgCpu + ", maxCpu=" + maxCpu + "}";
    }
}
