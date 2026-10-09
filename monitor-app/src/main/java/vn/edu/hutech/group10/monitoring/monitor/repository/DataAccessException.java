package vn.edu.hutech.group10.monitoring.monitor.repository;

/**
 * Wraps a {@link java.sql.SQLException} so that callers above the repository
 * layer - services, schedulers, the GUI - never have to import JDBC.
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
