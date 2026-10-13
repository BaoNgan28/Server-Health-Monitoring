package vn.edu.hutech.group10.monitoring.monitor.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.hutech.group10.monitoring.agent.config.AgentConfig;
import vn.edu.hutech.group10.monitoring.agent.config.AgentConfigLoader;
import vn.edu.hutech.group10.monitoring.agent.metrics.OshiSystemMetricsCollector;
import vn.edu.hutech.group10.monitoring.agent.metrics.SystemMetricsCollector;
import vn.edu.hutech.group10.monitoring.agent.network.TcpControlServer;
import vn.edu.hutech.group10.monitoring.agent.service.ConfigUpdateService;
import vn.edu.hutech.group10.monitoring.common.model.AgentInfoMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigAckMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigUpdateMessage;
import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a real agent control server and talks to it with the real monitoring
 * client connection over a loopback socket.
 *
 * <p>This is what proves a configuration change actually travels over TCP and
 * comes back acknowledged, rather than only being applied locally in the GUI.
 * The length-prefixed framing, the JSON encoding and the agent's validation are
 * all exercised for real.
 */
class TcpIntegrationTest {

    private static final int TIMEOUT_MS = 5000;

    private SystemMetricsCollector collector;
    private TcpControlServer server;
    private AgentConfig agentConfig;
    private AtomicInteger configChanges;
    private final JsonMessageCodec codec = new JsonMessageCodec();

    @BeforeEach
    void setUp() throws IOException {
        // An agent binds the port named in its configuration, so a free port is
        // found first and the agent is then configured with it.
        int port;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        Properties properties = new Properties();
        properties.setProperty(AgentConfigLoader.KEY_AGENT_ID, "AGENT-TCP-TEST");
        properties.setProperty(AgentConfigLoader.KEY_AGENT_NAME, "TCP-TEST-AGENT");
        properties.setProperty(AgentConfigLoader.KEY_SHARED_SECRET, "TCP_TEST_SECRET");
        properties.setProperty(AgentConfigLoader.KEY_AGENT_TCP_PORT, String.valueOf(port));
        agentConfig = AgentConfigLoader.fromProperties(properties);

        collector = new OshiSystemMetricsCollector();
        configChanges = new AtomicInteger();
        ConfigUpdateService configUpdateService =
                new ConfigUpdateService(agentConfig, changed -> configChanges.incrementAndGet());

        server = new TcpControlServer(agentConfig, collector, configUpdateService, codec, "1.0.0-test");
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
        if (collector != null) {
            collector.close();
        }
    }

    private AgentTcpConnection connect() throws IOException {
        AgentTcpConnection connection = new AgentTcpConnection(agentConfig.getAgentId(),
                "127.0.0.1", agentConfig.getAgentTcpPort(), TIMEOUT_MS, codec);
        connection.connect();
        return connection;
    }

    @Test
    @DisplayName("PING is answered with PONG")
    void pingIsAnswered() throws IOException {
        try (AgentTcpConnection connection = connect()) {
            assertTrue(connection.isConnected());
            assertTrue(connection.ping());
        }
    }

    @Test
    @DisplayName("The agent describes its machine on request")
    void agentInfoIsReturned() throws IOException {
        try (AgentTcpConnection connection = connect()) {
            AgentInfoMessage info = connection.requestInfo();

            assertAll(
                    () -> assertEquals("AGENT-TCP-TEST", info.getAgentId()),
                    () -> assertEquals("TCP-TEST-AGENT", info.getAgentName()),
                    () -> assertEquals("1.0.0-test", info.getAgentVersion()),
                    () -> assertTrue(info.getCpuLogicalCores() > 0, "real machine details"),
                    () -> assertTrue(info.getTotalMemoryBytes() > 0),
                    () -> assertEquals(1000L, info.getMetricIntervalMs()));
        }
    }

    @Test
    @DisplayName("A configuration change travels over TCP and is acknowledged")
    void configUpdateIsAppliedAndAcknowledged() throws IOException {
        try (AgentTcpConnection connection = connect()) {
            ConfigUpdateMessage update = new ConfigUpdateMessage();
            update.setCpuThreshold(5.0d);
            update.setMetricIntervalMs(2000L);

            ConfigAckMessage ack = connection.sendConfigUpdate(update);

            assertAll(
                    () -> assertTrue(ack.isSuccess(), ack.getMessage()),
                    () -> assertEquals(ConfigAckMessage.STATUS_SUCCESS, ack.getStatus()),
                    () -> assertEquals(update.getRequestId(), ack.getRequestId(),
                            "the reply must be tied to the request"),
                    () -> assertEquals(5.0d, ack.getEffectiveCpuThreshold(), 0.0001d),
                    () -> assertEquals(2000L, ack.getEffectiveMetricIntervalMs()),
                    // The value really changed on the agent, not only in the reply.
                    () -> assertEquals(5.0d, agentConfig.getCpuThreshold(), 0.0001d),
                    () -> assertEquals(2000L, agentConfig.getMetricIntervalMs()),
                    () -> assertEquals(1, configChanges.get(),
                            "the agent's schedulers must be told to re-arm"));
        }
    }

    @Test
    @DisplayName("An invalid configuration is refused with a reason and nothing is applied")
    void invalidConfigUpdateIsRefused() throws IOException {
        try (AgentTcpConnection connection = connect()) {
            long originalInterval = agentConfig.getMetricIntervalMs();

            ConfigUpdateMessage update = new ConfigUpdateMessage();
            update.setMetricIntervalMs(5L);

            ConfigAckMessage ack = connection.sendConfigUpdate(update);

            assertAll(
                    () -> assertFalse(ack.isSuccess()),
                    () -> assertEquals(ConfigAckMessage.STATUS_ERROR, ack.getStatus()),
                    () -> assertTrue(ack.getMessage().contains("metricIntervalMs"), ack.getMessage()),
                    () -> assertEquals(originalInterval, agentConfig.getMetricIntervalMs()));
        }
    }

    @Test
    @DisplayName("Several requests run back to back on one connection")
    void connectionIsReusedAcrossRequests() throws IOException {
        try (AgentTcpConnection connection = connect()) {
            for (int i = 0; i < 5; i++) {
                assertTrue(connection.ping(), "ping " + i);
            }
            assertEquals("AGENT-TCP-TEST", connection.requestInfo().getAgentId());

            ConfigUpdateMessage update = new ConfigUpdateMessage();
            update.setMemoryThreshold(70.0d);
            assertTrue(connection.sendConfigUpdate(update).isSuccess());

            assertTrue(connection.ping(), "the connection still works after a config update");
        }
    }

    @Test
    @DisplayName("Several monitoring clients can be connected at the same time")
    void severalConnectionsAreServed() throws IOException {
        try (AgentTcpConnection first = connect();
             AgentTcpConnection second = connect()) {

            assertAll(
                    () -> assertTrue(first.ping()),
                    () -> assertTrue(second.ping()),
                    () -> assertEquals("AGENT-TCP-TEST", second.requestInfo().getAgentId()));
        }
    }

    @Test
    @DisplayName("Connecting to a closed port fails quickly instead of hanging")
    void connectingToNothingFails() throws IOException {
        int freePort;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        AgentTcpConnection connection = new AgentTcpConnection("AGENT-ABSENT", "127.0.0.1",
                freePort, TIMEOUT_MS, codec);

        long startedAt = System.currentTimeMillis();
        assertThrows(IOException.class, connection::connect);
        assertTrue(System.currentTimeMillis() - startedAt < TIMEOUT_MS * 2L,
                "the connect timeout must bound the wait");
        assertFalse(connection.isConnected());
        connection.close();
    }

    @Test
    @DisplayName("Using a connection that was never opened is reported, not hung")
    void usingAClosedConnectionFails() {
        AgentTcpConnection connection = new AgentTcpConnection("AGENT-TCP-TEST", "127.0.0.1",
                agentConfig.getAgentTcpPort(), TIMEOUT_MS, codec);

        assertThrows(IOException.class, connection::ping);
    }

    @Test
    @DisplayName("The connection manager keeps one connection per agent and closes them all")
    void connectionManagerLifecycle() throws IOException {
        var config = vn.edu.hutech.group10.monitoring.monitor.TestSupport.config(
                java.nio.file.Path.of("unused.db"));
        try (AgentTcpConnectionManager manager = new AgentTcpConnectionManager(codec, config)) {
            var record = vn.edu.hutech.group10.monitoring.monitor.TestSupport.agent("AGENT-TCP-TEST");
            record.setTcpPort(agentConfig.getAgentTcpPort());

            manager.connect(record);
            assertTrue(manager.isConnected("AGENT-TCP-TEST"));
            assertEquals(1, manager.getConnectionCount());

            // Connecting again must replace, not accumulate.
            manager.connect(record);
            assertEquals(1, manager.getConnectionCount());

            manager.disconnect("AGENT-TCP-TEST");
            assertFalse(manager.isConnected("AGENT-TCP-TEST"));
        }
    }
}
