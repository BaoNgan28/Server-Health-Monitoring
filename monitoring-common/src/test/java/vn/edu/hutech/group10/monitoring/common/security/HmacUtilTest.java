package vn.edu.hutech.group10.monitoring.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacUtilTest {

    @Test
    @DisplayName("Matches the RFC 4231 test case 2 vector for HMAC-SHA256")
    void matchesKnownVector() {
        // RFC 4231 test case 2: key "Jefe", data "what do ya want for nothing?"
        String expected = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";
        assertEquals(expected, HmacUtil.hmacSha256Hex("Jefe", "what do ya want for nothing?"));
    }

    @Test
    @DisplayName("Signing is deterministic and key dependent")
    void deterministicAndKeyDependent() {
        String a = HmacUtil.hmacSha256Hex("secret", "payload");
        String b = HmacUtil.hmacSha256Hex("secret", "payload");
        String c = HmacUtil.hmacSha256Hex("other-secret", "payload");
        assertAll(
                () -> assertEquals(a, b, "same key and data must give the same digest"),
                () -> assertFalse(a.equals(c), "a different key must give a different digest"),
                () -> assertEquals(64, a.length(), "SHA-256 hex digest is 64 characters"));
    }

    @Test
    @DisplayName("Digest is lower-case hex")
    void digestIsLowerCaseHex() {
        String digest = HmacUtil.hmacSha256Hex("k", "d");
        assertTrue(digest.matches("[0-9a-f]{64}"), "unexpected digest format: " + digest);
    }

    @Test
    @DisplayName("Empty secret or null data is rejected")
    void rejectsBadInput() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> HmacUtil.hmacSha256Hex("", "data")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> HmacUtil.hmacSha256Hex(null, "data")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> HmacUtil.hmacSha256Hex("secret", null)));
    }

    @Test
    @DisplayName("Constant-time comparison accepts equal digests and rejects everything else")
    void constantTimeComparison() {
        String digest = HmacUtil.hmacSha256Hex("k", "d");
        assertAll(
                () -> assertTrue(HmacUtil.constantTimeEquals(digest, digest)),
                () -> assertTrue(HmacUtil.constantTimeEquals(digest, digest.toUpperCase()),
                        "comparison is case insensitive"),
                () -> assertFalse(HmacUtil.constantTimeEquals(digest, digest.substring(1))),
                () -> assertFalse(HmacUtil.constantTimeEquals(digest, null)),
                () -> assertFalse(HmacUtil.constantTimeEquals(null, digest)),
                () -> assertFalse(HmacUtil.constantTimeEquals(null, null)));
    }
}
