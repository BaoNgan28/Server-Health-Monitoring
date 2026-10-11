package vn.edu.hutech.group10.monitoring.common;

import vn.edu.hutech.group10.monitoring.common.model.HeartbeatPacket;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageType;
import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.common.security.PacketSigner;

/** Builders that keep the security tests readable. */
public final class TestPackets {

    public static final String AGENT_ID = "AGENT-01";
    public static final String SECRET = "UNIT_TEST_SECRET_01";
    public static final String SESSION = "TEST-SESSION-1";

    private TestPackets() {
    }

    public static TelemetryPacket telemetry(long sequence, long timestamp) {
        TelemetryPacket packet = new TelemetryPacket();
        packet.setProtocolVersion(ProtocolConstants.PROTOCOL_VERSION);
        packet.setType(MessageType.TELEMETRY.wireName());
        packet.setAgentId(AGENT_ID);
        packet.setHostname("UNIT-TEST-HOST");
        packet.setSessionId(SESSION);
        packet.setTimestamp(timestamp);
        packet.setSequence(sequence);
        packet.setCpuUsage(23.5d);
        packet.setMemoryUsage(61.25d);
        packet.setConnectionCount(42);
        packet.setUptimeSeconds(3600L);
        packet.setNetworkInBytesPerSecond(12345.5d);
        packet.setNetworkOutBytesPerSecond(6789.25d);
        return packet;
    }

    public static TelemetryPacket signedTelemetry(long sequence, long timestamp) {
        TelemetryPacket packet = telemetry(sequence, timestamp);
        PacketSigner.sign(packet, SECRET);
        return packet;
    }

    public static HeartbeatPacket heartbeat(long sequence, long timestamp) {
        HeartbeatPacket packet = new HeartbeatPacket();
        packet.setProtocolVersion(ProtocolConstants.PROTOCOL_VERSION);
        packet.setType(MessageType.HEARTBEAT.wireName());
        packet.setAgentId(AGENT_ID);
        packet.setHostname("UNIT-TEST-HOST");
        packet.setSessionId(SESSION);
        packet.setTimestamp(timestamp);
        packet.setSequence(sequence);
        return packet;
    }

    public static HeartbeatPacket signedHeartbeat(long sequence, long timestamp) {
        HeartbeatPacket packet = heartbeat(sequence, timestamp);
        PacketSigner.sign(packet, SECRET);
        return packet;
    }
}
