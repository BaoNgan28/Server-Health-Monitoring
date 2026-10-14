package vn.edu.hutech.group10.monitoring.monitor.notification;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.monitor.config.EmailConfig;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends alert e-mail through SMTP.
 *
 * <p>Delivery runs on one worker thread behind a bounded queue, so an alert
 * never blocks the UDP receiver, the Swing event dispatch thread or the
 * heartbeat timer, and a mail server that has stopped answering cannot make the
 * queue grow without limit - once it is full, further messages are dropped with
 * a warning rather than consuming memory.
 *
 * <p>An SMTP failure is logged with the server's own reason and nothing else;
 * the password never reaches a log line.
 */
public class EmailNotifier implements Notifier {

    private static final Logger LOG = Logger.getLogger(EmailNotifier.class.getName());

    /** Enough to absorb a burst of alerts, small enough to bound memory. */
    private static final int QUEUE_CAPACITY = 50;

    private final EmailConfig config;
    private final MailTransport transport;
    private final ThreadPoolExecutor executor;

    private final LongAdder sentCount = new LongAdder();
    private final LongAdder failedCount = new LongAdder();
    private final LongAdder droppedCount = new LongAdder();
    private volatile String lastError;

    public EmailNotifier(EmailConfig config, MailTransport transport) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (transport == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
        this.config = config;
        this.transport = transport;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "email-sender");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /** Builds a notifier with the real SMTP transport for {@code config}. */
    public static EmailNotifier withSmtp(EmailConfig config) {
        return new EmailNotifier(config, new SmtpMailTransport(config));
    }

    @Override
    public void sendAlert(AlertEvent event) {
        if (event == null) {
            return;
        }
        EmailMessage message = EmailMessageBuilder.forAlert(config, event);
        submit(message, "alert " + event.getAlertType() + " for " + event.getAgentId());
    }

    private void submit(EmailMessage message, String description) {
        try {
            executor.execute(() -> deliver(message, description));
        } catch (RejectedExecutionException e) {
            droppedCount.increment();
            LOG.warning("Email notification dropped, the send queue is full: " + description);
        }
    }

    private void deliver(EmailMessage message, String description) {
        try {
            transport.send(message);
            sentCount.increment();
            LOG.info("Email notification sent: " + description);
        } catch (Exception e) {
            failedCount.increment();
            // The server's reason only; never the credentials used to reach it.
            lastError = safeReason(e);
            LOG.log(Level.WARNING, "Email notification failed: " + lastError);
        }
    }

    /**
     * Sends the test message and waits for the result, because the settings
     * screen shows the outcome in a dialog. Called from a background worker, not
     * from the event dispatch thread.
     */
    @Override
    public NotificationResult sendTestMessage() {
        EmailMessage message = EmailMessageBuilder.forTest(config, System.currentTimeMillis());
        try {
            transport.send(message);
            sentCount.increment();
            LOG.info("Test email sent to " + config.getRecipient());
            return NotificationResult.success("Test email sent successfully.");
        } catch (Exception e) {
            failedCount.increment();
            lastError = safeReason(e);
            LOG.log(Level.WARNING, "Test email failed: " + lastError);
            return NotificationResult.failure("Test email could not be sent." + System.lineSeparator()
                    + "Reason: " + lastError);
        }
    }

    /** The exception's own message, with any credential-looking text removed. */
    private String safeReason(Exception e) {
        String reason = e.getMessage();
        if (reason == null || reason.isEmpty()) {
            reason = e.getClass().getSimpleName();
        }
        String password = config.getAppPassword();
        if (password != null && !password.isEmpty()) {
            reason = reason.replace(password, "***");
        }
        return reason;
    }

    @Override
    public boolean isEnabled() {
        return config.isConfigured();
    }

    @Override
    public String describe() {
        return "Email to " + config.getRecipient() + " through " + transport.describe();
    }

    public long getSentCount() {
        return sentCount.sum();
    }

    public long getFailedCount() {
        return failedCount.sum();
    }

    public long getDroppedCount() {
        return droppedCount.sum();
    }

    /** Reason of the most recent failure, or {@code null}; safe to display. */
    public String getLastError() {
        return lastError;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try {
            transport.close();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Closing the mail transport failed", e);
        }
    }
}
