package vn.edu.hutech.group10.monitoring.monitor.config;

/**
 * SMTP settings for alert e-mail.
 *
 * <p>{@link #isConfigured()} is the only thing the rest of the application asks:
 * when it is false the notifier is disabled and nothing ever attempts a login,
 * which is what keeps a checked-out project with placeholder credentials running
 * normally.
 *
 * <p>The password is held only in this object, is never logged, never shown in
 * the GUI and never leaves the process except inside the SMTP handshake.
 */
public class EmailConfig {

    /** Shown as the display name of the sender. */
    public static final String SENDER_NAME = "Group 10 Server Health Monitoring";

    private final String host;
    private final int port;
    private final boolean startTls;
    private final boolean auth;

    private final String username;
    private final String appPassword;
    private final String recipient;

    private final boolean configured;
    private final String disabledReason;

    private EmailConfig(String host, int port, boolean startTls, boolean auth, String username,
                        String appPassword, String recipient, boolean configured,
                        String disabledReason) {
        this.host = host;
        this.port = port;
        this.startTls = startTls;
        this.auth = auth;
        this.username = username;
        this.appPassword = appPassword;
        this.recipient = recipient;
        this.configured = configured;
        this.disabledReason = disabledReason;
    }

    /** A usable configuration. */
    public static EmailConfig configured(String host, int port, boolean startTls, boolean auth,
                                         String username, String appPassword, String recipient) {
        return new EmailConfig(host, port, startTls, auth, username, appPassword, recipient,
                true, null);
    }

    /** An incomplete configuration; e-mail is switched off and the reason is kept for the GUI. */
    public static EmailConfig disabled(String host, int port, boolean startTls, boolean auth,
                                       String username, String recipient, String reason) {
        return new EmailConfig(host, port, startTls, auth, username, null, recipient, false, reason);
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Why e-mail is off, phrased for the GUI; {@code null} when it is on. */
    public String getDisabledReason() {
        return disabledReason;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isStartTls() {
        return startTls;
    }

    public boolean isAuth() {
        return auth;
    }

    public String getUsername() {
        return username;
    }

    /** Never log or display this value. */
    public String getAppPassword() {
        return appPassword;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSenderName() {
        return SENDER_NAME;
    }

    /** For example {@code smtp.gmail.com:587 STARTTLS}. */
    public String describeServer() {
        return host + ":" + port + (startTls ? " STARTTLS" : " plain");
    }

    @Override
    public String toString() {
        return "EmailConfig{server=" + describeServer()
                + ", username=" + username
                + ", recipient=" + recipient
                + ", configured=" + configured + "}";
    }
}
