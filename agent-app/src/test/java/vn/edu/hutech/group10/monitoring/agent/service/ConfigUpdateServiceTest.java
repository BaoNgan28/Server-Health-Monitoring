package vn.edu.hutech.group10.monitoring.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.hutech.group10.monitoring.agent.config.AgentConfig;
import vn.edu.hutech.group10.monitoring.agent.config.AgentConfigLoader;
import vn.edu.hutech.group10.monitoring.common.model.ConfigAckMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigUpdateMessage;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdateServiceTest {

    private AgentConfig config;
    private AtomicInteger notifications;
    private ConfigUpdateService service;

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.setProperty(AgentConfigLoader.KEY_AGENT_ID, "AGENT-01");
        properties.setProperty(AgentConfigLoader.KEY_SHARED_SECRET, "SECRET");
        config = AgentConfigLoader.fromProperties(properties);

        notifications = new AtomicInteger();
        service = new ConfigUpdateService(config, changed -> notifications.incrementAndGet());
    }

    @Test
    @DisplayName("A valid update is applied and acknowledged with the effective values")
    void appliesValidUpdate() {
        ConfigUpdateMessage update = new ConfigUpdateMessage();
        update.setRequestId("req-1");
        update.setMetricIntervalMs(2000L);
        update.setCpuThreshold(5.0d);

        ConfigAckMessage ack = service.apply(update);

        assertAll(
                () -> assertTrue(ack.isSuccess(), ack.getMessage()),
                () -> assertEquals("req-1", ack.getRequestId()),
                () -> assertEquals(2000L, config.getMetricIntervalMs()),
                () -> assertEquals(5.0d, config.getCpuThreshold(), 0.0001d),
                () -> assertEquals(2000L, ack.getEffectiveMetricIntervalMs()),
                () -> assertEquals(5.0d, ack.getEffectiveCpuThreshold(), 0.0001d),
                () -> assertEquals(1, notifications.get(), "schedulers must be told to re-arm"));
    }

    @Test
    @DisplayName("Unset fields are left exactly as they were")
    void nullFieldsMeanLeaveUnchanged() {
        long originalHeartbeat = config.getHeartbeatIntervalMs();
        double originalMemory = config.getMemoryThreshold();

        ConfigUpdateMessage update = new ConfigUpdateMessage();
        update.setRequestId("req-2");
        update.setCpuThreshold(42.0d);

        assertTrue(service.apply(update).isSuccess());
        assertAll(
                () -> assertEquals(originalHeartbeat, config.getHeartbeatIntervalMs()),
                () -> assertEquals(originalMemory, config.getMemoryThreshold(), 0.0001d));
    }

    @Test
    @DisplayName("An interval below the minimum is rejected and nothing is applied")
    void rejectsTooSmallInterval() {
        long original = config.getMetricIntervalMs();

        ConfigUpdateMessage update = new ConfigUpdateMessage();
        update.setRequestId("req-3");
        update.setMetricIntervalMs(10L);

        ConfigAckMessage ack = service.apply(update);

        assertAll(
                () -> assertFalse(ack.isSuccess()),
                () -> assertEquals(ConfigAckMessage.STATUS_ERROR, ack.getStatus()),
                () -> assertTrue(ack.getMessage().contains("metricIntervalMs"), ack.getMessage()),
                () -> assertEquals(original, config.getMetricIntervalMs(), "must not be applied"),
                () -> assertEquals(0, notifications.get()));
    }

    @Test
    @DisplayName("One bad field rejects the whole message, leaving the good fields unapplied")
    void validationIsAllOrNothing() {
        long originalInterval = config.getMetricIntervalMs();

        ConfigUpdateMessage update = new ConfigUpdateMessage();
        update.setRequestId("req-4");
        update.setMetricIntervalMs(3000L);
        update.setMemoryThreshold(500.0d);

        ConfigAckMessage ack = service.apply(update);

        assertAll(
                () -> assertFalse(ack.isSuccess()),
                () -> assertEquals(originalInterval, config.getMetricIntervalMs()),
                () -> assertTrue(ack.getMessage().contains("memoryThreshold"), ack.getMessage()));
    }

    @Test
    @DisplayName("Non-finite and negative numbers are rejected")
    void rejectsNonFiniteAndNegativeValues() {
        ConfigUpdateMessage nan = new ConfigUpdateMessage();
        nan.setCpuThreshold(Double.NaN);
        assertFalse(service.apply(nan).isSuccess());

        ConfigUpdateMessage negative = new ConfigUpdateMessage();
        negative.setNetworkInThresholdMbps(-1.0d);
        assertFalse(service.apply(negative).isSuccess());

        ConfigUpdateMessage zeroPercent = new ConfigUpdateMessage();
        zeroPercent.setCpuThreshold(0.0d);
        assertFalse(service.apply(zeroPercent).isSuccess(),
                "a 0% threshold would fire permanently and is refused");
    }

    @Test
    @DisplayName("An empty update succeeds without disturbing anything")
    void emptyUpdateIsANoOp() {
        ConfigAckMessage ack = service.apply(new ConfigUpdateMessage());

        assertAll(
                () -> assertTrue(ack.isSuccess()),
                () -> assertEquals(0, notifications.get()));
    }

    @Test
    @DisplayName("A null message is answered with an error rather than an exception")
    void nullUpdateIsAnError() {
        ConfigAckMessage ack = service.apply(null);
        assertFalse(ack.isSuccess());
    }
}
