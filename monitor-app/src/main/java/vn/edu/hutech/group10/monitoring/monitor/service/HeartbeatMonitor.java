package vn.edu.hutech.group10.monitoring.monitor.service;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRuntimeState;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decides whether each agent is alive, and owns both liveness transitions.
 *
 * <p>Going down: a periodic sweep compares the wall clock with the last accepted
 * packet of each agent. Past the timeout the agent becomes OFFLINE and exactly
 * one AGENT_OFFLINE event is opened - the sweep runs every couple of seconds,
 * so the "already open" check in {@link AlertService} is what stops a stream of
 * duplicate alerts.
 *
 * <p>Coming back: {@link #markAlive} is called for every accepted packet,
 * telemetry or heartbeat alike, and promotes the agent to ONLINE, raising
 * AGENT_RECOVERED when it had been written off.
 *
 * <p>An agent stays UNKNOWN until this client has actually heard from it, so
 * neither adding an agent to the list nor restarting the client produces an
 * outage e-mail for a machine nobody has tried to reach yet. The last-seen time
 * loaded from the database is shown in the table as history, but it is not
 * treated as evidence that an agent is unreachable now.
 *
 * <p>The clock is injected, so the tests can move time forward instead of
 * sleeping for fifteen seconds.
 */
public class HeartbeatMonitor {

    private static final Logger LOG = Logger.getLogger(HeartbeatMonitor.class.getName());

    private final AgentRegistry registry;
    private final AlertService alertService;
    private final long timeoutMs;
    private final long checkIntervalMs;
    private final LongSupplier clock;

    private volatile ScheduledFuture<?> task;

    public HeartbeatMonitor(AgentRegistry registry, AlertService alertService,
                            long timeoutMs, long checkIntervalMs) {
        this(registry, alertService, timeoutMs, checkIntervalMs, System::currentTimeMillis);
    }

    public HeartbeatMonitor(AgentRegistry registry, AlertService alertService,
                            long timeoutMs, long checkIntervalMs, LongSupplier clock) {
        this.registry = registry;
        this.alertService = alertService;
        this.timeoutMs = timeoutMs;
        this.checkIntervalMs = checkIntervalMs;
        this.clock = clock;
    }

    /** Starts the periodic sweep on {@code executor}. */
    public void start(ScheduledExecutorService executor) {
        task = executor.scheduleAtFixedRate(this::safeCheck,
                checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        LOG.info("Heartbeat monitor started: timeout " + timeoutMs
                + " ms, checked every " + checkIntervalMs + " ms");
    }

    private void safeCheck() {
        try {
            check(clock.getAsLong());
        } catch (RuntimeException e) {
            // Never let this escape: the executor would cancel the sweep.
            LOG.log(Level.WARNING, "Heartbeat sweep failed", e);
        }
    }

    /**
     * Records that a valid packet arrived, promoting the agent to ONLINE.
     *
     * @param packetTimestamp when the agent produced the packet
     * @param nowMs           receiver clock
     */
    public void markAlive(String agentId, long packetTimestamp, long nowMs) {
        AgentRuntimeState state = registry.find(agentId).orElse(null);
        if (state == null) {
            return;
        }
        // Record the arrival against the receiver clock: an agent whose clock is
        // a little behind must not look stale the moment its packet lands.
        registry.markSeen(agentId, Math.max(packetTimestamp, nowMs));

        AgentStatus previous = state.getStatus();
        if (previous == AgentStatus.ONLINE) {
            return;
        }
        registry.setStatus(agentId, AgentStatus.ONLINE);
        if (previous == AgentStatus.OFFLINE) {
            LOG.info("Agent " + agentId + " recovered");
            alertService.raiseRecovered(state.getRecord(), nowMs);
        }
    }

    /** One sweep. Exposed so tests can drive it with an explicit clock value. */
    public int check(long nowMs) {
        int wentOffline = 0;
        for (AgentRuntimeState state : registry.states()) {
            AgentStatus status = state.getStatus();
            if (status != AgentStatus.ONLINE && status != AgentStatus.WARNING) {
                // Only an agent this client has actually heard from can be
                // declared down. An agent that is still UNKNOWN has either never
                // reported, or has not reported since this client started - its
                // stored last-seen time is history, not evidence that it is
                // unreachable now. Without this, restarting the client would
                // e-mail an outage for every registered agent at once.
                continue;
            }
            long lastSeen = state.getLastSeen();
            if (lastSeen <= 0L) {
                continue;
            }
            if (nowMs - lastSeen > timeoutMs) {
                registry.setStatus(state.getAgentId(), AgentStatus.OFFLINE);
                alertService.raiseOffline(state.getRecord(), lastSeen, timeoutMs, nowMs);
                LOG.warning("Agent " + state.getAgentId() + " is OFFLINE: nothing received for "
                        + (nowMs - lastSeen) + " ms");
                wentOffline++;
            }
        }
        return wentOffline;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void stop() {
        ScheduledFuture<?> current = task;
        if (current != null) {
            current.cancel(false);
            task = null;
            LOG.info("Heartbeat monitor stopped");
        }
    }
}
