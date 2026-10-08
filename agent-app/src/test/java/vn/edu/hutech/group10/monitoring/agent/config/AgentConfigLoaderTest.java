package vn.edu.hutech.group10.monitoring.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigLoaderTest {

    private static Properties minimal() {
        Properties properties = new Properties();
        properties.setProperty(AgentConfigLoader.KEY_AGENT_ID, "AGENT-01");
        properties.setProperty(AgentConfigLoader.KEY_SHARED_SECRET, "GROUP10_DEMO_SECRET_01");
        return properties;
    }

    @Test
    @DisplayName("A full properties file is read into the configuration")
    void readsEveryProperty() {
        Properties properties = minimal();
        properties.setProperty(AgentConfigLoader.KEY_AGENT_NAME, "LOCAL-AGENT-01");
        properties.setProperty(AgentConfigLoader.KEY_MONITOR_HOST, "192.168.1.10");
        properties.setProperty(AgentConfigLoader.KEY_MONITOR_UDP_PORT, "6000");
        properties.setProperty(AgentConfigLoader.KEY_AGENT_TCP_PORT, "5001");
        properties.setProperty(AgentConfigLoader.KEY_METRIC_INTERVAL, "1000");
        properties.setProperty(AgentConfigLoader.KEY_HEARTBEAT_INTERVAL, "5000");
        properties.setProperty(AgentConfigLoader.KEY_CPU_THRESHOLD, "80.5");

        AgentConfig config = AgentConfigLoader.fromProperties(properties);

        assertAll(
                () -> assertEquals("AGENT-01", config.getAgentId()),
                () -> assertEquals("LOCAL-AGENT-01", config.getAgentName()),
                () -> assertEquals("192.168.1.10", config.getMonitorHost()),
                () -> assertEquals(6000, config.getMonitorUdpPort()),
                () -> assertEquals(5001, config.getAgentTcpPort()),
                () -> assertEquals(1000L, config.getMetricIntervalMs()),
                () -> assertEquals(5000L, config.getHeartbeatIntervalMs()),
                () -> assertEquals(80.5d, config.getCpuThreshold(), 0.0001d),
                () -> assertEquals("GROUP10_DEMO_SECRET_01", config.getSharedSecret()));
    }

    @Test
    @DisplayName("Only the id and the shared secret are mandatory")
    void appliesDefaults() {
        AgentConfig config = AgentConfigLoader.fromProperties(minimal());

        assertAll(
                () -> assertEquals("AGENT-01", config.getAgentName(), "name defaults to the id"),
                () -> assertEquals("127.0.0.1", config.getMonitorHost()),
                () -> assertEquals(6000, config.getMonitorUdpPort()),
                () -> assertEquals(5000, config.getAgentTcpPort()),
                () -> assertEquals(1000L, config.getMetricIntervalMs()),
                () -> assertEquals(5000L, config.getHeartbeatIntervalMs()),
                () -> assertFalse(config.getHostname() == null || config.getHostname().isEmpty()));
    }

    @Test
    @DisplayName("A missing id or secret is refused with a message naming the property")
    void rejectsMissingMandatoryProperties() {
        Properties noId = minimal();
        noId.remove(AgentConfigLoader.KEY_AGENT_ID);
        IllegalArgumentException idError = assertThrows(IllegalArgumentException.class,
                () -> AgentConfigLoader.fromProperties(noId));
        assertTrue(idError.getMessage().contains(AgentConfigLoader.KEY_AGENT_ID), idError.getMessage());

        Properties noSecret = minimal();
        noSecret.remove(AgentConfigLoader.KEY_SHARED_SECRET);
        IllegalArgumentException secretError = assertThrows(IllegalArgumentException.class,
                () -> AgentConfigLoader.fromProperties(noSecret));
        assertTrue(secretError.getMessage().contains(AgentConfigLoader.KEY_SHARED_SECRET),
                secretError.getMessage());
    }

    @Test
    @DisplayName("Out-of-range values are refused rather than silently clamped")
    void rejectsOutOfRangeValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> {
                    Properties p = minimal();
                    p.setProperty(AgentConfigLoader.KEY_AGENT_TCP_PORT, "70000");
                    AgentConfigLoader.fromProperties(p);
                }),
                () -> assertThrows(IllegalArgumentException.class, () -> {
                    Properties p = minimal();
                    p.setProperty(AgentConfigLoader.KEY_METRIC_INTERVAL, "10");
                    AgentConfigLoader.fromProperties(p);
                }),
                () -> assertThrows(IllegalArgumentException.class, () -> {
                    Properties p = minimal();
                    p.setProperty(AgentConfigLoader.KEY_CPU_THRESHOLD, "150");
                    AgentConfigLoader.fromProperties(p);
                }),
                () -> assertThrows(IllegalArgumentException.class, () -> {
                    Properties p = minimal();
                    p.setProperty(AgentConfigLoader.KEY_MONITOR_UDP_PORT, "not-a-port");
                    AgentConfigLoader.fromProperties(p);
                }));
    }

    @Test
    @DisplayName("Decimal thresholds are parsed with a dot regardless of the default locale")
    void parsesDecimalsLocaleIndependently() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Properties p = minimal();
            p.setProperty(AgentConfigLoader.KEY_MEMORY_THRESHOLD, "77.5");

            assertEquals(77.5d, AgentConfigLoader.fromProperties(p).getMemoryThreshold(), 0.0001d);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("A real file on disk is loaded, and a missing one is reported clearly")
    void loadsFromDisk(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("agent01.properties");
        Files.write(file, ("agent.id=AGENT-77\n"
                + "agent.name=DISK-AGENT\n"
                + "agent.tcp.port=5077\n"
                + "security.sharedSecret=DISK_SECRET\n").getBytes(StandardCharsets.UTF_8));

        AgentConfig config = AgentConfigLoader.load(file);

        assertAll(
                () -> assertEquals("AGENT-77", config.getAgentId()),
                () -> assertEquals("DISK-AGENT", config.getAgentName()),
                () -> assertEquals(5077, config.getAgentTcpPort()));

        assertThrows(IOException.class, () -> AgentConfigLoader.load(tempDir.resolve("nope.properties")));
    }

    @Test
    @DisplayName("The startup banner never contains the shared secret")
    void bannerHidesTheSecret() {
        AgentConfig config = AgentConfigLoader.fromProperties(minimal());

        assertAll(
                () -> assertFalse(config.describeForConsole().contains("GROUP10_DEMO_SECRET_01")),
                () -> assertFalse(config.toString().contains("GROUP10_DEMO_SECRET_01")));
    }
}
