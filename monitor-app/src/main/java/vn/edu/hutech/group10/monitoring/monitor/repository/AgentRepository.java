package vn.edu.hutech.group10.monitoring.monitor.repository;

import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads and writes the {@code agents} table. */
public class AgentRepository {

    private static final String SELECT_COLUMNS =
            "id, agent_id, name, host, tcp_port, shared_secret, cpu_threshold, memory_threshold, "
                    + "network_in_threshold, network_out_threshold, status, last_seen, created_at, updated_at";

    private final DatabaseManager database;

    public AgentRepository(DatabaseManager database) {
        this.database = database;
    }

    /** Every agent, ordered by id so the table keeps a stable order. */
    public List<AgentRecord> findAll() {
        return database.execute("list agents", connection -> {
            List<AgentRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + SELECT_COLUMNS + " FROM agents ORDER BY agent_id");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    records.add(map(rs));
                }
            }
            return records;
        });
    }

    public Optional<AgentRecord> findByAgentId(String agentId) {
        return database.execute("find agent " + agentId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + SELECT_COLUMNS + " FROM agents WHERE agent_id = ?")) {
                statement.setString(1, agentId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<AgentRecord>empty();
                }
            }
        });
    }

    /** Inserts {@code record} and returns it with the generated id filled in. */
    public AgentRecord insert(AgentRecord record) {
        return database.execute("insert agent " + record.getAgentId(), connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO agents (agent_id, name, host, tcp_port, shared_secret, "
                            + "cpu_threshold, memory_threshold, network_in_threshold, "
                            + "network_out_threshold, status, last_seen, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, record.getAgentId());
                statement.setString(2, record.getName());
                statement.setString(3, record.getHost());
                statement.setInt(4, record.getTcpPort());
                statement.setString(5, record.getSharedSecret());
                statement.setDouble(6, record.getCpuThreshold());
                statement.setDouble(7, record.getMemoryThreshold());
                statement.setDouble(8, record.getNetworkInThresholdMbps());
                statement.setDouble(9, record.getNetworkOutThresholdMbps());
                statement.setString(10, statusName(record.getStatus()));
                statement.setLong(11, record.getLastSeen());
                statement.setLong(12, now);
                statement.setLong(13, now);
                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        record.setId(keys.getLong(1));
                    }
                }
            }
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            return record;
        });
    }

    /** Updates everything an operator can edit; identity and timestamps are left alone. */
    public void update(AgentRecord record) {
        database.execute("update agent " + record.getAgentId(), connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE agents SET name = ?, host = ?, tcp_port = ?, shared_secret = ?, "
                            + "cpu_threshold = ?, memory_threshold = ?, network_in_threshold = ?, "
                            + "network_out_threshold = ?, updated_at = ? WHERE agent_id = ?")) {
                statement.setString(1, record.getName());
                statement.setString(2, record.getHost());
                statement.setInt(3, record.getTcpPort());
                statement.setString(4, record.getSharedSecret());
                statement.setDouble(5, record.getCpuThreshold());
                statement.setDouble(6, record.getMemoryThreshold());
                statement.setDouble(7, record.getNetworkInThresholdMbps());
                statement.setDouble(8, record.getNetworkOutThresholdMbps());
                statement.setLong(9, now);
                statement.setString(10, record.getAgentId());
                statement.executeUpdate();
            }
            record.setUpdatedAt(now);
            return null;
        });
    }

    /** Writes the live status and last-seen stamp; called by the heartbeat monitor. */
    public void updateStatus(String agentId, AgentStatus status, long lastSeen) {
        database.execute("update agent status " + agentId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE agents SET status = ?, last_seen = ?, updated_at = ? WHERE agent_id = ?")) {
                statement.setString(1, statusName(status));
                statement.setLong(2, lastSeen);
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, agentId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Deletes an agent. Its metrics and alerts go with it through the foreign
     * key cascade declared in the schema.
     */
    public boolean delete(String agentId) {
        return database.execute("delete agent " + agentId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM agents WHERE agent_id = ?")) {
                statement.setString(1, agentId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    public boolean exists(String agentId) {
        return findByAgentId(agentId).isPresent();
    }

    private static String statusName(AgentStatus status) {
        return (status == null ? AgentStatus.UNKNOWN : status).name();
    }

    private static AgentRecord map(ResultSet rs) throws SQLException {
        AgentRecord record = new AgentRecord();
        record.setId(rs.getLong("id"));
        record.setAgentId(rs.getString("agent_id"));
        record.setName(rs.getString("name"));
        record.setHost(rs.getString("host"));
        record.setTcpPort(rs.getInt("tcp_port"));
        record.setSharedSecret(rs.getString("shared_secret"));
        record.setCpuThreshold(rs.getDouble("cpu_threshold"));
        record.setMemoryThreshold(rs.getDouble("memory_threshold"));
        record.setNetworkInThresholdMbps(rs.getDouble("network_in_threshold"));
        record.setNetworkOutThresholdMbps(rs.getDouble("network_out_threshold"));
        record.setStatus(parseStatus(rs.getString("status")));
        record.setLastSeen(rs.getLong("last_seen"));
        record.setCreatedAt(rs.getLong("created_at"));
        record.setUpdatedAt(rs.getLong("updated_at"));
        return record;
    }

    private static AgentStatus parseStatus(String value) {
        if (value == null) {
            return AgentStatus.UNKNOWN;
        }
        try {
            return AgentStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return AgentStatus.UNKNOWN;
        }
    }
}
