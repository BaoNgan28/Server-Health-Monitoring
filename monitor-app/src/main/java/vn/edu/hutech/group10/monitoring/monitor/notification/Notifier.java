package vn.edu.hutech.group10.monitoring.monitor.notification;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;

/**
 * Remote alert channel. Implementations must never block the caller: the alert
 * service calls this from the UDP receive path and from the heartbeat timer.
 */
public interface Notifier extends AutoCloseable {

    /** Delivers {@code event}. Failures are handled inside and never thrown. */
    void sendAlert(AlertEvent event);

    /**
     * Sends a fixed test message, so an operator can prove the channel works
     * without waiting for a real incident.
     *
     * @return the outcome, never {@code null}
     */
    NotificationResult sendTestMessage();

    /** False when the channel is not configured; the GUI shows this. */
    boolean isEnabled();

    /** One line describing the channel for the settings screen. */
    String describe();

    @Override
    void close();
}
