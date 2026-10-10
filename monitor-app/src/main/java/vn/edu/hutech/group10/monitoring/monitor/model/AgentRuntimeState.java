package vn.edu.hutech.group10.monitoring.monitor.model;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;

import java.util.concurrent.atomic.LongAdder;

/**
 * Live view of one agent: its stored record plus whatever the last accepted
 * packet said.
 *
 * <p>Read by the Swing event dispatch thread and written by the UDP receiver and
 * the heartbeat monitor. Every mutable field is therefore {@code volatile}, the
 * packet counters are {@link LongAdder}s because several workers increment them
 * at once, and {@link #snapshot()} hands the GUI an immutable copy rather than
 * this object.
 */
public class AgentRuntimeState {

    private volatile AgentRecord record;

    private volatile AgentStatus status = AgentStatus.UNKNOWN;
    private volatile long lastSeen;

    private volatile double cpuUsage;
    private volatile double memoryUsage;
    private volatile int connectionCount;
    private volatile long uptimeSeconds;
    private volatile double networkInBytesPerSecond;
    private volatile double networkOutBytesPerSecond;

    private volatile long lastMetricTimestamp;
    // Counted from several UDP worker threads, so these must be atomic.
    private final LongAdder acceptedPackets = new LongAdder();
    private final LongAdder rejectedPackets = new LongAdder();
    private volatile boolean tcpConnected;

    public AgentRuntimeState(AgentRecord record) {
        this.record = record;
        this.status = record.getStatus() == null ? AgentStatus.UNKNOWN : record.getStatus();
        this.lastSeen = record.getLastSeen();
    }

    public AgentRecord getRecord() {
        return record;
    }

    public void setRecord(AgentRecord record) {
        this.record = record;
    }

    public String getAgentId() {
        return record.getAgentId();
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    /** Copies the numbers of an accepted sample into the live view. */
    public void applySample(MetricSample sample) {
        this.cpuUsage = sample.getCpuUsage();
        this.memoryUsage = sample.getMemoryUsage();
        this.connectionCount = sample.getConnectionCount();
        this.uptimeSeconds = sample.getUptimeSeconds();
        this.networkInBytesPerSecond = sample.getNetworkInBytesPerSecond();
        this.networkOutBytesPerSecond = sample.getNetworkOutBytesPerSecond();
        this.lastMetricTimestamp = sample.getTimestamp();
    }

    public void countAccepted() {
        acceptedPackets.increment();
    }

    public void countRejected() {
        rejectedPackets.increment();
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
        return acceptedPackets.sum();
    }

    public long getRejectedPackets() {
        return rejectedPackets.sum();
    }

    public boolean isTcpConnected() {
        return tcpConnected;
    }

    public void setTcpConnected(boolean tcpConnected) {
        this.tcpConnected = tcpConnected;
    }

    /** Immutable copy for the GUI, taken without holding any lock. */
    public AgentSnapshot snapshot() {
        return new AgentSnapshot(record.copy(), status, lastSeen, cpuUsage, memoryUsage,
                connectionCount, uptimeSeconds, networkInBytesPerSecond, networkOutBytesPerSecond,
                lastMetricTimestamp, acceptedPackets.sum(), rejectedPackets.sum(), tcpConnected);
    }
}
