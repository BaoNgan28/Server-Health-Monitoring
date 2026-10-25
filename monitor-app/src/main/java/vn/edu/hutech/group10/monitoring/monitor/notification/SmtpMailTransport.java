package vn.edu.hutech.group10.monitoring.monitor.notification;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import vn.edu.hutech.group10.monitoring.monitor.config.EmailConfig;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Sends mail through an SMTP server with STARTTLS, which is what Gmail expects
 * on port 587.
 *
 * <p>The app password is read from {@link EmailConfig} at send time and handed
 * straight to the authenticator. It is never written to a log, a properties dump
 * or an exception message.
 */
public class SmtpMailTransport implements MailTransport {

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private final EmailConfig config;

    public SmtpMailTransport(EmailConfig config) {
        if (config == null || !config.isConfigured()) {
            throw new IllegalArgumentException("SmtpMailTransport needs a configured EmailConfig");
        }
        this.config = config;
    }

    @Override
    public void send(EmailMessage message) throws Exception {
        Session session = Session.getInstance(buildProperties(), new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.getUsername(), config.getAppPassword());
            }
        });

        MimeMessage mime = new MimeMessage(session);
        mime.setFrom(fromAddress(message));
        mime.setRecipients(Message.RecipientType.TO,
                InternetAddress.parse(message.getToAddress(), false));
        mime.setSubject(message.getSubject(), StandardCharsets.UTF_8.name());
        mime.setText(message.getBody(), StandardCharsets.UTF_8.name());
        mime.setSentDate(new java.util.Date());

        Transport.send(mime);
    }

    private InternetAddress fromAddress(EmailMessage message) throws UnsupportedEncodingException {
        String address = message.getFromAddress() == null
                ? config.getUsername() : message.getFromAddress();
        String name = message.getFromName() == null ? config.getSenderName() : message.getFromName();
        return new InternetAddress(address, name, StandardCharsets.UTF_8.name());
    }

    private Properties buildProperties() {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", config.getHost());
        properties.put("mail.smtp.port", String.valueOf(config.getPort()));
        properties.put("mail.smtp.auth", String.valueOf(config.isAuth()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(config.isStartTls()));
        if (config.isStartTls()) {
            // Refuse to fall back to an unencrypted session if STARTTLS fails.
            properties.put("mail.smtp.starttls.required", "true");
        }
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        // Without these a network problem would hang the sending thread forever.
        properties.put("mail.smtp.connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        properties.put("mail.smtp.timeout", String.valueOf(READ_TIMEOUT_MS));
        properties.put("mail.smtp.writetimeout", String.valueOf(READ_TIMEOUT_MS));
        return properties;
    }

    @Override
    public String describe() {
        return config.describeServer();
    }
}
