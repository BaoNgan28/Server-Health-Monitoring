package vn.edu.hutech.group10.monitoring.monitor.notification;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Fans one alert out to several channels.
 *
 * <p>Each delegate is isolated: a channel that throws is logged and the
 * remaining channels still receive the alert, so one broken notifier cannot
 * silence the others.
 */
public class CompositeNotifier implements Notifier {

    private static final Logger LOG = Logger.getLogger(CompositeNotifier.class.getName());

    private final List<Notifier> delegates;

    public CompositeNotifier(Notifier... delegates) {
        this(delegates == null ? Collections.emptyList() : Arrays.asList(delegates));
    }

    public CompositeNotifier(List<Notifier> delegates) {
        this.delegates = new ArrayList<>();
        if (delegates != null) {
            for (Notifier delegate : delegates) {
                if (delegate != null) {
                    this.delegates.add(delegate);
                }
            }
        }
    }

    @Override
    public void sendAlert(AlertEvent event) {
        for (Notifier delegate : delegates) {
            try {
                delegate.sendAlert(event);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Notifier " + delegate.getClass().getSimpleName()
                        + " failed to handle an alert", e);
            }
        }
    }

    /** Sends through every enabled channel; succeeds when at least one does. */
    @Override
    public NotificationResult sendTestMessage() {
        if (delegates.isEmpty()) {
            return NotificationResult.failure("No notification channel is configured.");
        }
        List<String> failures = new ArrayList<>();
        boolean anySuccess = false;
        for (Notifier delegate : delegates) {
            try {
                NotificationResult result = delegate.sendTestMessage();
                if (result.isSuccess()) {
                    anySuccess = true;
                } else {
                    failures.add(result.getMessage());
                }
            } catch (RuntimeException e) {
                failures.add(delegate.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        if (anySuccess) {
            return NotificationResult.success("Test message sent successfully.");
        }
        return NotificationResult.failure(String.join(System.lineSeparator(), failures));
    }

    @Override
    public boolean isEnabled() {
        return delegates.stream().anyMatch(Notifier::isEnabled);
    }

    @Override
    public String describe() {
        if (delegates.isEmpty()) {
            return "No notification channel";
        }
        return delegates.stream().map(Notifier::describe).collect(Collectors.joining("; "));
    }

    public List<Notifier> getDelegates() {
        return Collections.unmodifiableList(delegates);
    }

    @Override
    public void close() {
        for (Notifier delegate : delegates) {
            try {
                delegate.close();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Closing a notifier failed", e);
            }
        }
    }
}
