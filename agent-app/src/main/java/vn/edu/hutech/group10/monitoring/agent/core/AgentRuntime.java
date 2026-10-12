package vn.edu.hutech.group10.monitoring.agent.core;

import vn.edu.hutech.group10.monitoring.agent.config.AgentConfig;
import vn.edu.hutech.group10.monitoring.agent.metrics.OshiSystemMetricsCollector;
import vn.edu.hutech.group10.monitoring.agent.metrics.SystemMetricsCollector;
import vn.edu.hutech.group10.monitoring.agent.network.TcpControlServer;
import vn.edu.hutech.group10.monitoring.agent.network.UdpTelemetrySender;
import vn.edu.hutech.group10.monitoring.agent.service.ConfigUpdateService;
import vn.edu.hutech.group10.monitoring.agent.service.HeartbeatScheduler;
import vn.edu.hutech.group10.monitoring.agent.service.TelemetryScheduler;
import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns every moving part of an agent and the order in which they start and stop.
 *
 * <p>Startup: metrics collector, UDP sender, TCP control server, then the two
 * schedulers. Shutdown runs in reverse and is idempotent, so the shutdown hook
 * and an explicit stop cannot conflict.
 */
public class AgentRuntime implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AgentRuntime.class.getName());

    /** Two threads: one for the telemetry tick, one for the heartbeat tick. */
    private static final int SCHEDULER_THREADS = 2;

    private final AgentConfig config;
    private final String agentVersion;
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final AtomicLong sequence = new AtomicLong();

    /**
     * Identifies this run of this agent. A restart produces a new value, which
     * is how the monitoring client tells a restarted agent counting from 1
     * again apart from a replayed capture.
     */
    private final String sessionId = UUID.randomUUID().toString();

    private SystemMetricsCollector collector;
    private UdpTelemetrySender sender;
    private TcpControlServer controlServer;
    private ScheduledExecutorService scheduler;
    private TelemetryScheduler telemetryScheduler;
    private HeartbeatScheduler heartbeatScheduler;

    private volatile boolean running;

    public AgentRuntime(AgentConfig config, String agentVersion) {
        this.config = config;
        this.agentVersion = agentVersion;
    }

    /** Brings the agent up. Any failure here rolls back what has already started. */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        try {
            collector = new OshiSystemMetricsCollector();
            LOG.info("System metrics collector ready");

            sender = new UdpTelemetrySender(config.getMonitorHost(), config.getMonitorUdpPort(), codec);
            LOG.info("UDP telemetry target is " + sender.getTarget());

            ConfigUpdateService configUpdateService = new ConfigUpdateService(config, changed -> {
                telemetryScheduler.reschedule();
                heartbeatScheduler.reschedule();
            });

            controlServer = new TcpControlServer(config, collector, configUpdateService, codec, agentVersion);
            controlServer.start();

            AtomicInteger counter = new AtomicInteger();
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "agent-scheduler-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
            scheduler = Executors.newScheduledThreadPool(SCHEDULER_THREADS, factory);

            telemetryScheduler = new TelemetryScheduler(config, collector, sender, scheduler,
                    sequence, sessionId);
            heartbeatScheduler = new HeartbeatScheduler(config, sender, scheduler,
                    sequence, sessionId);
            telemetryScheduler.start();
            heartbeatScheduler.start();

            running = true;
            LOG.info("Agent " + config.getAgentId() + " started");
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Agent startup failed, rolling back", e);
            stop();
            throw e;
        }
    }

    /** Stops everything in reverse order. Safe to call more than once. */
    public synchronized void stop() {
        if (telemetryScheduler != null) {
            telemetryScheduler.cancel();
        }
        if (heartbeatScheduler != null) {
            heartbeatScheduler.cancel();
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        if (controlServer != null) {
            controlServer.close();
            controlServer = null;
        }
        if (sender != null) {
            sender.close();
            sender = null;
        }
        if (collector != null) {
            collector.close();
            collector = null;
        }
        if (running) {
            LOG.info("Agent " + config.getAgentId() + " stopped");
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public AgentConfig getConfig() {
        return config;
    }

    public TelemetryScheduler getTelemetryScheduler() {
        return telemetryScheduler;
    }

    public UdpTelemetrySender getSender() {
        return sender;
    }

    /** Session id of this run; shown at startup and carried in every packet. */
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public void close() {
        stop();
    }
}
