package vn.edu.hutech.group10.monitoring.common.model;

/**
 * Agent reply to a {@link ConfigUpdateMessage}. {@code status} is either
 * {@link #STATUS_SUCCESS} or {@link #STATUS_ERROR}; on error {@code message}
 * explains which setting was rejected and why.
 */
public class ConfigAckMessage {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";

    private String type;
    private String requestId;
    private String status;
    private String message;

    /** Settings actually in force on the agent after applying the update. */
    private long effectiveMetricIntervalMs;
    private long effectiveHeartbeatIntervalMs;
    private double effectiveCpuThreshold;
    private double effectiveMemoryThreshold;
    private double effectiveNetworkInThresholdMbps;
    private double effectiveNetworkOutThresholdMbps;

    public ConfigAckMessage() {
    }

    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getEffectiveMetricIntervalMs() {
        return effectiveMetricIntervalMs;
    }

    public void setEffectiveMetricIntervalMs(long effectiveMetricIntervalMs) {
        this.effectiveMetricIntervalMs = effectiveMetricIntervalMs;
    }

    public long getEffectiveHeartbeatIntervalMs() {
        return effectiveHeartbeatIntervalMs;
    }

    public void setEffectiveHeartbeatIntervalMs(long effectiveHeartbeatIntervalMs) {
        this.effectiveHeartbeatIntervalMs = effectiveHeartbeatIntervalMs;
    }

    public double getEffectiveCpuThreshold() {
        return effectiveCpuThreshold;
    }

    public void setEffectiveCpuThreshold(double effectiveCpuThreshold) {
        this.effectiveCpuThreshold = effectiveCpuThreshold;
    }

    public double getEffectiveMemoryThreshold() {
        return effectiveMemoryThreshold;
    }

    public void setEffectiveMemoryThreshold(double effectiveMemoryThreshold) {
        this.effectiveMemoryThreshold = effectiveMemoryThreshold;
    }

    public double getEffectiveNetworkInThresholdMbps() {
        return effectiveNetworkInThresholdMbps;
    }

    public void setEffectiveNetworkInThresholdMbps(double effectiveNetworkInThresholdMbps) {
        this.effectiveNetworkInThresholdMbps = effectiveNetworkInThresholdMbps;
    }

    public double getEffectiveNetworkOutThresholdMbps() {
        return effectiveNetworkOutThresholdMbps;
    }

    public void setEffectiveNetworkOutThresholdMbps(double effectiveNetworkOutThresholdMbps) {
        this.effectiveNetworkOutThresholdMbps = effectiveNetworkOutThresholdMbps;
    }

    @Override
    public String toString() {
        return "ConfigAckMessage{requestId=" + requestId + ", status=" + status
                + ", message=" + message + "}";
    }
}
