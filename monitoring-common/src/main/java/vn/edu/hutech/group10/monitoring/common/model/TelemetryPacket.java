package vn.edu.hutech.group10.monitoring.common.model;

import java.util.Objects;

/**
 * Realtime metric sample sent by an agent to the monitoring client over UDP.
 *
 * <p>The {@code hmac} field carries the HmacSHA256 signature of the canonical
 * representation of every other field (see
 * {@code vn.edu.hutech.group10.monitoring.common.security.PacketCanonicalizer}).
 * The signature field itself is never part of the signed string.
 *
 * <p>All timestamps are epoch milliseconds.
 */
public class TelemetryPacket {

    private String protocolVersion;
    private String type;

    private String agentId;
    private String hostname;

    /**
     * Identifies one run of one agent. A fresh value is generated every time
     * an agent starts, which is what lets the monitoring client tell a
     * restarted agent counting from 1 again apart from a replayed capture.
     */
    private String sessionId;

    private long timestamp;

    /** Strictly increasing per agent; the basis of replay protection. */
    private long sequence;

    private double cpuUsage;
    private double memoryUsage;

    private int connectionCount;
    private long uptimeSeconds;

    private double networkInBytesPerSecond;
    private double networkOutBytesPerSecond;

    private String hmac;

    public TelemetryPacket() {
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public int getConnectionCount() {
        return connectionCount;
    }

    public void setConnectionCount(int connectionCount) {
        this.connectionCount = connectionCount;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public double getNetworkInBytesPerSecond() {
        return networkInBytesPerSecond;
    }

    public void setNetworkInBytesPerSecond(double networkInBytesPerSecond) {
        this.networkInBytesPerSecond = networkInBytesPerSecond;
    }

    public double getNetworkOutBytesPerSecond() {
        return networkOutBytesPerSecond;
    }

    public void setNetworkOutBytesPerSecond(double networkOutBytesPerSecond) {
        this.networkOutBytesPerSecond = networkOutBytesPerSecond;
    }

    public String getHmac() {
        return hmac;
    }

    public void setHmac(String hmac) {
        this.hmac = hmac;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TelemetryPacket)) {
            return false;
        }
        TelemetryPacket that = (TelemetryPacket) o;
        return timestamp == that.timestamp
                && sequence == that.sequence
                && Double.compare(cpuUsage, that.cpuUsage) == 0
                && Double.compare(memoryUsage, that.memoryUsage) == 0
                && connectionCount == that.connectionCount
                && uptimeSeconds == that.uptimeSeconds
                && Double.compare(networkInBytesPerSecond, that.networkInBytesPerSecond) == 0
                && Double.compare(networkOutBytesPerSecond, that.networkOutBytesPerSecond) == 0
                && Objects.equals(protocolVersion, that.protocolVersion)
                && Objects.equals(type, that.type)
                && Objects.equals(agentId, that.agentId)
                && Objects.equals(hostname, that.hostname)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(hmac, that.hmac);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocolVersion, type, agentId, hostname, sessionId, timestamp,
                sequence, cpuUsage, memoryUsage, connectionCount, uptimeSeconds,
                networkInBytesPerSecond, networkOutBytesPerSecond, hmac);
    }

    @Override
    public String toString() {
        return "TelemetryPacket{agentId=" + agentId
                + ", seq=" + sequence
                + ", ts=" + timestamp
                + ", cpu=" + cpuUsage
                + ", mem=" + memoryUsage
                + ", conn=" + connectionCount
                + ", uptime=" + uptimeSeconds
                + ", in=" + networkInBytesPerSecond
                + ", out=" + networkOutBytesPerSecond
                + "}";
    }
}
