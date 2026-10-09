package vn.edu.hutech.group10.monitoring.monitor.repository;

import vn.edu.hutech.group10.monitoring.monitor.model.MetricAggregate;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricSample;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes {@code metric_raw} and {@code metric_5m}. */
public class MetricRepository {

    private static final String RAW_COLUMNS =
            "agent_id, timestamp, cpu_usage, memory_usage, connection_count, uptime_seconds, "
                    + "network_in_bps, network_out_bps";

    private static final String AGG_COLUMNS =
            "agent_id, bucket_start, sample_count, avg_cpu, max_cpu, avg_memory, max_memory, "
                    + "avg_connections, avg_network_in_bps, max_network_in_bps, "
                    + "avg_network_out_bps, max_network_out_bps";

    private final DatabaseManager database;

    public MetricRepository(DatabaseManager database) {
        this.database = database;
    }

    /** Stores one accepted sample. */
    public void insertRaw(MetricSample sample) {
        database.execute("insert raw metric for " + sample.getAgentId(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO metric_raw (" + RAW_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                bindRaw(statement, sample);
                statement.executeUpdate();
            }
            return null;
        });
    }

    /** Stores many samples in one transaction; used by the tests and by imports. */
    public void insertRawBatch(List<MetricSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return;
        }
        database.inTransaction("insert " + samples.size() + " raw metrics", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO metric_raw (" + RAW_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (MetricSample sample : samples) {
                    bindRaw(statement, sample);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    /** Raw samples of one agent inside {@code [fromMs, toMs]}, oldest first. */
    public List<MetricSample> findRaw(String agentId, long fromMs, long toMs) {
        return database.execute("query raw metrics for " + agentId, connection -> {
            List<MetricSample> samples = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + RAW_COLUMNS + " FROM metric_raw "
                            + "WHERE agent_id = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp")) {
                statement.setString(1, agentId);
                statement.setLong(2, fromMs);
                statement.setLong(3, toMs);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        samples.add(new MetricSample(
                                rs.getString("agent_id"),
                                rs.getLong("timestamp"),
                                rs.getDouble("cpu_usage"),
                                rs.getDouble("memory_usage"),
                                rs.getInt("connection_count"),
                                rs.getLong("uptime_seconds"),
                                rs.getDouble("network_in_bps"),
                                rs.getDouble("network_out_bps")));
                    }
                }
            }
            return samples;
        });
    }

    /** Aggregated buckets of one agent inside {@code [fromMs, toMs]}, oldest first. */
    public List<MetricAggregate> findAggregates(String agentId, long fromMs, long toMs) {
        return database.execute("query aggregated metrics for " + agentId, connection -> {
            List<MetricAggregate> aggregates = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + AGG_COLUMNS + " FROM metric_5m "
                            + "WHERE agent_id = ? AND bucket_start BETWEEN ? AND ? ORDER BY bucket_start")) {
                statement.setString(1, agentId);
                statement.setLong(2, fromMs);
                statement.setLong(3, toMs);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        aggregates.add(mapAggregate(rs));
                    }
                }
            }
            return aggregates;
        });
    }

    /**
     * Folds every raw sample older than {@code cutoffMs} into buckets of
     * {@code bucketMs} and deletes the rows that were folded, all in one
     * transaction, so raw data is never dropped without its summary existing.
     *
     * <p>{@code INSERT OR IGNORE} together with the unique index on
     * {@code (agent_id, bucket_start)} makes a bucket impossible to write twice
     * even if the job is run again over the same window.
     *
     * @return how many buckets were written
     */
    public int aggregateAndPrune(long cutoffMs, long bucketMs) {
        if (bucketMs <= 0L) {
            throw new IllegalArgumentException("bucketMs must be positive");
        }
        return database.inTransaction("aggregate raw metrics", connection -> {
            int buckets;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO metric_5m (" + AGG_COLUMNS + ") "
                            + "SELECT agent_id, (timestamp / ?) * ? AS bucket_start, COUNT(*), "
                            + "AVG(cpu_usage), MAX(cpu_usage), "
                            + "AVG(memory_usage), MAX(memory_usage), "
                            + "AVG(connection_count), "
                            + "AVG(network_in_bps), MAX(network_in_bps), "
                            + "AVG(network_out_bps), MAX(network_out_bps) "
                            + "FROM metric_raw WHERE timestamp < ? "
                            + "GROUP BY agent_id, bucket_start")) {
                statement.setLong(1, bucketMs);
                statement.setLong(2, bucketMs);
                statement.setLong(3, cutoffMs);
                buckets = statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM metric_raw WHERE timestamp < ?")) {
                statement.setLong(1, cutoffMs);
                statement.executeUpdate();
            }
            return buckets;
        });
    }

    /** Drops buckets that start before {@code cutoffMs}. */
    public int deleteAggregatesBefore(long cutoffMs) {
        return database.execute("prune aggregated metrics", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM metric_5m WHERE bucket_start < ?")) {
                statement.setLong(1, cutoffMs);
                return statement.executeUpdate();
            }
        });
    }

    public long countRaw() {
        return count("SELECT COUNT(*) FROM metric_raw", "count raw metrics");
    }

    public long countRaw(String agentId) {
        return database.execute("count raw metrics for " + agentId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM metric_raw WHERE agent_id = ?")) {
                statement.setString(1, agentId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    public long countAggregates() {
        return count("SELECT COUNT(*) FROM metric_5m", "count aggregated metrics");
    }

    /** Timestamp of the newest raw sample of an agent, or 0 when there is none. */
    public long latestRawTimestamp(String agentId) {
        return database.execute("latest raw timestamp for " + agentId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT MAX(timestamp) FROM metric_raw WHERE agent_id = ?")) {
                statement.setString(1, agentId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    private long count(String sql, String description) {
        return database.execute(description, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    private static void bindRaw(PreparedStatement statement, MetricSample sample) throws SQLException {
        statement.setString(1, sample.getAgentId());
        statement.setLong(2, sample.getTimestamp());
        statement.setDouble(3, sample.getCpuUsage());
        statement.setDouble(4, sample.getMemoryUsage());
        statement.setInt(5, sample.getConnectionCount());
        statement.setLong(6, sample.getUptimeSeconds());
        statement.setDouble(7, sample.getNetworkInBytesPerSecond());
        statement.setDouble(8, sample.getNetworkOutBytesPerSecond());
    }

    private static MetricAggregate mapAggregate(ResultSet rs) throws SQLException {
        return new MetricAggregate(
                rs.getString("agent_id"),
                rs.getLong("bucket_start"),
                rs.getInt("sample_count"),
                rs.getDouble("avg_cpu"),
                rs.getDouble("max_cpu"),
                rs.getDouble("avg_memory"),
                rs.getDouble("max_memory"),
                rs.getDouble("avg_connections"),
                rs.getDouble("avg_network_in_bps"),
                rs.getDouble("max_network_in_bps"),
                rs.getDouble("avg_network_out_bps"),
                rs.getDouble("max_network_out_bps"));
    }
}
