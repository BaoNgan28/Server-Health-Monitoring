package vn.edu.hutech.group10.monitoring.monitor;

import vn.edu.hutech.group10.monitoring.common.util.AppPaths;
import vn.edu.hutech.group10.monitoring.common.util.LoggingSetup;
import vn.edu.hutech.group10.monitoring.monitor.config.MonitorConfig;
import vn.edu.hutech.group10.monitoring.monitor.config.MonitorConfigLoader;
import vn.edu.hutech.group10.monitoring.monitor.core.MonitorApplication;
import vn.edu.hutech.group10.monitoring.monitor.ui.MainFrame;
import vn.edu.hutech.group10.monitoring.monitor.ui.UiConstants;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point of the monitoring client.
 *
 * <pre>
 *   java -jar monitor-app-1.0.0-all.jar
 *   java -jar monitor-app-1.0.0-all.jar --config="config\monitor-demo.properties"
 * </pre>
 *
 * <p>Everything is started before the window appears, so a port already in use
 * or an unreadable database is reported as a dialog instead of an empty screen.
 */
public final class MonitoringMain {

    public static final String MONITOR_VERSION = "1.0.0";

    private static final String CONFIG_OPTION = "--config";
    private static final String DEFAULT_CONFIG = "config/monitor.properties";

    private static final Logger LOG = Logger.getLogger(MonitoringMain.class.getName());

    private MonitoringMain() {
    }

    public static void main(String[] args) {
        Path configPath = resolveConfigPath(args);
        LoggingSetup.configure("monitor");

        MonitorConfig config;
        try {
            config = MonitorConfigLoader.load(configPath);
        } catch (IllegalArgumentException e) {
            fail("The monitoring configuration is not valid.", e);
            return;
        }

        printBanner(config, configPath);

        MonitorApplication application = new MonitorApplication(config);
        try {
            application.start();
        } catch (IOException | RuntimeException e) {
            fail("The monitoring client could not start.", e);
            return;
        }

        // A last resort: if the window never comes up, the sockets still close.
        Runtime.getRuntime().addShutdownHook(new Thread(application::close, "monitor-shutdown-hook"));

        SwingUtilities.invokeLater(() -> {
            applyLookAndFeel();
            new MainFrame(application).setVisible(true);
        });
    }

    /** Reads {@code --config=...}, also accepting {@code --config path} and a bare path. */
    static Path resolveConfigPath(String[] args) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg == null) {
                    continue;
                }
                if (arg.startsWith(CONFIG_OPTION + "=")) {
                    return Paths.get(stripQuotes(arg.substring(CONFIG_OPTION.length() + 1)));
                }
                if (arg.equals(CONFIG_OPTION) && i + 1 < args.length) {
                    return Paths.get(stripQuotes(args[i + 1]));
                }
                if (!arg.startsWith("--") && arg.toLowerCase(Locale.ROOT).endsWith(".properties")) {
                    return Paths.get(stripQuotes(arg));
                }
            }
        }
        return AppPaths.home().resolve(DEFAULT_CONFIG);
    }

    private static String stripQuotes(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static void applyLookAndFeel() {
        try {
            com.formdev.flatlaf.FlatDarkLaf.setup();
            UIManager.put("Component.arc", 8);
            UIManager.put("Button.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("ScrollBar.showButtons", false);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("TabbedPane.tabHeight", 38);
            UIManager.put("TabbedPane.underlineColor", UiConstants.ACCENT_CYAN);
            UIManager.put("TabbedPane.underlineHeight", 3);
            UIManager.put("Table.rowHeight", 34);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.gridColor", UiConstants.CARD_BORDER);
            UIManager.put("TableHeader.height", 34);
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | UnsupportedOperationException
                     | javax.swing.UnsupportedLookAndFeelException fallbackError) {
                LOG.log(Level.FINE, "Falling back to the default look and feel", fallbackError);
            }
        }
    }

    private static void printBanner(MonitorConfig config, Path configPath) {
        System.out.println("============================================================");
        System.out.println(" GROUP 10 - SERVER HEALTH MONITORING - CLIENT " + MONITOR_VERSION);
        System.out.println("============================================================");
        System.out.println("Profile             : " + config.getProfileName());
        System.out.println("Configuration File  : " + configPath.toAbsolutePath());
        System.out.println("UDP Listen Port     : " + config.getUdpPort());
        System.out.println("Heartbeat Timeout   : " + config.getHeartbeatTimeoutMs() + " ms");
        System.out.println("Database            : " + config.getDatabasePath());
        System.out.println("Log Directory       : " + AppPaths.logsDir());
        System.out.println("============================================================");
    }

    private static void fail(String headline, Exception e) {
        LOG.log(Level.SEVERE, headline, e);
        System.err.println(headline + " " + e.getMessage());
        try {
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(null,
                    headline + System.lineSeparator() + "Reason: " + e.getMessage(),
                    "Server Health Monitoring", JOptionPane.ERROR_MESSAGE));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException | RuntimeException dialogFailure) {
            // A headless environment has no dialog; the console message stands.
            LOG.log(Level.FINE, "Could not show the failure dialog", dialogFailure);
        }
        System.exit(1);
    }
}
