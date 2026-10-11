package vn.edu.hutech.group10.monitoring.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatting helpers for epoch-millisecond timestamps and durations. */
public final class TimeUtil {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private static final DateTimeFormatter TIME_ONLY_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private TimeUtil() {
    }

    /** Renders epoch millis in the local zone, or {@code "-"} for a zero timestamp. */
    public static String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    /** Renders only the clock time, for chart axes and log lines. */
    public static String formatTimeOnly(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return TIME_ONLY_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    /** Renders a machine uptime as {@code 3d 04h 17m 09s}. */
    public static String formatUptime(long seconds) {
        if (seconds < 0) {
            return "-";
        }
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (days > 0) {
            return String.format(Locale.ROOT, "%dd %02dh %02dm %02ds", days, hours, minutes, secs);
        }
        if (hours > 0) {
            return String.format(Locale.ROOT, "%02dh %02dm %02ds", hours, minutes, secs);
        }
        return String.format(Locale.ROOT, "%02dm %02ds", minutes, secs);
    }

    /** Renders a duration in milliseconds as a short human string. */
    public static String formatDuration(long millis) {
        if (millis < 0) {
            return "-";
        }
        if (millis < 1000L) {
            return millis + " ms";
        }
        return formatUptime(millis / 1000L);
    }

    /**
     * Start of the bucket that {@code epochMillis} falls into, for a bucket of
     * {@code bucketMillis}. Buckets are aligned to the epoch, so every process
     * computing them agrees.
     */
    public static long bucketStart(long epochMillis, long bucketMillis) {
        if (bucketMillis <= 0) {
            throw new IllegalArgumentException("bucketMillis must be positive");
        }
        return Math.floorDiv(epochMillis, bucketMillis) * bucketMillis;
    }
}
