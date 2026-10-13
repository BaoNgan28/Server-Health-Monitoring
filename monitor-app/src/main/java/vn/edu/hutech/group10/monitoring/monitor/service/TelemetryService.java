package vn.edu.hutech.group10.monitoring.monitor.service;

import vn.edu.hutech.group10.monitoring.common.model.AlertType;
import vn.edu.hutech.group10.monitoring.common.model.HeartbeatPacket;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;
import vn.edu.hutech.group10.monitoring.common.protocol.JsonMessageCodec;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageType;
import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolException;
import vn.edu.hutech.group10.monitoring.common.security.PacketValidator;
import vn.edu.hutech.group10.monitoring.common.security.ValidationResult;
import vn.edu.hutech.group10.monitoring.monitor.model.AgentRuntimeState;
import vn.edu.hutech.group10.monitoring.monitor.model.MetricSample;
import vn.edu.hutech.group10.monitoring.monitor.repository.MetricRepository;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single path every inbound datagram takes.
 *
 * <pre>
 *   bytes -> JSON -> validate -> registry -> database -> alert engine -> GUI
 * </pre>
 *
 * <p>Nothing downstream of the validator runs for a packet that was refused. A
 * forged datagram claiming 100% CPU therefore never reaches the table, the
 * charts, {@code metric_raw} or the alert engine; it is counted, logged and
 * dropped.
 */
public class TelemetryService {

    private static final Logger LOG = Logger.getLogger(TelemetryService.class.getName());

    private final JsonMessageCodec codec;
    private final PacketValidator validator;
    private final AgentRegistry registry;
    private final MetricRepository metricRepository;
    private final AlertService alertService;
    private final HeartbeatMonitor heartbeatMonitor;
    private final MonitorEventBus eventBus;

    // Incremented from several UDP worker threads at once, so a plain counter
    // would silently lose updates.
    private final LongAdder acceptedCount = new LongAdder();
    private final LongAdder rejectedCount = new LongAdder();

    public TelemetryService(JsonMessageCodec codec, PacketValidator validator, AgentRegistry registry,
                            MetricRepository metricRepository, AlertService alertService,
                            HeartbeatMonitor heartbeatMonitor, MonitorEventBus eventBus) {
        this.codec = codec;
        this.validator = validator;
        this.registry = registry;
        this.metricRepository = metricRepository;
        this.alertService = alertService;
        this.heartbeatMonitor = heartbeatMonitor;
        this.eventBus = eventBus;
    }

    /**
     * Handles one datagram payload.
     *
     * @param sourceDescription where it came from, for the log only; never trusted
     * @return {@code true} when the packet was accepted
     */
    public boolean handleDatagram(byte[] payload, int offset, int length, String sourceDescription) {
        long now = System.currentTimeMillis();
        String json;
        try {
            json = new String(payload, offset, length, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            reject(null, "undecodable datagram from " + sourceDescription, null, now);
            return false;
        }

        MessageType type = codec.peekType(json);
        if (type == null) {
            reject(null, "unrecognised datagram from " + sourceDescription, null, now);
            return false;
        }

        try {
            switch (type) {
                case TELEMETRY:
                    return handleTelemetry(codec.fromJson(json, TelemetryPacket.class), now);
                case HEARTBEAT:
                    return handleHeartbeat(codec.fromJson(json, HeartbeatPacket.class), now);
                default:
                    reject(null, "message type " + type + " is not accepted over UDP", null, now);
                    return false;
            }
        } catch (ProtocolException e) {
            reject(null, "malformed " + type + " from " + sourceDescription, null, now);
            return false;
        } catch (RuntimeException e) {
            // One bad packet must never stop the receiver loop.
            LOG.log(Level.WARNING, "Failed to process a datagram from " + sourceDescription, e);
            rejectedCount.increment();
            return false;
        }
    }

    private boolean handleTelemetry(TelemetryPacket packet, long now) {
        ValidationResult result = validator.validate(packet, now);
        if (!result.isAccepted()) {
            reject(packet.getAgentId(), result.getDetail(), result.getReason(), now);
            return false;
        }

        acceptedCount.increment();
        MetricSample sample = MetricSample.from(packet);

        // Liveness first, so a recovery alert is raised before any threshold alert.
        heartbeatMonitor.markAlive(packet.getAgentId(), packet.getTimestamp(), now);
        registry.applySample(sample);

        try {
            metricRepository.insertRaw(sample);
        } catch (RuntimeException e) {
            // A database hiccup must not cost the live view or the alerting.
            LOG.log(Level.WARNING, "Could not store a metric sample for "
                    + packet.getAgentId() + ": " + e.getMessage());
        }

        AgentRuntimeState state = registry.find(packet.getAgentId()).orElse(null);
        if (state != null) {
            alertService.evaluate(state.getRecord(), sample);
        }
        return true;
    }

    private boolean handleHeartbeat(HeartbeatPacket packet, long now) {
        ValidationResult result = validator.validate(packet, now);
        if (!result.isAccepted()) {
            reject(packet.getAgentId(), result.getDetail(), result.getReason(), now);
            return false;
        }
        acceptedCount.increment();
        heartbeatMonitor.markAlive(packet.getAgentId(), packet.getTimestamp(), now);
        return true;
    }

    private void reject(String agentId, String detail, ValidationResult.Reason reason, long now) {
        rejectedCount.increment();
        if (agentId != null) {
            registry.countRejected(agentId);
        }
        AlertType securityType = securityTypeOf(reason);
        if (securityType != null && agentId != null) {
            alertService.recordSecurityEvent(agentId, securityType, detail, now);
        } else {
            LOG.warning("Packet rejected: " + detail);
        }
        eventBus.firePacketRejected(agentId, detail);
    }

    /** Only signature and replay failures are treated as security incidents. */
    private static AlertType securityTypeOf(ValidationResult.Reason reason) {
        if (reason == null) {
            return null;
        }
        switch (reason) {
            case INVALID_HMAC:
                return AlertType.SECURITY_INVALID_HMAC;
            case REPLAY:
                return AlertType.SECURITY_REPLAY_PACKET;
            default:
                return null;
        }
    }

    public long getAcceptedCount() {
        return acceptedCount.sum();
    }

    public long getRejectedCount() {
        return rejectedCount.sum();
    }
}
