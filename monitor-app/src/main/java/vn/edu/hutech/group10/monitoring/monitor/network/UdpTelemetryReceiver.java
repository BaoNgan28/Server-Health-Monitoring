package vn.edu.hutech.group10.monitoring.monitor.network;

import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.monitor.service.TelemetryService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens for agent telemetry on the monitoring UDP port.
 *
 * <p>One thread does nothing but {@code receive()}, and the work of validating,
 * storing and alerting happens on worker threads. Keeping the socket loop free
 * matters: while it is busy the kernel buffer fills and datagrams are lost.
 *
 * <p><b>Ordering.</b> The workers are not one shared pool. Each is a single
 * thread with its own queue, and a datagram is always given to the worker chosen
 * by its sender's address and port. Agents send from one socket, so every packet
 * from an agent lands on the same worker and is processed in the order it
 * arrived. A shared pool would let packet 40 overtake packet 39, and the replay
 * check would then refuse a perfectly good packet. Work is still spread across
 * threads, just by sender rather than packet by packet.
 *
 * <p><b>Back pressure.</b> Each queue is bounded and its rejection policy runs
 * the work on the receiving thread, which throttles a flood instead of letting
 * the queue grow without limit - the behaviour the twenty-agent load test
 * checks. That fallback can reorder relative to the queued work, which is the
 * other reason the replay check tolerates a window of late packets rather than
 * demanding a strictly rising sequence.
 */
public class UdpTelemetryReceiver implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(UdpTelemetryReceiver.class.getName());

    private static final int WORKER_COUNT = 4;
    private static final int QUEUE_CAPACITY = 2000;
    /** Asking the kernel for a larger buffer reduces loss during a burst. */
    private static final int SOCKET_RECEIVE_BUFFER = 1024 * 1024;

    private final int port;
    private final TelemetryService telemetryService;
    private final List<ThreadPoolExecutor> workers = new ArrayList<>(WORKER_COUNT);
    private final Thread receiveThread;

    private final LongAdder receivedCount = new LongAdder();

    private volatile DatagramSocket socket;
    private volatile boolean running;

    public UdpTelemetryReceiver(int port, TelemetryService telemetryService) {
        this.port = port;
        this.telemetryService = telemetryService;

        for (int i = 0; i < WORKER_COUNT; i++) {
            final int index = i;
            workers.add(new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                    runnable -> {
                        Thread thread = new Thread(runnable, "udp-worker-" + index);
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy()));
        }

        this.receiveThread = new Thread(this::receiveLoop, "udp-receiver");
        this.receiveThread.setDaemon(true);
    }

    /** Binds the socket and starts receiving. */
    public void start() throws SocketException {
        DatagramSocket bound = new DatagramSocket(port);
        try {
            bound.setReceiveBufferSize(SOCKET_RECEIVE_BUFFER);
        } catch (SocketException e) {
            LOG.fine("Could not enlarge the UDP receive buffer: " + e.getMessage());
        }
        socket = bound;
        running = true;
        receiveThread.start();
        LOG.info("UDP telemetry receiver listening on port " + getPort());
    }

    private void receiveLoop() {
        byte[] buffer = new byte[ProtocolConstants.MAX_UDP_PACKET_SIZE];
        while (running) {
            try {
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                socket.receive(datagram);
                receivedCount.increment();

                // Copy out of the shared buffer before handing the bytes to a worker.
                byte[] payload = Arrays.copyOfRange(datagram.getData(),
                        datagram.getOffset(), datagram.getOffset() + datagram.getLength());
                String source = datagram.getAddress() == null
                        ? "unknown" : datagram.getAddress().getHostAddress() + ":" + datagram.getPort();

                workerFor(source).execute(() -> process(payload, source));
            } catch (IOException e) {
                if (running) {
                    LOG.log(Level.WARNING, "UDP receive failed", e);
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Dispatching a datagram failed", e);
            }
        }
        LOG.info("UDP receive loop stopped");
    }

    /** Always the same worker for the same sender, so that sender's order is kept. */
    private ThreadPoolExecutor workerFor(String source) {
        int index = Math.floorMod(source.hashCode(), WORKER_COUNT);
        return workers.get(index);
    }

    private void process(byte[] payload, String source) {
        try {
            telemetryService.handleDatagram(payload, 0, payload.length, source);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Processing a datagram from " + source + " failed", e);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        DatagramSocket current = socket;
        return current == null ? port : current.getLocalPort();
    }

    /** How many datagrams reached the socket, valid or not. */
    public long getReceivedCount() {
        return receivedCount.sum();
    }

    /** Total datagrams waiting across every worker queue. */
    public int getQueueDepth() {
        int depth = 0;
        for (ThreadPoolExecutor worker : workers) {
            depth += worker.getQueue().size();
        }
        return depth;
    }

    @Override
    public void close() {
        running = false;
        DatagramSocket current = socket;
        if (current != null) {
            // Closing the socket is what unblocks receive().
            current.close();
        }
        for (ThreadPoolExecutor worker : workers) {
            worker.shutdown();
        }
        for (ThreadPoolExecutor worker : workers) {
            try {
                if (!worker.awaitTermination(2, TimeUnit.SECONDS)) {
                    worker.shutdownNow();
                }
            } catch (InterruptedException e) {
                worker.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOG.info("UDP telemetry receiver stopped");
    }
}
