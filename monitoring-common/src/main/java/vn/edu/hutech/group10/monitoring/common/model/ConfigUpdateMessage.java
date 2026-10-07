package vn.edu.hutech.group10.monitoring.common.model;

/**
 * Configuration change pushed from the monitoring client to an agent over TCP.
 *
 * <p>Every setting is boxed so that {@code null} means "leave unchanged"; a
 * monitoring client may therefore send a partial update.
 */
public class ConfigUpdateMessage {

    private String type;
    private String requestId;

    private Long metricIntervalMs;
    private Long heartbeatIntervalMs;

    private Double cpuThreshold;
    private Double memoryThreshold;

    private Double networkInThresholdMbps;
    private Double networkOutThresholdMbps;

    public ConfigUpdateMessage() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getMetricIntervalMs() {
        return metricIntervalMs;
    }

    public void setMetricIntervalMs(Long metricIntervalMs) {
        this.metricIntervalMs = metricIntervalMs;
    }

    public Long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(Long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public Double getCpuThreshold() {
        return cpuThreshold;
    }

    public void setCpuThreshold(Double cpuThreshold) {
        this.cpuThreshold = cpuThreshold;
    }

    public Double getMemoryThreshold() {
        return memoryThreshold;
    }

    public void setMemoryThreshold(Double memoryThreshold) {
        this.memoryThreshold = memoryThreshold;
    }

    public Double getNetworkInThresholdMbps() {
        return networkInThresholdMbps;
    }

    public void setNetworkInThresholdMbps(Double networkInThresholdMbps) {
        this.networkInThresholdMbps = networkInThresholdMbps;
    }

    public Double getNetworkOutThresholdMbps() {
        return networkOutThresholdMbps;
    }

    public void setNetworkOutThresholdMbps(Double networkOutThresholdMbps) {
        this.networkOutThresholdMbps = networkOutThresholdMbps;
    }

    @Override
    public String toString() {
        return "ConfigUpdateMessage{requestId=" + requestId
                + ", metricIntervalMs=" + metricIntervalMs
                + ", heartbeatIntervalMs=" + heartbeatIntervalMs
                + ", cpuThreshold=" + cpuThreshold
                + ", memoryThreshold=" + memoryThreshold
                + ", networkInThresholdMbps=" + networkInThresholdMbps
                + ", networkOutThresholdMbps=" + networkOutThresholdMbps + "}";
    }
}
