package vn.edu.hutech.group10.monitoring.common.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Length-prefixed framing for the TCP control channel.
 *
 * <p>Each frame is a 4-byte big-endian payload length followed by exactly that
 * many UTF-8 JSON bytes. TCP is a byte stream with no message boundaries, so a
 * single {@code read()} may return a partial frame or several frames at once;
 * {@link DataInputStream#readFully(byte[])} is used to keep reading until the
 * declared payload has arrived. Line-oriented reading is deliberately avoided:
 * JSON payloads are not guaranteed to be newline free.
 */
public final class MessageFraming {

    private MessageFraming() {
    }

    /** Writes one frame and flushes it. */
    public static void writeFrame(DataOutputStream out, String json) throws IOException {
        if (json == null) {
            throw new ProtocolException("Cannot frame a null payload");
        }
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        if (payload.length > ProtocolConstants.MAX_TCP_FRAME_SIZE) {
            throw new ProtocolException("Frame of " + payload.length
                    + " bytes exceeds the limit of " + ProtocolConstants.MAX_TCP_FRAME_SIZE);
        }
        synchronized (out) {
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        }
    }

    /**
     * Reads exactly one frame, blocking until the whole payload has arrived.
     *
     * @return the decoded JSON string, or {@code null} when the peer closed the
     *         connection cleanly between frames
     * @throws IOException          on a transport failure or a truncated frame
     * @throws ProtocolException    when the length prefix is not usable
     */
    public static String readFrame(DataInputStream in) throws IOException {
        int length;
        try {
            length = in.readInt();
        } catch (EOFException eof) {
            return null;
        }
        if (length < 0 || length > ProtocolConstants.MAX_TCP_FRAME_SIZE) {
            throw new ProtocolException("Rejected frame length " + length);
        }
        if (length == 0) {
            return "";
        }
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new String(payload, StandardCharsets.UTF_8);
    }
}
