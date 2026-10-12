package vn.edu.hutech.group10.monitoring.agent;

import vn.edu.hutech.group10.monitoring.agent.config.AgentConfig;
import vn.edu.hutech.group10.monitoring.agent.config.AgentConfigLoader;
import vn.edu.hutech.group10.monitoring.agent.core.AgentRuntime;
import vn.edu.hutech.group10.monitoring.common.util.AppPaths;
import vn.edu.hutech.group10.monitoring.common.util.LoggingSetup;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Console entry point of the agent.
 *
 * <pre>
 *   java -jar agent-app-1.0.0-all.jar --config="config\agent01.properties"
 * </pre>
 *
 * <p>The agent has no GUI on purpose: it runs on the machine being watched, so
 * it must stay small and cheap.
 */
public final class AgentMain {

    public static final String AGENT_VERSION = "1.0.0";

    private static final String CONFIG_OPTION = "--config";
    private static final String DEFAULT_CONFIG = "config/agent01.properties";

    private static final Logger LOG = Logger.getLogger(AgentMain.class.getName());

    private AgentMain() {
    }

    public static void main(String[] args) {
        Path configPath = resolveConfigPath(args);
        LoggingSetup.configure("agent-" + stem(configPath));

        AgentConfig config;
        try {
            config = AgentConfigLoader.load(configPath);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.err.println("Expected a properties file, for example:");
            System.err.println("  java -jar agent-app-1.0.0-all.jar --config=\"config\\agent01.properties\"");
            LOG.log(Level.SEVERE, "Agent configuration could not be loaded", e);
            System.exit(2);
            return;
        }

        printBanner(config, configPath);

        AgentRuntime runtime = new AgentRuntime(config, AGENT_VERSION);
        CountDownLatch shutdownLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("Stopping agent " + config.getAgentId() + " ...");
            runtime.stop();
            shutdownLatch.countDown();
        }, "agent-shutdown"));

        try {
            runtime.start();
        } catch (IOException | RuntimeException e) {
            System.err.println("Agent failed to start: " + e.getMessage());
            LOG.log(Level.SEVERE, "Agent failed to start", e);
            System.exit(3);
            return;
        }

        System.out.println("Agent is running. Press Ctrl+C to stop.");
        try {
            // Park the main thread; every worker is a daemon owned by the runtime.
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runtime.stop();
        }
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

    private static String stem(Path path) {
        String name = path.getFileName() == null ? "agent" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void printBanner(AgentConfig config, Path configPath) {
        System.out.println("============================================================");
        System.out.println(" GROUP 10 - SERVER HEALTH MONITORING - AGENT " + AGENT_VERSION);
        System.out.println("============================================================");
        System.out.println(config.describeForConsole());
        System.out.println("Configuration File  : " + configPath.toAbsolutePath());
        System.out.println("Log Directory       : " + AppPaths.logsDir());
        System.out.println("============================================================");
        // The shared secret is intentionally absent from this banner and from the logs.
    }
}
