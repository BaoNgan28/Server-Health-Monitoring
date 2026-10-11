package vn.edu.hutech.group10.monitoring.common.security;

import vn.edu.hutech.group10.monitoring.common.model.HeartbeatPacket;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;
import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.common.util.ValidationUtil;

/**
 * The single gate every inbound UDP packet passes through.
 *
 * <p>Checks run in this order, and the first failure stops the rest:
 * shape and range, known agent, signature, timestamp freshness, replay. Nothing
 * downstream - GUI, database, alert engine - ever sees a packet this class did
 * not accept, so a forged datagram cannot raise a fake CPU alarm.
 */
public class PacketValidator {

    private final SecretProvider secretProvider;
    private final ReplayProtector replayProtector;
    private final long timestampToleranceMs;

    public PacketValidator(SecretProvider secretProvider, ReplayProtector replayProtector) {
        this(secretProvider, replayProtector, ProtocolConstants.DEFAULT_TIMESTAMP_TOLERANCE_MS);
    }

    public PacketValidator(SecretProvider secretProvider, ReplayProtector replayProtector,
                           long timestampToleranceMs) {
        if (secretProvider == null) {
            throw new IllegalArgumentException("secretProvider must not be null");
        }
        if (replayProtector == null) {
            throw new IllegalArgumentException("replayProtector must not be null");
        }
        this.secretProvider = secretProvider;
        this.replayProtector = replayProtector;
        this.timestampToleranceMs = timestampToleranceMs;
    }

    public long getTimestampToleranceMs() {
        return timestampToleranceMs;
    }

    /** Validates a telemetry packet against the receiver clock {@code nowMs}. */
    public ValidationResult validate(TelemetryPacket packet, long nowMs) {
        if (packet == null) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED, "null packet");
        }

        ValidationResult shape = checkTelemetryShape(packet);
        if (!shape.isAccepted()) {
            return shape;
        }

        ValidationResult common = checkVersionAndAgent(packet.getProtocolVersion(), packet.getAgentId());
        if (!common.isAccepted()) {
            return common;
        }

        String secret = secretProvider.secretFor(packet.getAgentId());
        String canonical;
        try {
            canonical = PacketCanonicalizer.canonicalize(packet);
        } catch (IllegalArgumentException e) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED, e.getMessage());
        }

        String expected = HmacUtil.hmacSha256Hex(secret, canonical);
        if (!HmacUtil.constantTimeEquals(expected, packet.getHmac())) {
            return ValidationResult.rejected(ValidationResult.Reason.INVALID_HMAC,
                    "signature mismatch for agent " + packet.getAgentId());
        }

        ValidationResult freshness = checkTimestamp(packet.getTimestamp(), nowMs);
        if (!freshness.isAccepted()) {
            return freshness;
        }

        if (!replayProtector.accept(packet.getAgentId(), packet.getSessionId(),
                packet.getSequence())) {
            return ValidationResult.rejected(ValidationResult.Reason.REPLAY,
                    "sequence " + packet.getSequence() + " was already seen in session "
                            + packet.getSessionId());
        }

        return ValidationResult.accepted();
    }

    /** Validates a heartbeat packet against the receiver clock {@code nowMs}. */
    public ValidationResult validate(HeartbeatPacket packet, long nowMs) {
        if (packet == null) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED, "null packet");
        }
        if (packet.getSequence() < 0) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED, "negative sequence");
        }
        if (ValidationUtil.isBlank(packet.getSessionId())) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED, "missing sessionId");
        }

        ValidationResult common = checkVersionAndAgent(packet.getProtocolVersion(), packet.getAgentId());
        if (!common.isAccepted()) {
            return common;
        }

        String secret = secretProvider.secretFor(packet.getAgentId());
        String canonical = PacketCanonicalizer.canonicalize(packet);
        String expected = HmacUtil.hmacSha256Hex(secret, canonical);
        if (!HmacUtil.constantTimeEquals(expected, packet.getHmac())) {
            return ValidationResult.rejected(ValidationResult.Reason.INVALID_HMAC,
                    "signature mismatch for agent " + packet.getAgentId());
        }

        ValidationResult freshness = checkTimestamp(packet.getTimestamp(), nowMs);
        if (!freshness.isAccepted()) {
            return freshness;
        }

        if (!replayProtector.accept(packet.getAgentId(), packet.getSessionId(),
                packet.getSequence())) {
            return ValidationResult.rejected(ValidationResult.Reason.REPLAY,
                    "sequence " + packet.getSequence() + " was already seen in session "
                            + packet.getSessionId());
        }

        return ValidationResult.accepted();
    }

    private ValidationResult checkVersionAndAgent(String protocolVersion, String agentId) {
        if (!ProtocolConstants.PROTOCOL_VERSION.equals(protocolVersion)) {
            return ValidationResult.rejected(ValidationResult.Reason.UNSUPPORTED_VERSION,
                    "protocol version " + protocolVersion);
        }
        if (ValidationUtil.isBlank(agentId)) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED, "missing agentId");
        }
        String secret = secretProvider.secretFor(agentId);
        if (ValidationUtil.isBlank(secret)) {
            return ValidationResult.rejected(ValidationResult.Reason.UNKNOWN_AGENT,
                    "no shared secret registered for agent " + agentId);
        }
        return ValidationResult.accepted();
    }

    private ValidationResult checkTimestamp(long timestamp, long nowMs) {
        long drift = Math.abs(nowMs - timestamp);
        if (drift > timestampToleranceMs) {
            return ValidationResult.rejected(ValidationResult.Reason.TIMESTAMP_OUT_OF_TOLERANCE,
                    "packet clock drift of " + drift + " ms exceeds " + timestampToleranceMs + " ms");
        }
        return ValidationResult.accepted();
    }

    private ValidationResult checkTelemetryShape(TelemetryPacket packet) {
        if (ValidationUtil.isBlank(packet.getSessionId())) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED, "missing sessionId");
        }
        if (packet.getSequence() < 0) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED, "negative sequence");
        }
        if (!ValidationUtil.isFinite(packet.getCpuUsage())
                || !ValidationUtil.isFinite(packet.getMemoryUsage())
                || !ValidationUtil.isFinite(packet.getNetworkInBytesPerSecond())
                || !ValidationUtil.isFinite(packet.getNetworkOutBytesPerSecond())) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED,
                    "non-finite metric value");
        }
        if (!ValidationUtil.isPercentage(packet.getCpuUsage())
                || !ValidationUtil.isPercentage(packet.getMemoryUsage())) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED,
                    "usage percentage outside 0..100");
        }
        if (packet.getConnectionCount() < 0 || packet.getUptimeSeconds() < 0) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED,
                    "negative connection count or uptime");
        }
        if (packet.getNetworkInBytesPerSecond() < 0 || packet.getNetworkOutBytesPerSecond() < 0) {
            return ValidationResult.rejected(ValidationResult.Reason.MALFORMED,
                    "negative network rate");
        }
        return ValidationResult.accepted();
    }
}
