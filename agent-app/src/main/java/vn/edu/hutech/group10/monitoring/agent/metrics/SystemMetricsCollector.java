package vn.edu.hutech.group10.monitoring.agent.metrics;

import vn.edu.hutech.group10.monitoring.common.model.AgentInfoMessage;

/**
 * Source of operating-system metrics. Kept as an interface so the schedulers can
 * be unit tested without touching real hardware.
 */
public interface SystemMetricsCollector extends AutoCloseable {

    /**
     * Takes one sample. Implementations must never throw for a metric that is
     * simply unavailable on this platform; they report a safe value instead.
     */
    SystemMetrics collect();

    /** Fills the machine description fields of {@code message}. */
    void describe(AgentInfoMessage message);

    @Override
    void close();
}
