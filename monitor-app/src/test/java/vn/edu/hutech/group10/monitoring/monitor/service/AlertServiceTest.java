package vn.edu.hutech.group10.monitoring.monitor.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.common.model.AlertState;
import vn.edu.hutech.group10.monitoring.common.model.AlertType;
import vn.edu.hutech.group10.monitoring.monitor.TestSupport;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;
import vn.edu.hutech.group10.monitoring.monitor.notification.RecordingNotifier;
import vn.edu.hutech.group10.monitoring.monitor.repository.AgentRepository;
import vn.edu.hutech.group10.monitoring.monitor.repository.AlertRepository;
import vn.edu.hutech.group10.monitoring.monitor.repository.DatabaseManager;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertServiceTest {

    private static final long NOW = 1_700_000_000_000L;

    private DatabaseManager database;
    private AlertRepository alertRepository;
    private RecordingNotifier notifier;
    private AlertService alertService;
    private AgentRecord agent;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        database = new DatabaseManager(tempDir.resolve("monitoring.db"));
        database.initialize();

        agent = TestSupport.agent();
        agent.setCpuThreshold(50.0d);
        agent.setMemoryThreshold(80.0d);
        new AgentRepository(database).insert(agent);

        alertRepository = new AlertRepository(database);
        notifier = new RecordingNotifier();
        alertService = new AlertService(alertRepository, notifier, new MonitorEventBus());
        alertService.load();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName("Crossing the CPU threshold opens one alert, stored and notified")
    void opensCpuAlert() {
        alertService.evaluate(agent, TestSupport.sample(NOW, 75.0d, 10.0d));

        List<AlertEvent> open = alertRepository.findAllOpen();
        assertAll(
                () -> assertEquals(1, open.size()),
                () -> assertEquals(AlertType.CPU_HIGH, open.get(0).getAlertType()),
                () -> assertEquals(AlertState.OPEN, open.get(0).getState()),
                () -> assertEquals(75.0d, open.get(0).getCurrentValue(), 0.0001d),
                () -> assertEquals(50.0d, open.get(0).getThreshold(), 0.0001d),
                () -> assertEquals(1, notifier.countOf(AlertType.CPU_HIGH)),
                () -> assertTrue(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH)));
    }

    @Test
    @DisplayName("A metric staying over the threshold does not produce a second alert or e-mail")
    void suppressesDuplicateAlerts() {
        // Sixty samples, one per second, all above the threshold.
        for (int i = 0; i < 60; i++) {
            alertService.evaluate(agent, TestSupport.sample(NOW + i * 1000L, 75.0d, 10.0d));
        }

        assertAll(
                () -> assertEquals(1L, alertRepository.count(), "one row, not sixty"),
                () -> assertEquals(1, notifier.countOf(AlertType.CPU_HIGH), "one e-mail, not sixty"));
    }

    @Test
    @DisplayName("Recovery below the hysteresis band resolves the alert")
    void resolvesOnRecovery() {
        alertService.evaluate(agent, TestSupport.sample(NOW, 75.0d, 10.0d));
        assertTrue(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH));

        alertService.evaluate(agent, TestSupport.sample(NOW + 1000L, 20.0d, 10.0d));

        assertAll(
                () -> assertFalse(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH)),
                () -> assertEquals(0, alertRepository.findAllOpen().size()),
                () -> assertEquals(1L, alertRepository.count(), "the event is resolved, not deleted"));

        AlertEvent stored = alertRepository.findByAgent(TestSupport.AGENT_ID, 10).get(0);
        assertAll(
                () -> assertEquals(AlertState.RESOLVED, stored.getState()),
                () -> assertEquals(NOW + 1000L, stored.getResolvedAt()));
    }

    @Test
    @DisplayName("A value hovering at the threshold does not flap the alert open and shut")
    void hysteresisPreventsFlapping() {
        alertService.evaluate(agent, TestSupport.sample(NOW, 55.0d, 10.0d));
        assertTrue(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH));

        // Just below the threshold, but inside the two-point band: still open.
        alertService.evaluate(agent, TestSupport.sample(NOW + 1000L, 49.0d, 10.0d));
        assertTrue(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH),
                "49% is within the recovery band of a 50% threshold");

        alertService.evaluate(agent, TestSupport.sample(NOW + 2000L, 55.0d, 10.0d));
        assertEquals(1L, alertRepository.count(), "no second alert was opened");

        // Clear of the band: now it resolves.
        alertService.evaluate(agent, TestSupport.sample(NOW + 3000L, 47.0d, 10.0d));
        assertFalse(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH));
    }

    @Test
    @DisplayName("A new violation after a recovery opens a new alert")
    void reopensAfterRecovery() {
        alertService.evaluate(agent, TestSupport.sample(NOW, 75.0d, 10.0d));
        alertService.evaluate(agent, TestSupport.sample(NOW + 1000L, 10.0d, 10.0d));
        alertService.evaluate(agent, TestSupport.sample(NOW + 2000L, 80.0d, 10.0d));

        assertAll(
                () -> assertEquals(2L, alertRepository.count()),
                () -> assertEquals(1, alertRepository.findAllOpen().size()),
                () -> assertEquals(2, notifier.countOf(AlertType.CPU_HIGH)));
    }

    @Test
    @DisplayName("Each metric has its own independent alert")
    void metricsAreIndependent() {
        alertService.evaluate(agent, TestSupport.sample(NOW, 75.0d, 90.0d));

        assertAll(
                () -> assertTrue(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH)),
                () -> assertTrue(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.MEMORY_HIGH)),
                () -> assertEquals(2, alertService.getOpenAlertCount()));

        // CPU recovers, memory does not.
        alertService.evaluate(agent, TestSupport.sample(NOW + 1000L, 5.0d, 90.0d));

        assertAll(
                () -> assertFalse(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH)),
                () -> assertTrue(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.MEMORY_HIGH)));
    }

    @Test
    @DisplayName("Network thresholds are compared in Mbps, not in bytes per second")
    void networkThresholdUsesMbps() {
        agent.setNetworkInThresholdMbps(1.0d);
        // 1 MB/s is 8 Mbps, comfortably over a 1 Mbps threshold.
        alertService.evaluate(agent, new vn.edu.hutech.group10.monitoring.monitor.model.MetricSample(
                TestSupport.AGENT_ID, NOW, 5.0d, 5.0d, 10, 100L, 1_000_000.0d, 0.0d));

        assertTrue(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.NETWORK_IN_HIGH));
        AlertEvent event = alertRepository.findAllOpen().get(0);
        assertAll(
                () -> assertEquals(8.0d, event.getCurrentValue(), 0.01d),
                () -> assertEquals("Mbps", event.getUnit()));
    }

    @Test
    @DisplayName("An offline alert is opened once, however often the sweep runs")
    void offlineAlertIsRaisedOnce() {
        for (int i = 0; i < 10; i++) {
            alertService.raiseOffline(agent, NOW, 15_000L, NOW + 20_000L + i * 1000L);
        }

        assertAll(
                () -> assertEquals(1L, alertRepository.count()),
                () -> assertEquals(1, notifier.countOf(AlertType.AGENT_OFFLINE)),
                () -> assertTrue(alertService.hasOpenAlert(TestSupport.AGENT_ID,
                        AlertType.AGENT_OFFLINE)));
    }

    @Test
    @DisplayName("Recovery resolves the outage and records a recovery notice")
    void recoveryResolvesOffline() {
        alertService.raiseOffline(agent, NOW, 15_000L, NOW + 20_000L);
        alertService.raiseRecovered(agent, NOW + 30_000L);

        assertAll(
                () -> assertFalse(alertService.hasOpenAlert(TestSupport.AGENT_ID,
                        AlertType.AGENT_OFFLINE)),
                () -> assertEquals(0, alertRepository.findAllOpen().size(),
                        "a recovery notice is stored already closed"),
                () -> assertEquals(1, notifier.countOf(AlertType.AGENT_RECOVERED)),
                () -> assertEquals(2L, alertRepository.count()));
    }

    @Test
    @DisplayName("A second outage after a recovery opens a new alert")
    void offlineCanBeRaisedAgainAfterRecovery() {
        alertService.raiseOffline(agent, NOW, 15_000L, NOW + 20_000L);
        alertService.raiseRecovered(agent, NOW + 30_000L);
        alertService.raiseOffline(agent, NOW + 40_000L, 15_000L, NOW + 60_000L);

        assertEquals(2, notifier.countOf(AlertType.AGENT_OFFLINE));
    }

    @Test
    @DisplayName("Security events are recorded, rate limited, and never e-mailed")
    void securityEventsAreRecordedButNotEmailed() {
        alertService.recordSecurityEvent(TestSupport.AGENT_ID, AlertType.SECURITY_INVALID_HMAC,
                "signature mismatch", NOW);
        // A flood inside the cooldown must not become a flood of rows.
        for (int i = 1; i <= 20; i++) {
            alertService.recordSecurityEvent(TestSupport.AGENT_ID, AlertType.SECURITY_INVALID_HMAC,
                    "signature mismatch", NOW + i * 1000L);
        }

        assertAll(
                () -> assertEquals(1L, alertRepository.count()),
                () -> assertEquals(0, notifier.countOf(AlertType.SECURITY_INVALID_HMAC),
                        "forged packets must not generate e-mail"),
                () -> assertEquals(AlertState.RESOLVED,
                        alertRepository.findByAgent(TestSupport.AGENT_ID, 1).get(0).getState()));

        // Past the cooldown, a new incident is recorded again.
        alertService.recordSecurityEvent(TestSupport.AGENT_ID, AlertType.SECURITY_INVALID_HMAC,
                "signature mismatch", NOW + 120_000L);
        assertEquals(2L, alertRepository.count());
    }

    @Test
    @DisplayName("Open alerts are restored from the database on restart")
    void reloadsOpenAlertsOnStartup() {
        alertService.evaluate(agent, TestSupport.sample(NOW, 75.0d, 10.0d));

        AlertService restarted = new AlertService(alertRepository, notifier, new MonitorEventBus());
        restarted.load();

        assertTrue(restarted.hasOpenAlert(TestSupport.AGENT_ID, AlertType.CPU_HIGH),
                "a restart must not reopen an alert that is already open");

        restarted.evaluate(agent, TestSupport.sample(NOW + 1000L, 75.0d, 10.0d));
        assertEquals(1L, alertRepository.count());
    }

    @Test
    @DisplayName("A zero threshold disables that check instead of firing constantly")
    void zeroThresholdDisablesTheCheck() {
        agent.setNetworkOutThresholdMbps(0.0d);
        alertService.evaluate(agent, TestSupport.sample(NOW, 1.0d, 1.0d));

        assertFalse(alertService.hasOpenAlert(TestSupport.AGENT_ID, AlertType.NETWORK_OUT_HIGH));
    }

    @Test
    @DisplayName("Forgetting a deleted agent clears its cached alerts")
    void forgetAgentClearsCache() {
        alertService.evaluate(agent, TestSupport.sample(NOW, 75.0d, 90.0d));
        assertEquals(2, alertService.getOpenAlertCount());

        alertService.forgetAgent(TestSupport.AGENT_ID);

        assertEquals(0, alertService.getOpenAlertCount());
    }
}
