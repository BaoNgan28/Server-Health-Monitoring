package vn.edu.hutech.group10.monitoring.monitor.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.hutech.group10.monitoring.common.model.AgentStatus;
import vn.edu.hutech.group10.monitoring.monitor.TestSupport;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    @Test
    @DisplayName("The database file and its folder are created on first use")
    void createsDatabaseAndDirectories(@TempDir Path tempDir) {
        Path databasePath = tempDir.resolve("nested").resolve("created").resolve("monitoring.db");
        assertFalse(Files.exists(databasePath));

        try (DatabaseManager database = new DatabaseManager(databasePath)) {
            database.initialize();
            assertAll(
                    () -> assertTrue(Files.exists(databasePath), "the database file must be created"),
                    () -> assertTrue(database.isOpen()));
        }
    }

    @Test
    @DisplayName("Every table and index of the schema is present")
    void appliesTheWholeSchema(@TempDir Path tempDir) {
        try (DatabaseManager database = open(tempDir)) {
            Set<String> tables = names(database, "table");
            Set<String> indexes = names(database, "index");

            assertAll(
                    () -> assertTrue(tables.contains("agents")),
                    () -> assertTrue(tables.contains("metric_raw")),
                    () -> assertTrue(tables.contains("metric_5m")),
                    () -> assertTrue(tables.contains("alerts")),
                    () -> assertTrue(indexes.contains("idx_metric_raw_agent_time")),
                    () -> assertTrue(indexes.contains("idx_metric_5m_agent_bucket")),
                    () -> assertTrue(indexes.contains("idx_alerts_agent_status")));
        }
    }

    @Test
    @DisplayName("Opening an existing database again keeps the data")
    void schemaApplicationIsIdempotent(@TempDir Path tempDir) {
        Path databasePath = tempDir.resolve("monitoring.db");

        try (DatabaseManager database = new DatabaseManager(databasePath)) {
            database.initialize();
            new AgentRepository(database).insert(TestSupport.agent());
        }

        try (DatabaseManager reopened = new DatabaseManager(databasePath)) {
            reopened.initialize();
            List<AgentRecord> agents = new AgentRepository(reopened).findAll();
            assertEquals(1, agents.size(), "re-applying the schema must not drop rows");
        }
    }

    @Test
    @DisplayName("The pragmas the application depends on are in force")
    void enablesPragmas(@TempDir Path tempDir) {
        try (DatabaseManager database = open(tempDir)) {
            String journalMode = database.execute("read journal mode", connection -> {
                try (PreparedStatement statement = connection.prepareStatement("PRAGMA journal_mode");
                     ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            });
            int foreignKeys = database.execute("read foreign_keys", connection -> {
                try (PreparedStatement statement = connection.prepareStatement("PRAGMA foreign_keys");
                     ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            });

            assertAll(
                    () -> assertEquals("wal", journalMode.toLowerCase(java.util.Locale.ROOT)),
                    () -> assertEquals(1, foreignKeys, "foreign keys must be enforced"));
        }
    }

    @Test
    @DisplayName("Deleting an agent cascades to its metrics and alerts")
    void foreignKeysCascade(@TempDir Path tempDir) {
        try (DatabaseManager database = open(tempDir)) {
            AgentRepository agents = new AgentRepository(database);
            MetricRepository metrics = new MetricRepository(database);

            agents.insert(TestSupport.agent());
            metrics.insertRaw(TestSupport.sample(1000L, 10.0d, 20.0d));
            assertEquals(1L, metrics.countRaw());

            agents.delete(TestSupport.AGENT_ID);

            assertEquals(0L, metrics.countRaw(), "the metric rows must go with the agent");
        }
    }

    @Test
    @DisplayName("A failed transaction leaves nothing behind")
    void transactionRollsBackOnFailure(@TempDir Path tempDir) {
        try (DatabaseManager database = open(tempDir)) {
            new AgentRepository(database).insert(TestSupport.agent());
            MetricRepository metrics = new MetricRepository(database);

            assertThrows(DataAccessException.class, () -> database.inTransaction("failing work",
                    connection -> {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO metric_raw (agent_id, timestamp, cpu_usage, memory_usage, "
                                        + "connection_count, uptime_seconds, network_in_bps, "
                                        + "network_out_bps) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                            statement.setString(1, TestSupport.AGENT_ID);
                            statement.setLong(2, 1000L);
                            statement.setDouble(3, 1.0d);
                            statement.setDouble(4, 2.0d);
                            statement.setInt(5, 3);
                            statement.setLong(6, 4L);
                            statement.setDouble(7, 5.0d);
                            statement.setDouble(8, 6.0d);
                            statement.executeUpdate();
                        }
                        // Now fail, after a successful write inside the same transaction.
                        throw new SQLException("deliberate failure");
                    }));

            assertEquals(0L, metrics.countRaw(), "the insert must have been rolled back");
        }
    }

    @Test
    @DisplayName("Using the manager before initialise is reported clearly")
    void refusesWorkBeforeInitialise(@TempDir Path tempDir) {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("monitoring.db"));
        DataAccessException error = assertThrows(DataAccessException.class,
                () -> database.execute("too early", connection -> null));
        assertTrue(error.getMessage().contains("not been initialised"), error.getMessage());
    }

    @Test
    @DisplayName("Concurrent writers are serialised rather than colliding")
    void concurrentWritesAreSerialised(@TempDir Path tempDir) throws Exception {
        try (DatabaseManager database = open(tempDir)) {
            new AgentRepository(database).insert(TestSupport.agent());
            MetricRepository metrics = new MetricRepository(database);

            int threads = 4;
            int perThread = 25;
            List<Thread> workers = new ArrayList<>();
            List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());

            for (int t = 0; t < threads; t++) {
                final int offset = t * perThread;
                Thread worker = new Thread(() -> {
                    try {
                        for (int i = 0; i < perThread; i++) {
                            metrics.insertRaw(TestSupport.sample(1000L + offset + i, 10.0d, 20.0d));
                        }
                    } catch (RuntimeException e) {
                        failures.add(e);
                    }
                });
                workers.add(worker);
                worker.start();
            }
            for (Thread worker : workers) {
                worker.join(30_000L);
            }

            assertAll(
                    () -> assertTrue(failures.isEmpty(), "unexpected failures: " + failures),
                    () -> assertEquals((long) threads * perThread, metrics.countRaw()));
        }
    }

    @Test
    @DisplayName("Closing twice is harmless")
    void closeIsIdempotent(@TempDir Path tempDir) {
        DatabaseManager database = open(tempDir);
        database.close();
        database.close();
        assertFalse(database.isOpen());
    }

    @Test
    @DisplayName("An agent can be found again after insertion")
    void insertAndFind(@TempDir Path tempDir) {
        try (DatabaseManager database = open(tempDir)) {
            AgentRepository agents = new AgentRepository(database);
            AgentRecord stored = agents.insert(TestSupport.agent());

            assertTrue(stored.getId() > 0, "the generated id must be filled in");

            Optional<AgentRecord> found = agents.findByAgentId(TestSupport.AGENT_ID);
            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals("TEST-AGENT-01", found.get().getName()),
                    () -> assertEquals(5001, found.get().getTcpPort()),
                    () -> assertEquals(AgentStatus.UNKNOWN, found.get().getStatus()));
        }
    }

    private static DatabaseManager open(Path tempDir) {
        DatabaseManager database = new DatabaseManager(tempDir.resolve("monitoring.db"));
        database.initialize();
        return database;
    }

    private static Set<String> names(DatabaseManager database, String type) {
        return database.execute("list " + type + "s", connection -> {
            Set<String> found = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type = ?")) {
                statement.setString(1, type);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        found.add(rs.getString(1));
                    }
                }
            }
            return found;
        });
    }
}
