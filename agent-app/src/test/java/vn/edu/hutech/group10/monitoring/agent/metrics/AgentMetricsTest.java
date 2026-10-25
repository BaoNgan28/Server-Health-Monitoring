package vn.edu.hutech.group10.monitoring.agent.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.hutech.group10.monitoring.common.model.AgentInfoMessage;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageType;
import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.common.security.HmacUtil;
import vn.edu.hutech.group10.monitoring.common.security.PacketCanonicalizer;
import vn.edu.hutech.group10.monitoring.common.security.PacketSigner;
import vn.edu.hutech.group10.monitoring.common.util.ValidationUtil;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real OSHI collector against the machine running the build.
 * Values are checked for plausibility rather than for an exact figure, since
 * they describe live hardware.
 */
class AgentMetricsTest {

    @Test
    @DisplayName("A real sample is in range on every metric")
    void collectsPlausibleMetrics() {
        try (SystemMetricsCollector collector = new OshiSystemMetricsCollector()) {
            // The first CPU reading only establishes a tick baseline.
            collector.collect();
            SystemMetrics metrics = collector.collect();

            assertAll(
                    () -> assertTrue(ValidationUtil.isPercentage(metrics.getCpuUsage()),
                            "CPU out of range: " + metrics.getCpuUsage()),
                    () -> assertTrue(ValidationUtil.isPercentage(metrics.getMemoryUsage()),
                            "memory out of range: " + metrics.getMemoryUsage()),
                    () -> assertTrue(metrics.getMemoryUsage() > 0.0d,
                            "a running JVM always uses some memory"),
                    () -> assertTrue(metrics.getConnectionCount() >= 0),
                    () -> assertTrue(metrics.getUptimeSeconds() > 0,
                            "the machine has been up for some time"),
                    () -> assertTrue(metrics.getNetworkInBytesPerSecond() >= 0.0d),
                    () -> assertTrue(metrics.getNetworkOutBytesPerSecond() >= 0.0d),
                    () -> assertTrue(metrics.getTimestamp() > 0));
        }
    }

    @Test
    @DisplayName("Repeated collection stays stable and never throws")
    void repeatedCollectionIsStable() {
        try (SystemMetricsCollector collector = new OshiSystemMetricsCollector()) {
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 5; i++) {
                    SystemMetrics metrics = collector.collect();
                    assertTrue(ValidationUtil.isPercentage(metrics.getCpuUsage()));
                }
            });
        }
    }

    @Test
    @DisplayName("The machine description is filled in")
    void describesTheMachine() {
        try (SystemMetricsCollector collector = new OshiSystemMetricsCollector()) {
            AgentInfoMessage info = new AgentInfoMessage();
            collector.describe(info);

            assertAll(
                    () -> assertNotNull(info.getOsName()),
                    () -> assertNotNull(info.getCpuModel()),
                    () -> assertTrue(info.getCpuLogicalCores() > 0),
                    () -> assertTrue(info.getTotalMemoryBytes() > 0));
        }
    }

    @Test
    @DisplayName("A real sample can be packed into a packet the validator would accept")
    void realSampleProducesAVerifiablePacket() {
        String secret = "AGENT_METRICS_TEST_SECRET";
        try (SystemMetricsCollector collector = new OshiSystemMetricsCollector()) {
            SystemMetrics metrics = collector.collect();

            TelemetryPacket packet = new TelemetryPacket();
            packet.setProtocolVersion(ProtocolConstants.PROTOCOL_VERSION);
            packet.setType(MessageType.TELEMETRY.wireName());
            packet.setAgentId("AGENT-TEST");
            packet.setHostname("TEST-HOST");
            packet.setTimestamp(metrics.getTimestamp());
            packet.setSequence(1L);
            packet.setCpuUsage(metrics.getCpuUsage());
            packet.setMemoryUsage(metrics.getMemoryUsage());
            packet.setConnectionCount(metrics.getConnectionCount());
            packet.setUptimeSeconds(metrics.getUptimeSeconds());
            packet.setNetworkInBytesPerSecond(metrics.getNetworkInBytesPerSecond());
            packet.setNetworkOutBytesPerSecond(metrics.getNetworkOutBytesPerSecond());

            PacketSigner.sign(packet, secret);

            assertEquals(HmacUtil.hmacSha256Hex(secret, PacketCanonicalizer.canonicalize(packet)),
                    packet.getHmac());
        }
    }
}
