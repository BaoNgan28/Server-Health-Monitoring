package vn.edu.hutech.group10.monitoring.simulator;

import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;

import java.io.IOException;

/**
 * Sends correctly signed telemetry and heartbeats as one agent, the way a real
 * agent would. Used as the control case: whatever the security modes do, this
 * one must always be accepted.
 */
public class ValidAgentSimulator {

    private final SimulatorOptions options;

    public ValidAgentSimulator(SimulatorOptions options) {
        this.options = options;
    }

    /** Runs for the configured duration. */
    public void run() throws IOException, InterruptedException {
        System.out.println("Valid agent simulator: " + options.describe());

        try (SimulatorUdpSender sender = new SimulatorUdpSender(
                options.getMonitorHost(), options.getMonitorPort())) {

            // This run has its own session id, so it starts at sequence 1 without
            // disturbing the real agent's window.
            long sequence = 0L;
            long deadline = System.currentTimeMillis() + options.getDurationSeconds() * 1000L;
            long heartbeatEvery = Math.max(1L, 5000L / Math.max(1L, options.getIntervalMs()));
            long tick = 0;

            while (System.currentTimeMillis() < deadline) {
                TelemetryPacket packet = PacketFactory.signedTelemetry(options.getAgentId(),
                        options.getHostname(), ++sequence, options.getSecret());
                sender.send(packet);

                if (++tick % heartbeatEvery == 0) {
                    sender.send(PacketFactory.signedHeartbeat(options.getAgentId(),
                            options.getHostname(), ++sequence, options.getSecret()));
                }

                System.out.printf("sent seq=%d cpu=%.1f%% mem=%.1f%%%n",
                        packet.getSequence(), packet.getCpuUsage(), packet.getMemoryUsage());
                Thread.sleep(options.getIntervalMs());
            }

            System.out.println("Valid agent simulator finished: " + sender.getSentCount()
                    + " packet(s) sent");
        }
    }
}
