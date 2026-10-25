package vn.edu.hutech.group10.monitoring.simulator;

import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;

import java.util.Locale;

/** Command line options shared by every simulator mode. */
public class SimulatorOptions {

    private String monitorHost = "127.0.0.1";
    private int monitorPort = ProtocolConstants.DEFAULT_MONITOR_UDP_PORT;

    private String agentId = "AGENT-01";
    private String hostname = "SIMULATOR";
    private String secret = "GROUP10_DEMO_SECRET_01";

    private int agents = 20;
    private int durationSeconds = 60;
    private long intervalMs = ProtocolConstants.DEFAULT_METRIC_INTERVAL_MS;

    private String agentPrefix = "LOAD-AGENT-";

    /** Parses {@code --name=value} arguments, ignoring the leading mode word. */
    public static SimulatorOptions parse(String[] args) {
        SimulatorOptions options = new SimulatorOptions();
        if (args == null) {
            return options;
        }
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            int separator = arg.indexOf('=');
            String name = arg.substring(2, separator).trim().toLowerCase(Locale.ROOT);
            String value = strip(arg.substring(separator + 1));

            switch (name) {
                case "host":
                case "monitor-host":
                    options.monitorHost = value;
                    break;
                case "port":
                case "monitor-port":
                    options.monitorPort = parseInt(name, value);
                    break;
                case "agent":
                case "agent-id":
                    options.agentId = value;
                    break;
                case "hostname":
                    options.hostname = value;
                    break;
                case "secret":
                    options.secret = value;
                    break;
                case "agents":
                    options.agents = parseInt(name, value);
                    break;
                case "duration":
                    options.durationSeconds = parseInt(name, value);
                    break;
                case "interval":
                    options.intervalMs = parseInt(name, value);
                    break;
                case "agent-prefix":
                    options.agentPrefix = value;
                    break;
                default:
                    System.out.println("Ignoring unknown option --" + name);
                    break;
            }
        }
        return options;
    }

    private static String strip(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + name + " must be a whole number, got " + value);
        }
    }

    public String getMonitorHost() {
        return monitorHost;
    }

    public int getMonitorPort() {
        return monitorPort;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getHostname() {
        return hostname;
    }

    public String getSecret() {
        return secret;
    }

    public int getAgents() {
        return agents;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public String getAgentPrefix() {
        return agentPrefix;
    }

    /** Console summary. The shared secret is deliberately not included. */
    public String describe() {
        return "monitor=" + monitorHost + ":" + monitorPort
                + ", agent=" + agentId
                + ", agents=" + agents
                + ", duration=" + durationSeconds + "s"
                + ", interval=" + intervalMs + "ms";
    }
}
