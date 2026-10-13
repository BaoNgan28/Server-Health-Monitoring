package vn.edu.hutech.group10.monitoring.monitor.service;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
import vn.edu.hutech.group10.monitoring.common.security.SecretProvider;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRuntimeState;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentSnapshot;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricSample;
import vn.edu.hutech.group10.monitoring.monitor.repository.AgentRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * In-memory view of every registered agent, kept in step with the
 * {@code agents} table.
 *
 * <p>It is also the {@link SecretProvider} the packet validator consults, which
 * means an agent that is not in this registry has no secret and its packets are
 * dropped as UNKNOWN_AGENT before any signature is computed.
 *
 * <p>Thread safe: the map is concurrent and each entry keeps its own volatile
 * fields, so the UDP receiver, the heartbeat timer and the GUI can work at the
 * same time without a global lock.
 */
public class AgentRegistry implements SecretProvider {

    private static final Logger LOG = Logger.getLogger(AgentRegistry.class.getName());

    private final AgentRepository repository;
    private final MonitorEventBus eventBus;
    private final Map<String, AgentRuntimeState> agents = new ConcurrentHashMap<>();

    public AgentRegistry(AgentRepository repository, MonitorEventBus eventBus) {
        this.repository = repository;
        this.eventBus = eventBus;
    }

    /** Loads every stored agent. Called once at startup. */
    public void load() {
        agents.clear();
        for (AgentRecord record : repository.findAll()) {
            // A stored status is history, not evidence: until a packet arrives
            // the agent's real state is unknown.
            record.setStatus(AgentStatus.UNKNOWN);
            agents.put(record.getAgentId(), new AgentRuntimeState(record));
        }
        LOG.info("Loaded " + agents.size() + " agent(s) from the database");
    }

    @Override
    public String secretFor(String agentId) {
        AgentRuntimeState state = agents.get(agentId);
        return state == null ? null : state.getRecord().getSharedSecret();
    }

    public boolean contains(String agentId) {
        return agents.containsKey(agentId);
    }

    public Optional<AgentRuntimeState> find(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    /** Snapshots of every agent, ordered by id so the table does not jump around. */
    public List<AgentSnapshot> snapshots() {
        List<AgentSnapshot> result = new ArrayList<>(agents.size());
        for (AgentRuntimeState state : agents.values()) {
            result.add(state.snapshot());
        }
        result.sort(Comparator.comparing(AgentSnapshot::getAgentId));
        return result;
    }

    /** Live state of every agent, for the components that must read it directly. */
    public Collection<AgentRuntimeState> states() {
        return Collections.unmodifiableCollection(agents.values());
    }

    public Optional<AgentSnapshot> snapshot(String agentId) {
        return find(agentId).map(AgentRuntimeState::snapshot);
    }

    public int size() {
        return agents.size();
    }

    /** Adds a freshly inserted record to the live view. */
    public AgentRuntimeState register(AgentRecord record) {
        AgentRuntimeState state = new AgentRuntimeState(record);
        agents.put(record.getAgentId(), state);
        eventBus.fireAgentListChanged();
        return state;
    }

    /** Replaces the stored record of an existing agent, keeping its live metrics. */
    public void updateRecord(AgentRecord record) {
        AgentRuntimeState state = agents.get(record.getAgentId());
        if (state == null) {
            register(record);
            return;
        }
        state.setRecord(record);
        eventBus.fireAgentListChanged();
    }

    /** Removes an agent from the live view. */
    public void unregister(String agentId) {
        if (agents.remove(agentId) != null) {
            eventBus.fireAgentListChanged();
        }
    }

    /**
     * Applies an accepted metric sample and, if the agent was not already
     * healthy, promotes it to ONLINE.
     */
    public void applySample(MetricSample sample) {
        AgentRuntimeState state = agents.get(sample.getAgentId());
        if (state == null) {
            return;
        }
        state.applySample(sample);
        state.setLastSeen(sample.getTimestamp());
        state.countAccepted();
        eventBus.fireAgentUpdated(state.snapshot());
    }

    /** Records that a valid packet of any kind arrived from {@code agentId}. */
    public void markSeen(String agentId, long timestamp) {
        AgentRuntimeState state = agents.get(agentId);
        if (state == null) {
            return;
        }
        if (timestamp > state.getLastSeen()) {
            state.setLastSeen(timestamp);
        }
    }

    public void countRejected(String agentId) {
        AgentRuntimeState state = agents.get(agentId);
        if (state != null) {
            state.countRejected();
        }
    }

    /** Sets the status in memory and in the database, and tells the GUI. */
    public void setStatus(String agentId, AgentStatus status) {
        AgentRuntimeState state = agents.get(agentId);
        if (state == null || state.getStatus() == status) {
            return;
        }
        state.setStatus(status);
        state.getRecord().setStatus(status);
        repository.updateStatus(agentId, status, state.getLastSeen());
        eventBus.fireAgentUpdated(state.snapshot());
    }

    /** Persists the current last-seen stamp of every agent; used on shutdown. */
    public void persistLastSeen() {
        for (AgentRuntimeState state : agents.values()) {
            repository.updateStatus(state.getAgentId(), state.getStatus(), state.getLastSeen());
        }
    }

    public void setTcpConnected(String agentId, boolean connected) {
        AgentRuntimeState state = agents.get(agentId);
        if (state != null) {
            state.setTcpConnected(connected);
            eventBus.fireAgentUpdated(state.snapshot());
        }
    }

    /** Counts agents by status, for the dashboard summary. */
    public int countByStatus(AgentStatus status) {
        int count = 0;
        for (AgentRuntimeState state : agents.values()) {
            if (state.getStatus() == status) {
                count++;
            }
        }
        return count;
    }
}
