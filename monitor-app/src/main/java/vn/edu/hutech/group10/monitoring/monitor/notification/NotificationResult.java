package vn.edu.hutech.group10.monitoring.monitor.notification;

/**
 * Outcome of a delivery attempt.
 *
 * <p>The message is written for a dialog box, so it explains what to do next and
 * never repeats a credential.
 */
public class NotificationResult {

    private final boolean success;
    private final String message;

    private NotificationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static NotificationResult success(String message) {
        return new NotificationResult(true, message);
    }

    public static NotificationResult failure(String message) {
        return new NotificationResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return (success ? "OK: " : "FAILED: ") + message;
    }
}
