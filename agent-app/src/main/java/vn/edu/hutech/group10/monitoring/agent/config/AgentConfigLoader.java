package vn.edu.hutech.group10.monitoring.agent.config;

import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.common.util.ValidationUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Reads an agent properties file into an {@link AgentConfig}.
 *
 * <p>Only {@code agent.id} and {@code security.sharedSecret} are mandatory;
 * everything else falls back to the protocol defaults so that a minimal file is
 * enough to bring an agent up. Every value is range checked here rather than
 * failing later inside a scheduler.
 */
public final class AgentConfigLoader {

    public static final String KEY_AGENT_ID = "agent.id";
    public static final String KEY_AGENT_NAME = "agent.name";
    public static final String KEY_MONITOR_HOST = "monitor.host";
    public static final String KEY_MONITOR_UDP_PORT = "monitor.udp.port";
    public static final String KEY_AGENT_TCP_PORT = "agent.tcp.port";
    public static final String KEY_METRIC_INTERVAL = "metric.interval.ms";
    public static final String KEY_HEARTBEAT_INTERVAL = "heartbeat.interval.ms";
    public static final String KEY_SHARED_SECRET = "security.sharedSecret";
    public static final String KEY_CPU_THRESHOLD = "alert.cpu.threshold";
    public static final String KEY_MEMORY_THRESHOLD = "alert.memory.threshold";
    public static final String KEY_NET_IN_THRESHOLD = "alert.network.in.threshold.mbps";
    public static final String KEY_NET_OUT_THRESHOLD = "alert.network.out.threshold.mbps";

    private AgentConfigLoader() {
    }

    /** Loads and validates the properties file at {@code path}. */
    public static AgentConfig load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Configuration path must not be null");
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("Agent configuration file not found: " + path.toAbsolutePath());
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        }
        return fromProperties(properties);
    }

    /** Builds a configuration from already loaded properties. */
    public static AgentConfig fromProperties(Properties properties) {
        String agentId = requireText(properties, KEY_AGENT_ID);
        String sharedSecret = requireText(properties, KEY_SHARED_SECRET);

        String agentName = text(properties, KEY_AGENT_NAME, agentId);
        String monitorHost = text(properties, KEY_MONITOR_HOST, "127.0.0.1");

        int monitorUdpPort = port(properties, KEY_MONITOR_UDP_PORT,
                ProtocolConstants.DEFAULT_MONITOR_UDP_PORT);
        int agentTcpPort = port(properties, KEY_AGENT_TCP_PORT,
                ProtocolConstants.DEFAULT_AGENT_TCP_PORT);

        long metricInterval = interval(properties, KEY_METRIC_INTERVAL,
                ProtocolConstants.DEFAULT_METRIC_INTERVAL_MS, ProtocolConstants.MIN_METRIC_INTERVAL_MS);
        long heartbeatInterval = interval(properties, KEY_HEARTBEAT_INTERVAL,
                ProtocolConstants.DEFAULT_HEARTBEAT_INTERVAL_MS,
                ProtocolConstants.MIN_HEARTBEAT_INTERVAL_MS);

        double cpuThreshold = percentage(properties, KEY_CPU_THRESHOLD, 85.0d);
        double memoryThreshold = percentage(properties, KEY_MEMORY_THRESHOLD, 85.0d);
        double netIn = rate(properties, KEY_NET_IN_THRESHOLD, 50.0d);
        double netOut = rate(properties, KEY_NET_OUT_THRESHOLD, 50.0d);

        return new AgentConfig(agentId, agentName, resolveHostname(),
                monitorHost, monitorUdpPort, agentTcpPort, sharedSecret,
                metricInterval, heartbeatInterval,
                cpuThreshold, memoryThreshold, netIn, netOut);
    }

    /** Local host name, falling back to a constant when name resolution fails. */
    public static String resolveHostname() {
        try {
            String name = InetAddress.getLocalHost().getHostName();
            return ValidationUtil.isBlank(name) ? "UNKNOWN-HOST" : name;
        } catch (UnknownHostException e) {
            String env = System.getenv("COMPUTERNAME");
            return ValidationUtil.isBlank(env) ? "UNKNOWN-HOST" : env;
        }
    }

    private static String requireText(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (ValidationUtil.isBlank(value)) {
            throw new IllegalArgumentException("Missing required property '" + key + "'");
        }
        return value.trim();
    }

    private static String text(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return ValidationUtil.isBlank(value) ? fallback : value.trim();
    }

    private static int port(Properties properties, String key, int fallback) {
        String raw = properties.getProperty(key);
        if (ValidationUtil.isBlank(raw)) {
            return fallback;
        }
        int value = parseInt(key, raw);
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException("Property '" + key + "' must be a port in 1..65535, got " + value);
        }
        return value;
    }

    private static long interval(Properties properties, String key, long fallback, long minimum) {
        String raw = properties.getProperty(key);
        if (ValidationUtil.isBlank(raw)) {
            return fallback;
        }
        long value = parseLong(key, raw);
        if (!ValidationUtil.inRange(value, minimum, ProtocolConstants.MAX_INTERVAL_MS)) {
            throw new IllegalArgumentException("Property '" + key + "' must be between " + minimum
                    + " and " + ProtocolConstants.MAX_INTERVAL_MS + " ms, got " + value);
        }
        return value;
    }

    private static double percentage(Properties properties, String key, double fallback) {
        String raw = properties.getProperty(key);
        if (ValidationUtil.isBlank(raw)) {
            return fallback;
        }
        double value = parseDouble(key, raw);
        if (!ValidationUtil.isPercentage(value)) {
            throw new IllegalArgumentException("Property '" + key
                    + "' must be a percentage in 0..100, got " + value);
        }
        return value;
    }

    private static double rate(Properties properties, String key, double fallback) {
        String raw = properties.getProperty(key);
        if (ValidationUtil.isBlank(raw)) {
            return fallback;
        }
        double value = parseDouble(key, raw);
        if (!ValidationUtil.isNonNegative(value)) {
            throw new IllegalArgumentException("Property '" + key
                    + "' must be a non-negative rate in Mbps, got " + value);
        }
        return value;
    }

    private static int parseInt(String key, String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Property '" + key + "' is not an integer: " + raw);
        }
    }

    private static long parseLong(String key, String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Property '" + key + "' is not an integer: " + raw);
        }
    }

    private static double parseDouble(String key, String raw) {
        try {
            // Locale.ROOT parsing: the file always uses a dot as the decimal separator.
            return Double.parseDouble(raw.trim().toLowerCase(Locale.ROOT));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Property '" + key + "' is not a number: " + raw);
        }
    }
}
