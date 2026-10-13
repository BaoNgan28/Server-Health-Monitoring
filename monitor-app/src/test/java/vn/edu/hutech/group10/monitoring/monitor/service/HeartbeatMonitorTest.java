package vn.edu.hutech.group10.monitoring.monitor.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
import vn.edu.hutech.group10.monitoring.common.model.AlertType;
import vn.edu.hutech.group10.monitoring.monitor.TestSupport;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;
import vn.edu.hutech.group10.monitoring.monitor.notification.RecordingNotifier;
import vn.edu.hutech.group10.monitoring.monitor.repository.AgentRepository;
import vn.edu.hutech.group10.monitoring.monitor.repository.AlertRepository;
import vn.edu.hutech.group10.monitoring.monitor.repository.DatabaseManager;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clock is a parameter of {@link HeartbeatMonitor#check(long)}, so these
 * tests move time forward by arithmetic instead of sleeping for the timeout.
 */
class HeartbeatMonitorTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long TIMEOUT_MS = 15_000L;

    private DatabaseManager database;
    private AgentRegistry registry;
    private AlertRepository alertRepository;
    private RecordingNotifier notifier;
    private AlertService alertService;
    private HeartbeatMonitor monitor;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        database = new DatabaseManager(tempDir.resolve("monitoring.db"));
        database.initialize();

        AgentRepository agentRepository = new AgentRepository(database);
        agentRepository.insert(TestSupport.agent());

        MonitorEventBus eventBus = new MonitorEventBus();
        registry = new AgentRegistry(agentRepository, eventBus);
        registry.load();

        alertRepository = new AlertRepository(database);
        notifier = new RecordingNotifier();
        alertService = new AlertService(alertRepository, notifier, eventBus);
        alertService.load();

        monitor = new HeartbeatMonitor(registry, alertService, TIMEOUT_MS, 2000L, () -> NOW);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private AgentStatus status() {
        return registry.snapshot(TestSupport.AGENT_ID).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("A fresh agent is UNKNOWN until something arrives from it")
    void startsUnknown() {
        assertEquals(AgentStatus.UNKNOWN, status());
    }

    @Test
    @DisplayName("An accepted packet turns an agent ONLINE")
    void packetMakesAgentOnline() {
        monitor.markAlive(TestSupport.AGENT_ID, NOW, NOW);

        assertEquals(AgentStatus.ONLINE, status());
    }

    @Test
    @DisplayName("Silence past the timeout turns the agent OFFLINE and opens one alert")
    void timeoutMakesAgentOffline() {
        monitor.markAlive(TestSupport.AGENT_ID, NOW, NOW);

        assertEquals(0, monitor.check(NOW + TIMEOUT_MS), "the boundary itself is not yet late");
        assertEquals(AgentStatus.ONLINE, status());

        assertEquals(1, monitor.check(NOW + TIMEOUT_MS + 1));
        assertAll(
                () -> assertEquals(AgentStatus.OFFLINE, status()),
                () -> assertTrue(alertService.hasOpenAlert(TestSupport.AGENT_ID,
                        AlertType.AGENT_OFFLINE)),
                () -> assertEquals(1, notifier.countOf(AlertType.AGENT_OFFLINE)));
    }

    @Test
    @DisplayName("Repeated sweeps of an offline agent do not repeat the alert")
    void offlineAlertIsNotRepeated() {
        monitor.markAlive(TestSupport.AGENT_ID, NOW, NOW);
        monitor.check(NOW + 20_000L);

        // The sweep runs every couple of seconds; an outage must not mean an
        // alert every couple of seconds.
        for (int i = 1; i <= 30; i++) {
            monitor.check(NOW + 20_000L + i * 2000L);
        }

        assertAll(
                () -> assertEquals(1L, alertRepository.count()),
                () -> assertEquals(1, notifier.countOf(AlertType.AGENT_OFFLINE)));
    }

    @Test
    @DisplayName("A packet after an outage brings the agent back and records a recovery")
    void recoveryIsDetected() {
        monitor.markAlive(TestSupport.AGENT_ID, NOW, NOW);
        monitor.check(NOW + 20_000L);
        assertEquals(AgentStatus.OFFLINE, status());

        long recoveryTime = NOW + 30_000L;
        monitor.markAlive(TestSupport.AGENT_ID, recoveryTime, recoveryTime);

        assertAll(
                () -> assertEquals(AgentStatus.ONLINE, status()),
                () -> assertEquals(1, notifier.countOf(AlertType.AGENT_RECOVERED)),
                () -> assertFalse(alertService.hasOpenAlert(TestSupport.AGENT_ID,
                        AlertType.AGENT_OFFLINE), "the outage must be resolved"));
    }

    @Test
    @DisplayName("Packets from a healthy agent do not produce repeated recovery notices")
    void noRecoverySpamWhileOnline() {
        for (int i = 0; i < 20; i++) {
            monitor.markAlive(TestSupport.AGENT_ID, NOW + i * 1000L, NOW + i * 1000L);
        }

        assertEquals(0, notifier.countOf(AlertType.AGENT_RECOVERED));
    }

    @Test
    @DisplayName("An agent that was never heard from is not reported as an outage")
    void neverSeenAgentIsNotReportedOffline() {
        assertEquals(0, monitor.check(NOW + 10 * TIMEOUT_MS));
        assertAll(
                () -> assertEquals(AgentStatus.UNKNOWN, status()),
                () -> assertEquals(0L, alertRepository.count()));
    }

    @Test
    @DisplayName("Restarting the client does not report an outage from a stale last-seen time")
    void staleLastSeenFromTheDatabaseDoesNotRaiseAnOutage() {
        // What a restart looks like: the agent row carries a last-seen time from
        // hours ago, and the registry has reloaded it with status UNKNOWN.
        AgentRepository repository = new AgentRepository(database);
        repository.updateStatus(TestSupport.AGENT_ID, AgentStatus.ONLINE, NOW - 3_600_000L);
        registry.load();

        assertEquals(0, monitor.check(NOW),
                "a stored timestamp is history, not evidence that the agent is down now");
        assertAll(
                () -> assertEquals(AgentStatus.UNKNOWN, status()),
                () -> assertEquals(0L, alertRepository.count(), "no outage e-mail on startup"));

        // Once it actually reports, the normal cycle applies again.
        monitor.markAlive(TestSupport.AGENT_ID, NOW, NOW);
        assertEquals(AgentStatus.ONLINE, status());
        assertEquals(1, monitor.check(NOW + TIMEOUT_MS + 1));
    }

    @Test
    @DisplayName("The full outage and recovery cycle can repeat")
    void fullCycleRepeats() {
        monitor.markAlive(TestSupport.AGENT_ID, NOW, NOW);
        monitor.check(NOW + 20_000L);
        monitor.markAlive(TestSupport.AGENT_ID, NOW + 25_000L, NOW + 25_000L);
        monitor.check(NOW + 50_000L);
        monitor.markAlive(TestSupport.AGENT_ID, NOW + 55_000L, NOW + 55_000L);

        assertAll(
                () -> assertEquals(AgentStatus.ONLINE, status()),
                () -> assertEquals(2, notifier.countOf(AlertType.AGENT_OFFLINE)),
                () -> assertEquals(2, notifier.countOf(AlertType.AGENT_RECOVERED)));
    }

    @Test
    @DisplayName("A packet for an agent that is not registered is ignored")
    void unknownAgentIsIgnored() {
        monitor.markAlive("AGENT-NOT-REGISTERED", NOW, NOW);

        assertEquals(AgentStatus.UNKNOWN, status());
    }

    @Test
    @DisplayName("The status change is written to the database")
    void statusIsPersisted() {
        monitor.markAlive(TestSupport.AGENT_ID, NOW, NOW);
        monitor.check(NOW + 20_000L);

        AgentRecord stored = new AgentRepository(database)
                .findByAgentId(TestSupport.AGENT_ID).orElseThrow();
        assertEquals(AgentStatus.OFFLINE, stored.getStatus());
    }
}
