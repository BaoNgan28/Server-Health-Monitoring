package vn.edu.hutech.group10.monitoring.agent.service;

import vn.edu.hutech.group10.monitoring.agent.config.AgentConfig;
import vn.edu.hutech.group10.monitoring.agent.metrics.SystemMetrics;
import vn.edu.hutech.group10.monitoring.agent.metrics.SystemMetricsCollector;
import vn.edu.hutech.group10.monitoring.agent.network.UdpTelemetrySender;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageType;
import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.common.security.PacketSigner;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Samples the machine and sends a signed telemetry packet on the configured
 * interval.
 *
 * <p>The task never throws: a scheduled task that escapes with an exception is
 * silently cancelled by {@link ScheduledExecutorService}, which would stop the
 * agent reporting with no visible error, so everything is caught and logged.
 *
 * <p>The sequence counter is shared with {@link HeartbeatScheduler}, because the
 * monitoring client keeps a single replay counter per agent covering both packet
 * kinds.
 */
public class TelemetryScheduler {

    private static final Logger LOG = Logger.getLogger(TelemetryScheduler.class.getName());

    private final AgentConfig config;
    private final SystemMetricsCollector collector;
    private final UdpTelemetrySender sender;
    private final ScheduledExecutorService executor;
    private final AtomicLong sequence;
    private final String sessionId;

    private volatile ScheduledFuture<?> task;
    private volatile long currentIntervalMs;
    private volatile SystemMetrics lastSample;

    public TelemetryScheduler(AgentConfig config, SystemMetricsCollector collector,
                              UdpTelemetrySender sender, ScheduledExecutorService executor,
                              AtomicLong sequence, String sessionId) {
        this.config = config;
        this.collector = collector;
        this.sender = sender;
        this.executor = executor;
        this.sequence = sequence;
        this.sessionId = sessionId;
    }

    /** Starts sending on the interval currently held in the configuration. */
    public synchronized void start() {
        schedule(config.getMetricIntervalMs());
    }

    /** Re-arms the timer when CONFIG_UPDATE changed the interval. */
    public synchronized void reschedule() {
        long desired = config.getMetricIntervalMs();
        if (desired == currentIntervalMs && task != null && !task.isCancelled()) {
            return;
        }
        schedule(desired);
        LOG.info("Telemetry interval is now " + desired + " ms");
    }

    private void schedule(long intervalMs) {
        cancel();
        currentIntervalMs = intervalMs;
        task = executor.scheduleAtFixedRate(this::tick, 0L, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        try {
            SystemMetrics metrics = collector.collect();
            lastSample = metrics;

            TelemetryPacket packet = new TelemetryPacket();
            packet.setProtocolVersion(ProtocolConstants.PROTOCOL_VERSION);
            packet.setType(MessageType.TELEMETRY.wireName());
            packet.setAgentId(config.getAgentId());
            packet.setHostname(config.getHostname());
            packet.setSessionId(sessionId);
            packet.setTimestamp(metrics.getTimestamp());
            packet.setSequence(sequence.incrementAndGet());
            packet.setCpuUsage(metrics.getCpuUsage());
            packet.setMemoryUsage(metrics.getMemoryUsage());
            packet.setConnectionCount(metrics.getConnectionCount());
            packet.setUptimeSeconds(metrics.getUptimeSeconds());
            packet.setNetworkInBytesPerSecond(metrics.getNetworkInBytesPerSecond());
            packet.setNetworkOutBytesPerSecond(metrics.getNetworkOutBytesPerSecond());

            PacketSigner.sign(packet, config.getSharedSecret());
            sender.send(packet);
        } catch (RuntimeException e) {
            // Swallowed on purpose: letting this escape would cancel the timer.
            LOG.log(Level.WARNING, "Telemetry tick failed", e);
        }
    }

    /** Most recent sample, or {@code null} before the first tick. */
    public SystemMetrics getLastSample() {
        return lastSample;
    }

    public long getCurrentIntervalMs() {
        return currentIntervalMs;
    }

    /** Cancels the timer; the executor itself belongs to the caller. */
    public synchronized void cancel() {
        ScheduledFuture<?> current = task;
        if (current != null) {
            current.cancel(false);
            task = null;
        }
    }
}
