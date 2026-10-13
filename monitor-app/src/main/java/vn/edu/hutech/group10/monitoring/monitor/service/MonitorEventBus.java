package vn.edu.hutech.group10.monitoring.monitor.service;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentSnapshot;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delivers service-layer events to the registered listeners.
 *
 * <p>A copy-on-write list is used because listeners are registered once at
 * startup and then read on every packet. A listener that throws is logged and
 * skipped, so a GUI bug cannot break telemetry processing.
 */
public class MonitorEventBus {

    private static final Logger LOG = Logger.getLogger(MonitorEventBus.class.getName());

    private final List<MonitorEventListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(MonitorEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(MonitorEventListener listener) {
        listeners.remove(listener);
    }

    public void fireAgentUpdated(AgentSnapshot snapshot) {
        dispatch(listener -> listener.onAgentUpdated(snapshot));
    }

    public void fireAgentListChanged() {
        dispatch(MonitorEventListener::onAgentListChanged);
    }

    public void fireAlert(AlertEvent event) {
        dispatch(listener -> listener.onAlert(event));
    }

    public void firePacketRejected(String agentId, String reason) {
        dispatch(listener -> listener.onPacketRejected(agentId, reason));
    }

    public void fireAggregationCompleted(int bucketsWritten, int aggregatesPruned) {
        dispatch(listener -> listener.onAggregationCompleted(bucketsWritten, aggregatesPruned));
    }

    private void dispatch(Consumer<MonitorEventListener> action) {
        for (MonitorEventListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "A monitor event listener failed", e);
            }
        }
    }

    public int getListenerCount() {
        return listeners.size();
    }
}
