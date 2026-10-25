package vn.edu.hutech.group10.monitoring.monitor.notification;

/**
 * The step that actually talks to an SMTP server.
 *
 * <p>Separated from {@link EmailNotifier} so the notifier - subjects, bodies,
 * queueing, failure handling - can be unit tested with a stub, without a live
 * Gmail login and without a real password in the test sources.
 */
public interface MailTransport extends AutoCloseable {

    /**
     * Delivers one message.
     *
     * @throws Exception when the server refuses it or cannot be reached
     */
    void send(EmailMessage message) throws Exception;

    /** One line naming the server, for the settings screen. */
    String describe();

    @Override
    default void close() {
        // Nothing to release by default; SMTP connections are per send.
    }
}
