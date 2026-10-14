package vn.edu.hutech.group10.monitoring.monitor.notification;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.common.model.AlertSeverity;
import vn.edu.hutech.group10.monitoring.common.model.AlertType;
import vn.edu.hutech.group10.monitoring.common.util.TimeUtil;
import vn.edu.hutech.group10.monitoring.monitor.config.EmailConfig;

import java.util.Locale;

/**
 * Builds the subject and body of every alert e-mail.
 *
 * <p>Kept free of any network code so the exact wording can be asserted in unit
 * tests without sending anything.
 */
public final class EmailMessageBuilder {

    private static final String HEADER = "GROUP 10 - SERVER HEALTH MONITORING";
    private static final String NL = System.lineSeparator();

    private EmailMessageBuilder() {
    }

    /** Subject line, for example {@code [WARNING] CPU HIGH - AGENT-01}. */
    public static String buildSubject(AlertEvent event) {
        return "[" + subjectTag(event) + "] " + event.getAlertType().getDisplayName()
                + " - " + event.getAgentId();
    }

    private static String subjectTag(AlertEvent event) {
        if (event.getAlertType() == AlertType.AGENT_RECOVERED) {
            return "RECOVERED";
        }
        AlertSeverity severity = event.getSeverity();
        return severity == null ? "INFO" : severity.name();
    }

    /** Body of an alert e-mail. */
    public static String buildBody(AlertEvent event) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(HEADER).append(NL).append(NL);

        switch (event.getAlertType()) {
            case AGENT_OFFLINE:
                sb.append("CRITICAL ALERT").append(NL).append(NL);
                sb.append("Agent:").append(NL).append(event.getAgentId()).append(NL).append(NL);
                sb.append("Server:").append(NL).append(nullSafe(event.getAgentName())).append(NL).append(NL);
                sb.append("Status:").append(NL).append("OFFLINE").append(NL).append(NL);
                sb.append("Reason:").append(NL)
                        .append("No valid heartbeat received for more than ")
                        .append(formatSeconds(event.getThreshold())).append(" seconds.")
                        .append(NL).append(NL);
                sb.append("Last Seen:").append(NL)
                        .append(TimeUtil.formatTimestamp((long) event.getCurrentValue()))
                        .append(NL).append(NL);
                break;
            case AGENT_RECOVERED:
                sb.append("RECOVERY NOTICE").append(NL).append(NL);
                sb.append("Agent:").append(NL).append(event.getAgentId()).append(NL).append(NL);
                sb.append("Server:").append(NL).append(nullSafe(event.getAgentName())).append(NL).append(NL);
                sb.append("Status:").append(NL).append("ONLINE").append(NL).append(NL);
                sb.append("Reason:").append(NL)
                        .append("Valid telemetry is being received again.").append(NL).append(NL);
                break;
            default:
                sb.append("Alert Type: ").append(event.getAlertType().getDisplayName()).append(NL);
                sb.append("Severity: ").append(event.getSeverity()).append(NL).append(NL);
                sb.append("Agent:").append(NL).append(event.getAgentId()).append(NL).append(NL);
                sb.append("Server:").append(NL).append(nullSafe(event.getAgentName())).append(NL).append(NL);
                sb.append("Current ").append(metricLabel(event.getAlertType())).append(":").append(NL)
                        .append(formatValue(event.getCurrentValue(), event.getUnit())).append(NL).append(NL);
                sb.append("Threshold:").append(NL)
                        .append(formatValue(event.getThreshold(), event.getUnit())).append(NL).append(NL);
                break;
        }

        sb.append("Detected At:").append(NL)
                .append(TimeUtil.formatTimestamp(event.getOpenedAt())).append(NL);
        return sb.toString();
    }

    /** Subject of the test e-mail sent from the settings screen. */
    public static String buildTestSubject() {
        return "[TEST] Group 10 Server Health Monitoring";
    }

    /** Body of the test e-mail sent from the settings screen. */
    public static String buildTestBody(EmailConfig config, long nowMillis) {
        StringBuilder sb = new StringBuilder(320);
        sb.append(HEADER).append(NL).append(NL);
        sb.append("This is a test notification.").append(NL).append(NL);
        sb.append("SMTP email notification is configured successfully.").append(NL).append(NL);
        sb.append("Sender: ").append(nullSafe(config.getUsername())).append(NL);
        sb.append("Recipient: ").append(nullSafe(config.getRecipient())).append(NL);
        sb.append("Time: ").append(TimeUtil.formatTimestamp(nowMillis)).append(NL);
        return sb.toString();
    }

    /** Assembles the full message for an alert. */
    public static EmailMessage forAlert(EmailConfig config, AlertEvent event) {
        return new EmailMessage(config.getUsername(), config.getSenderName(), config.getRecipient(),
                buildSubject(event), buildBody(event));
    }

    /** Assembles the full test message. */
    public static EmailMessage forTest(EmailConfig config, long nowMillis) {
        return new EmailMessage(config.getUsername(), config.getSenderName(), config.getRecipient(),
                buildTestSubject(), buildTestBody(config, nowMillis));
    }

    private static String metricLabel(AlertType type) {
        switch (type) {
            case CPU_HIGH:
                return "CPU";
            case MEMORY_HIGH:
                return "Memory";
            case NETWORK_IN_HIGH:
                return "Network IN";
            case NETWORK_OUT_HIGH:
                return "Network OUT";
            default:
                return "Value";
        }
    }

    private static String formatValue(double value, String unit) {
        String rendered = String.format(Locale.ROOT, "%.1f", value);
        return unit == null || unit.isEmpty() ? rendered : rendered + " " + unit;
    }

    private static String formatSeconds(double millis) {
        return String.format(Locale.ROOT, "%.0f", millis / 1000.0d);
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }
}
