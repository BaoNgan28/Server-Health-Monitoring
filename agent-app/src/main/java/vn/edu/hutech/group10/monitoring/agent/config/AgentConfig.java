package vn.edu.hutech.group10.monitoring.agent.config;

import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;

/**
 * Runtime settings of one agent.
 *
 * <p>Identity and endpoints are fixed at startup, while intervals and thresholds
 * may be changed at any time by a CONFIG_UPDATE arriving on the TCP control
 * channel. Those mutable fields are {@code volatile} because the scheduler
 * threads read them while a TCP session thread writes them.
 *
 * <p>{@link #getSharedSecret()} is deliberately absent from {@link #toString()}
 * and from every log line in this project.
 */
public class AgentConfig {

    private final String agentId;
    private final String agentName;
    private final String hostname;

    private final String monitorHost;
    private final int monitorUdpPort;
    private final int agentTcpPort;

    private final String sharedSecret;

    private volatile long metricIntervalMs;
    private volatile long heartbeatIntervalMs;

    private volatile double cpuThreshold;
    private volatile double memoryThreshold;
    private volatile double networkInThresholdMbps;
    private volatile double networkOutThresholdMbps;

    public AgentConfig(String agentId, String agentName, String hostname,
                       String monitorHost, int monitorUdpPort, int agentTcpPort,
                       String sharedSecret, long metricIntervalMs, long heartbeatIntervalMs,
                       double cpuThreshold, double memoryThreshold,
                       double networkInThresholdMbps, double networkOutThresholdMbps) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.hostname = hostname;
        this.monitorHost = monitorHost;
        this.monitorUdpPort = monitorUdpPort;
        this.agentTcpPort = agentTcpPort;
        this.sharedSecret = sharedSecret;
        this.metricIntervalMs = metricIntervalMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.cpuThreshold = cpuThreshold;
        this.memoryThreshold = memoryThreshold;
        this.networkInThresholdMbps = networkInThresholdMbps;
        this.networkOutThresholdMbps = networkOutThresholdMbps;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getHostname() {
        return hostname;
    }

    public String getMonitorHost() {
        return monitorHost;
    }

    public int getMonitorUdpPort() {
        return monitorUdpPort;
    }

    public int getAgentTcpPort() {
        return agentTcpPort;
    }

    /** Never log, print or transmit this value. */
    public String getSharedSecret() {
        return sharedSecret;
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

    /** Multi-line banner printed at startup. The shared secret is never included. */
    public String describeForConsole() {
        StringBuilder sb = new StringBuilder(320);
        sb.append("Agent ID            : ").append(agentId).append(System.lineSeparator());
        sb.append("Agent Name          : ").append(agentName).append(System.lineSeparator());
        sb.append("Hostname            : ").append(hostname).append(System.lineSeparator());
        sb.append("Monitoring Host     : ").append(monitorHost).append(System.lineSeparator());
        sb.append("Monitoring UDP Port : ").append(monitorUdpPort).append(System.lineSeparator());
        sb.append("Agent TCP Port      : ").append(agentTcpPort).append(System.lineSeparator());
        sb.append("Metric Interval     : ").append(metricIntervalMs).append(" ms")
                .append(System.lineSeparator());
        sb.append("Heartbeat Interval  : ").append(heartbeatIntervalMs).append(" ms");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "AgentConfig{agentId=" + agentId
                + ", monitor=" + monitorHost + ":" + monitorUdpPort
                + ", tcpPort=" + agentTcpPort
                + ", metricIntervalMs=" + metricIntervalMs
                + ", heartbeatIntervalMs=" + heartbeatIntervalMs
                + ", defaultTimestampToleranceMs=" + ProtocolConstants.DEFAULT_TIMESTAMP_TOLERANCE_MS
                + "}";
    }
}
