package vn.edu.hutech.group10.monitoring.common.util;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Sends java.util.logging output to both the console and a rotating file under
 * {@code logs/}. OSHI and the other libraries log through SLF4J, which the
 * {@code slf4j-jdk14} binding routes here as well, so one setup covers everything.
 *
 * <p>Secrets are never passed to a logger anywhere in this project; see
 * {@code docs/ARCHITECTURE.md}.
 */
public final class LoggingSetup {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);

    private static boolean configured;

    private LoggingSetup() {
    }

    /**
     * Configures the root logger once per process.
     *
     * @param baseName file name stem, for example {@code monitor} or {@code agent01}
     */
    public static synchronized void configure(String baseName) {
        if (configured) {
            return;
        }
        configured = true;

        Logger root = LogManager.getLogManager().getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        root.setLevel(Level.INFO);

        Formatter formatter = new SingleLineFormatter();

        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(Level.INFO);
        console.setFormatter(formatter);
        root.addHandler(console);

        try {
            Path logs = AppPaths.ensureDirectory(AppPaths.logsDir());
            String safeName = ValidationUtil.isBlank(baseName) ? "application" : baseName.trim();
            // 5 files of 2 MiB each, appended across restarts.
            FileHandler file = new FileHandler(
                    logs.resolve(safeName + "-%g.log").toString(), 2 * 1024 * 1024, 5, true);
            file.setLevel(Level.INFO);
            file.setFormatter(formatter);
            root.addHandler(file);
        } catch (IOException | SecurityException e) {
            // Losing the file handler must not stop the application; the console
            // handler is already installed, so say so there and carry on.
            root.log(Level.WARNING, "File logging disabled: " + e.getMessage());
        }

        // OSHI is chatty at FINE and below; keep it at WARNING.
        Logger.getLogger("oshi").setLevel(Level.WARNING);
        Logger.getLogger("com.sun.mail").setLevel(Level.WARNING);
    }

    /** One record per line: timestamp, level, logger, message. */
    private static final class SingleLineFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder(160);
            sb.append(STAMP.format(Instant.ofEpochMilli(record.getMillis())
                    .atZone(ZoneId.systemDefault())));
            sb.append(" [").append(record.getLevel().getName()).append("] ");
            sb.append(shortLoggerName(record.getLoggerName())).append(" - ");
            sb.append(formatMessage(record));
            sb.append(System.lineSeparator());
            if (record.getThrown() != null) {
                sb.append("    ").append(record.getThrown()).append(System.lineSeparator());
                for (StackTraceElement element : record.getThrown().getStackTrace()) {
                    sb.append("        at ").append(element).append(System.lineSeparator());
                }
            }
            return sb.toString();
        }

        private String shortLoggerName(String name) {
            if (name == null) {
                return "-";
            }
            int lastDot = name.lastIndexOf('.');
            return lastDot < 0 ? name : name.substring(lastDot + 1);
        }
    }
}
