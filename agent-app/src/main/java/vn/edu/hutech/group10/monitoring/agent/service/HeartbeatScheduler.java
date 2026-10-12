package vn.edu.hutech.group10.monitoring.agent.service;

import vn.edu.hutech.group10.monitoring.agent.config.AgentConfig;
import vn.edu.hutech.group10.monitoring.agent.network.UdpTelemetrySender;
import vn.edu.hutech.group10.monitoring.common.model.HeartbeatPacket;
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
 * Sends a signed heartbeat on the configured interval.
 *
 * <p>Telemetry already proves the agent is alive, but the heartbeat is much
 * cheaper and keeps arriving even if metric collection is struggling, so the
 * monitoring client can tell "no metrics" apart from "machine gone".
 */
public class HeartbeatScheduler {

    private static final Logger LOG = Logger.getLogger(HeartbeatScheduler.class.getName());

    private final AgentConfig config;
    private final UdpTelemetrySender sender;
    private final ScheduledExecutorService executor;
    private final AtomicLong sequence;
    private final String sessionId;

    private volatile ScheduledFuture<?> task;
    private volatile long currentIntervalMs;

    public HeartbeatScheduler(AgentConfig config, UdpTelemetrySender sender,
                              ScheduledExecutorService executor, AtomicLong sequence,
                              String sessionId) {
        this.config = config;
        this.sender = sender;
        this.executor = executor;
        this.sequence = sequence;
        this.sessionId = sessionId;
    }

    public synchronized void start() {
        schedule(config.getHeartbeatIntervalMs());
    }

    /** Re-arms the timer when CONFIG_UPDATE changed the interval. */
    public synchronized void reschedule() {
        long desired = config.getHeartbeatIntervalMs();
        if (desired == currentIntervalMs && task != null && !task.isCancelled()) {
            return;
        }
        schedule(desired);
        LOG.info("Heartbeat interval is now " + desired + " ms");
    }

    private void schedule(long intervalMs) {
        cancel();
        currentIntervalMs = intervalMs;
        task = executor.scheduleAtFixedRate(this::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        try {
            HeartbeatPacket packet = new HeartbeatPacket();
            packet.setProtocolVersion(ProtocolConstants.PROTOCOL_VERSION);
            packet.setType(MessageType.HEARTBEAT.wireName());
            packet.setAgentId(config.getAgentId());
            packet.setHostname(config.getHostname());
            packet.setSessionId(sessionId);
            packet.setTimestamp(System.currentTimeMillis());
            packet.setSequence(sequence.incrementAndGet());

            PacketSigner.sign(packet, config.getSharedSecret());
            sender.send(packet);
        } catch (RuntimeException e) {
            // Swallowed on purpose: letting this escape would cancel the timer.
            LOG.log(Level.WARNING, "Heartbeat tick failed", e);
        }
    }

    public long getCurrentIntervalMs() {
        return currentIntervalMs;
    }

    public synchronized void cancel() {
        ScheduledFuture<?> current = task;
        if (current != null) {
            current.cancel(false);
            task = null;
        }
    }
}
