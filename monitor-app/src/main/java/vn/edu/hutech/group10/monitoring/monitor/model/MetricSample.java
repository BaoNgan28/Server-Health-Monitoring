package vn.edu.hutech.group10.monitoring.monitor.model;

import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;

/** One accepted telemetry sample, as stored in {@code metric_raw}. */
public class MetricSample {

    private final String agentId;
    private final long timestamp;
    private final double cpuUsage;
    private final double memoryUsage;
    private final int connectionCount;
    private final long uptimeSeconds;
    private final double networkInBytesPerSecond;
    private final double networkOutBytesPerSecond;

    public MetricSample(String agentId, long timestamp, double cpuUsage, double memoryUsage,
                        int connectionCount, long uptimeSeconds,
                        double networkInBytesPerSecond, double networkOutBytesPerSecond) {
        this.agentId = agentId;
        this.timestamp = timestamp;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.connectionCount = connectionCount;
        this.uptimeSeconds = uptimeSeconds;
        this.networkInBytesPerSecond = networkInBytesPerSecond;
        this.networkOutBytesPerSecond = networkOutBytesPerSecond;
    }

    /** Builds a sample from a packet that has already passed validation. */
    public static MetricSample from(TelemetryPacket packet) {
        return new MetricSample(packet.getAgentId(), packet.getTimestamp(),
                packet.getCpuUsage(), packet.getMemoryUsage(), packet.getConnectionCount(),
                packet.getUptimeSeconds(), packet.getNetworkInBytesPerSecond(),
                packet.getNetworkOutBytesPerSecond());
    }

    public String getAgentId() {
        return agentId;
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
        return "MetricSample{agentId=" + agentId + ", timestamp=" + timestamp
                + ", cpu=" + cpuUsage + ", memory=" + memoryUsage + "}";
    }
}
