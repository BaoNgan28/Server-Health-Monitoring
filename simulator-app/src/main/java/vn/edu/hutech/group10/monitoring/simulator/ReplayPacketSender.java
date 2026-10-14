package vn.edu.hutech.group10.monitoring.simulator;

import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;

import java.io.IOException;

/**
 * Captures a valid packet and sends it again, byte for byte.
 *
 * <p>The signature is genuine, so this is the attack a signature alone cannot
 * stop: only the sequence number tells the second copy apart from the first.
 * The monitoring client must accept the first delivery and refuse every later
 * one, then still accept the next genuine packet.
 */
public class ReplayPacketSender {

    private final SimulatorOptions options;

    public ReplayPacketSender(SimulatorOptions options) {
        this.options = options;
    }

    public void run() throws IOException, InterruptedException {
        System.out.println("Replay packet sender: " + options.describe());

        try (SimulatorUdpSender sender = new SimulatorUdpSender(
                options.getMonitorHost(), options.getMonitorPort())) {

            // This run has its own session id, so it starts at sequence 1 without
            // disturbing the real agent's window.
            long sequence = 0L;
            TelemetryPacket original = PacketFactory.signedTelemetry(options.getAgentId(),
                    options.getHostname(), ++sequence, options.getSecret());

            sender.send(original);
            System.out.println("1/4 sent: original packet with sequence " + original.getSequence()
                    + " - must be ACCEPTED");
            Thread.sleep(1000L);

            sender.send(original);
            System.out.println("2/4 sent: the very same packet again - must be REJECTED as a replay");
            Thread.sleep(1000L);

            sender.send(original);
            System.out.println("3/4 sent: a third copy - must be REJECTED as a replay");
            Thread.sleep(1000L);

            TelemetryPacket fresh = PacketFactory.signedTelemetry(options.getAgentId(),
                    options.getHostname(), original.getSequence() + 1, options.getSecret());
            sender.send(fresh);
            System.out.println("4/4 sent: a fresh packet with sequence " + fresh.getSequence()
                    + " - must be ACCEPTED");

            System.out.println();
            System.out.println("Expected on the monitoring client:");
            System.out.println("  - packets 1 and 4 accepted");
            System.out.println("  - packets 2 and 3 rejected, with SECURITY_REPLAY_PACKET logged");
        }
    }
}
