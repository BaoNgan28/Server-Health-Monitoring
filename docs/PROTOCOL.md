# Protocol

Group 10 — Server Health Monitoring System — protocol version **1.0**

Everything on both transports is UTF-8 JSON. The protocol version is compared for
exact equality, so a packet from any other version is refused.

---

## 1. Ports

| Role | Default | Local four-agent demo |
|---|---|---|
| Monitoring client, UDP listen | 6000 | 6000 |
| Agent, TCP control listen | 5000 | 5001, 5002, 5003, 5004 |

The four different TCP ports exist only because the demo puts four agents on one
machine. When each agent owns a machine they all use 5000.

---

## 2. Why two transports

**UDP for telemetry.** A metric sample is small, another one follows a second
later, and a lost one is worthless by the time it could be retransmitted. A
connection per agent would cost state and handshakes for no benefit.

**TCP for control.** A configuration change must not be silently lost, and the
agent's acknowledgement is what proves it arrived and was accepted.

---

## 3. UDP messages

### 3.1 TELEMETRY

Sent every `metric.interval.ms` (default 1000).

```json
{
  "protocolVersion": "1.0",
  "type": "TELEMETRY",
  "agentId": "AGENT-01",
  "hostname": "SRV-HANOI-01",
  "sessionId": "0f0b9a61-3a3b-4a64-9a3d-1d0c2b5f7e11",
  "timestamp": 1790668331245,
  "sequence": 417,
  "cpuUsage": 23.5,
  "memoryUsage": 61.25,
  "connectionCount": 42,
  "uptimeSeconds": 143284,
  "networkInBytesPerSecond": 12345.5,
  "networkOutBytesPerSecond": 6789.25,
  "hmac": "9f2b...64 hex characters..."
}
```

| Field | Meaning |
|---|---|
| `timestamp` | epoch milliseconds when the sample was taken |
| `sequence` | strictly increasing within one session |
| `sessionId` | one value per agent **run**; a new one after every restart |
| `cpuUsage`, `memoryUsage` | percentages, 0..100 |
| `connectionCount` | established TCP connections, v4 + v6 |
| `uptimeSeconds` | machine uptime |
| `network*BytesPerSecond` | throughput, a **rate**, not a total |
| `hmac` | HmacSHA256 over the canonical string, lower-case hex |

### 3.2 HEARTBEAT

Sent every `heartbeat.interval.ms` (default 5000). Same identity fields, no
metrics, so it stays cheap and keeps arriving even if metric collection is
struggling.

```json
{
  "protocolVersion": "1.0",
  "type": "HEARTBEAT",
  "agentId": "AGENT-01",
  "hostname": "SRV-HANOI-01",
  "sessionId": "0f0b9a61-3a3b-4a64-9a3d-1d0c2b5f7e11",
  "timestamp": 1790668336245,
  "sequence": 418,
  "hmac": "3c81...64 hex characters..."
}
```

Telemetry and heartbeats share one sequence counter per agent, because the
monitoring client keeps one replay window covering both.

A datagram is capped at 4096 bytes; the messages above are well under 1 kB.

---

## 4. Authentication

### 4.1 The canonical string

The signature is **not** computed over the raw JSON. Two encoders can order
fields or format numbers differently while describing the same sample, and the
signature would then depend on the encoder rather than on the data.

Telemetry, fields joined with `|`:

```
protocolVersion | type | agentId | hostname | sessionId | timestamp | sequence |
cpuUsage | memoryUsage | connectionCount | uptimeSeconds |
networkInBytesPerSecond | networkOutBytesPerSecond
```

Heartbeat:

```
protocolVersion | type | agentId | hostname | sessionId | timestamp | sequence
```

Rules both sides must apply identically:

* the `hmac` field is **never** part of the string it signs;
* doubles are rendered with `Locale.ROOT` and exactly four decimals, so a
  machine whose locale uses a comma decimal separator produces the same bytes as
  one that uses a dot;
* `-0.0` is normalised to `0.0000`;
* a null string becomes empty, never the text `null`;
* a non-finite double is refused rather than signed as `NaN`.

Worked example:

```
1.0|TELEMETRY|AGENT-01|SRV-HANOI-01|0f0b9a61-...|1790668331245|417|
23.5000|61.2500|42|143284|12345.5000|6789.2500
```

(shown wrapped; there are no line breaks in the real string).

### 4.2 Signing and checking

```
hmac = lowercase_hex( HmacSHA256( sharedSecret, canonicalString ) )
```

The receiver recomputes it and compares with `MessageDigest.isEqual`, which does
not leak how many leading characters matched.

Each agent has its **own** secret, so a secret that leaks cannot be used to
impersonate a different agent.

---

## 5. Validation order

A packet is checked in this order, and the first failure stops the rest:

1. **shape** — a session id is present, the sequence is not negative, the
   doubles are finite, percentages are within 0..100, counts are not negative;
2. **protocol version** — must equal `1.0`;
3. **known agent** — an unregistered `agentId` has no secret, so the packet is
   dropped before any signature is computed;
4. **signature** — HmacSHA256 over the canonical string, compared in constant
   time;
5. **timestamp** — within `packet.timestamp.tolerance.ms` (default 30 000) of the
   receiver's clock, in both directions;
6. **replay** — see below.

A packet that fails any step updates nothing: no dashboard, no `metric_raw` row,
no alert. It is counted, logged, and recorded as a security event.

---

## 6. Replay protection

Each agent numbers its packets within one session. The monitoring client keeps,
per `(agentId, sessionId)`, the highest sequence it has accepted plus a 64-bit
map of which of the previous 64 it has already seen — the shape IPsec uses.

| Case | Result |
|---|---|
| sequence newer than anything seen | accept, slide the window forward |
| sequence inside the window, bit clear | accept — it merely arrived late |
| sequence inside the window, bit set | **reject**, a repeat |
| sequence older than the window | **reject** |

A window rather than "must be larger than the last one", because UDP does not
promise order: a packet can overtake another on the network, and a receiver
spreading work across threads can finish them out of order. Demanding a strictly
rising sequence would throw away genuine packets.

**Why the window is per session.** An agent that restarts begins counting from 1
again. Against a single per-agent window that is indistinguishable from a replay,
and the restarted machine would sit there OFFLINE for no good reason. A new
session id opens a fresh window while the previous session keeps its own, so a
capture from the old session replayed into the new one still lands in the old
window, where its bit is already set, and is still rejected. The last four
sessions of an agent are remembered.

This also stops two senders interfering: the test simulator uses its own session
id, so a packet it sends with a huge sequence cannot push the real agent's window
out of range.

The residual risk of any timestamp-plus-sequence scheme is a capture replayed
within the timestamp tolerance to a receiver that never saw the original. The
30-second tolerance is what bounds it.

---

## 7. TCP framing

A frame is:

```
+--------------------------+------------------------------+
| 4 bytes, big-endian      | that many bytes of UTF-8 JSON|
| payload length           |                              |
+--------------------------+------------------------------+
```

TCP is a byte stream with no message boundaries. A single `read()` can return
half a message, or two messages at once, so the reader uses
`DataInputStream.readFully` to keep reading until the whole declared payload has
arrived.

Line-oriented reading is deliberately **not** used: a JSON payload is not
guaranteed to be newline free, and an error message containing a line break would
split one message into two and desynchronise the stream for ever.

A length above 1 MiB is refused rather than allocated, so a hostile or corrupted
prefix cannot exhaust memory. A clean close between frames reads as end of
stream, not as an error.

---

## 8. TCP messages

### 8.1 PING / PONG

```json
{"type":"PING","timestamp":1790668331245}
```
```json
{"type":"PONG","agentId":"AGENT-01","timestamp":1790668331248}
```

### 8.2 AGENT_INFO_REQUEST / AGENT_INFO_RESPONSE

```json
{"type":"AGENT_INFO_REQUEST","timestamp":1790668331245}
```
```json
{
  "type": "AGENT_INFO_RESPONSE",
  "agentId": "AGENT-01",
  "agentName": "LOCAL-AGENT-01",
  "hostname": "SRV-HANOI-01",
  "osName": "Windows",
  "osVersion": "11 build 26200",
  "cpuModel": "Intel(R) Core(TM) i7",
  "cpuLogicalCores": 12,
  "totalMemoryBytes": 17098688512,
  "uptimeSeconds": 143284,
  "metricIntervalMs": 1000,
  "heartbeatIntervalMs": 5000,
  "agentVersion": "1.0.0"
}
```

### 8.3 CONFIG_UPDATE / CONFIG_ACK

```json
{
  "type": "CONFIG_UPDATE",
  "requestId": "0a7c4f2e-9d13-4a80-9d66-7f4c1f2e33aa",
  "metricIntervalMs": 2000,
  "heartbeatIntervalMs": null,
  "cpuThreshold": 5.0,
  "memoryThreshold": null,
  "networkInThresholdMbps": null,
  "networkOutThresholdMbps": null
}
```

**A `null` field means "leave this setting alone."** That is why every setting is
a boxed type: a monitoring client can push one threshold without restating the
rest.

The agent validates every field **before applying any of them**, so a message
with one bad value leaves the agent exactly as it was rather than half updated:

| Setting | Accepted range |
|---|---|
| `metricIntervalMs` | 200 … 3 600 000 |
| `heartbeatIntervalMs` | 1 000 … 3 600 000 |
| `cpuThreshold`, `memoryThreshold` | greater than 0, at most 100 |
| `networkInThresholdMbps`, `networkOutThresholdMbps` | finite, not negative |

Success:

```json
{
  "type": "CONFIG_ACK",
  "requestId": "0a7c4f2e-9d13-4a80-9d66-7f4c1f2e33aa",
  "status": "SUCCESS",
  "message": "Applied metricIntervalMs=2000, cpuThreshold=5.0",
  "effectiveMetricIntervalMs": 2000,
  "effectiveHeartbeatIntervalMs": 5000,
  "effectiveCpuThreshold": 5.0,
  "effectiveMemoryThreshold": 85.0,
  "effectiveNetworkInThresholdMbps": 50.0,
  "effectiveNetworkOutThresholdMbps": 50.0
}
```

Rejection:

```json
{
  "type": "CONFIG_ACK",
  "requestId": "0a7c4f2e-9d13-4a80-9d66-7f4c1f2e33aa",
  "status": "ERROR",
  "message": "metricIntervalMs must be between 200 and 3600000 ms, got 5",
  "effectiveMetricIntervalMs": 1000,
  "...": "unchanged values"
}
```

The acknowledgement always reports what is **actually in force** on the agent,
whether the update was applied or refused. The monitoring client writes the new
threshold to its own database only after a `SUCCESS`, so the screen can never
show a setting the agent rejected.

### 8.4 ERROR

Sent when a request cannot be understood at all.

```json
{"type":"ERROR","agentId":"AGENT-01","timestamp":1790668331248,
 "message":"Unrecognised or malformed control message"}
```

---

## 9. Liveness

| Setting | Default |
|---|---|
| telemetry interval | 1 000 ms |
| heartbeat interval | 5 000 ms |
| heartbeat timeout | 15 000 ms |
| liveness sweep | every 2 000 ms |

Any accepted packet — telemetry or heartbeat — refreshes the agent's last-seen
time. With a 5-second heartbeat and a 15-second timeout, two heartbeats can be
lost before a healthy agent is declared down.

An agent with nothing received for longer than the timeout becomes `OFFLINE` and
gets exactly one `AGENT_OFFLINE` event, however often the sweep runs. The next
valid packet returns it to `ONLINE` and records `AGENT_RECOVERED`. An agent that
has never been heard from stays `UNKNOWN` rather than being reported as an
outage.
