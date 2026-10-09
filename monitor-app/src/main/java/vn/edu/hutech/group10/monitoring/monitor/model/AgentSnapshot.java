package vn.edu.hutech.group10.monitoring.monitor.model;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;

/**
 * Immutable picture of one agent at a moment in time, handed to the GUI.
 *
 * <p>The GUI never touches {@link AgentRuntimeState} directly, so a table being
 * repainted cannot see half of an update written by the UDP receiver.
 */
public class AgentSnapshot {

    private final AgentRecord record;
    private final AgentStatus status;
    private final long lastSeen;

    private final double cpuUsage;
    private final double memoryUsage;
    private final int connectionCount;
    private final long uptimeSeconds;
    private final double networkInBytesPerSecond;
    private final double networkOutBytesPerSecond;

    private final long lastMetricTimestamp;
    private final long acceptedPackets;
    private final long rejectedPackets;
    private final boolean tcpConnected;

    public AgentSnapshot(AgentRecord record, AgentStatus status, long lastSeen,
                         double cpuUsage, double memoryUsage, int connectionCount,
                         long uptimeSeconds, double networkInBytesPerSecond,
                         double networkOutBytesPerSecond, long lastMetricTimestamp,
                         long acceptedPackets, long rejectedPackets, boolean tcpConnected) {
        this.record = record;
        this.status = status;
        this.lastSeen = lastSeen;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.connectionCount = connectionCount;
        this.uptimeSeconds = uptimeSeconds;
        this.networkInBytesPerSecond = networkInBytesPerSecond;
        this.networkOutBytesPerSecond = networkOutBytesPerSecond;
        this.lastMetricTimestamp = lastMetricTimestamp;
        this.acceptedPackets = acceptedPackets;
        this.rejectedPackets = rejectedPackets;
        this.tcpConnected = tcpConnected;
    }

    public AgentRecord getRecord() {
        return record;
    }

    public String getAgentId() {
        return record.getAgentId();
    }

    public String getName() {
        return record.getName();
    }

    public String getHost() {
        return record.getHost();
    }

    public int getTcpPort() {
        return record.getTcpPort();
    }

    public AgentStatus getStatus() {
        return status;
    }

    public long getLastSeen() {
        return lastSeen;
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

    public long getLastMetricTimestamp() {
        return lastMetricTimestamp;
    }

    public long getAcceptedPackets() {
        return acceptedPackets;
    }

    public long getRejectedPackets() {
        return rejectedPackets;
    }

    public boolean isTcpConnected() {
        return tcpConnected;
    }

    /** True once at least one metric sample has been accepted. */
    public boolean hasMetrics() {
        return lastMetricTimestamp > 0L;
    }
}
