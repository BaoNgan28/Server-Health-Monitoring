package vn.edu.hutech.group10.monitoring.common.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.nio.charset.StandardCharsets;

/**
 * Turns protocol messages into compact UTF-8 JSON and back.
 *
 * <p>The instance is stateless and the underlying {@link Gson} is thread safe,
 * so a single codec is shared by the UDP receiver, the TCP sessions and the
 * schedulers.
 */
public class JsonMessageCodec {

    private static final String TYPE_FIELD = "type";

    private final Gson gson;

    public JsonMessageCodec() {
        this.gson = new GsonBuilder()
                .serializeNulls()
                .disableHtmlEscaping()
                .create();
    }

    /** Serialises {@code message} to JSON. */
    public String toJson(Object message) {
        if (message == null) {
            throw new ProtocolException("Cannot encode a null message");
        }
        try {
            return gson.toJson(message);
        } catch (RuntimeException e) {
            throw new ProtocolException("Failed to encode " + message.getClass().getSimpleName(), e);
        }
    }

    /** Serialises {@code message} to UTF-8 JSON bytes, ready for a datagram or a TCP frame. */
    public byte[] toBytes(Object message) {
        return toJson(message).getBytes(StandardCharsets.UTF_8);
    }

    /** Parses {@code json} into {@code type}. */
    public <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isEmpty()) {
            throw new ProtocolException("Cannot decode an empty payload");
        }
        try {
            T value = gson.fromJson(json, type);
            if (value == null) {
                throw new ProtocolException("Payload decoded to null for " + type.getSimpleName());
            }
            return value;
        } catch (JsonSyntaxException e) {
            throw new ProtocolException("Malformed JSON for " + type.getSimpleName(), e);
        }
    }

    /** Parses UTF-8 JSON bytes into {@code type}. */
    public <T> T fromBytes(byte[] payload, int offset, int length, Class<T> type) {
        if (payload == null || length <= 0) {
            throw new ProtocolException("Cannot decode an empty payload");
        }
        return fromJson(new String(payload, offset, length, StandardCharsets.UTF_8), type);
    }

    /** Parses UTF-8 JSON bytes into {@code type}. */
    public <T> T fromBytes(byte[] payload, Class<T> type) {
        if (payload == null) {
            throw new ProtocolException("Cannot decode a null payload");
        }
        return fromBytes(payload, 0, payload.length, type);
    }

    /**
     * Reads only the {@code type} discriminator so a receiver can pick the right
     * class before doing the full parse. Returns {@code null} when the payload is
     * not a JSON object or carries no usable type.
     */
    public MessageType peekType(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            JsonElement typeElement = object.get(TYPE_FIELD);
            if (typeElement == null || !typeElement.isJsonPrimitive()) {
                return null;
            }
            return MessageType.fromWire(typeElement.getAsString());
        } catch (JsonSyntaxException | IllegalStateException | UnsupportedOperationException e) {
            return null;
        }
    }

    /** Reads only the {@code type} discriminator from UTF-8 JSON bytes. */
    public MessageType peekType(byte[] payload, int offset, int length) {
        if (payload == null || length <= 0) {
            return null;
        }
        return peekType(new String(payload, offset, length, StandardCharsets.UTF_8));
    }
}
