package vn.edu.hutech.group10.monitoring.monitor.repository;

import vn.edu.hutech.group10.monitoring.common.model.AlertEvent;
import vn.edu.hutech.group10.monitoring.common.model.AlertSeverity;
import vn.edu.hutech.group10.monitoring.common.model.AlertState;
import vn.edu.hutech.group10.monitoring.common.model.AlertType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads and writes the {@code alerts} table. */
public class AlertRepository {

    private static final String COLUMNS =
            "id, agent_id, alert_type, severity, message, opened_at, resolved_at, status, "
                    + "current_value, threshold, unit";

    private final DatabaseManager database;

    public AlertRepository(DatabaseManager database) {
        this.database = database;
    }

    /** Inserts a newly opened alert and fills its generated id in. */
    public AlertEvent insert(AlertEvent event) {
        return database.execute("insert alert for " + event.getAgentId(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO alerts (agent_id, alert_type, severity, message, opened_at, "
                            + "resolved_at, status, current_value, threshold, unit) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, event.getAgentId());
                statement.setString(2, event.getAlertType().name());
                statement.setString(3, event.getSeverity().name());
                statement.setString(4, event.getMessage());
                statement.setLong(5, event.getOpenedAt());
                if (event.getResolvedAt() > 0L) {
                    statement.setLong(6, event.getResolvedAt());
                } else {
                    statement.setNull(6, java.sql.Types.INTEGER);
                }
                statement.setString(7, event.getState().name());
                statement.setDouble(8, event.getCurrentValue());
                statement.setDouble(9, event.getThreshold());
                statement.setString(10, event.getUnit());
                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        event.setId(keys.getLong(1));
                    }
                }
            }
            return event;
        });
    }

    /** Marks an alert resolved. */
    public void resolve(long alertId, long resolvedAt) {
        database.execute("resolve alert " + alertId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE alerts SET status = ?, resolved_at = ? WHERE id = ?")) {
                statement.setString(1, AlertState.RESOLVED.name());
                statement.setLong(2, resolvedAt);
                statement.setLong(3, alertId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    /** The open alert of this type for this agent, if one exists. */
    public Optional<AlertEvent> findOpen(String agentId, AlertType alertType) {
        return database.execute("find open alert for " + agentId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM alerts "
                            + "WHERE agent_id = ? AND alert_type = ? AND status = ? "
                            + "ORDER BY opened_at DESC LIMIT 1")) {
                statement.setString(1, agentId);
                statement.setString(2, alertType.name());
                statement.setString(3, AlertState.OPEN.name());
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.<AlertEvent>empty();
                }
            }
        });
    }

    /** Every alert still open, newest first. */
    public List<AlertEvent> findAllOpen() {
        return database.execute("list open alerts", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM alerts WHERE status = ? ORDER BY opened_at DESC")) {
                statement.setString(1, AlertState.OPEN.name());
                return readAll(statement);
            }
        });
    }

    /** The most recent alerts of any state, newest first. */
    public List<AlertEvent> findRecent(int limit) {
        return database.execute("list recent alerts", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM alerts ORDER BY opened_at DESC LIMIT ?")) {
                statement.setInt(1, Math.max(1, limit));
                return readAll(statement);
            }
        });
    }

    /** The most recent alerts of one agent, newest first. */
    public List<AlertEvent> findByAgent(String agentId, int limit) {
        return database.execute("list alerts for " + agentId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM alerts WHERE agent_id = ? "
                            + "ORDER BY opened_at DESC LIMIT ?")) {
                statement.setString(1, agentId);
                statement.setInt(2, Math.max(1, limit));
                return readAll(statement);
            }
        });
    }

    public long countOpen() {
        return database.execute("count open alerts", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM alerts WHERE status = ?")) {
                statement.setString(1, AlertState.OPEN.name());
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    public long count() {
        return database.execute("count alerts", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM alerts");
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    private static List<AlertEvent> readAll(PreparedStatement statement) throws SQLException {
        List<AlertEvent> events = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                events.add(map(rs));
            }
        }
        return events;
    }

    private static AlertEvent map(ResultSet rs) throws SQLException {
        AlertEvent event = new AlertEvent();
        event.setId(rs.getLong("id"));
        event.setAgentId(rs.getString("agent_id"));
        event.setAlertType(AlertType.valueOf(rs.getString("alert_type")));
        event.setSeverity(AlertSeverity.valueOf(rs.getString("severity")));
        event.setMessage(rs.getString("message"));
        event.setOpenedAt(rs.getLong("opened_at"));
        event.setResolvedAt(rs.getLong("resolved_at"));
        event.setState(AlertState.valueOf(rs.getString("status")));
        event.setCurrentValue(rs.getDouble("current_value"));
        event.setThreshold(rs.getDouble("threshold"));
        event.setUnit(rs.getString("unit"));
        return event;
    }
}
