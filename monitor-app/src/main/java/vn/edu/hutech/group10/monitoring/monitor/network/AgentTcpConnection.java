package vn.edu.hutech.group10.monitoring.monitor.network;

import vn.edu.hutech.group10.monitoring.common.model.AgentInfoMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigAckMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigUpdateMessage;
import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageFraming;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One control connection from the monitoring client to one agent.
 *
 * <p>TCP is used here because a configuration change must not be silently lost
 * the way a datagram can be: the agent's CONFIG_ACK is the proof it arrived and
 * was accepted. Frames are length prefixed (see {@link MessageFraming}).
 *
 * <p>Every call is a request followed by its reply on the same socket, so the
 * instance is synchronised: two GUI actions cannot interleave two conversations
 * on one connection. Connect and read timeouts are always set, so a machine that
 * has gone away fails in seconds instead of hanging a worker for ever.
 *
 * <p>This class is never called from the Swing event dispatch thread.
 */
public class AgentTcpConnection implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AgentTcpConnection.class.getName());

    private final String agentId;
    private final String host;
    private final int port;
    private final int timeoutMs;
    private final JsonMessageCodec codec;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    public AgentTcpConnection(String agentId, String host, int port, int timeoutMs,
                              JsonMessageCodec codec) {
        this.agentId = agentId;
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
        this.codec = codec;
    }

    /** Opens the socket. Does nothing when it is already open. */
    public synchronized void connect() throws IOException {
        if (isConnected()) {
            return;
        }
        Socket candidate = new Socket();
        try {
            candidate.connect(new InetSocketAddress(host, port), timeoutMs);
            candidate.setSoTimeout(timeoutMs);
            candidate.setTcpNoDelay(true);
            socket = candidate;
            in = new DataInputStream(candidate.getInputStream());
            out = new DataOutputStream(candidate.getOutputStream());
            LOG.info("TCP control connection to " + agentId + " at " + endpoint() + " established");
        } catch (IOException e) {
            closeQuietly(candidate);
            throw e;
        }
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /** Sends PING and waits for the agent's reply. */
    public synchronized boolean ping() throws IOException {
        Map<String, Object> request = new HashMap<>();
        request.put("type", MessageType.PING.wireName());
        request.put("timestamp", System.currentTimeMillis());

        String response = exchange(codec.toJson(request));
        return MessageType.PONG == codec.peekType(response);
    }

    /** Asks the agent to describe itself. */
    public synchronized AgentInfoMessage requestInfo() throws IOException {
        Map<String, Object> request = new HashMap<>();
        request.put("type", MessageType.AGENT_INFO_REQUEST.wireName());
        request.put("timestamp", System.currentTimeMillis());

        String response = exchange(codec.toJson(request));
        if (MessageType.AGENT_INFO_RESPONSE != codec.peekType(response)) {
            throw new IOException("Agent " + agentId + " did not return its details");
        }
        return codec.fromJson(response, AgentInfoMessage.class);
    }

    /**
     * Pushes a configuration change and returns the agent's acknowledgement.
     *
     * <p>A request id is generated here and echoed by the agent, so the reply can
     * be tied to the request that caused it.
     */
    public synchronized ConfigAckMessage sendConfigUpdate(ConfigUpdateMessage update)
            throws IOException {
        update.setType(MessageType.CONFIG_UPDATE.wireName());
        if (update.getRequestId() == null) {
            update.setRequestId(UUID.randomUUID().toString());
        }

        String response = exchange(codec.toJson(update));
        MessageType type = codec.peekType(response);
        if (type != MessageType.CONFIG_ACK) {
            throw new IOException("Agent " + agentId + " answered " + type
                    + " instead of CONFIG_ACK");
        }
        ConfigAckMessage ack = codec.fromJson(response, ConfigAckMessage.class);
        LOG.info("CONFIG_ACK from " + agentId + ": " + ack.getStatus() + " - " + ack.getMessage());
        return ack;
    }

    /** Writes one frame and reads exactly one frame back. */
    private String exchange(String request) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to agent " + agentId);
        }
        MessageFraming.writeFrame(out, request);
        String response = MessageFraming.readFrame(in);
        if (response == null) {
            throw new IOException("Agent " + agentId + " closed the connection");
        }
        return response;
    }

    public String getAgentId() {
        return agentId;
    }

    public String endpoint() {
        return host + ":" + port;
    }

    @Override
    public synchronized void close() {
        if (socket != null) {
            closeQuietly(socket);
            LOG.info("TCP control connection to " + agentId + " closed");
        }
        socket = null;
        in = null;
        out = null;
    }

    private static void closeQuietly(Socket target) {
        try {
            target.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Closing a control socket failed", e);
        }
    }
}
