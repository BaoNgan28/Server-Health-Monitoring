package vn.edu.hutech.group10.monitoring.monitor.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.common.model.AlertSeverity;
import vn.edu.hutech.group10.monitoring.common.model.AlertType;
import vn.edu.hutech.group10.monitoring.monitor.config.EmailConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the notifier through a stub transport.
 *
 * <p>No live SMTP session is opened and no real App Password appears anywhere in
 * these sources; delivery against Gmail is a manual step described in
 * {@code docs/EMAIL_SETUP.md}.
 */
class EmailNotifierTest {

    private static final long NOW = 1_700_000_000_000L;

    /** Records messages instead of sending them; can be told to fail. */
    private static final class StubTransport implements MailTransport {
        private final List<EmailMessage> sent = new ArrayList<>();
        private final AtomicInteger failures = new AtomicInteger();
        private volatile boolean shouldFail;
        private volatile String failureMessage = "stub failure";

        @Override
        public synchronized void send(EmailMessage message) throws Exception {
            if (shouldFail) {
                failures.incrementAndGet();
                throw new IllegalStateException(failureMessage);
            }
            sent.add(message);
        }

        @Override
        public String describe() {
            return "stub-smtp";
        }

        synchronized List<EmailMessage> messages() {
            return new ArrayList<>(sent);
        }
    }

    private static EmailConfig configured() {
        return EmailConfig.configured("smtp.gmail.com", 587, true, true,
                "bocon2663@gmail.com", "not-a-real-password", "ops@example.com");
    }

    private static AlertEvent cpuAlert() {
        AlertEvent event = new AlertEvent("AGENT-01", "LOCAL-AGENT-01", AlertType.CPU_HIGH,
                AlertSeverity.WARNING, "CPU HIGH is 25.4 %, above the threshold of 5.0 %", NOW);
        event.setCurrentValue(25.4d);
        event.setThreshold(5.0d);
        event.setUnit("%");
        return event;
    }

    /** Waits for the notifier's single worker thread to drain. */
    private static void waitForDelivery(StubTransport transport, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && transport.messages().size() < expected) {
            Thread.sleep(20L);
        }
    }

    @Test
    @DisplayName("An alert is handed to the transport with the agreed subject and body")
    void sendsAnAlert() throws Exception {
        StubTransport transport = new StubTransport();
        try (EmailNotifier notifier = new EmailNotifier(configured(), transport)) {
            notifier.sendAlert(cpuAlert());
            waitForDelivery(transport, 1);

            List<EmailMessage> messages = transport.messages();
            assertEquals(1, messages.size());
            EmailMessage message = messages.get(0);
            assertAll(
                    () -> assertEquals("[WARNING] CPU HIGH - AGENT-01", message.getSubject()),
                    () -> assertEquals("ops@example.com", message.getToAddress()),
                    () -> assertEquals("bocon2663@gmail.com", message.getFromAddress()),
                    () -> assertEquals("Group 10 Server Health Monitoring", message.getFromName()),
                    () -> assertTrue(message.getBody().contains("AGENT-01")),
                    () -> assertTrue(message.getBody().contains("25.4 %")),
                    () -> assertTrue(message.getBody().contains("5.0 %")));
        }
    }

    @Test
    @DisplayName("Sending does not block the caller")
    void deliveryIsAsynchronous() throws Exception {
        StubTransport transport = new StubTransport();
        try (EmailNotifier notifier = new EmailNotifier(configured(), transport)) {
            long startedAt = System.nanoTime();
            for (int i = 0; i < 20; i++) {
                notifier.sendAlert(cpuAlert());
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertTrue(elapsedMs < 1000L,
                    "queueing 20 alerts must be immediate, took " + elapsedMs + " ms");
            waitForDelivery(transport, 20);
            assertEquals(20, transport.messages().size());
        }
    }

    @Test
    @DisplayName("A transport failure is counted and never thrown at the caller")
    void transportFailureIsContained() throws Exception {
        StubTransport transport = new StubTransport();
        transport.shouldFail = true;

        try (EmailNotifier notifier = new EmailNotifier(configured(), transport)) {
            notifier.sendAlert(cpuAlert());

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && notifier.getFailedCount() == 0) {
                Thread.sleep(20L);
            }

            assertAll(
                    () -> assertEquals(1L, notifier.getFailedCount()),
                    () -> assertEquals(0L, notifier.getSentCount()),
                    () -> assertEquals("stub failure", notifier.getLastError()));
        }
    }

    @Test
    @DisplayName("A failure message never repeats the password")
    void failureMessageHidesThePassword() throws Exception {
        StubTransport transport = new StubTransport();
        transport.shouldFail = true;
        transport.failureMessage = "535 rejected credential not-a-real-password for user";

        try (EmailNotifier notifier = new EmailNotifier(configured(), transport)) {
            NotificationResult result = notifier.sendTestMessage();

            assertAll(
                    () -> assertFalse(result.isSuccess()),
                    () -> assertFalse(result.getMessage().contains("not-a-real-password"),
                            "the password must be masked: " + result.getMessage()),
                    () -> assertTrue(result.getMessage().contains("***")));
        }
    }

    @Test
    @DisplayName("The test message carries the agreed subject and body")
    void sendsTheTestMessage() {
        StubTransport transport = new StubTransport();
        try (EmailNotifier notifier = new EmailNotifier(configured(), transport)) {
            NotificationResult result = notifier.sendTestMessage();

            assertTrue(result.isSuccess(), result.getMessage());
            EmailMessage message = transport.messages().get(0);
            assertAll(
                    () -> assertEquals("[TEST] Group 10 Server Health Monitoring",
                            message.getSubject()),
                    () -> assertTrue(message.getBody().contains("This is a test notification.")),
                    () -> assertTrue(message.getBody().contains("bocon2663@gmail.com")),
                    () -> assertTrue(message.getBody().contains("ops@example.com")),
                    () -> assertEquals("Test email sent successfully.", result.getMessage()));
        }
    }

    @Test
    @DisplayName("The no-op notifier reports that it is off and sends nothing")
    void noOpNotifierIsSilent() {
        NoOpNotifier notifier = new NoOpNotifier("smtp.appPassword is still the placeholder");

        notifier.sendAlert(cpuAlert());
        notifier.sendAlert(cpuAlert());

        assertAll(
                () -> assertFalse(notifier.isEnabled()),
                () -> assertEquals(2L, notifier.getSuppressedCount()),
                () -> assertFalse(notifier.sendTestMessage().isSuccess()),
                () -> assertTrue(notifier.describe().contains("placeholder")));
        notifier.close();
    }

    @Test
    @DisplayName("The composite fans out and survives a failing channel")
    void compositeIsolatesFailures() throws Exception {
        StubTransport transport = new StubTransport();
        try (EmailNotifier working = new EmailNotifier(configured(), transport);
             CompositeNotifier composite = new CompositeNotifier(new ThrowingNotifier(), working)) {

            composite.sendAlert(cpuAlert());
            waitForDelivery(transport, 1);

            assertAll(
                    () -> assertEquals(1, transport.messages().size(),
                            "the working channel still received the alert"),
                    () -> assertTrue(composite.isEnabled()),
                    () -> assertEquals(2, composite.getDelegates().size()));
        }
    }

    @Test
    @DisplayName("A composite with no channel reports failure rather than silence")
    void emptyCompositeReportsFailure() {
        try (CompositeNotifier composite = new CompositeNotifier()) {
            assertAll(
                    () -> assertFalse(composite.isEnabled()),
                    () -> assertFalse(composite.sendTestMessage().isSuccess()));
        }
    }

    /** A channel that always blows up, to prove the composite contains it. */
    private static final class ThrowingNotifier implements Notifier {
        @Override
        public void sendAlert(AlertEvent event) {
            throw new IllegalStateException("this channel is broken");
        }

        @Override
        public NotificationResult sendTestMessage() {
            throw new IllegalStateException("this channel is broken");
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public String describe() {
            return "broken";
        }

        @Override
        public void close() {
            // Nothing to release.
        }
    }
}
