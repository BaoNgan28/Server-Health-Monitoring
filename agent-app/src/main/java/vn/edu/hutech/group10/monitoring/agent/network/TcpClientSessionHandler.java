package vn.edu.hutech.group10.monitoring.agent.network;

import vn.edu.hutech.group10.monitoring.agent.config.AgentConfig;
import vn.edu.hutech.group10.monitoring.agent.metrics.SystemMetricsCollector;
import vn.edu.hutech.group10.monitoring.agent.service.ConfigUpdateService;
import vn.edu.hutech.group10.monitoring.common.model.AgentInfoMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigAckMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigUpdateMessage;
import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageFraming;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageType;
import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves one TCP control connection for its whole lifetime.
 *
 * <p>Runs on a pool thread, so a client that connects and then stops talking
 * costs one idle thread and nothing else; a read timeout closes such a session.
 * Every failure is contained here: a malformed request produces an ERROR frame,
 * and an exception closes only this socket, never the accept loop.
 */
public class TcpClientSessionHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger(TcpClientSessionHandler.class.getName());

    /** A control session that says nothing for this long is closed. */
    private static final int SESSION_READ_TIMEOUT_MS = 120_000;

    private final Socket socket;
    private final AgentConfig config;
    private final SystemMetricsCollector collector;
    private final ConfigUpdateService configUpdateService;
    private final JsonMessageCodec codec;
    private final String agentVersion;

    public TcpClientSessionHandler(Socket socket, AgentConfig config,
                                   SystemMetricsCollector collector,
                                   ConfigUpdateService configUpdateService,
                                   JsonMessageCodec codec, String agentVersion) {
        this.socket = socket;
        this.config = config;
        this.collector = collector;
        this.configUpdateService = configUpdateService;
        this.codec = codec;
        this.agentVersion = agentVersion;
    }

    @Override
    public void run() {
        String peer = socket.getRemoteSocketAddress() == null
                ? "unknown" : socket.getRemoteSocketAddress().toString();
        LOG.info("TCP control session opened from " + peer);
        try {
            socket.setSoTimeout(SESSION_READ_TIMEOUT_MS);
            socket.setTcpNoDelay(true);

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            String request;
            while ((request = MessageFraming.readFrame(in)) != null) {
                String response = handle(request);
                if (response != null) {
                    MessageFraming.writeFrame(out, response);
                }
            }
            LOG.info("TCP control session closed by " + peer);
        } catch (SocketTimeoutException e) {
            LOG.info("TCP control session from " + peer + " timed out while idle");
        } catch (IOException e) {
            LOG.info("TCP control session from " + peer + " ended: " + e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "TCP control session from " + peer + " failed", e);
        } finally {
            closeQuietly();
        }
    }

    /** Turns one request frame into one response frame. */
    private String handle(String request) {
        MessageType type = codec.peekType(request);
        if (type == null) {
            LOG.warning("Ignoring an unrecognised control message");
            return error("Unrecognised or malformed control message");
        }
        try {
            switch (type) {
                case PING:
                    return codec.toJson(simple(MessageType.PONG));
                case AGENT_INFO_REQUEST:
                    return codec.toJson(buildAgentInfo());
                case CONFIG_UPDATE:
                    ConfigUpdateMessage update = codec.fromJson(request, ConfigUpdateMessage.class);
                    ConfigAckMessage ack = configUpdateService.apply(update);
                    return codec.toJson(ack);
                default:
                    return error("Message type " + type + " is not accepted by an agent");
            }
        } catch (ProtocolException e) {
            LOG.warning("Malformed " + type + " request: " + e.getMessage());
            return error("Malformed " + type + " request");
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to handle " + type, e);
            return error("Agent failed to handle " + type);
        }
    }

    private AgentInfoMessage buildAgentInfo() {
        AgentInfoMessage info = new AgentInfoMessage();
        info.setType(MessageType.AGENT_INFO_RESPONSE.wireName());
        info.setAgentId(config.getAgentId());
        info.setAgentName(config.getAgentName());
        info.setHostname(config.getHostname());
        info.setMetricIntervalMs(config.getMetricIntervalMs());
        info.setHeartbeatIntervalMs(config.getHeartbeatIntervalMs());
        info.setAgentVersion(agentVersion);
        collector.describe(info);
        return info;
    }

    private Map<String, Object> simple(MessageType type) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type.wireName());
        message.put("agentId", config.getAgentId());
        message.put("timestamp", System.currentTimeMillis());
        return message;
    }

    private String error(String reason) {
        Map<String, Object> message = simple(MessageType.ERROR);
        message.put("message", reason);
        return codec.toJson(message);
    }

    private void closeQuietly() {
        try {
            socket.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Closing a control socket failed", e);
        }
    }
}
