package vn.edu.hutech.group10.monitoring.common.util;

import java.util.Locale;

/**
 * Renders network throughput for the GUI and for e-mail bodies, and converts
 * between the bytes-per-second the wire carries and the Mbps the thresholds use.
 *
 * <p>Throughput uses decimal units (1 kB/s = 1000 B/s), which is what network
 * equipment reports, while memory sizes use binary units.
 */
public final class ByteRateFormatter {

    private static final double BITS_PER_BYTE = 8.0d;
    private static final double BITS_PER_MEGABIT = 1_000_000.0d;

    private ByteRateFormatter() {
    }

    /** Formats a byte rate as {@code 1.23 MB/s}. */
    public static String formatRate(double bytesPerSecond) {
        if (!ValidationUtil.isFinite(bytesPerSecond) || bytesPerSecond < 0) {
            return "-";
        }
        if (bytesPerSecond < 1000.0d) {
            return String.format(Locale.ROOT, "%.0f B/s", bytesPerSecond);
        }
        if (bytesPerSecond < 1_000_000.0d) {
            return String.format(Locale.ROOT, "%.2f kB/s", bytesPerSecond / 1000.0d);
        }
        if (bytesPerSecond < 1_000_000_000.0d) {
            return String.format(Locale.ROOT, "%.2f MB/s", bytesPerSecond / 1_000_000.0d);
        }
        return String.format(Locale.ROOT, "%.2f GB/s", bytesPerSecond / 1_000_000_000.0d);
    }

    /** Formats a byte rate as {@code 9.87 Mbps}. */
    public static String formatMbps(double bytesPerSecond) {
        if (!ValidationUtil.isFinite(bytesPerSecond) || bytesPerSecond < 0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.3f Mbps", toMbps(bytesPerSecond));
    }

    /** Converts bytes per second to megabits per second. */
    public static double toMbps(double bytesPerSecond) {
        return bytesPerSecond * BITS_PER_BYTE / BITS_PER_MEGABIT;
    }

    /** Converts megabits per second to bytes per second. */
    public static double toBytesPerSecond(double mbps) {
        return mbps * BITS_PER_MEGABIT / BITS_PER_BYTE;
    }

    /** Formats a byte total as {@code 15.9 GiB}, using binary units. */
    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "-";
        }
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024.0d && unit < units.length - 1) {
            value /= 1024.0d;
            unit++;
        }
        return String.format(Locale.ROOT, unit == 0 ? "%.0f %s" : "%.2f %s", value, units[unit]);
    }
}
