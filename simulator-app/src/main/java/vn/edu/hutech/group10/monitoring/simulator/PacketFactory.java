package vn.edu.hutech.group10.monitoring.simulator;

import vn.edu.hutech.group10.monitoring.common.model.HeartbeatPacket;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageType;
import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.common.security.PacketSigner;

import java.util.Random;
import java.util.UUID;

/** Builds the packets the simulator modes send. */
public final class PacketFactory {

    private static final Random RANDOM = new Random();

    /**
     * One session id for this simulator run. Because it differs from the real
     * agent's, the simulator gets its own replay window and cannot disturb the
     * agent it is impersonating.
     */
    private static final String SESSION_ID = "SIM-" + UUID.randomUUID();

    private PacketFactory() {
    }

    /** Session id this run signs its packets with. */
    public static String sessionId() {
        return SESSION_ID;
    }

    /** A plausible telemetry packet, unsigned. */
    public static TelemetryPacket telemetry(String agentId, String hostname, long sequence) {
        TelemetryPacket packet = new TelemetryPacket();
        packet.setProtocolVersion(ProtocolConstants.PROTOCOL_VERSION);
        packet.setType(MessageType.TELEMETRY.wireName());
        packet.setAgentId(agentId);
        packet.setHostname(hostname);
        packet.setSessionId(SESSION_ID);
        packet.setTimestamp(System.currentTimeMillis());
        packet.setSequence(sequence);
        packet.setCpuUsage(round(10.0d + RANDOM.nextDouble() * 40.0d));
        packet.setMemoryUsage(round(30.0d + RANDOM.nextDouble() * 40.0d));
        packet.setConnectionCount(20 + RANDOM.nextInt(80));
        packet.setUptimeSeconds(3600L + sequence);
        packet.setNetworkInBytesPerSecond(round(RANDOM.nextDouble() * 500_000.0d));
        packet.setNetworkOutBytesPerSecond(round(RANDOM.nextDouble() * 250_000.0d));
        return packet;
    }

    /** A telemetry packet signed with {@code secret}. */
    public static TelemetryPacket signedTelemetry(String agentId, String hostname, long sequence,
                                                  String secret) {
        TelemetryPacket packet = telemetry(agentId, hostname, sequence);
        PacketSigner.sign(packet, secret);
        return packet;
    }

    /** A heartbeat signed with {@code secret}. */
    public static HeartbeatPacket signedHeartbeat(String agentId, String hostname, long sequence,
                                                  String secret) {
        HeartbeatPacket packet = new HeartbeatPacket();
        packet.setProtocolVersion(ProtocolConstants.PROTOCOL_VERSION);
        packet.setType(MessageType.HEARTBEAT.wireName());
        packet.setAgentId(agentId);
        packet.setHostname(hostname);
        packet.setSessionId(SESSION_ID);
        packet.setTimestamp(System.currentTimeMillis());
        packet.setSequence(sequence);
        PacketSigner.sign(packet, secret);
        return packet;
    }

    private static double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }
}
