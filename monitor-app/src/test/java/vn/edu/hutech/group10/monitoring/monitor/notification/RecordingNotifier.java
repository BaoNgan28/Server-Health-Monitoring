package vn.edu.hutech.group10.monitoring.monitor.notification;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.common.model.AlertType;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double that records what would have been sent, so the alert rules can be
 * asserted without any SMTP traffic.
 */
public class RecordingNotifier implements Notifier {

    private final List<AlertEvent> sent = new ArrayList<>();
    private boolean enabled = true;
    private int testMessagesSent;

    @Override
    public void sendAlert(AlertEvent event) {
        sent.add(event);
    }

    @Override
    public NotificationResult sendTestMessage() {
        testMessagesSent++;
        return enabled ? NotificationResult.success("recorded")
                : NotificationResult.failure("disabled");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String describe() {
        return "Recording notifier";
    }

    public List<AlertEvent> getSent() {
        return sent;
    }

    public int countOf(AlertType type) {
        return (int) sent.stream().filter(event -> event.getAlertType() == type).count();
    }

    public int getTestMessagesSent() {
        return testMessagesSent;
    }

    public void clear() {
        sent.clear();
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
