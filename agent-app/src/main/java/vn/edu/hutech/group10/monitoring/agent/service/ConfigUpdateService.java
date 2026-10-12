package vn.edu.hutech.group10.monitoring.agent.service;

import vn.edu.hutech.group10.monitoring.agent.config.AgentConfig;
import vn.edu.hutech.group10.monitoring.common.model.ConfigAckMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigUpdateMessage;
import vn.edu.hutech.group10.monitoring.common.protocol.MessageType;
import vn.edu.hutech.group10.monitoring.common.protocol.ProtocolConstants;
import vn.edu.hutech.group10.monitoring.common.util.ValidationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Validates and applies a CONFIG_UPDATE, then builds the CONFIG_ACK that goes
 * back over the same TCP connection.
 *
 * <p>Validation happens for every field before anything is applied, so a message
 * with one bad value leaves the agent exactly as it was instead of half updated.
 * A {@code null} field means "leave unchanged", which lets the monitoring client
 * push a single threshold without restating the rest.
 */
public class ConfigUpdateService {

    private static final Logger LOG = Logger.getLogger(ConfigUpdateService.class.getName());

    /** Notified after a change has been applied, so schedulers can re-arm. */
    @FunctionalInterface
    public interface ConfigChangeListener {
        void onConfigChanged(AgentConfig config);
    }

    private final AgentConfig config;
    private final ConfigChangeListener listener;

    public ConfigUpdateService(AgentConfig config, ConfigChangeListener listener) {
        this.config = config;
        this.listener = listener;
    }

    /** Validates {@code update}, applies it when it is sound, and returns the acknowledgement. */
    public synchronized ConfigAckMessage apply(ConfigUpdateMessage update) {
        ConfigAckMessage ack = new ConfigAckMessage();
        ack.setType(MessageType.CONFIG_ACK.wireName());

        if (update == null) {
            ack.setStatus(ConfigAckMessage.STATUS_ERROR);
            ack.setMessage("Empty configuration message");
            fillEffective(ack);
            return ack;
        }
        ack.setRequestId(update.getRequestId());

        List<String> problems = validate(update);
        if (!problems.isEmpty()) {
            ack.setStatus(ConfigAckMessage.STATUS_ERROR);
            ack.setMessage(String.join("; ", problems));
            fillEffective(ack);
            LOG.warning("Rejected CONFIG_UPDATE " + update.getRequestId() + ": " + ack.getMessage());
            return ack;
        }

        List<String> applied = new ArrayList<>();
        if (update.getMetricIntervalMs() != null) {
            config.setMetricIntervalMs(update.getMetricIntervalMs());
            applied.add("metricIntervalMs=" + update.getMetricIntervalMs());
        }
        if (update.getHeartbeatIntervalMs() != null) {
            config.setHeartbeatIntervalMs(update.getHeartbeatIntervalMs());
            applied.add("heartbeatIntervalMs=" + update.getHeartbeatIntervalMs());
        }
        if (update.getCpuThreshold() != null) {
            config.setCpuThreshold(update.getCpuThreshold());
            applied.add("cpuThreshold=" + update.getCpuThreshold());
        }
        if (update.getMemoryThreshold() != null) {
            config.setMemoryThreshold(update.getMemoryThreshold());
            applied.add("memoryThreshold=" + update.getMemoryThreshold());
        }
        if (update.getNetworkInThresholdMbps() != null) {
            config.setNetworkInThresholdMbps(update.getNetworkInThresholdMbps());
            applied.add("networkInThresholdMbps=" + update.getNetworkInThresholdMbps());
        }
        if (update.getNetworkOutThresholdMbps() != null) {
            config.setNetworkOutThresholdMbps(update.getNetworkOutThresholdMbps());
            applied.add("networkOutThresholdMbps=" + update.getNetworkOutThresholdMbps());
        }

        if (applied.isEmpty()) {
            ack.setStatus(ConfigAckMessage.STATUS_SUCCESS);
            ack.setMessage("No change requested; current configuration kept");
        } else {
            ack.setStatus(ConfigAckMessage.STATUS_SUCCESS);
            ack.setMessage("Applied " + String.join(", ", applied));
            if (listener != null) {
                listener.onConfigChanged(config);
            }
            LOG.info("Applied CONFIG_UPDATE " + update.getRequestId() + ": " + ack.getMessage());
        }

        fillEffective(ack);
        return ack;
    }

    private List<String> validate(ConfigUpdateMessage update) {
        List<String> problems = new ArrayList<>();

        checkInterval(problems, "metricIntervalMs", update.getMetricIntervalMs(),
                ProtocolConstants.MIN_METRIC_INTERVAL_MS);
        checkInterval(problems, "heartbeatIntervalMs", update.getHeartbeatIntervalMs(),
                ProtocolConstants.MIN_HEARTBEAT_INTERVAL_MS);

        checkPercentage(problems, "cpuThreshold", update.getCpuThreshold());
        checkPercentage(problems, "memoryThreshold", update.getMemoryThreshold());

        checkRate(problems, "networkInThresholdMbps", update.getNetworkInThresholdMbps());
        checkRate(problems, "networkOutThresholdMbps", update.getNetworkOutThresholdMbps());

        return problems;
    }

    private void checkInterval(List<String> problems, String name, Long value, long minimum) {
        if (value == null) {
            return;
        }
        if (!ValidationUtil.inRange(value, minimum, ProtocolConstants.MAX_INTERVAL_MS)) {
            problems.add(name + " must be between " + minimum + " and "
                    + ProtocolConstants.MAX_INTERVAL_MS + " ms, got " + value);
        }
    }

    private void checkPercentage(List<String> problems, String name, Double value) {
        if (value == null) {
            return;
        }
        if (!ValidationUtil.isFinite(value)) {
            problems.add(name + " must be a finite number");
        } else if (value <= 0.0d || value > 100.0d) {
            problems.add(name + " must be a percentage greater than 0 and at most 100, got " + value);
        }
    }

    private void checkRate(List<String> problems, String name, Double value) {
        if (value == null) {
            return;
        }
        if (!ValidationUtil.isFinite(value)) {
            problems.add(name + " must be a finite number");
        } else if (value < 0.0d) {
            problems.add(name + " must not be negative, got " + value);
        }
    }

    private void fillEffective(ConfigAckMessage ack) {
        ack.setEffectiveMetricIntervalMs(config.getMetricIntervalMs());
        ack.setEffectiveHeartbeatIntervalMs(config.getHeartbeatIntervalMs());
        ack.setEffectiveCpuThreshold(config.getCpuThreshold());
        ack.setEffectiveMemoryThreshold(config.getMemoryThreshold());
        ack.setEffectiveNetworkInThresholdMbps(config.getNetworkInThresholdMbps());
        ack.setEffectiveNetworkOutThresholdMbps(config.getNetworkOutThresholdMbps());
    }
}
