package vn.edu.hutech.group10.monitoring.common.util;

/** Small range and shape checks shared by the agent, the client and the tests. */
public final class ValidationUtil {

    private ValidationUtil() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /** True when {@code value} is a finite percentage in the inclusive range 0..100. */
    public static boolean isPercentage(double value) {
        return isFinite(value) && value >= 0.0d && value <= 100.0d;
    }

    /** Clamps a possibly noisy reading into 0..100; non-finite input becomes 0. */
    public static double clampPercentage(double value) {
        if (!isFinite(value)) {
            return 0.0d;
        }
        if (value < 0.0d) {
            return 0.0d;
        }
        return Math.min(value, 100.0d);
    }

    /** True when {@code value} is finite and not negative. */
    public static boolean isNonNegative(double value) {
        return isFinite(value) && value >= 0.0d;
    }

    /** True when {@code value} lies inside the inclusive range. */
    public static boolean inRange(long value, long minInclusive, long maxInclusive) {
        return value >= minInclusive && value <= maxInclusive;
    }

    /** Throws {@link IllegalArgumentException} when {@code value} is blank. */
    public static String requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value.trim();
    }

    /** True when {@code value} looks like an e-mail address well enough to try SMTP. */
    public static boolean looksLikeEmail(String value) {
        if (isBlank(value)) {
            return false;
        }
        String trimmed = value.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0 || at != trimmed.lastIndexOf('@') || at == trimmed.length() - 1) {
            return false;
        }
        String domain = trimmed.substring(at + 1);
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".")
                && !trimmed.contains(" ");
    }
}
