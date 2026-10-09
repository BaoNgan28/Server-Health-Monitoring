package vn.edu.hutech.group10.monitoring.common.model;

/** Categories of alert produced by the monitoring client. */
public enum AlertType {
    CPU_HIGH("CPU HIGH"),
    MEMORY_HIGH("MEMORY HIGH"),
    NETWORK_IN_HIGH("NETWORK TRAFFIC HIGH"),
    NETWORK_OUT_HIGH("NETWORK TRAFFIC HIGH"),
    AGENT_OFFLINE("SERVER OFFLINE"),
    AGENT_RECOVERED("SERVER ONLINE"),
    SECURITY_INVALID_HMAC("SECURITY INVALID HMAC"),
    SECURITY_REPLAY_PACKET("SECURITY REPLAY PACKET");

    private final String displayName;

    AlertType(String displayName) {
        this.displayName = displayName;
    }

    /** Human readable label used in e-mail subjects and in the GUI. */
    public String getDisplayName() {
        return displayName;
    }
}
