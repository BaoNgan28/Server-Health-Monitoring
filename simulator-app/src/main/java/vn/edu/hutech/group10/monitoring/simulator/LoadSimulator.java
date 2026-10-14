package vn.edu.hutech.group10.monitoring.simulator;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives many agent identities at once to show the monitoring client copes.
 *
 * <p>The simulator uses one scheduled pool rather than a thread per identity,
 * so twenty agents cost a handful of threads on this side as well - a load
 * generator that falls over first proves nothing about the receiver.
 *
 * <p>By default the identities are named {@code LOAD-AGENT-01} and upwards and
 * are not registered on the monitoring client, so every packet is refused as
 * UNKNOWN_AGENT. That is the intended result: the point of this mode is that the
 * receiver keeps up and stays alive under a flood, and refusing unregistered
 * senders is exactly what it should do. To see them accepted instead, register
 * the identities first and pass the shared secret with {@code --secret}.
 */
public class LoadSimulator {

    private final SimulatorOptions options;

    public LoadSimulator(SimulatorOptions options) {
        this.options = options;
    }

    public void run() throws IOException, InterruptedException {
        int agents = Math.max(1, options.getAgents());
        int duration = Math.max(1, options.getDurationSeconds());

        System.out.println("Load simulator: " + agents + " identities for " + duration
                + "s at " + options.getIntervalMs() + " ms, target "
                + options.getMonitorHost() + ":" + options.getMonitorPort());

        AtomicLong sent = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        AtomicInteger threadCounter = new AtomicInteger();

        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "load-sender-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        // A few threads serve many identities; the work per tick is a single send.
        int poolSize = Math.min(4, agents);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(poolSize, factory);

        try (SimulatorUdpSender sender = new SimulatorUdpSender(
                options.getMonitorHost(), options.getMonitorPort())) {

            for (int i = 1; i <= agents; i++) {
                String agentId = String.format("%s%02d", options.getAgentPrefix(), i);
                AtomicLong sequence = new AtomicLong();

                // Spread the starts so all identities do not fire in the same millisecond.
                long stagger = (options.getIntervalMs() * i) / agents;
                executor.scheduleAtFixedRate(() -> {
                    try {
                        sender.send(PacketFactory.signedTelemetry(agentId, agentId,
                                sequence.incrementAndGet(), options.getSecret()));
                        sent.incrementAndGet();
                    } catch (IOException | RuntimeException e) {
                        failed.incrementAndGet();
                    }
                }, stagger, options.getIntervalMs(), TimeUnit.MILLISECONDS);
            }

            long deadline = System.currentTimeMillis() + duration * 1000L;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(5000L);
                System.out.println("  " + sent.get() + " packet(s) sent, " + failed.get() + " failed");
            }

            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            long total = sent.get();
            System.out.println();
            System.out.println("Load simulator finished");
            System.out.println("  identities      : " + agents);
            System.out.println("  duration        : " + duration + " s");
            System.out.println("  packets sent    : " + total);
            System.out.println("  packets failed  : " + failed.get());
            System.out.println("  average rate    : " + (total / duration) + " packet(s)/s");
            System.out.println();
            System.out.println("Check on the monitoring client: the window is still responsive, the");
            System.out.println("received counter matches, and the thread count has not grown.");
        } finally {
            executor.shutdownNow();
        }
    }
}
