package vn.edu.hutech.group10.monitoring.common.security;

import vn.edu.hutech.group10.monitoring.common.model.HeartbeatPacket;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;

/**
 * Stamps outbound packets with their HmacSHA256 signature. Used by the agent and
 * by the simulator, so both sign exactly the way {@link PacketValidator} checks.
 */
public final class PacketSigner {

    private PacketSigner() {
    }

    /** Computes and stores the signature of {@code packet}. */
    public static void sign(TelemetryPacket packet, String secret) {
        packet.setHmac(HmacUtil.hmacSha256Hex(secret, PacketCanonicalizer.canonicalize(packet)));
    }

    /** Computes and stores the signature of {@code packet}. */
    public static void sign(HeartbeatPacket packet, String secret) {
        packet.setHmac(HmacUtil.hmacSha256Hex(secret, PacketCanonicalizer.canonicalize(packet)));
    }
}
