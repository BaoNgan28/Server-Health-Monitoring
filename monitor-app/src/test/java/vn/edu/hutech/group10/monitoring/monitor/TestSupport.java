package vn.edu.hutech.group10.monitoring.monitor;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
import vn.edu.hutech.group10.monitoring.monitor.config.MonitorConfig;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricSample;

import java.nio.file.Path;

/**
 * Builders shared by the monitor tests.
 *
 * <p>Every test opens its own database under a JUnit temporary folder, so no
 * test ever touches {@code data/monitoring.db}.
 */
public final class TestSupport {

    public static final String AGENT_ID = "AGENT-01";
    public static final String SECRET = "UNIT_TEST_SECRET_01";

    private TestSupport() {
    }

    /** A registered agent with generous thresholds. */
    public static AgentRecord agent(String agentId) {
        AgentRecord record = new AgentRecord();
        record.setAgentId(agentId);
        record.setName("TEST-" + agentId);
        record.setHost("127.0.0.1");
        record.setTcpPort(5001);
        record.setSharedSecret(SECRET);
        record.setCpuThreshold(85.0d);
        record.setMemoryThreshold(85.0d);
        record.setNetworkInThresholdMbps(50.0d);
        record.setNetworkOutThresholdMbps(50.0d);
        record.setStatus(AgentStatus.UNKNOWN);
        return record;
    }

    public static AgentRecord agent() {
        return agent(AGENT_ID);
    }

    public static MetricSample sample(String agentId, long timestamp, double cpu, double memory) {
        return new MetricSample(agentId, timestamp, cpu, memory, 42, 3600L, 10_000.0d, 5_000.0d);
    }

    public static MetricSample sample(long timestamp, double cpu, double memory) {
        return sample(AGENT_ID, timestamp, cpu, memory);
    }

    /**
     * A configuration pointing at {@code databasePath} with demo-scale retention:
     * raw kept 60 s, buckets 10 s wide, aggregates kept 300 s.
     */
    public static MonitorConfig config(Path databasePath) {
        return new MonitorConfig(0, 15_000L, 2_000L, 30_000L, databasePath,
                60_000L, 10_000L, 300_000L, 15_000L, 120, 2_000, "test");
    }

    /** A configuration with explicit retention values. */
    public static MonitorConfig config(Path databasePath, long rawRetentionMs, long bucketMs,
                                       long aggregateRetentionMs) {
        return new MonitorConfig(0, 15_000L, 2_000L, 30_000L, databasePath,
                rawRetentionMs, bucketMs, aggregateRetentionMs, 15_000L, 120, 2_000, "test");
    }
}
