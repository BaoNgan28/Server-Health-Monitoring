package vn.edu.hutech.group10.monitoring.common.protocol;

/**
 * Every message type carried by either transport. The wire value is the
 * constant name itself, so the JSON stays readable during a demo.
 */
public enum MessageType {

    /** UDP: periodic metric sample. */
    TELEMETRY,
    /** UDP: periodic liveness signal. */
    HEARTBEAT,

    /** TCP: connectivity probe from the monitoring client. */
    PING,
    /** TCP: agent reply to {@link #PING}. */
    PONG,
    /** TCP: monitoring client asks for machine details. */
    AGENT_INFO_REQUEST,
    /** TCP: agent replies with machine details. */
    AGENT_INFO_RESPONSE,
    /** TCP: monitoring client pushes new settings. */
    CONFIG_UPDATE,
    /** TCP: agent accepts or rejects the settings. */
    CONFIG_ACK,
    /** TCP: agent could not understand the request at all. */
    ERROR;

    /** Wire representation of this type. */
    public String wireName() {
        return name();
    }

    /**
     * Parses a wire value, returning {@code null} rather than throwing when the
     * value is unknown, so that a malformed packet cannot kill a receiver loop.
     */
    public static MessageType fromWire(String value) {
        if (value == null) {
            return null;
        }
        for (MessageType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
