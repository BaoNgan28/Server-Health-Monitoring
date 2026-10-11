package vn.edu.hutech.group10.monitoring.common.model;

import java.util.Objects;

/**
 * A single alert occurrence. An event is created when a condition first trips
 * ({@link AlertState#OPEN}) and is closed when the condition clears
 * ({@link AlertState#RESOLVED}); repeated violations of an already-open alert
 * do not create new events.
 */
public class AlertEvent {

    private long id;

    private String agentId;
    private String agentName;

    private AlertType alertType;
    private AlertSeverity severity;
    private AlertState state;

    private String message;

    private long openedAt;
    /** Zero while the alert is still open. */
    private long resolvedAt;

    /** Measured value that tripped the alert; meaningless for offline events. */
    private double currentValue;
    private double threshold;
    private String unit;

    public AlertEvent() {
    }

    public AlertEvent(String agentId, String agentName, AlertType alertType,
                      AlertSeverity severity, String message, long openedAt) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.alertType = alertType;
        this.severity = severity;
        this.state = AlertState.OPEN;
        this.message = message;
        this.openedAt = openedAt;
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

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    public AlertState getState() {
        return state;
    }

    public void setState(AlertState state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(long openedAt) {
        this.openedAt = openedAt;
    }

    public long getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(long resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(double currentValue) {
        this.currentValue = currentValue;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AlertEvent)) {
            return false;
        }
        AlertEvent that = (AlertEvent) o;
        return id == that.id
                && openedAt == that.openedAt
                && Objects.equals(agentId, that.agentId)
                && alertType == that.alertType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, agentId, alertType, openedAt);
    }

    @Override
    public String toString() {
        return "AlertEvent{id=" + id + ", agentId=" + agentId + ", type=" + alertType
                + ", severity=" + severity + ", state=" + state + "}";
    }
}
