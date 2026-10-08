package vn.edu.hutech.group10.monitoring.common.model;

import java.util.Objects;

/**
 * Lightweight liveness packet sent by an agent over UDP on its own schedule.
 * Signed exactly like {@link TelemetryPacket}, but over a shorter canonical string.
 */
public class HeartbeatPacket {

    private String protocolVersion;
    private String type;

    private String agentId;
    private String hostname;

    /** Identifies one run of one agent; see {@link TelemetryPacket#getSessionId()}. */
    private String sessionId;

    private long timestamp;
    private long sequence;

    private String hmac;

    public HeartbeatPacket() {
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
        if (!(o instanceof HeartbeatPacket)) {
            return false;
        }
        HeartbeatPacket that = (HeartbeatPacket) o;
        return timestamp == that.timestamp
                && sequence == that.sequence
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
                sequence, hmac);
    }

    @Override
    public String toString() {
        return "HeartbeatPacket{agentId=" + agentId + ", seq=" + sequence + ", ts=" + timestamp + "}";
    }
}
