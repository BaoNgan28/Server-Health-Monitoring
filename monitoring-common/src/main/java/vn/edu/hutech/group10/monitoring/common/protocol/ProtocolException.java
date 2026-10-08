package vn.edu.hutech.group10.monitoring.common.protocol;

/** Raised when a message cannot be encoded, decoded or framed. */
public class ProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
