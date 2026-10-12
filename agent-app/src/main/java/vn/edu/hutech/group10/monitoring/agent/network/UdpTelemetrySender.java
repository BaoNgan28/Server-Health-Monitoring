package vn.edu.hutech.group10.monitoring.agent.network;

import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;
import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends signed JSON messages to the monitoring client over UDP.
 *
 * <p>UDP is the right fit for telemetry: samples are small, they arrive once a
 * second, and a lost sample is simply replaced by the next one - the cost of a
 * retransmission would exceed its value. Losing a datagram therefore never
 * blocks the agent, and a send failure is logged and swallowed rather than
 * propagated into the scheduler.
 */
public class UdpTelemetrySender implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(UdpTelemetrySender.class.getName());

    private final String monitorHost;
    private final int monitorPort;
    private final JsonMessageCodec codec;
    private final DatagramSocket socket;

    private volatile InetAddress resolvedAddress;
    // The telemetry and heartbeat schedulers share this sender, so the
    // counters are incremented from two threads.
    private final LongAdder sentCount = new LongAdder();
    private final LongAdder failedCount = new LongAdder();

    public UdpTelemetrySender(String monitorHost, int monitorPort, JsonMessageCodec codec)
            throws SocketException {
        this.monitorHost = monitorHost;
        this.monitorPort = monitorPort;
        this.codec = codec;
        this.socket = new DatagramSocket();
    }

    /**
     * Encodes and sends {@code message}.
     *
     * @return {@code true} when the datagram was handed to the network stack
     */
    public boolean send(Object message) {
        try {
            byte[] payload = codec.toBytes(message);
            if (payload.length > ProtocolConstants.MAX_UDP_PACKET_SIZE) {
                LOG.warning("Refusing to send an oversized datagram of " + payload.length + " bytes");
                failedCount.increment();
                return false;
            }
            InetAddress address = address();
            socket.send(new DatagramPacket(payload, payload.length, address, monitorPort));
            sentCount.increment();
            return true;
        } catch (IOException | RuntimeException e) {
            failedCount.increment();
            // Drop the cached address so a monitor that moved is picked up again.
            resolvedAddress = null;
            LOG.log(Level.WARNING, "UDP send to " + monitorHost + ":" + monitorPort
                    + " failed: " + e.getMessage());
            return false;
        }
    }

    private InetAddress address() throws UnknownHostException {
        InetAddress cached = resolvedAddress;
        if (cached == null) {
            cached = InetAddress.getByName(monitorHost);
            resolvedAddress = cached;
        }
        return cached;
    }

    public long getSentCount() {
        return sentCount.sum();
    }

    public long getFailedCount() {
        return failedCount.sum();
    }

    public String getTarget() {
        return monitorHost + ":" + monitorPort;
    }

    @Override
    public void close() {
        socket.close();
    }
}
