package vn.edu.hutech.group10.monitoring.monitor.model;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;

/**
 * A registered agent as stored in the {@code agents} table: how to reach it, the
 * secret its packets are signed with, and the thresholds its metrics are judged
 * against.
 */
public class AgentRecord {

    private long id;

    private String agentId;
    private String name;
    private String host;
    private int tcpPort;

    private String sharedSecret;

    private double cpuThreshold;
    private double memoryThreshold;
    private double networkInThresholdMbps;
    private double networkOutThresholdMbps;

    private AgentStatus status = AgentStatus.UNKNOWN;
    private long lastSeen;
    private long createdAt;
    private long updatedAt;

    public AgentRecord() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    /** Never shown in the agent table or in any log line. */
    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public double getCpuThreshold() {
        return cpuThreshold;
    }

    public void setCpuThreshold(double cpuThreshold) {
        this.cpuThreshold = cpuThreshold;
    }

    public double getMemoryThreshold() {
        return memoryThreshold;
    }

    public void setMemoryThreshold(double memoryThreshold) {
        this.memoryThreshold = memoryThreshold;
    }

    public double getNetworkInThresholdMbps() {
        return networkInThresholdMbps;
    }

    public void setNetworkInThresholdMbps(double networkInThresholdMbps) {
        this.networkInThresholdMbps = networkInThresholdMbps;
    }

    public double getNetworkOutThresholdMbps() {
        return networkOutThresholdMbps;
    }

    public void setNetworkOutThresholdMbps(double networkOutThresholdMbps) {
        this.networkOutThresholdMbps = networkOutThresholdMbps;
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

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** A copy with the same field values; used so the GUI cannot mutate stored state. */
    public AgentRecord copy() {
        AgentRecord copy = new AgentRecord();
        copy.id = id;
        copy.agentId = agentId;
        copy.name = name;
        copy.host = host;
        copy.tcpPort = tcpPort;
        copy.sharedSecret = sharedSecret;
        copy.cpuThreshold = cpuThreshold;
        copy.memoryThreshold = memoryThreshold;
        copy.networkInThresholdMbps = networkInThresholdMbps;
        copy.networkOutThresholdMbps = networkOutThresholdMbps;
        copy.status = status;
        copy.lastSeen = lastSeen;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }

    @Override
    public String toString() {
        return "AgentRecord{agentId=" + agentId + ", name=" + name
                + ", endpoint=" + host + ":" + tcpPort + ", status=" + status + "}";
    }
}
