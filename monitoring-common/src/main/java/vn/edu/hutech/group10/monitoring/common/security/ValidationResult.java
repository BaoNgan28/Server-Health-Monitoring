package vn.edu.hutech.group10.monitoring.common.security;

/** Outcome of validating one inbound UDP packet. */
public class ValidationResult {

    /** Why a packet was accepted or dropped. */
    public enum Reason {
        ACCEPTED,
        /** The packet is not valid JSON, or a required field is missing or out of range. */
        MALFORMED,
        /** No agent with that id is registered, so no secret exists to check against. */
        UNKNOWN_AGENT,
        /** The packet was produced by an incompatible protocol version. */
        UNSUPPORTED_VERSION,
        /** The signature did not match the canonical string. */
        INVALID_HMAC,
        /** The timestamp is further from the receiver clock than the tolerance allows. */
        TIMESTAMP_OUT_OF_TOLERANCE,
        /** The sequence number did not move forward, so this is a replayed packet. */
        REPLAY
    }

    private final Reason reason;
    private final String detail;

    private ValidationResult(Reason reason, String detail) {
        this.reason = reason;
        this.detail = detail;
    }

    public static ValidationResult accepted() {
        return new ValidationResult(Reason.ACCEPTED, "accepted");
    }

    public static ValidationResult rejected(Reason reason, String detail) {
        return new ValidationResult(reason, detail);
    }

    public boolean isAccepted() {
        return reason == Reason.ACCEPTED;
    }

    public Reason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return reason + (detail == null ? "" : " (" + detail + ")");
    }
}
