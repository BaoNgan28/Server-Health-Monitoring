package vn.edu.hutech.group10.monitoring.monitor.service;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentSnapshot;

/**
 * How the service layer tells the GUI that something happened.
 *
 * <p>Callbacks arrive on worker threads - the UDP receiver, the heartbeat timer,
 * the aggregation timer - so a Swing listener must hop to the event dispatch
 * thread itself. Every method has a default, so a listener implements only what
 * it cares about.
 */
public interface MonitorEventListener {

    /** A new metric sample or a status change was applied to one agent. */
    default void onAgentUpdated(AgentSnapshot snapshot) {
    }

    /** An agent was added, edited or deleted; the whole list should be reloaded. */
    default void onAgentListChanged() {
    }

    /** An alert was opened or resolved. */
    default void onAlert(AlertEvent event) {
    }

    /** A packet was refused; {@code reason} is already safe to display. */
    default void onPacketRejected(String agentId, String reason) {
    }

    /** An aggregation run finished. */
    default void onAggregationCompleted(int bucketsWritten, int aggregatesPruned) {
    }
}
