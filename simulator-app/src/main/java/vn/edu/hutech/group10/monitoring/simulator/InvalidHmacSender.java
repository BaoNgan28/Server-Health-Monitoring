package vn.edu.hutech.group10.monitoring.simulator;

import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;
import vn.edu.hutech.group10.monitoring.common.security.PacketSigner;

import java.io.IOException;

/**
 * Impersonates a registered agent with a forged signature.
 *
 * <p>Three variants are sent, each of which would raise a false alarm if the
 * monitoring client trusted the payload:
 * <ol>
 *   <li>a packet claiming 100% CPU and 100% memory with a junk signature;</li>
 *   <li>the same, signed with the wrong key;</li>
 *   <li>a genuine packet whose CPU figure was raised after it was signed.</li>
 * </ol>
 *
 * <p>The monitoring client must refuse all three: nothing on the dashboard, no
 * row in {@code metric_raw}, no CPU alert, and a security warning in the log.
 */
public class InvalidHmacSender {

    private static final String WRONG_SECRET = "ATTACKER_GUESSED_SECRET";
    private static final String JUNK_SIGNATURE =
            "00000000000000000000000000000000000000000000000000000000deadbeef";

    private final SimulatorOptions options;

    public InvalidHmacSender(SimulatorOptions options) {
        this.options = options;
    }

    public void run() throws IOException, InterruptedException {
        System.out.println("Invalid HMAC sender: " + options.describe());
        System.out.println("Every packet below must be REJECTED by the monitoring client.");

        try (SimulatorUdpSender sender = new SimulatorUdpSender(
                options.getMonitorHost(), options.getMonitorPort())) {

            // This run has its own session id, so it starts at sequence 1 without
            // disturbing the real agent's window.
            long sequence = 0L;

            TelemetryPacket junk = spike(++sequence);
            junk.setHmac(JUNK_SIGNATURE);
            sender.send(junk);
            System.out.println("1/3 sent: CPU 100%, junk signature");
            Thread.sleep(500L);

            TelemetryPacket wrongKey = spike(++sequence);
            PacketSigner.sign(wrongKey, WRONG_SECRET);
            sender.send(wrongKey);
            System.out.println("2/3 sent: CPU 100%, signed with the wrong key");
            Thread.sleep(500L);

            TelemetryPacket tampered = PacketFactory.signedTelemetry(options.getAgentId(),
                    options.getHostname(), ++sequence, options.getSecret());
            // Signed honestly, then altered: this is what the signature exists to catch.
            tampered.setCpuUsage(100.0d);
            sender.send(tampered);
            System.out.println("3/3 sent: genuine packet with the CPU value raised after signing");

            System.out.println();
            System.out.println("Expected on the monitoring client:");
            System.out.println("  - the dashboard still shows the real CPU value");
            System.out.println("  - no new rows in metric_raw for these packets");
            System.out.println("  - no CPU_HIGH alert caused by them");
            System.out.println("  - SECURITY_INVALID_HMAC in the log and in the alert panel");
        }
    }

    private TelemetryPacket spike(long sequence) {
        TelemetryPacket packet = PacketFactory.telemetry(options.getAgentId(),
                options.getHostname(), sequence);
        packet.setCpuUsage(100.0d);
        packet.setMemoryUsage(100.0d);
        return packet;
    }
}
