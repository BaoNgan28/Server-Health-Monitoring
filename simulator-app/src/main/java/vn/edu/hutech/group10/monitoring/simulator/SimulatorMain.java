package vn.edu.hutech.group10.monitoring.simulator;

import vn.edu.hutech.group10.monitoring.common.util.LoggingSetup;

import java.util.Locale;

/**
 * Command line entry point of the test traffic generator.
 *
 * <pre>
 *   java -jar simulator-app-1.0.0-all.jar valid        --agent=AGENT-01 --duration=60
 *   java -jar simulator-app-1.0.0-all.jar invalid-hmac --agent=AGENT-01
 *   java -jar simulator-app-1.0.0-all.jar replay       --agent=AGENT-01
 *   java -jar simulator-app-1.0.0-all.jar load         --agents=20 --duration=60
 * </pre>
 */
public final class SimulatorMain {

    private SimulatorMain() {
    }

    public static void main(String[] args) {
        LoggingSetup.configure("simulator");

        if (args == null || args.length == 0) {
            printUsage();
            System.exit(1);
            return;
        }

        String mode = args[0].trim().toLowerCase(Locale.ROOT);
        SimulatorOptions options;
        try {
            options = SimulatorOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsage();
            System.exit(2);
            return;
        }

        System.out.println("============================================================");
        System.out.println(" GROUP 10 - SERVER HEALTH MONITORING - SIMULATOR");
        System.out.println(" mode: " + mode);
        System.out.println("============================================================");

        try {
            switch (mode) {
                case "valid":
                    new ValidAgentSimulator(options).run();
                    break;
                case "invalid-hmac":
                    new InvalidHmacSender(options).run();
                    break;
                case "replay":
                    new ReplayPacketSender(options).run();
                    break;
                case "load":
                    new LoadSimulator(options).run();
                    break;
                default:
                    System.err.println("Unknown mode: " + mode);
                    printUsage();
                    System.exit(2);
                    return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Interrupted.");
        } catch (Exception e) {
            System.err.println("Simulator failed: " + e.getMessage());
            System.exit(3);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar simulator-app-1.0.0-all.jar <mode> [options]");
        System.out.println();
        System.out.println("Modes:");
        System.out.println("  valid          send correctly signed telemetry as one agent");
        System.out.println("  invalid-hmac   send forged packets that must be rejected");
        System.out.println("  replay         send a valid packet, then send it again");
        System.out.println("  load           drive many identities at once");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --host=127.0.0.1        monitoring client address");
        System.out.println("  --port=6000             monitoring client UDP port");
        System.out.println("  --agent=AGENT-01        agent id to send as");
        System.out.println("  --secret=...            shared secret registered for that agent");
        System.out.println("  --hostname=SIMULATOR    hostname to report");
        System.out.println("  --agents=20             identities for the load mode");
        System.out.println("  --agent-prefix=LOAD-AGENT-   identity prefix for the load mode");
        System.out.println("  --duration=60           seconds to run");
        System.out.println("  --interval=1000         milliseconds between packets");
    }
}
