package vn.edu.hutech.group10.monitoring.monitor.notification;

/** A plain-text message ready to hand to a {@link MailTransport}. */
public class EmailMessage {

    private final String fromAddress;
    private final String fromName;
    private final String toAddress;
    private final String subject;
    private final String body;

    public EmailMessage(String fromAddress, String fromName, String toAddress,
                        String subject, String body) {
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.toAddress = toAddress;
        this.subject = subject;
        this.body = body;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public String getToAddress() {
        return toAddress;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "EmailMessage{to=" + toAddress + ", subject=" + subject + "}";
    }
}
