package vn.edu.hutech.group10.monitoring.monitor.core;

import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;
import vn.edu.hutech.group10.monitoring.common.security.PacketValidator;
import vn.edu.hutech.group10.monitoring.common.security.ReplayProtector;
import vn.edu.hutech.group10.monitoring.monitor.config.EmailConfig;
import vn.edu.hutech.group10.monitoring.monitor.config.EmailConfigLoader;
import vn.edu.hutech.group10.monitoring.monitor.config.MonitorConfig;
import vn.edu.hutech.group10.monitoring.monitor.network.AgentTcpConnectionManager;
import vn.edu.hutech.group10.monitoring.monitor.network.UdpTelemetryReceiver;
import vn.edu.hutech.group10.monitoring.monitor.notification.CompositeNotifier;
import vn.edu.hutech.group10.monitoring.monitor.notification.EmailNotifier;
import vn.edu.hutech.group10.monitoring.monitor.notification.NoOpNotifier;
import vn.edu.hutech.group10.monitoring.monitor.notification.Notifier;
import vn.edu.hutech.group10.monitoring.monitor.repository.AgentRepository;
import vn.edu.hutech.group10.monitoring.monitor.repository.AlertRepository;
import vn.edu.hutech.group10.monitoring.monitor.repository.DatabaseManager;
import vn.edu.hutech.group10.monitoring.monitor.repository.MetricRepository;
import vn.edu.hutech.group10.monitoring.monitor.service.AgentManagementService;
import vn.edu.hutech.group10.monitoring.monitor.service.AgentRegistry;
import vn.edu.hutech.group10.monitoring.monitor.service.AggregationService;
import vn.edu.hutech.group10.monitoring.monitor.service.AlertService;
import vn.edu.hutech.group10.monitoring.monitor.service.HeartbeatMonitor;
import vn.edu.hutech.group10.monitoring.monitor.service.HistoryService;
import vn.edu.hutech.group10.monitoring.monitor.service.MonitorEventBus;
import vn.edu.hutech.group10.monitoring.monitor.service.TelemetryService;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wires the monitoring client together and owns its lifecycle.
 *
 * <p>Layering is enforced here by construction: the GUI is handed services, and
 * only services hold repositories, sockets and the notifier. Nothing in the user
 * interface can reach a {@code DatagramSocket}, a JDBC statement or an SMTP
 * session.
 *
 * <p>Startup order is database, registry, alert state, notifier, services,
 * sockets, timers. Shutdown is the reverse and is safe to run twice.
 */
public class MonitorApplication implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MonitorApplication.class.getName());

    /** One thread for the heartbeat sweep, one for aggregation. */
    private static final int SCHEDULER_THREADS = 2;

    private final MonitorConfig config;
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final MonitorEventBus eventBus = new MonitorEventBus();

    private DatabaseManager database;
    private AgentRepository agentRepository;
    private MetricRepository metricRepository;
    private AlertRepository alertRepository;

    private EmailConfig emailConfig;
    private Notifier notifier;

    private AgentRegistry registry;
    private AlertService alertService;
    private HeartbeatMonitor heartbeatMonitor;
    private TelemetryService telemetryService;
    private AggregationService aggregationService;
    private HistoryService historyService;
    private AgentManagementService agentManagementService;

    private AgentTcpConnectionManager connectionManager;
    private UdpTelemetryReceiver udpReceiver;
    private ScheduledExecutorService scheduler;

    private volatile boolean running;

    public MonitorApplication(MonitorConfig config) {
        this.config = config;
    }

    /** Brings everything up. Rolls back on failure so no socket or file is left open. */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        try {
            database = new DatabaseManager(config.getDatabasePath());
            database.initialize();

            agentRepository = new AgentRepository(database);
            metricRepository = new MetricRepository(database);
            alertRepository = new AlertRepository(database);

            emailConfig = EmailConfigLoader.load();
            notifier = buildNotifier(emailConfig);

            registry = new AgentRegistry(agentRepository, eventBus);
            registry.load();

            alertService = new AlertService(alertRepository, notifier, eventBus);
            alertService.load();

            heartbeatMonitor = new HeartbeatMonitor(registry, alertService,
                    config.getHeartbeatTimeoutMs(), config.getHeartbeatCheckIntervalMs());

            PacketValidator validator = new PacketValidator(registry, new ReplayProtector(),
                    config.getTimestampToleranceMs());

            telemetryService = new TelemetryService(codec, validator, registry, metricRepository,
                    alertService, heartbeatMonitor, eventBus);

            connectionManager = new AgentTcpConnectionManager(codec, config);
            agentManagementService = new AgentManagementService(agentRepository, registry,
                    alertService, connectionManager);

            historyService = new HistoryService(metricRepository, config);
            aggregationService = new AggregationService(metricRepository, config, eventBus);

            udpReceiver = new UdpTelemetryReceiver(config.getUdpPort(), telemetryService);
            udpReceiver.start();

            AtomicInteger counter = new AtomicInteger();
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "monitor-scheduler-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
            scheduler = Executors.newScheduledThreadPool(SCHEDULER_THREADS, factory);
            heartbeatMonitor.start(scheduler);
            aggregationService.start(scheduler);

            running = true;
            LOG.info("Monitoring client started with profile '" + config.getProfileName() + "'");
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Monitoring client startup failed, rolling back", e);
            close();
            throw e;
        }
    }

    /**
     * Builds the alert channel. Missing credentials give a no-op channel and a
     * log line, never a startup failure.
     */
    private Notifier buildNotifier(EmailConfig email) {
        if (email.isConfigured()) {
            return new CompositeNotifier(EmailNotifier.withSmtp(email));
        }
        LOG.info(EmailConfigLoader.DISABLED_LOG_MESSAGE);
        return new CompositeNotifier(new NoOpNotifier(email.getDisabledReason()));
    }

    /**
     * Rebuilds the notifier from the current contents of the secrets file, so an
     * operator can fill the file in and switch e-mail on without a restart.
     */
    public synchronized EmailConfig reloadEmailConfiguration() {
        EmailConfig reloaded = EmailConfigLoader.load();
        Notifier replacement = buildNotifier(reloaded);
        Notifier previous = notifier;

        emailConfig = reloaded;
        notifier = replacement;
        if (alertService != null) {
            alertService = new AlertService(alertRepository, replacement, eventBus);
            alertService.load();
        }
        if (previous != null) {
            previous.close();
        }
        return reloaded;
    }

    @Override
    public synchronized void close() {
        if (aggregationService != null) {
            aggregationService.stop();
        }
        if (heartbeatMonitor != null) {
            heartbeatMonitor.stop();
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
        if (udpReceiver != null) {
            udpReceiver.close();
            udpReceiver = null;
        }
        if (connectionManager != null) {
            connectionManager.close();
            connectionManager = null;
        }
        if (registry != null) {
            try {
                registry.persistLastSeen();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Could not persist agent status on shutdown", e);
            }
        }
        if (notifier != null) {
            notifier.close();
            notifier = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
        if (running) {
            LOG.info("Monitoring client stopped");
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public MonitorConfig getConfig() {
        return config;
    }

    public MonitorEventBus getEventBus() {
        return eventBus;
    }

    public AgentRegistry getRegistry() {
        return registry;
    }

    public AgentManagementService getAgentManagementService() {
        return agentManagementService;
    }

    public HistoryService getHistoryService() {
        return historyService;
    }

    public AlertService getAlertService() {
        return alertService;
    }

    public AlertRepository getAlertRepository() {
        return alertRepository;
    }

    public AggregationService getAggregationService() {
        return aggregationService;
    }

    public TelemetryService getTelemetryService() {
        return telemetryService;
    }

    public UdpTelemetryReceiver getUdpReceiver() {
        return udpReceiver;
    }

    public EmailConfig getEmailConfig() {
        return emailConfig;
    }

    public Notifier getNotifier() {
        return notifier;
    }
}
