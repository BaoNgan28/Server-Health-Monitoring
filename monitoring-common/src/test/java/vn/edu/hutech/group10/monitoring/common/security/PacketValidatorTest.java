package vn.edu.hutech.group10.monitoring.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.hutech.group10.monitoring.common.TestPackets;
import vn.edu.hutech.group10.monitoring.common.model.HeartbeatPacket;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketValidatorTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long TOLERANCE = 30_000L;

    private PacketValidator validator;

    @BeforeEach
    void setUp() {
        SecretProvider secrets = agentId ->
                TestPackets.AGENT_ID.equals(agentId) ? TestPackets.SECRET : null;
        validator = new PacketValidator(secrets, new ReplayProtector(), TOLERANCE);
    }

    @Test
    @DisplayName("A correctly signed, fresh telemetry packet is accepted")
    void acceptsGoodTelemetry() {
        ValidationResult result = validator.validate(TestPackets.signedTelemetry(1L, NOW), NOW);
        assertTrue(result.isAccepted(), "unexpected rejection: " + result);
    }

    @Test
    @DisplayName("A correctly signed heartbeat is accepted")
    void acceptsGoodHeartbeat() {
        HeartbeatPacket packet = TestPackets.signedHeartbeat(1L, NOW);
        assertTrue(validator.validate(packet, NOW).isAccepted());
    }

    @Test
    @DisplayName("A forged signature is rejected as INVALID_HMAC")
    void rejectsForgedSignature() {
        TelemetryPacket packet = TestPackets.signedTelemetry(1L, NOW);
        packet.setHmac("00000000000000000000000000000000000000000000000000000000deadbeef");

        ValidationResult result = validator.validate(packet, NOW);
        assertFalse(result.isAccepted());
        assertEquals(ValidationResult.Reason.INVALID_HMAC, result.getReason());
    }

    @Test
    @DisplayName("Tampering with a metric after signing is detected")
    void rejectsTamperedMetric() {
        TelemetryPacket packet = TestPackets.signedTelemetry(1L, NOW);
        // This is exactly the attack the simulator's invalid-hmac mode performs:
        // claim 100% CPU while keeping a signature computed over the real value.
        packet.setCpuUsage(100.0d);

        ValidationResult result = validator.validate(packet, NOW);
        assertEquals(ValidationResult.Reason.INVALID_HMAC, result.getReason());
    }

    @Test
    @DisplayName("A packet signed with the wrong key is rejected")
    void rejectsWrongKey() {
        TelemetryPacket packet = TestPackets.telemetry(1L, NOW);
        PacketSigner.sign(packet, "SOME_OTHER_SECRET");
        assertEquals(ValidationResult.Reason.INVALID_HMAC, validator.validate(packet, NOW).getReason());
    }

    @Test
    @DisplayName("An agent that is not registered is rejected before any signature check")
    void rejectsUnknownAgent() {
        TelemetryPacket packet = TestPackets.telemetry(1L, NOW);
        packet.setAgentId("AGENT-UNREGISTERED");
        PacketSigner.sign(packet, TestPackets.SECRET);

        assertEquals(ValidationResult.Reason.UNKNOWN_AGENT, validator.validate(packet, NOW).getReason());
    }

    @Test
    @DisplayName("A packet from another protocol version is rejected")
    void rejectsUnsupportedVersion() {
        TelemetryPacket packet = TestPackets.telemetry(1L, NOW);
        packet.setProtocolVersion("0.9");
        PacketSigner.sign(packet, TestPackets.SECRET);

        assertEquals(ValidationResult.Reason.UNSUPPORTED_VERSION,
                validator.validate(packet, NOW).getReason());
    }

    @Test
    @DisplayName("Timestamps outside the tolerance window are rejected in both directions")
    void rejectsStaleAndFutureTimestamps() {
        TelemetryPacket stale = TestPackets.signedTelemetry(1L, NOW - TOLERANCE - 1L);
        assertEquals(ValidationResult.Reason.TIMESTAMP_OUT_OF_TOLERANCE,
                validator.validate(stale, NOW).getReason());

        TelemetryPacket future = TestPackets.signedTelemetry(2L, NOW + TOLERANCE + 1L);
        assertEquals(ValidationResult.Reason.TIMESTAMP_OUT_OF_TOLERANCE,
                validator.validate(future, NOW).getReason());

        TelemetryPacket edge = TestPackets.signedTelemetry(3L, NOW - TOLERANCE);
        assertTrue(validator.validate(edge, NOW).isAccepted(), "the boundary itself is still fresh");
    }

    @Test
    @DisplayName("Replaying an accepted packet is rejected the second time")
    void rejectsReplay() {
        TelemetryPacket packet = TestPackets.signedTelemetry(5L, NOW);

        assertTrue(validator.validate(packet, NOW).isAccepted(), "first delivery");
        assertEquals(ValidationResult.Reason.REPLAY, validator.validate(packet, NOW + 10L).getReason());
    }

    @Test
    @DisplayName("Out-of-range metric values are rejected as malformed")
    void rejectsImpossibleValues() {
        TelemetryPacket packet = TestPackets.telemetry(1L, NOW);
        packet.setCpuUsage(150.0d);
        PacketSigner.sign(packet, TestPackets.SECRET);
        assertEquals(ValidationResult.Reason.MALFORMED, validator.validate(packet, NOW).getReason());

        TelemetryPacket negative = TestPackets.telemetry(2L, NOW);
        negative.setConnectionCount(-3);
        PacketSigner.sign(negative, TestPackets.SECRET);
        assertEquals(ValidationResult.Reason.MALFORMED, validator.validate(negative, NOW).getReason());
    }

    @Test
    @DisplayName("A rejected packet never advances the replay counter")
    void rejectedPacketsDoNotConsumeSequence() {
        TelemetryPacket forged = TestPackets.signedTelemetry(7L, NOW);
        forged.setHmac("ff");
        assertFalse(validator.validate(forged, NOW).isAccepted());

        // The genuine packet carrying the same sequence must still get through.
        assertTrue(validator.validate(TestPackets.signedTelemetry(7L, NOW), NOW).isAccepted());
    }

    @Test
    @DisplayName("A packet with no session id is rejected as malformed")
    void rejectsMissingSessionId() {
        TelemetryPacket packet = TestPackets.telemetry(1L, NOW);
        packet.setSessionId(null);
        PacketSigner.sign(packet, TestPackets.SECRET);

        assertEquals(ValidationResult.Reason.MALFORMED, validator.validate(packet, NOW).getReason());
    }

    @Test
    @DisplayName("A restarted agent is accepted even though it counts from 1 again")
    void acceptsARestartedAgent() {
        TelemetryPacket beforeRestart = TestPackets.telemetry(500L, NOW);
        PacketSigner.sign(beforeRestart, TestPackets.SECRET);
        assertTrue(validator.validate(beforeRestart, NOW).isAccepted());

        TelemetryPacket afterRestart = TestPackets.telemetry(1L, NOW + 1000L);
        afterRestart.setSessionId("TEST-SESSION-2");
        PacketSigner.sign(afterRestart, TestPackets.SECRET);

        assertTrue(validator.validate(afterRestart, NOW + 1000L).isAccepted(),
                "a new session must not be mistaken for a replay");
    }

    @Test
    @DisplayName("A null packet is reported rather than thrown")
    void nullPacketIsMalformed() {
        assertEquals(ValidationResult.Reason.MALFORMED,
                validator.validate((TelemetryPacket) null, NOW).getReason());
        assertEquals(ValidationResult.Reason.MALFORMED,
                validator.validate((HeartbeatPacket) null, NOW).getReason());
    }
}
