package vn.edu.hutech.group10.monitoring.monitor.config;

import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.common.util.AppPaths;
import vn.edu.hutech.group10.monitoring.common.util.ValidationUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Reads {@code monitor.properties} (or {@code monitor-demo.properties}) into a
 * {@link MonitorConfig}.
 *
 * <p>Retention and bucket sizes may be written in seconds, minutes or days; the
 * loader converts all of them to milliseconds, and a value in seconds wins when
 * both forms are present. That is what lets the demo profile shrink an hour of
 * raw retention to a minute without any change to the aggregation code.
 */
public final class MonitorConfigLoader {

    private static final Logger LOG = Logger.getLogger(MonitorConfigLoader.class.getName());

    public static final String KEY_UDP_PORT = "monitor.udp.port";
    public static final String KEY_HEARTBEAT_TIMEOUT = "heartbeat.timeout.ms";
    public static final String KEY_HEARTBEAT_CHECK = "heartbeat.check.interval.ms";
    public static final String KEY_TIMESTAMP_TOLERANCE = "packet.timestamp.tolerance.ms";
    public static final String KEY_DATABASE_PATH = "database.path";

    public static final String KEY_RAW_RETENTION_SECONDS = "raw.retention.seconds";
    public static final String KEY_RAW_RETENTION_MINUTES = "raw.retention.minutes";
    public static final String KEY_BUCKET_SECONDS = "aggregation.bucket.seconds";
    public static final String KEY_BUCKET_MINUTES = "aggregation.bucket.minutes";
    public static final String KEY_AGGREGATE_RETENTION_SECONDS = "aggregate.retention.seconds";
    public static final String KEY_AGGREGATE_RETENTION_DAYS = "aggregate.retention.days";
    public static final String KEY_AGGREGATION_INTERVAL_SECONDS = "aggregation.interval.seconds";

    public static final String KEY_CHART_WINDOW = "chart.window.samples";
    public static final String KEY_TCP_TIMEOUT = "tcp.timeout.ms";
    public static final String KEY_PROFILE = "profile.name";

    private static final int MIN_CHART_WINDOW = 60;
    private static final int MAX_CHART_WINDOW = 300;

    private MonitorConfigLoader() {
    }

    /**
     * Loads {@code path}. A missing file is not an error: the built-in
     * production defaults are used and a line is logged, so the client always
     * starts.
     */
    public static MonitorConfig load(Path path) {
        Properties properties = new Properties();
        if (path != null && Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
                LOG.info("Loaded monitor configuration from " + path.toAbsolutePath());
            } catch (IOException e) {
                LOG.warning("Could not read " + path.toAbsolutePath()
                        + " (" + e.getMessage() + "); using defaults");
            }
        } else {
            LOG.warning("Monitor configuration file not found"
                    + (path == null ? "" : " at " + path.toAbsolutePath()) + "; using defaults");
        }
        return fromProperties(properties);
    }

    /** Builds a configuration from already loaded properties, filling in defaults. */
    public static MonitorConfig fromProperties(Properties properties) {
        int udpPort = intValue(properties, KEY_UDP_PORT,
                ProtocolConstants.DEFAULT_MONITOR_UDP_PORT, 1, 65535);
        long heartbeatTimeout = longValue(properties, KEY_HEARTBEAT_TIMEOUT,
                ProtocolConstants.DEFAULT_HEARTBEAT_TIMEOUT_MS, 1000L, ProtocolConstants.MAX_INTERVAL_MS);
        long heartbeatCheck = longValue(properties, KEY_HEARTBEAT_CHECK,
                2000L, 200L, ProtocolConstants.MAX_INTERVAL_MS);
        long tolerance = longValue(properties, KEY_TIMESTAMP_TOLERANCE,
                ProtocolConstants.DEFAULT_TIMESTAMP_TOLERANCE_MS, 1000L, ProtocolConstants.MAX_INTERVAL_MS);

        String databaseText = text(properties, KEY_DATABASE_PATH, "data/monitoring.db");
        Path databasePath = resolveAgainstHome(databaseText);

        long rawRetention = duration(properties,
                KEY_RAW_RETENTION_SECONDS, 1000L,
                KEY_RAW_RETENTION_MINUTES, 60_000L,
                60L * 60_000L);
        long bucket = duration(properties,
                KEY_BUCKET_SECONDS, 1000L,
                KEY_BUCKET_MINUTES, 60_000L,
                5L * 60_000L);
        long aggregateRetention = duration(properties,
                KEY_AGGREGATE_RETENTION_SECONDS, 1000L,
                KEY_AGGREGATE_RETENTION_DAYS, 86_400_000L,
                30L * 86_400_000L);

        // Run the job often enough that a bucket is never far behind, but never
        // more often than once per bucket.
        long defaultAggregationInterval = Math.max(10_000L, Math.min(bucket, 300_000L));
        long aggregationInterval = 1000L * longValue(properties, KEY_AGGREGATION_INTERVAL_SECONDS,
                defaultAggregationInterval / 1000L, 5L, 3600L);

        int chartWindow = intValue(properties, KEY_CHART_WINDOW, 120, MIN_CHART_WINDOW, MAX_CHART_WINDOW);
        int tcpTimeout = intValue(properties, KEY_TCP_TIMEOUT,
                ProtocolConstants.DEFAULT_TCP_TIMEOUT_MS, 500, 60_000);
        String profile = text(properties, KEY_PROFILE, "production");

        return new MonitorConfig(udpPort, heartbeatTimeout, heartbeatCheck, tolerance,
                databasePath, rawRetention, bucket, aggregateRetention,
                aggregationInterval, chartWindow, tcpTimeout, profile);
    }

    /** Relative database paths hang off the project root, so a release stays portable. */
    private static Path resolveAgainstHome(String value) {
        Path candidate = Path.of(value);
        return candidate.isAbsolute() ? candidate.normalize() : AppPaths.home().resolve(candidate).normalize();
    }

    /**
     * Reads a duration written either in the fine-grained unit (seconds) or the
     * coarse one (minutes or days). The fine-grained key wins, which is how the
     * demo profile overrides a production value.
     */
    private static long duration(Properties properties,
                                 String fineKey, long fineUnitMs,
                                 String coarseKey, long coarseUnitMs,
                                 long fallbackMs) {
        String fine = properties.getProperty(fineKey);
        if (!ValidationUtil.isBlank(fine)) {
            return positive(fineKey, fine) * fineUnitMs;
        }
        String coarse = properties.getProperty(coarseKey);
        if (!ValidationUtil.isBlank(coarse)) {
            return positive(coarseKey, coarse) * coarseUnitMs;
        }
        return fallbackMs;
    }

    private static long positive(String key, String raw) {
        long value = parseLong(key, raw);
        if (value <= 0L) {
            throw new IllegalArgumentException("Property '" + key + "' must be positive, got " + value);
        }
        return value;
    }

    private static String text(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return ValidationUtil.isBlank(value) ? fallback : value.trim();
    }

    private static int intValue(Properties properties, String key, int fallback, int min, int max) {
        String raw = properties.getProperty(key);
        if (ValidationUtil.isBlank(raw)) {
            return fallback;
        }
        long value = parseLong(key, raw);
        if (value < min || value > max) {
            throw new IllegalArgumentException("Property '" + key + "' must be between "
                    + min + " and " + max + ", got " + value);
        }
        return (int) value;
    }

    private static long longValue(Properties properties, String key, long fallback, long min, long max) {
        String raw = properties.getProperty(key);
        if (ValidationUtil.isBlank(raw)) {
            return fallback;
        }
        long value = parseLong(key, raw);
        if (value < min || value > max) {
            throw new IllegalArgumentException("Property '" + key + "' must be between "
                    + min + " and " + max + ", got " + value);
        }
        return value;
    }

    private static long parseLong(String key, String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Property '" + key + "' is not an integer: " + raw);
        }
    }
}
