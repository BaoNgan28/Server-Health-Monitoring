package vn.edu.hutech.group10.monitoring.monitor.repository;

import vn.edu.hutech.group10.monitoring.common.util.AppPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the SQLite connection and the schema.
 *
 * <p>The database file and its folder are created on first start, and
 * {@code db/schema.sql} is executed on every start, so a user never has to
 * prepare anything by hand.
 *
 * <p>Concurrency: one connection is shared and every unit of work runs inside
 * {@link #execute}, which holds a single lock. That makes the writer count
 * exactly one by construction, which is the shape SQLite likes best; write-ahead
 * logging then lets a reader proceed while a write is in flight. A
 * {@code database is locked} result is still possible if another process holds
 * the file, so a bounded retry sits on top of the driver's own busy timeout.
 */
public class DatabaseManager implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";
    private static final int BUSY_TIMEOUT_MS = 5000;
    private static final int LOCK_RETRIES = 3;
    private static final long LOCK_RETRY_DELAY_MS = 150L;

    /** A unit of work against the database. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    private final Path databasePath;
    private final Object lock = new Object();

    private Connection connection;

    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath;
    }

    /** Creates the file if needed, opens it, applies the pragmas and the schema. */
    public void initialize() {
        synchronized (lock) {
            if (connection != null) {
                return;
            }
            try {
                Path parent = databasePath.toAbsolutePath().getParent();
                if (parent != null) {
                    AppPaths.ensureDirectory(parent);
                }
                connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
                connection.setAutoCommit(true);
                applyPragmas(connection);
                applySchema(connection);
                LOG.info("SQLite database ready at " + databasePath.toAbsolutePath());
            } catch (SQLException | IOException e) {
                throw new DataAccessException("Could not open the database at "
                        + databasePath.toAbsolutePath(), e);
            }
        }
    }

    private void applyPragmas(Connection target) throws SQLException {
        try (Statement statement = target.createStatement()) {
            // Referential integrity is off by default in SQLite and must be
            // switched on per connection.
            statement.execute("PRAGMA foreign_keys = ON");
            // Write-ahead logging lets history queries read while telemetry writes.
            statement.execute("PRAGMA journal_mode = WAL");
            // Wait rather than failing immediately when the file is briefly locked.
            statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
            statement.execute("PRAGMA synchronous = NORMAL");
        }
    }

    private void applySchema(Connection target) throws SQLException {
        for (String ddl : readSchemaStatements()) {
            try (Statement statement = target.createStatement()) {
                statement.execute(ddl);
            }
        }
        LOG.info("Database schema verified");
    }

    /** Splits the packaged schema into executable statements, dropping comments. */
    private List<String> readSchemaStatements() {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        try (InputStream in = DatabaseManager.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new DataAccessException("Schema resource " + SCHEMA_RESOURCE + " is missing from the jar");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                        continue;
                    }
                    current.append(trimmed).append(' ');
                    if (trimmed.endsWith(";")) {
                        statements.add(current.toString().trim());
                        current.setLength(0);
                    }
                }
            }
        } catch (IOException e) {
            throw new DataAccessException("Could not read the database schema", e);
        }
        if (current.length() > 0) {
            statements.add(current.toString().trim());
        }
        return statements;
    }

    /**
     * Runs {@code work} with the shared connection while holding the write lock,
     * retrying a few times if the file is momentarily locked by another process.
     */
    public <T> T execute(String description, SqlWork<T> work) {
        synchronized (lock) {
            Connection target = requireConnection();
            SQLException last = null;
            for (int attempt = 1; attempt <= LOCK_RETRIES; attempt++) {
                try {
                    return work.apply(target);
                } catch (SQLException e) {
                    last = e;
                    if (!isLockedError(e) || attempt == LOCK_RETRIES) {
                        break;
                    }
                    LOG.warning("Database busy during " + description
                            + " (attempt " + attempt + "), retrying");
                    sleepBriefly();
                }
            }
            throw new DataAccessException("Database operation failed: " + description, last);
        }
    }

    /**
     * Runs {@code work} inside one transaction, committing on success and rolling
     * back on any failure.
     */
    public <T> T inTransaction(String description, SqlWork<T> work) {
        return execute(description, target -> {
            boolean previousAutoCommit = target.getAutoCommit();
            target.setAutoCommit(false);
            try {
                T result = work.apply(target);
                target.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    target.rollback();
                } catch (SQLException rollbackFailure) {
                    LOG.log(Level.WARNING, "Rollback after a failed " + description
                            + " also failed", rollbackFailure);
                }
                throw e;
            } finally {
                target.setAutoCommit(previousAutoCommit);
            }
        });
    }

    private Connection requireConnection() {
        if (connection == null) {
            throw new DataAccessException("Database has not been initialised");
        }
        return connection;
    }

    private static boolean isLockedError(SQLException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("database is locked") || lower.contains("busy");
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(LOCK_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public boolean isOpen() {
        synchronized (lock) {
            return connection != null;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (connection == null) {
                return;
            }
            try {
                connection.close();
                LOG.info("Database closed");
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Closing the database failed", e);
            } finally {
                connection = null;
            }
        }
    }
}
