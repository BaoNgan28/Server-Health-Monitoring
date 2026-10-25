package vn.edu.hutech.group10.monitoring.monitor.notification;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;

import java.util.logging.Logger;

/**
 * Stands in for the e-mail channel when no credentials have been configured.
 *
 * <p>Alerts still reach the GUI and the database; only the remote channel is
 * silent, and each suppressed alert leaves a log line so a demo can show that
 * the engine fired even though nothing was e-mailed.
 */
public class NoOpNotifier implements Notifier {

    private static final Logger LOG = Logger.getLogger(NoOpNotifier.class.getName());

    private final String reason;
    private volatile long suppressedCount;

    public NoOpNotifier(String reason) {
        this.reason = reason == null ? "credentials not configured" : reason;
    }

    @Override
    public void sendAlert(AlertEvent event) {
        suppressedCount++;
        if (event != null) {
            LOG.info("Email notification suppressed (" + reason + ") for "
                    + event.getAlertType() + " on " + event.getAgentId());
        }
    }

    @Override
    public NotificationResult sendTestMessage() {
        return NotificationResult.failure("Email notification is not configured. " + reason);
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String describe() {
        return "Email disabled: " + reason;
    }

    /** How many alerts were not e-mailed because the channel is off. */
    public long getSuppressedCount() {
        return suppressedCount;
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
