package vn.edu.hutech.group10.monitoring.common.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Fixed protocol values and the defaults every module starts from. */
public final class ProtocolConstants {

    private ProtocolConstants() {
    }

    /** Bumped whenever the canonical string or framing changes incompatibly. */
    public static final String PROTOCOL_VERSION = "1.0";

    /** All JSON on both transports is encoded UTF-8. */
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    public static final String HMAC_ALGORITHM = "HmacSHA256";

    /** UDP port the monitoring client listens on by default. */
    public static final int DEFAULT_MONITOR_UDP_PORT = 6000;

    /** TCP port an agent listens on when each agent owns its own machine. */
    public static final int DEFAULT_AGENT_TCP_PORT = 5000;

    public static final long DEFAULT_METRIC_INTERVAL_MS = 1000L;
    public static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 5000L;
    public static final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 15000L;

    /** How far a packet timestamp may drift from the receiver clock. */
    public static final long DEFAULT_TIMESTAMP_TOLERANCE_MS = 30000L;

    /** Lowest metric interval an agent will accept through CONFIG_UPDATE. */
    public static final long MIN_METRIC_INTERVAL_MS = 200L;
    /** Lowest heartbeat interval an agent will accept through CONFIG_UPDATE. */
    public static final long MIN_HEARTBEAT_INTERVAL_MS = 1000L;
    /** Upper bound for both intervals; anything larger is a configuration mistake. */
    public static final long MAX_INTERVAL_MS = 3600_000L;

    /** Receive buffer for a datagram. Telemetry JSON is well under 1 KiB. */
    public static final int MAX_UDP_PACKET_SIZE = 4096;

    /** Hard cap on a single TCP frame, so a bad length prefix cannot exhaust memory. */
    public static final int MAX_TCP_FRAME_SIZE = 1024 * 1024;

    /** Default socket connect / read timeout for TCP control operations. */
    public static final int DEFAULT_TCP_TIMEOUT_MS = 5000;

}
