package vn.edu.hutech.group10.monitoring.common.security;

import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * HmacSHA256 helpers used to authenticate UDP packets.
 *
 * <p>The signature is computed over a canonical string rather than over the raw
 * JSON, because two encoders may order fields or format numbers differently
 * while describing the same sample. See {@link PacketCanonicalizer}.
 */
public final class HmacUtil {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private HmacUtil() {
    }

    /**
     * Signs {@code data} with {@code secret} and returns a lower-case hex digest.
     *
     * @throws IllegalArgumentException when the secret or data is missing
     * @throws IllegalStateException    when the JRE lacks HmacSHA256
     */
    public static String hmacSha256Hex(String secret, String data) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Shared secret must not be empty");
        }
        if (data == null) {
            throw new IllegalArgumentException("Data to sign must not be null");
        }
        try {
            Mac mac = Mac.getInstance(ProtocolConstants.HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
                    ProtocolConstants.HMAC_ALGORITHM));
            return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available in this JRE", e);
        }
    }

    /**
     * Compares two hex digests without leaking how many leading characters
     * matched. Case insensitive; {@code null} never matches.
     */
    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] a = expected.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
