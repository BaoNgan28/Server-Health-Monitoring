package vn.edu.hutech.group10.monitoring.monitor.service;

import vn.edu.hutech.group10.monitoring.common.model.AgentInfoMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigAckMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigUpdateMessage;
import vn.edu.hutech.group10.monitoring.common.util.ValidationUtil;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;
import vn.edu.hutech.group10.monitoring.monitor.network.AgentTcpConnection;
import vn.edu.hutech.group10.monitoring.monitor.network.AgentTcpConnectionManager;
import vn.edu.hutech.group10.monitoring.monitor.repository.AgentRepository;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Everything the agent management screen can do, kept out of the GUI.
 *
 * <p>The dialogs call these methods from a background worker and show what comes
 * back; they never open a socket or write SQL themselves.
 */
public class AgentManagementService {

    private static final Logger LOG = Logger.getLogger(AgentManagementService.class.getName());

    private final AgentRepository repository;
    private final AgentRegistry registry;
    private final AlertService alertService;
    private final AgentTcpConnectionManager connectionManager;

    public AgentManagementService(AgentRepository repository, AgentRegistry registry,
                                  AlertService alertService,
                                  AgentTcpConnectionManager connectionManager) {
        this.repository = repository;
        this.registry = registry;
        this.alertService = alertService;
        this.connectionManager = connectionManager;
    }

    /**
     * Registers a new agent.
     *
     * @throws IllegalArgumentException when a field is missing, out of range, or
     *                                  the id is already taken
     */
    public AgentRecord addAgent(AgentRecord record) {
        validate(record);
        if (repository.exists(record.getAgentId())) {
            throw new IllegalArgumentException("An agent with id " + record.getAgentId()
                    + " already exists");
        }
        AgentRecord stored = repository.insert(record);
        registry.register(stored);
        LOG.info("Agent " + stored.getAgentId() + " added at " + stored.getHost()
                + ":" + stored.getTcpPort());
        return stored;
    }

    /** Saves an edited agent. The id itself cannot be changed. */
    public void updateAgent(AgentRecord record) {
        validate(record);
        if (!repository.exists(record.getAgentId())) {
            throw new IllegalArgumentException("No agent with id " + record.getAgentId());
        }
        repository.update(record);
        registry.updateRecord(record);
        LOG.info("Agent " + record.getAgentId() + " updated");
    }

    /** Deletes an agent together with its metrics and alerts. */
    public boolean deleteAgent(String agentId) {
        connectionManager.disconnect(agentId);
        boolean deleted = repository.delete(agentId);
        if (deleted) {
            registry.unregister(agentId);
            alertService.forgetAgent(agentId);
            LOG.info("Agent " + agentId + " deleted");
        }
        return deleted;
    }

    /** Opens the control channel and confirms it with a PING. */
    public AgentInfoMessage connect(AgentRecord agent) throws IOException {
        AgentTcpConnection connection = connectionManager.connect(agent);
        if (!connection.ping()) {
            connectionManager.disconnect(agent.getAgentId());
            throw new IOException("Agent " + agent.getAgentId() + " did not answer PING");
        }
        AgentInfoMessage info = connection.requestInfo();
        registry.setTcpConnected(agent.getAgentId(), true);
        return info;
    }

    /** Closes the control channel. */
    public void disconnect(String agentId) {
        connectionManager.disconnect(agentId);
        registry.setTcpConnected(agentId, false);
    }

    public boolean isConnected(String agentId) {
        return connectionManager.isConnected(agentId);
    }

    /**
     * Pushes a configuration change over TCP and returns what the agent said.
     *
     * <p>The local thresholds are only written to the database once the agent has
     * acknowledged the change, so the screen can never show a setting the agent
     * rejected.
     */
    public ConfigAckMessage pushConfiguration(AgentRecord agent, ConfigUpdateMessage update)
            throws IOException {
        AgentTcpConnection connection = connectionManager.ensureConnected(agent);
        registry.setTcpConnected(agent.getAgentId(), true);

        ConfigAckMessage ack = connection.sendConfigUpdate(update);
        if (ack.isSuccess()) {
            applyLocally(agent, update);
            repository.update(agent);
            registry.updateRecord(agent);
        }
        return ack;
    }

    private void applyLocally(AgentRecord agent, ConfigUpdateMessage update) {
        if (update.getCpuThreshold() != null) {
            agent.setCpuThreshold(update.getCpuThreshold());
        }
        if (update.getMemoryThreshold() != null) {
            agent.setMemoryThreshold(update.getMemoryThreshold());
        }
        if (update.getNetworkInThresholdMbps() != null) {
            agent.setNetworkInThresholdMbps(update.getNetworkInThresholdMbps());
        }
        if (update.getNetworkOutThresholdMbps() != null) {
            agent.setNetworkOutThresholdMbps(update.getNetworkOutThresholdMbps());
        }
    }

    /** Checks everything a dialog can get wrong, with a message naming the field. */
    public static void validate(AgentRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("No agent details were supplied");
        }
        ValidationUtil.requireText(record.getAgentId(), "Agent ID");
        ValidationUtil.requireText(record.getName(), "Name");
        ValidationUtil.requireText(record.getHost(), "Host");
        ValidationUtil.requireText(record.getSharedSecret(), "Shared Secret");

        if (record.getTcpPort() < 1 || record.getTcpPort() > 65535) {
            throw new IllegalArgumentException("TCP Port must be between 1 and 65535");
        }
        requirePercentage(record.getCpuThreshold(), "CPU Threshold");
        requirePercentage(record.getMemoryThreshold(), "Memory Threshold");
        requireRate(record.getNetworkInThresholdMbps(), "Network IN Threshold");
        requireRate(record.getNetworkOutThresholdMbps(), "Network OUT Threshold");
    }

    private static void requirePercentage(double value, String name) {
        if (!ValidationUtil.isFinite(value) || value <= 0.0d || value > 100.0d) {
            throw new IllegalArgumentException(name + " must be greater than 0 and at most 100");
        }
    }

    private static void requireRate(double value, String name) {
        if (!ValidationUtil.isNonNegative(value)) {
            throw new IllegalArgumentException(name + " must be zero or a positive number of Mbps");
        }
    }
}
