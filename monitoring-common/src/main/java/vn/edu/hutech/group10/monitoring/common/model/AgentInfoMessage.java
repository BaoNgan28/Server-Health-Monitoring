package vn.edu.hutech.group10.monitoring.common.model;

/**
 * Descriptive information about an agent and the machine it runs on.
 * Carried over TCP as the payload of an AGENT_INFO_RESPONSE message.
 */
public class AgentInfoMessage {

    private String type;
    private String agentId;
    private String agentName;
    private String hostname;

    private String osName;
    private String osVersion;
    private String cpuModel;
    private int cpuLogicalCores;
    private long totalMemoryBytes;
    private long uptimeSeconds;

    private long metricIntervalMs;
    private long heartbeatIntervalMs;

    private String agentVersion;

    public AgentInfoMessage() {
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

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public int getCpuLogicalCores() {
        return cpuLogicalCores;
    }

    public void setCpuLogicalCores(int cpuLogicalCores) {
        this.cpuLogicalCores = cpuLogicalCores;
    }

    public long getTotalMemoryBytes() {
        return totalMemoryBytes;
    }

    public void setTotalMemoryBytes(long totalMemoryBytes) {
        this.totalMemoryBytes = totalMemoryBytes;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public long getMetricIntervalMs() {
        return metricIntervalMs;
    }

    public void setMetricIntervalMs(long metricIntervalMs) {
        this.metricIntervalMs = metricIntervalMs;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    @Override
    public String toString() {
        return "AgentInfoMessage{agentId=" + agentId + ", hostname=" + hostname
                + ", os=" + osName + " " + osVersion + ", cores=" + cpuLogicalCores + "}";
    }
}
