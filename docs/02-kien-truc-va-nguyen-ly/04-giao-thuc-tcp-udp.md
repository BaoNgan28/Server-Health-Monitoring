# 04 - Giao thức TCP và UDP

Hệ thống giám sát server từ xa — Group 10 — HUTECH, Lập trình mạng máy tính
Giao thức phiên bản **1.0** (`ProtocolConstants.PROTOCOL_VERSION`)

Mọi dữ liệu trên cả hai kênh vận chuyển (transport) đều là JSON UTF-8. Phiên bản
giao thức được so bằng **so sánh bằng nhau tuyệt đối** (`equals`), nên một gói
thuộc phiên bản khác sẽ bị từ chối ngay.

Tài liệu này mô tả wire format (định dạng trên đường dây) theo **code**, tham chiếu
chính: `monitoring-common/.../protocol/*`, `monitoring-common/.../model/*`,
`agent-app/.../network/*`, `monitor-app/.../network/*`.

---

## 1. Tổng quan hai kênh

```
   AGENT (máy được giám sát)                    MONITORING CLIENT (máy A)
   +----------------------------+               +----------------------------+
   | TelemetryScheduler         |   UDP 6000    | UdpTelemetryReceiver       |
   | HeartbeatScheduler         | ============> |   -> TelemetryService      |
   |   (ký HMAC-SHA256)         | TELEMETRY     |   -> PacketValidator       |
   |                            | HEARTBEAT     |   -> DB / Alert / GUI      |
   | TcpControlServer           |               |                            |
   |   TcpClientSessionHandler  | <===========  | AgentTcpConnection(Manager)|
   |                            |   TCP 5000    |   (khung 4 byte + JSON)    |
   +----------------------------+               +----------------------------+
```

| Kêh | Cổng mặc định | Vai trò |
|---|---|---|
| UDP (nhận) | `6000` — `ProtocolConstants.DEFAULT_MONITOR_UDP_PORT`, cấu hình `monitor.udp.port` | Nhận telemetry (dữ liệu đo) và heartbeat (tín hiệu giữ sống) |
| TCP (agent nghe) | `5000` — `ProtocolConstants.DEFAULT_AGENT_TCP_PORT` | Kênh điều khiển: PING, AGENT_INFO, CONFIG_UPDATE |

Bốn port TCP 5001–5004 chỉ tồn tại vì demo đặt 4 agent trên **một** máy; khi mỗi
agent ở một máy riêng thì tất cả dùng 5000.

**Vì sao dùng hai kênh vận chuyển khác nhau:**

- **UDP cho telemetry**: một mẫu metric (chỉ số đo) rất nhỏ, giây sau đã có mẫu
  thay thế; một gói mất đi vô giá trị khi đã có thể gửi lại. Mở kết nối cho từng
  agent sẽ tốn trạng thái và bắt tay mà không mang lại lợi ích gì
  (`UdpTelemetrySender` javadoc).
- **TCP cho điều khiển**: một thay đổi cấu hình không được mất âm thầm, và chính
  `CONFIG_ACK` của agent là bằng chứng nó đã đến và được chấp nhận.

---

## 2. Kênh TCP: khung tin (framing)

### 2.1 Cấu trúc khung

```
   +---------------------------+------------------------------+
   | 4 byte, big-endian        | đúng số byte đó là JSON      |
   | độ dài payload            | UTF-8                        |
   +---------------------------+------------------------------+
```

Code: `monitoring-common/.../protocol/MessageFraming.java`.

- `writeFrame(DataOutputStream, String)`: chuyển JSON sang bytes UTF-8, kiểm tra
  giới hạn, rồi `out.writeInt(payload.length)` (ghi số nguyên 32-bit theo thứ tự
  byte **big-endian**, tức byte cao nhất đứng trước — đây là thứ tự mặc định của
  `DataOutputStream`) + `out.write(payload)` + `flush()`. Toàn bộ được bọc trong
  `synchronized (out)` để hai thread không đan xen (interleave) hai khung.
- `readFrame(DataInputStream)`: `in.readInt()` để lấy độ dài, `new byte[length]`,
  rồi `in.readFully(payload)` — đọc **liên tục cho đủ** số byte đã khai báo.

### 2.2 Vì sao không đọc theo dòng (line-oriented)?

TCP là một **byte stream** (dòng byte) không có ranh giới tin nhắn. Một lần
`read()` có thể trả về nửa message, hoặc hai message cùng lúc. Code tránh đọc
theo dòng một cách có chủ đích vì:

> Một payload JSON **không được đảm bảo** không chứa ký tự xuống dòng. Một message
> lỗi chứa xuống dòng sẽ tách message đó thành hai và làm **mất đồng bộ vĩnh viễn**
> (desynchronise the stream for ever). — `MessageFraming` javadoc; README mục 11.

Đọc theo dòng chỉ hoạt động khi giao thức ép mỗi message nằm trên một dòng; JSON
không có ràng buộc đó nên phải dùng length-prefix (tiền tố độ dài).

### 2.3 Giới hạn kích thước khung

| Hằng | Giá trị | Nguồn |
|---|---|---|
| `ProtocolConstants.MAX_TCP_FRAME_SIZE` | `1024 * 1024` = **1 MiB** | `ProtocolConstants.java:44` |

- `readFrame`: `length < 0 || length > MAX_TCP_FRAME_SIZE` → ném
  `ProtocolException("Rejected frame length ...")`. **Từ chối thay vì cấp phát
  bộ nhớ**, để một tiền tố độ dài độc hại hoặc hỏng không thể làm cạn kiệt RAM.
- `writeFrame`: payload vượt giới hạn → `ProtocolException`.
- `length == 0` → trả về chuỗi rỗng `""`.
- `EOFException` ngay khi đọc `readInt()` → trả về `null` nghĩa là **đối phương
  đóng kết nối sạch giữa hai khung**, không phải lỗi.
- Trong phiên, agent đặt `socket.setSoTimeout(120_000)` (`TcpClientSessionHandler.SESSION_READ_TIMEOUT_MS`)
  — một phiên im lặng 120 giây sẽ bị đóng.
- Bên phía monitor, `tcp.timeout.ms = 5000` (`config/monitor.properties`) /
  `ProtocolConstants.DEFAULT_TCP_TIMEOUT_MS = 5000` là thời gian connect và read
  timeout.

### 2.4 Mã hóa JSON

`JsonMessageCodec` dùng Gson với hai tùy chọn:

```java
new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
```

- `serializeNulls()`: trường `null` **được ghi ra** (quan trọng với
  `CONFIG_UPDATE` — xem mục 3.4).
- `disableHtmlEscaping()`: không chuyển `&`, `<`, `>` thành mã HTML.
- `peekType(String)` chỉ đọc trường `type` trước, để receiver chọn đúng lớp Java
  rồi mới parse toàn bộ; trả về `null` nếu payload không phải JSON object hoặc
  `type` không nhận diện được — một gói hỏng không giết được vòng lặp receiver.

### 2.5 Các loại message

Enum `MessageType` (giá trị trên đường dây chính là **tên hằng**):

| Giá trị | Kênh | Hướng |
|---|---|---|
| `TELEMETRY` | UDP | agent → monitor |
| `HEARTBEAT` | UDP | agent → monitor |
| `PING` | TCP | monitor → agent |
| `PONG` | TCP | agent → monitor |
| `AGENT_INFO_REQUEST` | TCP | monitor → agent |
| `AGENT_INFO_RESPONSE` | TCP | agent → monitor |
| `CONFIG_UPDATE` | TCP | monitor → agent |
| `CONFIG_ACK` | TCP | agent → monitor |
| `ERROR` | TCP | agent → monitor |

Trên UDP chỉ chấp nhận `TELEMETRY` và `HEARTBEAT`; các loại khác bị từ chối với
thông báo `"message type ... is not accepted over UDP"` (`TelemetryService`).
Bên agent chỉ trả lời `PING`, `AGENT_INFO_REQUEST`, `CONFIG_UPDATE`; mọi loại khác
trả về `ERROR`.

---

## 3. Các message TCP

### 3.1 PING / PONG

Monitor gửi (xây trong `AgentTcpConnection.ping()`):

```json
{"type":"PING","timestamp":1790668331245}
```

| Field | Kiểu | Nghĩa |
|---|---|---|
| `type` | String | `"PING"` |
| `timestamp` | long | mốc thời gian epoch **ms** khi monitor gửi yêu cầu |

Agent trả lời (`TcpClientSessionHandler.simple(MessageType.PONG)`):

```json
{"type":"PONG","agentId":"AGENT-01","timestamp":1790668331248}
```

| Field | Kiểu | Nghĩa |
|---|---|---|
| `type` | String | `"PONG"` |
| `agentId` | String | định danh agent trả lời (lấy từ `AgentConfig`) |
| `timestamp` | long | epoch ms tại **lúc agent trả lời** — giá trị này do agent tự sinh, **không** echo (nhất lại) timestamp của yêu cầu |

Monitor coi kênh sống khi `peekType(response) == PONG`.

### 3.2 AGENT_INFO_REQUEST / AGENT_INFO_RESPONSE

Yêu cầu:

```json
{"type":"AGENT_INFO_REQUEST","timestamp":1790668331245}
```

Câu trả lời — dựng bởi `TcpClientSessionHandler.buildAgentInfo()` (phần
hệ thống do `SystemMetricsCollector.describe(info)` điền):

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

| Field | Kiểu | Nghĩa |
|---|---|---|
| `type` | String | `"AGENT_INFO_RESPONSE"` |
| `agentId` | String | định danh agent |
| `agentName` | String | tên hiển thị (`agent.name`) |
| `hostname` | String | tên máy chủ (hostname) |
| `osName` | String | tên hệ điều hành |
| `osVersion` | String | phiên bản hệ điều hành |
| `cpuModel` | String | mô hình CPU |
| `cpuLogicalCores` | int | số lõi CPU logic |
| `totalMemoryBytes` | long | tổng dung lượng RAM (byte) |
| `uptimeSeconds` | long | thời gian máy đã chạy (giây) |
| `metricIntervalMs` | long | chu kỳ gửi telemetry hiện hành (ms) |
| `heartbeatIntervalMs` | long | chu kỳ gửi heartbeat hiện hành (ms) |
| `agentVersion` | String | phiên bản agent |

Vì codec bật `serializeNulls`, một trường không có giá trị sẽ xuất hiện dạng
`"field": null` thay vì bị lược bỏ.

### 3.3 Nguyên tắc "một yêu cầu — một đáp trả"

`AgentTcpConnection.exchange(request)` làm đúng một việc: ghi **một** khung, đọc
**một** khung. Không pipelining (gửi dồn nhiều yêu cầu), không multiplex (ghép
nhiều phiên trên một kết nối). `requestId` là UUID do monitor sinh
(`UUID.randomUUID().toString()`) và agent nhất lại trong `CONFIG_ACK`, để câu
trả lời gắn được với yêu cầu gây ra nó.

### 3.4 CONFIG_UPDATE / CONFIG_ACK

Yêu cầu — lớp `ConfigUpdateMessage`, **mọi cài đặt đều là kiểu bọc**
(`Long`, `Double`) nên `null` có nghĩa là **"không đụng vào cài đặt này"**:

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

| Field | Kiểu | Nghĩa |
|---|---|---|
| `type` | String | `"CONFIG_UPDATE"` |
| `requestId` | String | UUID nhận diện yêu cầu; agent nhất lại y nguyên |
| `metricIntervalMs` | Long | chu kỳ telemetry (ms), `null` = giữ nguyên |
| `heartbeatIntervalMs` | Long | chu kỳ heartbeat (ms), `null` = giữ nguyên |
| `cpuThreshold` | Double | ngưỡng CPU (%), `null` = giữ nguyên |
| `memoryThreshold` | Double | ngưỡng RAM (%), `null` = giữ nguyên |
| `networkInThresholdMbps` | Double | ngưỡng mạng vào (Mbps), `null` = giữ nguyên |
| `networkOutThresholdMbps` | Double | ngưỡng mạng ra (Mbps), `null` = giữ nguyên |

Đây là lý do mỗi setting đều là kiểu bọc: monitor có thể đẩy **một** ngưỡng mà
không cần nhắc lại phần còn lại.

**Agent kiểm tra TẤT CẢ các trường TRƯỚC KHI áp dụng bất kỳ trường nào**
(`ConfigUpdateService.validate` → nếu có lỗi thì trả `ERROR` và không đổi gì),
nên một message có một giá trị xấu để nguyên trạng thái agent thay vì cập nhật
nửa chừng:

| Cài đặt | Khoảng chấp nhận | Nguồn code |
|---|---|---|
| `metricIntervalMs` | 200 … 3 600 000 | `MIN_METRIC_INTERVAL_MS=200`, `MAX_INTERVAL_MS=3600000` |
| `heartbeatIntervalMs` | 1 000 … 3 600 000 | `MIN_HEARTBEAT_INTERVAL_MS=1000`, `MAX_INTERVAL_MS=3600000` |
| `cpuThreshold`, `memoryThreshold` | hữu hạn, `> 0` và `<= 100` | `checkPercentage` |
| `networkInThresholdMbps`, `networkOutThresholdMbps` | hữu hạn, `>= 0` | `checkRate` |

Thành công:

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

Từ chối:

```json
{
  "type": "CONFIG_ACK",
  "requestId": "0a7c4f2e-9d13-4a80-9d66-7f4c1f2e33aa",
  "status": "ERROR",
  "message": "metricIntervalMs must be between 200 and 3600000 ms, got 5",
  "effectiveMetricIntervalMs": 1000,
  "effectiveHeartbeatIntervalMs": 5000,
  "effectiveCpuThreshold": 85.0,
  "effectiveMemoryThreshold": 85.0,
  "effectiveNetworkInThresholdMbps": 50.0,
  "effectiveNetworkOutThresholdMbps": 50.0
}
```

| Field | Kiểu | Nghĩa |
|---|---|---|
| `status` | String | `"SUCCESS"` (`ConfigAckMessage.STATUS_SUCCESS`) hoặc `"ERROR"` (`STATUS_ERROR`) |
| `message` | String | mô tả đã áp dụng gì hoặc từ chối vì sao; nhiều lỗi nối bằng `"; "` |
| `effective*` | long / double | **các giá trị thực sự đang có hiệu lực trên agent**, luôn được điền (`fillEffective`) bất kể SUCCESS hay ERROR |

Phía monitor **chỉ ghi ngưỡng mới vào database sau khi nhận `SUCCESS`**
(`AgentManagementService.pushConfiguration` theo 09-kien-truc-goc.md mục 4), nên màn
hình không bao giờ hiển thị một cài đặt đã bị agent từ chối.

Nếu update không có thay đổi nào (tất cả field `null`) thì vẫn là `SUCCESS` với
`message = "No change requested; current configuration kept"`.

### 3.5 ERROR

Gửi khi request không thể hiểu được:

```json
{"type":"ERROR","agentId":"AGENT-01","timestamp":1790668331248,"message":"Unrecognised or malformed control message"}
```

Các giá trị `message` trong code (`TcpClientSessionHandler`):

| Tình huống | `message` |
|---|---|
| `peekType` trả `null` | `Unrecognised or malformed control message` |
| TCP nhận được loại agent không xử lý | `Message type <TYPE> is not accepted by an agent` |
| JSON hỏng cho một loại đã biết | `Malformed <TYPE> request` |
| Lỗi runtime khi xử lý | `Agent failed to handle <TYPE>` |

---

## 4. Kênh UDP: gói telemetry

### 4.1 Cấu trúc packet TELEMETRY

Gửi mỗi `metric.interval.ms` (mặc định **1000 ms** — `ProtocolConstants.DEFAULT_METRIC_INTERVAL_MS`;
file `config/agent01.properties` đặt `metric.interval.ms=1000`). Timer của
`TelemetryScheduler` chạy với delay ban đầu **0** nên tick đầu tiên xảy ra ngay
khi agent khởi động.

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
  "hmac": "9f2b...64 ký tự hex..."
}
```

| Field | Kiểu | Nghĩa |
|---|---|---|
| `protocolVersion` | String | `"1.0"`, phải khớp tuyệt đối |
| `type` | String | `"TELEMETRY"` |
| `agentId` | String | định danh agent — dùng để tra secret khi xác thực |
| `hostname` | String | tên máy gửi |
| `sessionId` | String | **một giá trị cho mỗi lần agent chạy** (`UUID.randomUUID()` sinh trong `AgentRuntime`), sinh lại sau mỗi lần khởi động lại |
| `timestamp` | long | epoch **ms** lúc mẫu được lấy |
| `sequence` | long | số thứ tự tăng liên tục **trong một session** |
| `cpuUsage` | double | % CPU, 0..100 |
| `memoryUsage` | double | % RAM, 0..100 |
| `connectionCount` | int | số kết nối TCP đã thiết lập (v4 + v6) |
| `uptimeSeconds` | long | thời gian máy đã chạy (giây) |
| `networkInBytesPerSecond` | double | lưu lượng vào — một **tốc độ** (rate), không phải tổng cộng |
| `networkOutBytesPerSecond` | double | lưu lượng ra (rate) |
| `hmac` | String | chữ ký HmacSHA256 trên chuỗi canonical, hex **chữ thường**, 64 ký tự |

### 4.2 Gói heartbeat

Gửi mỗi `heartbeat.interval.ms` (mặc định **5000 ms** —
`ProtocolConstants.DEFAULT_HEARTBEAT_INTERVAL_MS`). `HeartbeatScheduler` dùng
delay ban đầu = interval, nên gói đầu tiên đến sau một chu kỳ.

```json
{
  "protocolVersion": "1.0",
  "type": "HEARTBEAT",
  "agentId": "AGENT-01",
  "hostname": "SRV-HANOI-01",
  "sessionId": "0f0b9a61-3a3b-4a64-9a3d-1d0c2b5f7e11",
  "timestamp": 1790668336245,
  "sequence": 418,
  "hmac": "3c81...64 ký tự hex..."
}
```

Cùng các trường định danh, **không có metric** — nên gói nhỏ và rẻ, và vẫn đến
được ngay cả khi việc thu thập metric đang gặp khó khăn. Nhờ vậy monitor phân
biệt được "không có metric" với "máy đã chết" (`HeartbeatScheduler` javadoc).

**Telemetry và heartbeat dùng chung một bộ đếm `sequence`** (cùng một
`AtomicLong` trong `AgentRuntime` được truyền vào cả hai scheduler), vì monitor
giữ **một** cửa sổ chống replay duy nhất bao trùm cả hai loại gói.

### 4.3 Giới hạn kích thước datagram

| Hằng | Giá trị | Nguồn |
|---|---|---|
| `ProtocolConstants.MAX_UDP_PACKET_SIZE` | `4096` byte | `ProtocolConstants.java:41` |

- **Bên gửi**: `UdpTelemetrySender.send()` từ chối gửi nếu payload > 4096
  (`"Refusing to send an oversized datagram of N bytes"`).
- **Bên nhận**: buffer `DatagramSocket` cũng được cấp đúng 4096 byte
  (`UdpTelemetryReceiver.receiveLoop`), cộng thêm `setReceiveBufferSize(1 MiB)`
  ở mức socket để giảm mất gói khi có burst (đợt bùng phát).
- Các message trong tài liệu 10-giao-thuc-goc.md đều dưới 1 kB, nên 4096 là dư dả.

### 4.4 Luồng nhận phía monitor

```
  DatagramSocket.receive()          thread "udp-receiver" — chỉ receive + copy
        |
        v
  copy bytes, chọn worker theo sender   cùng sender -> cùng worker, giữ thứ tự
        |
        v
  JSON -> TelemetryPacket / HeartbeatPacket    thread "udp-worker-0..3"
        |
        v
  PacketValidator.validate
```

- **4 worker** (`WORKER_COUNT = 4`), mỗi worker **1 thread** với hàng đợi riêng
  `ArrayBlockingQueue(QUEUE_CAPACITY = 2000)`.
- Chọn worker bằng `Math.floorMod(source.hashCode(), WORKER_COUNT)` — cùng một
  địa chỉ nguồn luôn vào cùng worker, nên các packet của một agent được xử lý theo
  **thứ tự đến**. Pool dùng chung sẽ để packet 40 vượt packet 39, và phép kiểm tra
  replay sẽ từ chối một packet hoàn toàn tốt.
- Chính sách hàng đợi đầy là `CallerRunsPolicy`: công việc chạy trên thread
  receiver, làm **throttle (làm chậm)** một trận flood thay vì để bộ nhớ phình to.
- Đếm: `TelemetryService` giữ `acceptedCount` / `rejectedCount` bằng `LongAdder`
  (vì nhiều worker cộng song song, `long` thường sẽ mất cập nhật).

### 4.5 Vì sao mất gói được chấp nhận

1. Telemetry gửi lặp mỗi giây — **mẫu bị mất được thay bằng mẫu kế tiếp**, không
   có gì phải giữ lại hay gửi lại (`UdpTelemetrySender` javadoc).
2. Gửi lại (retransmission) và trạng thái kết nối sẽ **tốn nhiều hơn giá trị**
   của một mẫu đơn lẻ.
3. Gửi lỗi UDP chỉ được log và nuốt, **không** đẩy ngược vào scheduler — một lần
   send fail không dừng việc đo.
4. Cửa sổ chống replay được thiết kế thành **cửa sổ 64 gói** thay vì "sequence
   phải luôn lớn hơn gói trước", vì UDP không hứa thứ tự: packet có thể vượt
   nhau trên mạng, và receiver chia việc xử lý cho nhiều thread cũng có thể xong
   sai thứ tự. Yêu cầu tăng nghiêm ngặt sẽ vứt bỏ packet thật chỉ đến muộn
   (chi tiết ở `05-bao-mat-hmac-va-chong-phat-lai.md`).
5. Một heartbeat bị mất không sao: timeout 15 s cho phép mất **hai heartbeat liên
   tiếp** trước khi một agent khỏe mạnh bị coi là chết.

---

## 5. Bảng tổng hợp nhanh các tham số

| Tham số | Giá trị | Nguồn |
|---|---|---|
| `PROTOCOL_VERSION` | `1.0` | `ProtocolConstants` |
| Cổng UDP monitor | `6000` | `ProtocolConstants` / `monitor.udp.port` |
| Cổng TCP agent | `5000` (mặc định) | `ProtocolConstants` |
| Chu kỳ telemetry | `1000 ms` | `ProtocolConstants` / `metric.interval.ms` |
| Chu kỳ heartbeat | `5000 ms` | `ProtocolConstants` / `heartbeat.interval.ms` |
| Giới hạn datagram UDP | `4096 byte` | `MAX_UDP_PACKET_SIZE` |
| Giới hạn khung TCP | `1 MiB` | `MAX_TCP_FRAME_SIZE` |
| TCP connect/read timeout (monitor) | `5000 ms` | `tcp.timeout.ms` |
| Idle timeout phiên TCP (agent) | `120 000 ms` | `TcpClientSessionHandler` |
| Ký tự mã hóa | UTF-8 | `ProtocolConstants.CHARSET` |
| Thuật toán HMAC | `HmacSHA256` | `ProtocolConstants.HMAC_ALGORITHM` |

---

## 6. Điểm khác biệt giữa docs và code

| Đề mục | README / 10-giao-thuc-goc.md nói | Code nói | Chọn |
|---|---|---|---|
| Chuỗi canonical (README mục 13) | Không có `sessionId`, và không nhắc heartbeat | `PacketCanonicalizer` **có** `sessionId` ở cả telemetry và heartbeat | **Code** — xem `05-bao-mat-hmac-va-chong-phat-lai.md` |
| README mục 13 | Liệt kê đủ thứ tự nhưng thiếu trường `sessionId` | Có đủ 13 trường (telemetry) / 7 trường (heartbeat) | **Code** |
| `10-giao-thuc-goc.md` mục 4.1 | Khớp với code | — | Giữ nguyên |

---

## Tài liệu liên quan

- [09-kien-truc-goc.md](09-kien-truc-goc.md) — phân lớp, luồng dữ liệu, mô hình thread
- [11-co-so-du-lieu-goc.md](11-co-so-du-lieu-goc.md) — bảng, index, retention, chế độ demo
- [01-cau-truc-du-an.md](01-cau-truc-du-an.md)
- [02-mo-hinh-he-thong.md](02-mo-hinh-he-thong.md)
- [03-cach-thuc-hoat-dong.md](03-cach-thuc-hoat-dong.md)
- [08-mo-hinh-da-luong.md](08-mo-hinh-da-luong.md)
