package vn.edu.hutech.group10.monitoring.monitor.network;

import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;
import vn.edu.hutech.group10.monitoring.monitor.config.MonitorConfig;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps at most one control connection per agent and makes sure a failing agent
 * only affects itself.
 *
 * <p>Connecting, disconnecting and every request are wrapped so that an
 * unreachable machine produces a message for the operator rather than an
 * exception escaping into the GUI. Closing the manager closes every connection.
 */
public class AgentTcpConnectionManager implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AgentTcpConnectionManager.class.getName());

    private final JsonMessageCodec codec;
    private final MonitorConfig config;
    private final Map<String, AgentTcpConnection> connections = new ConcurrentHashMap<>();

    public AgentTcpConnectionManager(JsonMessageCodec codec, MonitorConfig config) {
        this.codec = codec;
        this.config = config;
    }

    /**
     * Opens a connection to {@code agent}, replacing any previous one.
     *
     * @throws IOException when the agent cannot be reached
     */
    public AgentTcpConnection connect(AgentRecord agent) throws IOException {
        disconnect(agent.getAgentId());

        AgentTcpConnection connection = new AgentTcpConnection(agent.getAgentId(), agent.getHost(),
                agent.getTcpPort(), config.getTcpTimeoutMs(), codec);
        try {
            connection.connect();
        } catch (IOException e) {
            connection.close();
            throw e;
        }
        connections.put(agent.getAgentId(), connection);
        return connection;
    }

    /** The open connection for {@code agentId}, if there is one. */
    public Optional<AgentTcpConnection> get(String agentId) {
        AgentTcpConnection connection = connections.get(agentId);
        if (connection == null || !connection.isConnected()) {
            return Optional.empty();
        }
        return Optional.of(connection);
    }

    public boolean isConnected(String agentId) {
        return get(agentId).isPresent();
    }

    /** Closes and forgets the connection for {@code agentId}. */
    public void disconnect(String agentId) {
        AgentTcpConnection connection = connections.remove(agentId);
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * Returns the open connection, reconnecting first when the agent has dropped
     * the socket since it was opened.
     */
    public AgentTcpConnection ensureConnected(AgentRecord agent) throws IOException {
        Optional<AgentTcpConnection> existing = get(agent.getAgentId());
        if (existing.isPresent()) {
            return existing.get();
        }
        LOG.info("Reconnecting the control channel to " + agent.getAgentId());
        return connect(agent);
    }

    public int getConnectionCount() {
        return (int) connections.values().stream().filter(AgentTcpConnection::isConnected).count();
    }

    @Override
    public void close() {
        for (AgentTcpConnection connection : connections.values()) {
            try {
                connection.close();
            } catch (RuntimeException e) {
                LOG.log(Level.FINE, "Closing a control connection failed", e);
            }
        }
        connections.clear();
        LOG.info("All TCP control connections closed");
    }
}
