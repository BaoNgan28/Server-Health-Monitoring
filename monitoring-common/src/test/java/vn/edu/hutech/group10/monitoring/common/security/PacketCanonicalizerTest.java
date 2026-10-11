package vn.edu.hutech.group10.monitoring.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.hutech.group10.monitoring.common.TestPackets;
import vn.edu.hutech.group10.monitoring.common.model.HeartbeatPacket;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketCanonicalizerTest {

    @Test
    @DisplayName("Telemetry canonical string has the agreed field order and no signature")
    void telemetryFieldOrder() {
        TelemetryPacket packet = TestPackets.telemetry(7L, 1700000000000L);
        packet.setHmac("THIS_MUST_NOT_APPEAR");

        String canonical = PacketCanonicalizer.canonicalize(packet);

        assertEquals("1.0|TELEMETRY|AGENT-01|UNIT-TEST-HOST|TEST-SESSION-1|1700000000000|7"
                + "|23.5000|61.2500|42|3600|12345.5000|6789.2500", canonical);
        assertFalse(canonical.contains("THIS_MUST_NOT_APPEAR"),
                "the hmac field must never be part of the signed string");
    }

    @Test
    @DisplayName("Heartbeat canonical string carries only the identity fields")
    void heartbeatFieldOrder() {
        HeartbeatPacket packet = TestPackets.heartbeat(3L, 1700000000000L);
        assertEquals("1.0|HEARTBEAT|AGENT-01|UNIT-TEST-HOST|TEST-SESSION-1|1700000000000|3",
                PacketCanonicalizer.canonicalize(packet));
    }

    @Test
    @DisplayName("Double formatting does not depend on the default locale")
    void localeIndependentDoubles() {
        Locale original = Locale.getDefault();
        try {
            // German uses a comma as the decimal separator; the canonical form must not.
            Locale.setDefault(Locale.GERMANY);
            String canonical = PacketCanonicalizer.canonicalize(TestPackets.telemetry(1L, 1000L));
            assertTrue(canonical.contains("23.5000"), "expected a dot separator, got: " + canonical);
            assertFalse(canonical.contains("23,5000"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("Doubles always carry four decimals, and negative zero is normalised")
    void deterministicDoubleFormat() {
        assertAll(
                () -> assertEquals("0.0000", PacketCanonicalizer.formatDouble(0.0d)),
                () -> assertEquals("0.0000", PacketCanonicalizer.formatDouble(-0.0d)),
                () -> assertEquals("1.0000", PacketCanonicalizer.formatDouble(1.0d)),
                () -> assertEquals("99.9999", PacketCanonicalizer.formatDouble(99.99994d)),
                () -> assertEquals("12345.5000", PacketCanonicalizer.formatDouble(12345.5d)));
    }

    @Test
    @DisplayName("Non-finite values cannot be signed")
    void rejectsNonFinite() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PacketCanonicalizer.formatDouble(Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PacketCanonicalizer.formatDouble(Double.POSITIVE_INFINITY)));
    }

    @Test
    @DisplayName("Null text fields become empty strings, never the word null")
    void nullFieldsBecomeEmpty() {
        TelemetryPacket packet = TestPackets.telemetry(1L, 1000L);
        packet.setHostname(null);
        String canonical = PacketCanonicalizer.canonicalize(packet);
        assertTrue(canonical.startsWith("1.0|TELEMETRY|AGENT-01||TEST-SESSION-1|1000|1|"), canonical);
    }

    @Test
    @DisplayName("Changing any signed field changes the canonical string")
    void everySignedFieldMatters() {
        TelemetryPacket base = TestPackets.telemetry(1L, 1000L);
        String baseline = PacketCanonicalizer.canonicalize(base);

        TelemetryPacket changed = TestPackets.telemetry(1L, 1000L);
        changed.setCpuUsage(base.getCpuUsage() + 0.001d);

        assertFalse(baseline.equals(PacketCanonicalizer.canonicalize(changed)),
                "a CPU change of 0.001 must alter the signed string");
    }
}
