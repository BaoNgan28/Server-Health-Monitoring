package vn.edu.hutech.group10.monitoring.monitor.service;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.common.model.AlertSeverity;
import vn.edu.hutech.group10.monitoring.common.model.AlertState;
import vn.edu.hutech.group10.monitoring.common.model.AlertType;
import vn.edu.hutech.group10.monitoring.common.util.ByteRateFormatter;
import vn.edu.hutech.group10.monitoring.common.util.TimeUtil;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricSample;
import vn.edu.hutech.group10.monitoring.monitor.notification.Notifier;
import vn.edu.hutech.group10.monitoring.monitor.repository.AlertRepository;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Turns metric samples and liveness changes into alert events.
 *
 * <h2>State machine</h2>
 * Each (agent, alert type) pair is either closed or has exactly one OPEN event.
 * <ul>
 *   <li>value crosses the threshold and nothing is open - an event is opened,
 *       written to the database, pushed to the GUI and handed to the notifier;</li>
 *   <li>value stays over the threshold - nothing happens, which is what stops
 *       one alert per sample and one e-mail per second;</li>
 *   <li>value falls back below the threshold <em>minus the hysteresis band</em>
 *       - the open event is resolved;</li>
 *   <li>the value climbs again later - a brand new event is opened.</li>
 * </ul>
 *
 * <h2>Hysteresis</h2>
 * Resolving at exactly the threshold would make a metric hovering around it open
 * and close an alert on alternate samples. Recovery therefore needs the value to
 * come back down by a margin: two percentage points for CPU and memory, five per
 * cent of the threshold for network rates.
 *
 * <h2>Security events</h2>
 * A rejected packet is recorded as an already-resolved event so it is visible in
 * the alert panel, at most once per agent and reason per minute, and is not
 * e-mailed: a flood of forged packets must not turn into a flood of mail.
 */
public class AlertService {

    private static final Logger LOG = Logger.getLogger(AlertService.class.getName());

    /** Percentage points a usage metric must fall below the threshold to clear. */
    private static final double PERCENTAGE_HYSTERESIS = 2.0d;
    /** Fraction of the threshold a rate must fall below to clear. */
    private static final double RATE_HYSTERESIS_FRACTION = 0.05d;
    /** Shortest gap between two recorded security events of the same kind. */
    private static final long SECURITY_EVENT_COOLDOWN_MS = 60_000L;

    private final AlertRepository repository;
    private final Notifier notifier;
    private final MonitorEventBus eventBus;

    /** key is {@code agentId|ALERT_TYPE}; holds the currently open event. */
    private final Map<String, AlertEvent> openAlerts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSecurityEventAt = new ConcurrentHashMap<>();

    public AlertService(AlertRepository repository, Notifier notifier, MonitorEventBus eventBus) {
        this.repository = repository;
        this.notifier = notifier;
        this.eventBus = eventBus;
    }

    /** Rebuilds the open-alert cache from the database. Called once at startup. */
    public void load() {
        openAlerts.clear();
        for (AlertEvent event : repository.findAllOpen()) {
            openAlerts.put(key(event.getAgentId(), event.getAlertType()), event);
        }
        LOG.info("Loaded " + openAlerts.size() + " open alert(s) from the database");
    }

    /** Evaluates every threshold of one agent against one accepted sample. */
    public void evaluate(AgentRecord agent, MetricSample sample) {
        if (agent == null || sample == null) {
            return;
        }
        long now = sample.getTimestamp();

        evaluateThreshold(agent, AlertType.CPU_HIGH, AlertSeverity.WARNING,
                sample.getCpuUsage(), agent.getCpuThreshold(), "%",
                PERCENTAGE_HYSTERESIS, now);

        evaluateThreshold(agent, AlertType.MEMORY_HIGH, AlertSeverity.WARNING,
                sample.getMemoryUsage(), agent.getMemoryThreshold(), "%",
                PERCENTAGE_HYSTERESIS, now);

        double inMbps = ByteRateFormatter.toMbps(sample.getNetworkInBytesPerSecond());
        evaluateThreshold(agent, AlertType.NETWORK_IN_HIGH, AlertSeverity.WARNING,
                inMbps, agent.getNetworkInThresholdMbps(), "Mbps",
                agent.getNetworkInThresholdMbps() * RATE_HYSTERESIS_FRACTION, now);

        double outMbps = ByteRateFormatter.toMbps(sample.getNetworkOutBytesPerSecond());
        evaluateThreshold(agent, AlertType.NETWORK_OUT_HIGH, AlertSeverity.WARNING,
                outMbps, agent.getNetworkOutThresholdMbps(), "Mbps",
                agent.getNetworkOutThresholdMbps() * RATE_HYSTERESIS_FRACTION, now);
    }

    private void evaluateThreshold(AgentRecord agent, AlertType type, AlertSeverity severity,
                                   double value, double threshold, String unit,
                                   double hysteresis, long now) {
        if (threshold <= 0.0d) {
            return;
        }
        String key = key(agent.getAgentId(), type);
        AlertEvent open = openAlerts.get(key);

        if (value > threshold) {
            if (open == null) {
                openAlert(agent, type, severity, value, threshold, unit,
                        String.format(Locale.ROOT, "%s is %.1f %s, above the threshold of %.1f %s",
                                type.getDisplayName(), value, unit, threshold, unit),
                        now);
            }
            return;
        }

        if (open != null && value <= threshold - Math.max(0.0d, hysteresis)) {
            resolveAlert(key, open, now,
                    String.format(Locale.ROOT, "%s recovered to %.1f %s",
                            type.getDisplayName(), value, unit));
        }
    }

    /** Opens AGENT_OFFLINE for an agent whose heartbeat timed out. */
    public void raiseOffline(AgentRecord agent, long lastSeen, long timeoutMs, long now) {
        String key = key(agent.getAgentId(), AlertType.AGENT_OFFLINE);
        if (openAlerts.containsKey(key)) {
            // Already reported; the heartbeat monitor must not repeat it every tick.
            return;
        }
        AlertEvent event = newEvent(agent, AlertType.AGENT_OFFLINE, AlertSeverity.CRITICAL,
                "No valid packet received since " + TimeUtil.formatTimestamp(lastSeen), now);
        // The body of the offline e-mail reads these two as "last seen" and "timeout".
        event.setCurrentValue(lastSeen);
        event.setThreshold(timeoutMs);
        event.setUnit("ms");

        store(key, event);
    }

    /** Resolves AGENT_OFFLINE and opens an AGENT_RECOVERED notice. */
    public void raiseRecovered(AgentRecord agent, long now) {
        String offlineKey = key(agent.getAgentId(), AlertType.AGENT_OFFLINE);
        AlertEvent offline = openAlerts.get(offlineKey);
        if (offline != null) {
            resolveAlert(offlineKey, offline, now, "Agent is reachable again");
        }

        AlertEvent event = newEvent(agent, AlertType.AGENT_RECOVERED, AlertSeverity.INFO,
                "Agent is online again", now);
        // A recovery is an instant, not a condition, so it is stored already closed.
        event.setState(AlertState.RESOLVED);
        event.setResolvedAt(now);
        repository.insert(event);
        LOG.info("Alert " + AlertType.AGENT_RECOVERED + " for " + agent.getAgentId());
        eventBus.fireAlert(event);
        notifier.sendAlert(event);
    }

    /**
     * Records a refused packet. Rate limited, stored already resolved, and never
     * e-mailed.
     */
    public void recordSecurityEvent(String agentId, AlertType type, String detail, long now) {
        if (agentId == null || type == null) {
            return;
        }
        String key = key(agentId, type);
        Long last = lastSecurityEventAt.get(key);
        if (last != null && now - last < SECURITY_EVENT_COOLDOWN_MS) {
            return;
        }
        lastSecurityEventAt.put(key, now);

        AlertEvent event = new AlertEvent();
        event.setAgentId(agentId);
        event.setAgentName(agentId);
        event.setAlertType(type);
        event.setSeverity(AlertSeverity.WARNING);
        event.setState(AlertState.RESOLVED);
        event.setMessage(detail);
        event.setOpenedAt(now);
        event.setResolvedAt(now);
        event.setUnit("");

        try {
            repository.insert(event);
        } catch (RuntimeException e) {
            // An unknown agent has no row to hang the alert off; the log line is
            // the record in that case.
            LOG.warning("Security event for " + agentId + " could not be stored: " + e.getMessage());
        }
        LOG.warning("SECURITY " + type + " from " + agentId + ": " + detail);
        eventBus.fireAlert(event);
    }

    private void openAlert(AgentRecord agent, AlertType type, AlertSeverity severity,
                           double value, double threshold, String unit, String message, long now) {
        AlertEvent event = newEvent(agent, type, severity, message, now);
        event.setCurrentValue(value);
        event.setThreshold(threshold);
        event.setUnit(unit);
        store(key(agent.getAgentId(), type), event);
    }

    private void store(String key, AlertEvent event) {
        repository.insert(event);
        openAlerts.put(key, event);
        LOG.warning("Alert OPEN " + event.getAlertType() + " for " + event.getAgentId()
                + ": " + event.getMessage());
        eventBus.fireAlert(event);
        notifier.sendAlert(event);
    }

    private void resolveAlert(String key, AlertEvent event, long now, String reason) {
        openAlerts.remove(key);
        event.setState(AlertState.RESOLVED);
        event.setResolvedAt(now);
        repository.resolve(event.getId(), now);
        LOG.info("Alert RESOLVED " + event.getAlertType() + " for " + event.getAgentId()
                + ": " + reason);
        eventBus.fireAlert(event);
    }

    private AlertEvent newEvent(AgentRecord agent, AlertType type, AlertSeverity severity,
                                String message, long now) {
        AlertEvent event = new AlertEvent(agent.getAgentId(), agent.getName(), type, severity,
                message, now);
        event.setUnit("");
        return event;
    }

    /** True when an alert of this type is currently open for this agent. */
    public boolean hasOpenAlert(String agentId, AlertType type) {
        return openAlerts.containsKey(key(agentId, type));
    }

    public int getOpenAlertCount() {
        return openAlerts.size();
    }

    /** Forgets every cached open alert of an agent that has just been deleted. */
    public void forgetAgent(String agentId) {
        String prefix = agentId + "|";
        openAlerts.keySet().removeIf(key -> key.startsWith(prefix));
        lastSecurityEventAt.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String key(String agentId, AlertType type) {
        return agentId + "|" + type.name();
    }
}
