package vn.edu.hutech.group10.monitoring.common.security;

import vn.edu.hutech.group10.monitoring.common.model.HeartbeatPacket;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;

import java.util.Locale;

/**
 * Builds the deterministic string that is actually signed.
 *
 * <p>Rules, all of which the agent and the monitoring client must apply
 * identically or every packet would be rejected:
 * <ul>
 *   <li>fields appear in a fixed order, joined by {@code '|'};</li>
 *   <li>the {@code hmac} field is never included;</li>
 *   <li>doubles are rendered with {@link Locale#ROOT} and exactly four decimal
 *       places, so a machine with a comma decimal separator produces the same
 *       bytes as one with a dot;</li>
 *   <li>a null string becomes the empty string rather than the text "null".</li>
 * </ul>
 */
public final class PacketCanonicalizer {

    private static final char SEPARATOR = '|';
    private static final String DOUBLE_FORMAT = "%.4f";

    private PacketCanonicalizer() {
    }

    /**
     * Canonical form of a telemetry packet:
     * {@code protocolVersion|type|agentId|hostname|sessionId|timestamp|sequence|
     * cpuUsage|memoryUsage|connectionCount|uptimeSeconds|
     * networkInBytesPerSecond|networkOutBytesPerSecond}.
     */
    public static String canonicalize(TelemetryPacket packet) {
        if (packet == null) {
            throw new IllegalArgumentException("Packet must not be null");
        }
        StringBuilder sb = new StringBuilder(160);
        sb.append(nullSafe(packet.getProtocolVersion())).append(SEPARATOR);
        sb.append(nullSafe(packet.getType())).append(SEPARATOR);
        sb.append(nullSafe(packet.getAgentId())).append(SEPARATOR);
        sb.append(nullSafe(packet.getHostname())).append(SEPARATOR);
        sb.append(nullSafe(packet.getSessionId())).append(SEPARATOR);
        sb.append(packet.getTimestamp()).append(SEPARATOR);
        sb.append(packet.getSequence()).append(SEPARATOR);
        sb.append(formatDouble(packet.getCpuUsage())).append(SEPARATOR);
        sb.append(formatDouble(packet.getMemoryUsage())).append(SEPARATOR);
        sb.append(packet.getConnectionCount()).append(SEPARATOR);
        sb.append(packet.getUptimeSeconds()).append(SEPARATOR);
        sb.append(formatDouble(packet.getNetworkInBytesPerSecond())).append(SEPARATOR);
        sb.append(formatDouble(packet.getNetworkOutBytesPerSecond()));
        return sb.toString();
    }

    /**
     * Canonical form of a heartbeat packet:
     * {@code protocolVersion|type|agentId|hostname|sessionId|timestamp|sequence}.
     */
    public static String canonicalize(HeartbeatPacket packet) {
        if (packet == null) {
            throw new IllegalArgumentException("Packet must not be null");
        }
        StringBuilder sb = new StringBuilder(80);
        sb.append(nullSafe(packet.getProtocolVersion())).append(SEPARATOR);
        sb.append(nullSafe(packet.getType())).append(SEPARATOR);
        sb.append(nullSafe(packet.getAgentId())).append(SEPARATOR);
        sb.append(nullSafe(packet.getHostname())).append(SEPARATOR);
        sb.append(nullSafe(packet.getSessionId())).append(SEPARATOR);
        sb.append(packet.getTimestamp()).append(SEPARATOR);
        sb.append(packet.getSequence());
        return sb.toString();
    }

    /**
     * Renders a double the one way both sides agree on. Non-finite values are
     * rejected here rather than silently signing "NaN".
     */
    public static String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Cannot canonicalize non-finite value: " + value);
        }
        // Normalise -0.0 so that it never renders as "-0.0000".
        double normalised = value == 0.0d ? 0.0d : value;
        return String.format(Locale.ROOT, DOUBLE_FORMAT, normalised);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
