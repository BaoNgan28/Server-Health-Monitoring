package vn.edu.hutech.group10.monitoring.common.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.hutech.group10.monitoring.common.TestPackets;
import vn.edu.hutech.group10.monitoring.common.model.ConfigAckMessage;
import vn.edu.hutech.group10.monitoring.common.model.ConfigUpdateMessage;
import vn.edu.hutech.group10.monitoring.common.model.TelemetryPacket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMessageCodecTest {

    private JsonMessageCodec codec;

    @BeforeEach
    void setUp() {
        codec = new JsonMessageCodec();
    }

    @Test
    @DisplayName("A telemetry packet survives a round trip unchanged")
    void telemetryRoundTrip() {
        TelemetryPacket original = TestPackets.signedTelemetry(11L, 1_700_000_000_000L);

        TelemetryPacket decoded = codec.fromJson(codec.toJson(original), TelemetryPacket.class);

        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("Bytes round trip through UTF-8, including non-ASCII hostnames")
    void byteRoundTripKeepsUtf8() {
        TelemetryPacket original = TestPackets.telemetry(1L, 1000L);
        original.setHostname("MAY-CHU-HA-NOI");

        byte[] encoded = codec.toBytes(original);
        TelemetryPacket decoded = codec.fromBytes(encoded, TelemetryPacket.class);

        assertEquals("MAY-CHU-HA-NOI", decoded.getHostname());
    }

    @Test
    @DisplayName("Partial-buffer decoding reads only the requested range")
    void decodesFromBufferSlice() {
        byte[] json = codec.toBytes(TestPackets.telemetry(4L, 2000L));
        byte[] buffer = new byte[json.length + 64];
        System.arraycopy(json, 0, buffer, 0, json.length);

        TelemetryPacket decoded = codec.fromBytes(buffer, 0, json.length, TelemetryPacket.class);

        assertEquals(4L, decoded.getSequence());
    }

    @Test
    @DisplayName("Absent optional settings decode as null, not as zero")
    void partialConfigUpdateKeepsNulls() {
        String json = "{\"type\":\"CONFIG_UPDATE\",\"requestId\":\"r1\",\"cpuThreshold\":5.0}";

        ConfigUpdateMessage message = codec.fromJson(json, ConfigUpdateMessage.class);

        assertAll(
                () -> assertEquals("r1", message.getRequestId()),
                () -> assertEquals(5.0d, message.getCpuThreshold().doubleValue()),
                () -> assertNull(message.getMetricIntervalMs(), "unset means leave unchanged"),
                () -> assertNull(message.getMemoryThreshold()));
    }

    @Test
    @DisplayName("peekType reads the discriminator without a full parse")
    void peekTypeReadsDiscriminator() {
        assertAll(
                () -> assertEquals(MessageType.TELEMETRY,
                        codec.peekType(codec.toJson(TestPackets.telemetry(1L, 1L)))),
                () -> assertEquals(MessageType.CONFIG_ACK,
                        codec.peekType("{\"type\":\"CONFIG_ACK\",\"status\":\"SUCCESS\"}")));
    }

    @Test
    @DisplayName("peekType returns null instead of throwing on junk input")
    void peekTypeSurvivesJunk() {
        assertAll(
                () -> assertNull(codec.peekType("not json at all")),
                () -> assertNull(codec.peekType("{\"type\":\"NO_SUCH_TYPE\"}")),
                () -> assertNull(codec.peekType("{\"nope\":1}")),
                () -> assertNull(codec.peekType("[1,2,3]")),
                () -> assertNull(codec.peekType("")),
                () -> assertNull(codec.peekType((String) null)));
    }

    @Test
    @DisplayName("Malformed JSON raises ProtocolException rather than a raw Gson error")
    void malformedJsonIsWrapped() {
        assertAll(
                () -> assertThrows(ProtocolException.class,
                        () -> codec.fromJson("{ this is broken", TelemetryPacket.class)),
                () -> assertThrows(ProtocolException.class,
                        () -> codec.fromJson("", TelemetryPacket.class)),
                () -> assertThrows(ProtocolException.class, () -> codec.toJson(null)));
    }

    @Test
    @DisplayName("TCP frames survive a round trip and carry their exact length")
    void framingRoundTrip() throws IOException {
        ConfigAckMessage ack = new ConfigAckMessage();
        ack.setType(MessageType.CONFIG_ACK.wireName());
        ack.setRequestId("req-1");
        ack.setStatus(ConfigAckMessage.STATUS_SUCCESS);
        ack.setMessage("Configuration applied");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        String json = codec.toJson(ack);
        MessageFraming.writeFrame(out, json);

        byte[] wire = buffer.toByteArray();
        assertEquals(4 + json.getBytes(StandardCharsets.UTF_8).length, wire.length,
                "a frame is a 4-byte length prefix plus the payload");

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(wire));
        ConfigAckMessage decoded = codec.fromJson(MessageFraming.readFrame(in), ConfigAckMessage.class);

        assertAll(
                () -> assertTrue(decoded.isSuccess()),
                () -> assertEquals("req-1", decoded.getRequestId()));
    }

    @Test
    @DisplayName("Several frames written back to back are read one at a time")
    void framingSeparatesBackToBackMessages() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        MessageFraming.writeFrame(out, "{\"type\":\"PING\"}");
        MessageFraming.writeFrame(out, "{\"type\":\"PONG\"}");

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));

        assertAll(
                () -> assertEquals("{\"type\":\"PING\"}", MessageFraming.readFrame(in)),
                () -> assertEquals("{\"type\":\"PONG\"}", MessageFraming.readFrame(in)),
                () -> assertNull(MessageFraming.readFrame(in), "clean end of stream"));
    }

    @Test
    @DisplayName("A payload containing newlines is framed correctly, which readLine could not do")
    void framingHandlesEmbeddedNewlines() throws IOException {
        String payload = "{\"type\":\"CONFIG_ACK\",\"message\":\"line one\\nline two\"}";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MessageFraming.writeFrame(new DataOutputStream(buffer), payload);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));

        assertEquals(payload, MessageFraming.readFrame(in));
    }

    @Test
    @DisplayName("A hostile length prefix is refused instead of allocating a huge buffer")
    void framingRefusesAbsurdLength() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new DataOutputStream(buffer).writeInt(Integer.MAX_VALUE);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        assertThrows(ProtocolException.class, () -> MessageFraming.readFrame(in));
    }
}
