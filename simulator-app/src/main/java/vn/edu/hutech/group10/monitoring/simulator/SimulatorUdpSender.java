package vn.edu.hutech.group10.monitoring.simulator;

import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/** Minimal UDP sender used by every simulator mode. */
public class SimulatorUdpSender implements AutoCloseable {

    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final DatagramSocket socket;
    private final InetAddress address;
    private final int port;

    private long sentCount;

    public SimulatorUdpSender(String host, int port) throws SocketException, UnknownHostException {
        this.socket = new DatagramSocket();
        this.address = InetAddress.getByName(host);
        this.port = port;
    }

    /** Encodes {@code message} as JSON and sends it. */
    public void send(Object message) throws IOException {
        byte[] payload = codec.toBytes(message);
        socket.send(new DatagramPacket(payload, payload.length, address, port));
        sentCount++;
    }

    public long getSentCount() {
        return sentCount;
    }

    public JsonMessageCodec getCodec() {
        return codec;
    }

    @Override
    public void close() {
        socket.close();
    }
}
