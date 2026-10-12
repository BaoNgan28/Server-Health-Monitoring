package vn.edu.hutech.group10.monitoring.agent.network;

import vn.edu.hutech.group10.monitoring.agent.config.AgentConfig;
import vn.edu.hutech.group10.monitoring.agent.metrics.SystemMetricsCollector;
import vn.edu.hutech.group10.monitoring.agent.service.ConfigUpdateService;
import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Accepts TCP control connections from monitoring clients.
 *
 * <p>One thread owns the accept loop and a small bounded pool serves the
 * sessions, so the number of threads cannot grow with the number of connection
 * attempts. A failure while accepting one connection is logged and the loop
 * continues, so a single bad client cannot take the control channel down.
 */
public class TcpControlServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(TcpControlServer.class.getName());

    /** A monitoring client holds one session; a handful covers retries and several clients. */
    private static final int MAX_SESSIONS = 8;

    private final AgentConfig config;
    private final SystemMetricsCollector collector;
    private final ConfigUpdateService configUpdateService;
    private final JsonMessageCodec codec;
    private final String agentVersion;

    private final ExecutorService sessionPool;
    private final Thread acceptThread;

    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public TcpControlServer(AgentConfig config, SystemMetricsCollector collector,
                            ConfigUpdateService configUpdateService, JsonMessageCodec codec,
                            String agentVersion) {
        this.config = config;
        this.collector = collector;
        this.configUpdateService = configUpdateService;
        this.codec = codec;
        this.agentVersion = agentVersion;

        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "agent-tcp-session-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.sessionPool = Executors.newFixedThreadPool(MAX_SESSIONS, factory);
        this.acceptThread = new Thread(this::acceptLoop, "agent-tcp-accept");
        this.acceptThread.setDaemon(true);
    }

    /** Binds the listening socket and starts accepting. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(config.getAgentTcpPort());
        running = true;
        acceptThread.start();
        LOG.info("TCP control server listening on port " + config.getAgentTcpPort());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                sessionPool.submit(new TcpClientSessionHandler(
                        socket, config, collector, configUpdateService, codec, agentVersion));
            } catch (IOException e) {
                if (running) {
                    LOG.log(Level.WARNING, "Accepting a control connection failed", e);
                }
            } catch (RuntimeException e) {
                // For example a rejected submission once the pool is shutting down.
                LOG.log(Level.WARNING, "Dispatching a control connection failed", e);
            }
        }
        LOG.info("TCP accept loop stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        ServerSocket current = serverSocket;
        return current == null ? config.getAgentTcpPort() : current.getLocalPort();
    }

    @Override
    public void close() {
        running = false;
        ServerSocket current = serverSocket;
        if (current != null) {
            try {
                // Closing the listening socket unblocks accept().
                current.close();
            } catch (IOException e) {
                LOG.log(Level.FINE, "Closing the listening socket failed", e);
            }
        }
        sessionPool.shutdownNow();
        try {
            if (!sessionPool.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.warning("Some control sessions did not stop within 2 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.info("TCP control server stopped");
    }
}
