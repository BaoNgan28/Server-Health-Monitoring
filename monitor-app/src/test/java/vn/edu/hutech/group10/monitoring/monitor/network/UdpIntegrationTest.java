package vn.edu.hutech.group10.monitoring.monitor.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
import vn.edu.hutech.group10.monitoring.common.model.AlertType;
import vn.edu.hutech.group10.monitoring.common.model.HeartbeatPacket;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;
import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageType;
import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.common.security.PacketSigner;
import vn.edu.hutech.group10.monitoring.common.security.PacketValidator;
import vn.edu.hutech.group10.monitoring.common.security.ReplayProtector;
import vn.edu.hutech.group10.monitoring.monitor.TestSupport;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;
import vn.edu.hutech.group10.monitoring.monitor.notification.RecordingNotifier;
import vn.edu.hutech.group10.monitoring.monitor.repository.AgentRepository;
import vn.edu.hutech.group10.monitoring.monitor.repository.AlertRepository;
import vn.edu.hutech.group10.monitoring.monitor.repository.DatabaseManager;
import vn.edu.hutech.group10.monitoring.monitor.repository.MetricRepository;
import vn.edu.hutech.group10.monitoring.monitor.service.AgentRegistry;
import vn.edu.hutech.group10.monitoring.monitor.service.AlertService;
import vn.edu.hutech.group10.monitoring.monitor.service.HeartbeatMonitor;
import vn.edu.hutech.group10.monitoring.monitor.service.MonitorEventBus;
import vn.edu.hutech.group10.monitoring.monitor.service.TelemetryService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the whole receive path over a real UDP socket on the loopback
 * interface: datagram, JSON, signature check, replay check, registry, database,
 * alert engine.
 *
 * <p>The receiver binds port 0 so the test never collides with a running
 * monitoring client, and the database lives in a temporary folder.
 */
class UdpIntegrationTest {

    private static final long TIMEOUT_MS = 5000L;
    private static final String SESSION_ID = "UDP-INTEGRATION-SESSION";

    private DatabaseManager database;
    private MetricRepository metricRepository;
    private AlertRepository alertRepository;
    private AgentRegistry registry;
    private AlertService alertService;
    private RecordingNotifier notifier;
    private TelemetryService telemetryService;
    private UdpTelemetryReceiver receiver;
    private DatagramSocket clientSocket;
    private final JsonMessageCodec codec = new JsonMessageCodec();

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        database = new DatabaseManager(tempDir.resolve("monitoring.db"));
        database.initialize();

        AgentRepository agentRepository = new AgentRepository(database);
        AgentRecord agent = TestSupport.agent();
        agent.setCpuThreshold(50.0d);
        agentRepository.insert(agent);

        metricRepository = new MetricRepository(database);
        alertRepository = new AlertRepository(database);

        MonitorEventBus eventBus = new MonitorEventBus();
        registry = new AgentRegistry(agentRepository, eventBus);
        registry.load();

        notifier = new RecordingNotifier();
        alertService = new AlertService(alertRepository, notifier, eventBus);
        alertService.load();

        HeartbeatMonitor heartbeatMonitor = new HeartbeatMonitor(registry, alertService, 15_000L, 2000L);
        PacketValidator validator = new PacketValidator(registry, new ReplayProtector(),
                ProtocolConstants.DEFAULT_TIMESTAMP_TOLERANCE_MS);

        telemetryService = new TelemetryService(codec, validator, registry, metricRepository,
                alertService, heartbeatMonitor, eventBus);

        // Port 0: the operating system picks a free port for this test run.
        receiver = new UdpTelemetryReceiver(0, telemetryService);
        receiver.start();

        clientSocket = new DatagramSocket();
    }

    @AfterEach
    void tearDown() {
        if (clientSocket != null) {
            clientSocket.close();
        }
        if (receiver != null) {
            receiver.close();
        }
        if (database != null) {
            database.close();
        }
    }

    private void send(Object message) throws IOException {
        byte[] payload = codec.toBytes(message);
        clientSocket.send(new DatagramPacket(payload, payload.length,
                InetAddress.getLoopbackAddress(), receiver.getPort()));
    }

    private void sendRaw(String json) throws IOException {
        byte[] payload = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        clientSocket.send(new DatagramPacket(payload, payload.length,
                InetAddress.getLoopbackAddress(), receiver.getPort()));
    }

    /** Waits for an asynchronous condition rather than sleeping a fixed time. */
    private void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("Timed out waiting for: " + what
                + " (received=" + receiver.getReceivedCount()
                + ", accepted=" + telemetryService.getAcceptedCount()
                + ", rejected=" + telemetryService.getRejectedCount()
                + ", stored=" + metricRepository.countRaw() + ")");
    }

    private TelemetryPacket telemetry(long sequence, double cpu) {
        TelemetryPacket packet = new TelemetryPacket();
        packet.setProtocolVersion(ProtocolConstants.PROTOCOL_VERSION);
        packet.setType(MessageType.TELEMETRY.wireName());
        packet.setAgentId(TestSupport.AGENT_ID);
        packet.setHostname("INTEGRATION-HOST");
        packet.setSessionId(SESSION_ID);
        packet.setTimestamp(System.currentTimeMillis());
        packet.setSequence(sequence);
        packet.setCpuUsage(cpu);
        packet.setMemoryUsage(40.0d);
        packet.setConnectionCount(17);
        packet.setUptimeSeconds(7200L);
        packet.setNetworkInBytesPerSecond(1234.0d);
        packet.setNetworkOutBytesPerSecond(567.0d);
        return packet;
    }

    private TelemetryPacket signed(long sequence, double cpu) {
        TelemetryPacket packet = telemetry(sequence, cpu);
        PacketSigner.sign(packet, TestSupport.SECRET);
        return packet;
    }

    @Test
    @DisplayName("A signed telemetry packet reaches the registry and the database")
    void acceptsSignedTelemetry() throws Exception {
        send(signed(1L, 12.5d));

        await("the sample to be accepted", () -> telemetryService.getAcceptedCount() == 1);
        await("the row to be written", () -> metricRepository.countRaw() == 1L);

        var snapshot = registry.snapshot(TestSupport.AGENT_ID).orElseThrow();
        assertAll(
                () -> assertEquals(AgentStatus.ONLINE, snapshot.getStatus()),
                () -> assertEquals(12.5d, snapshot.getCpuUsage(), 0.0001d),
                () -> assertEquals(17, snapshot.getConnectionCount()),
                () -> assertEquals(7200L, snapshot.getUptimeSeconds()),
                () -> assertEquals(1234.0d, snapshot.getNetworkInBytesPerSecond(), 0.0001d));
    }

    @Test
    @DisplayName("A signed heartbeat keeps the agent online without writing a metric row")
    void acceptsHeartbeat() throws Exception {
        HeartbeatPacket packet = new HeartbeatPacket();
        packet.setProtocolVersion(ProtocolConstants.PROTOCOL_VERSION);
        packet.setType(MessageType.HEARTBEAT.wireName());
        packet.setAgentId(TestSupport.AGENT_ID);
        packet.setHostname("INTEGRATION-HOST");
        packet.setSessionId(SESSION_ID);
        packet.setTimestamp(System.currentTimeMillis());
        packet.setSequence(1L);
        PacketSigner.sign(packet, TestSupport.SECRET);

        send(packet);

        await("the heartbeat to be accepted", () -> telemetryService.getAcceptedCount() == 1);
        assertAll(
                () -> assertEquals(AgentStatus.ONLINE,
                        registry.snapshot(TestSupport.AGENT_ID).orElseThrow().getStatus()),
                () -> assertEquals(0L, metricRepository.countRaw(),
                        "a heartbeat carries no metrics"));
    }

    @Test
    @DisplayName("A forged packet claiming 100% CPU changes nothing anywhere")
    void rejectsForgedPacket() throws Exception {
        send(signed(1L, 10.0d));
        await("the genuine sample", () -> telemetryService.getAcceptedCount() == 1);

        TelemetryPacket forged = signed(2L, 10.0d);
        // Signed honestly, then raised: exactly what the simulator's invalid-hmac mode does.
        forged.setCpuUsage(100.0d);
        send(forged);

        await("the forged packet to be rejected", () -> telemetryService.getRejectedCount() == 1);

        var snapshot = registry.snapshot(TestSupport.AGENT_ID).orElseThrow();
        assertAll(
                () -> assertEquals(10.0d, snapshot.getCpuUsage(), 0.0001d,
                        "the dashboard must still show the real value"),
                () -> assertEquals(1L, metricRepository.countRaw(),
                        "no row may be written for a forged packet"),
                () -> assertFalse(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH),
                        "a forged packet must not raise a CPU alert"),
                () -> assertEquals(0, notifier.getSent().stream()
                        .filter(event -> event.getAlertType() == AlertType.CPU_HIGH).count()));
    }

    @Test
    @DisplayName("A replayed packet is accepted once and refused afterwards")
    void rejectsReplay() throws Exception {
        TelemetryPacket packet = signed(5L, 20.0d);

        send(packet);
        await("the first delivery", () -> telemetryService.getAcceptedCount() == 1);

        send(packet);
        send(packet);
        await("both replays to be refused", () -> telemetryService.getRejectedCount() == 2);

        assertEquals(1L, metricRepository.countRaw(), "only the first copy may be stored");

        // A genuinely newer packet must still get through afterwards.
        send(signed(6L, 21.0d));
        await("the fresh packet", () -> telemetryService.getAcceptedCount() == 2);
        assertEquals(2L, metricRepository.countRaw());
    }

    @Test
    @DisplayName("A packet from an unregistered agent is refused before any signature check")
    void rejectsUnknownAgent() throws Exception {
        TelemetryPacket packet = telemetry(1L, 50.0d);
        packet.setAgentId("AGENT-NOT-REGISTERED");
        PacketSigner.sign(packet, TestSupport.SECRET);

        send(packet);

        await("the unknown agent to be refused", () -> telemetryService.getRejectedCount() == 1);
        assertEquals(0L, metricRepository.countRaw());
    }

    @Test
    @DisplayName("A stale packet is refused")
    void rejectsStaleTimestamp() throws Exception {
        TelemetryPacket packet = telemetry(1L, 20.0d);
        packet.setTimestamp(System.currentTimeMillis()
                - ProtocolConstants.DEFAULT_TIMESTAMP_TOLERANCE_MS - 60_000L);
        PacketSigner.sign(packet, TestSupport.SECRET);

        send(packet);

        await("the stale packet to be refused", () -> telemetryService.getRejectedCount() == 1);
        assertEquals(0L, metricRepository.countRaw());
    }

    @Test
    @DisplayName("Junk on the port is ignored and the receiver stays up")
    void survivesGarbage() throws Exception {
        sendRaw("this is not JSON at all");
        sendRaw("{\"type\":\"TELEMETRY\", broken");
        sendRaw("{}");
        sendRaw("[]");

        await("the junk to be counted", () -> telemetryService.getRejectedCount() >= 4);

        // The receiver must still be working after all that.
        send(signed(1L, 33.0d));
        await("a real packet after the junk", () -> telemetryService.getAcceptedCount() == 1);
        assertTrue(receiver.isRunning());
    }

    @Test
    @DisplayName("Crossing a threshold over UDP opens exactly one alert")
    void raisesAlertFromRealTraffic() throws Exception {
        for (int i = 1; i <= 10; i++) {
            send(signed(i, 80.0d));
        }

        await("all ten samples", () -> telemetryService.getAcceptedCount() == 10);
        await("the alert", () -> alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH));

        assertAll(
                () -> assertEquals(1L, alertRepository.count(),
                        "ten samples over the threshold are one incident"),
                () -> assertEquals(1, notifier.countOf(AlertType.CPU_HIGH)));
    }

    @Test
    @DisplayName("A burst of packets is absorbed without loss on loopback")
    void handlesABurst() throws Exception {
        int count = 200;
        for (int i = 1; i <= count; i++) {
            send(signed(i, 10.0d));
        }

        await("the burst to drain", () -> telemetryService.getAcceptedCount() == count);

        assertAll(
                () -> assertEquals(count, telemetryService.getAcceptedCount()),
                () -> assertEquals(0L, telemetryService.getRejectedCount()),
                () -> assertEquals((long) count, metricRepository.countRaw()));
    }
}
