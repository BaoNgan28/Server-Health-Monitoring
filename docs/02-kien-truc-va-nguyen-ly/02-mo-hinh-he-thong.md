# Mô hình hệ thống

Hệ thống giám sát server từ xa — mô hình client-server với hai giao thức vận
chuyển (transport) khác nhau chạy song song: **UDP cho telemetry** (dòng dữ liệu
đo lường) và **TCP cho kênh điều khiển** (control channel).

---

## 1. Mô hình client–server

Hệ thống gồm hai loại tiến trình:

| Vai trò | Chương trình | Chạy ở đâu | Vai trò network |
|---|---|---|---|
| **Server** | `monitor-app` (client giám sát) | Máy quan sát (MACHINE A) | **Lắng nghe** UDP cổng 6000 (nhận dữ liệu) và **kết nối tới** agent qua TCP cổng 5000 (gửi lệnh) |
| **Client** | `agent-app` (agent) | Máy được giám sát (MACHINE B, C, D, E) | **Gửi** UDP tới monitor (dữ liệu) và **lắng nghe** TCP cổng 5000 (nhận lệnh) |

Tức là về **dòng telemetry**, agent là người gửi và monitor là người nhận; về
**kênh điều khiển**, monitor là client chủ động kết nối và agent là TCP server.
Một monitor phục vụ đồng thời nhiều agent; mỗi agent chỉ biết một monitor
(`monitor.host` trong file cấu hình).

Thuật ngữ:

- **telemetry** (dữ liệu đo lường): gói định kỳ chứa 6 chỉ số của máy
  (CPU %, bộ nhớ %, số kết nối TCP, uptime, lưu lượng mạng vào/ra tính bằng
  byte/giây).
- **heartbeat** (nhịp tim): gói "tôi còn sống" nhỏ gọn, không kèm chỉ số.
- **HMAC** (Hash-based Message Authentication Code — mã xác thực dựa trên hàm băm):
  chữ ký số dùng chia sẻ bí mật, ở đây là HmacSHA256, để bên nhận chứng minh gói
  thực sự đến từ agent đó và chưa bị sửa đổi.
- **framing** (đóng khung): quy ước đặt ranh giới thông điệp trên luồng byte TCP.

---

## 2. Sơ đồ tổng

```
   MÁY ĐƯỢC GIÁM SÁT (AGENT)                    MÁY QUAN SÁT (MONITOR)
 +-------------------------+                 +--------------------------------+
 |  agent-app (console)    |                 |  monitor-app (Java Swing GUI)  |
 |                         |                 |                                |
 |  OSHI                   |                 |  UdpTelemetryReceiver          |
 |    |                    |                 |    |  luồng udp-receiver       |
 |    v                    |                 |    v                           |
 |  OshiSystemMetrics-     |   UDP 6000      |  4 luồng udp-worker-0..3       |
 |    Collector            | - - - - - - ->  |    |                           |
 |    |                    |  TELEMETRY      |    v                           |
 |    v                    |  HEARTBEAT      |  TelemetryService              |
 |  TelemetryScheduler     |  (ký HmacSHA256)|    |                           |
 |  HeartbeatScheduler     |                 |    +--> PacketValidator        |
 |    |                    |                 |    +--> HeartbeatMonitor       |
 |    v                    |                 |    +--> AgentRegistry (GUI)    |
 |  UdpTelemetrySender     |                 |    +--> MetricRepository (DB)  |
 |                         |                 |    +--> AlertService           |
 |  TcpControlServer       | <=========== >  |        |                       |
 |    |  TCP 5000          |  TCP điều khiển |        v                       |
 |    v                    |  PING/PONG      |  AgentTcpConnection(Manager)   |
 |  TcpClientSessionHandler|  AGENT_INFO_*   |  EmailNotifier --> SMTP        |
 |  ConfigUpdateService    |  CONFIG_UPDATE  |  HeartbeatMonitor              |
 |                         |  CONFIG_ACK     |  AggregationService            |
 +-------------------------+  / ERROR        +--------------------------------+
```

Hai kênh chạy song song, độc lập với nhau:

| Kênh | Giao thức | Cổng (mặc định) | Hướng | Nội dung |
|---|---|---|---|---|
| Telemetry / heartbeat | **UDP** | Monitor lắng nghe **6000** (`monitor.udp.port`) | Agent → Monitor | `TELEMETRY`, `HEARTBEAT` |
| Điều khiển | **TCP** | Agent lắng nghe **5000** (`agent.tcp.port`) | Monitor → Agent | `PING`/`PONG`, `AGENT_INFO_REQUEST`/`AGENT_INFO_RESPONSE`, `CONFIG_UPDATE`/`CONFIG_ACK`, `ERROR` |

Giá trị mặc định nằm trong `ProtocolConstants`:
`DEFAULT_MONITOR_UDP_PORT = 6000`, `DEFAULT_AGENT_TCP_PORT = 5000`.

**Chế độ demo 4 agent trên một máy**: bốn agent dùng chung một máy nên mỗi agent
phải có cổng TCP riêng `5001, 5002, 5003, 5004` (xem `config/agent01..04.properties`).
Khi mỗi agent ở một máy riêng, tất cả đều dùng 5000. Cổng UDP của monitor vẫn
là 6000 trong mọi trường hợp.

---

## 3. Tại sao UDP cho telemetry và TCP cho điều khiển?

### 3.1 UDP cho telemetry

- **Mẫu dữ liệu nhỏ và đến đều đặn**: mỗi gói dưới 1 kB (giới hạn datagram là
  `MAX_UDP_PACKET_SIZE = 4096` byte), cứ 1000 ms (`metric.interval.ms`) lại có
  gói mới.
- **Mất một gói không có giá trị**: gói bị rơi hôm nay đã vô dụng khi truyền
  lại được; gói sau đến sau đó 1 giây sẽ thay thế. Không cần truyền lại
  (retransmission), không cần thiết lập kết nối.
- **Không tốn trạng thái kết nối**: mỗi agent giữ một `DatagramSocket` duy nhất
  (`UdpTelemetrySender`), không bắt tay, không giữ bảng kết nối ở hai phía.
- **Không kéo sập theo dây**: một agent chết không để lại kết nối "chết treo" ở
  monitor; monitor chỉ dựa vào việc không nhận được gì để khai báo OFFLINE.
- **Đổi địa chỉ monitor linh hoạt**: khi gửi lỗi, `UdpTelemetrySender` xóa địa
  chỉ IP đã cache để lần gửi sau phân giải lại tên miền.

Đổi lại, UDP không đảm bảo thứ tự, không đảm bảo giao hàng — đó chính là lý do
giao thức phải có **số thứ tự (sequence)**, **cửa sổ chống replay** và **chống
lặp theo cửa sổ** thay vì đòi sequence tăng nghiêm ngặt (chi tiết ở
[10-giao-thuc-goc.md](10-giao-thuc-goc.md) mục 6 và tài liệu `08-mo-hinh-da-luong.md`).

### 3.2 TCP cho kênh điều khiển

- **Lệnh cấu hình không được mất âm thầm**: một `CONFIG_UPDATE` bị rơi mà không
  ai biết nghĩa là monitor và agent lệch cấu hình vô thời hạn.
- **`CONFIG_ACK` là bằng chứng**: chỉ khi nhận được phản hồi trên cùng kết nối
  ta mới biết lệnh đã đến và được chấp nhận (hay bị từ chối kèm lý do).
- **Cần khung (framing) rõ ràng**: TCP là luồng byte không có ranh giới thông
  điệp, nên giao thức dùng khung **4 byte độ dài big-endian + JSON UTF-8**
  (`MessageFraming`), đọc bằng `readFully` để không bao giờ đọc nửa thông điệp.
- **Yêu cầu–đáp ứng tuần tự**: mỗi yêu cầu đi cùng một phản hồi trên đúng kết
  nối, nhờ đó `requestId` ghép đúng cặp câu trả lời.

Cả hai lý do này được ghi lại trong `10-giao-thuc-goc.md` mục 2 và `README.md` mục 2.

---

## 4. Các thành phần chính và trách nhiệm

### 4.1 Phía Agent

| Thành phần | Trách nhiệm |
|---|---|
| `AgentMain` | Entry point: đọc `--config`, cấu hình log, in banner (không bao giờ in bí mật), cài shutdown hook, giữ main thread chờ. |
| `AgentConfigLoader` / `AgentConfig` | Đọc file `.properties`, kiểm tra range ngay khi nạp; các field mutable (chu kỳ, ngưỡng) là `volatile` vì scheduler ghi/đọc song song. |
| `AgentRuntime` | Sở hữu và sắp xếp thứ tự khởi động/dừng: collector → UDP sender → TCP server → 2 scheduler; dừng theo thứ tự ngược, idempotent. |
| `OshiSystemMetricsCollector` | Thu 6 chỉ số qua OSHI; mọi lỗi được bọc, chỉ số hỏng trả về giá trị an toàn và cảnh báo tối đa 1 lần/phút. |
| `NetworkRateTracker` | Biến bộ đếm byte tích lũy thành tốc độ byte/giây (xử lý mẫu đầu, bộ đếm reset, elapsed <= 0). |
| `TelemetryScheduler` | Chạy mỗi `metric.interval.ms` (mặc định 1000 ms): đo → dựng packet → ký HMAC → gửi UDP. |
| `HeartbeatScheduler` | Chạy mỗi `heartbeat.interval.ms` (mặc định 5000 ms): gửi gói heartbeat đã ký, không kèm chỉ số. |
| `UdpTelemetrySender` | Mã hóa JSON, từ chối gói > 4096 byte, gửi datagram tới `monitor.host:monitor.udp.port`; đếm gửi/thành công/lỗi bằng `LongAdder`. |
| `TcpControlServer` | TCP server: 1 luồng `agent-tcp-accept` cho `accept()`, pool cố định 8 luồng `agent-tcp-session-*` cho từng phiên. |
| `TcpClientSessionHandler` | Phục vụ trọn một kết nối: đọc khung → `handle()` → ghi khung phản hồi; timeout idle 120 s. |
| `ConfigUpdateService` | Kiểm tra **tất cả** trường trước khi áp dụng **bất kỳ** trường nào; trả về `CONFIG_ACK` kèm giá trị `effective*` đang thực chạy. |

### 4.2 Phía Monitor

| Thành phần | Trách nhiệm |
|---|---|
| `MonitoringMain` | Entry point: nạp cấu hình, khởi động `MonitorApplication` **trước** khi hiện cửa sổ (lỗi cổng/DB hiện dialog thay vì màn hình trống), rồi dựng `MainFrame` trên EDT. |
| `MonitorApplication` | Nơi "wiring" duy nhất: DB → repository → notifier → registry → alert → heartbeat → validator → telemetry → TCP manager → socket → timer; thứ tự dừng là ngược lại. |
| `UdpTelemetryReceiver` | 1 luồng chỉ `receive()` + sao chép bytes; 4 worker (mỗi worker 1 luồng, queue 2000, `CallerRunsPolicy`); chọn worker theo địa chỉ nguồn để giữ thứ tự theo từng agent. |
| `TelemetryService` | Đường đi duy nhất của mọi datagram: JSON → loại thông điệp → `PacketValidator` → (chấp nhận) markAlive → registry → DB → alert engine; gói bị từ chối không đi xa hơn validator. |
| `PacketValidator` (trong common) | Thứ tự kiểm tra: shape/range → phiên bản giao thức → agent đã đăng ký → HMAC (so sánh hằng thời gian) → timestamp ±30 000 ms → replay. |
| `ReplayProtector` (trong common) | Cửa sổ 64 bit theo `(agentId, sessionId)`, nhớ 4 session gần nhất mỗi agent. |
| `AgentRegistry` | View runtime của các agent (bản sao của bảng `agents`), đồng thời là `SecretProvider` cấp bí mật cho validator; `ConcurrentHashMap` + field `volatile`. |
| `HeartbeatMonitor` | Quét định kỳ 2000 ms, quá 15 000 ms không nhận được gì → OFFLINE (đúng 1 sự kiện); packet hợp lệ kế tiếp → ONLINE + `AGENT_RECOVERED`. |
| `AlertService` | Máy trạng thái cảnh báo theo cặp (agent, loại): open/resolve với dải hysteretic (hysteresis — vùng trễ để khỏi flicker), gửi notifier, ghi DB, bắn event. |
| `MetricRepository` / `DatabaseManager` | Ghi `metric_raw`, gộp `metric_5m`, xóa theo retention; **một connection chia sẻ + một khóa duy nhất** → đúng 1 writer. |
| `AggregationService` | Job định kỳ gộp dữ liệu thô thành bucket và dọn dữ liệu hết hạn. |
| `AgentTcpConnection(Manager)` | Giữ tối đa 1 kết nối TCP điều khiển mỗi agent, tự nối lại khi rớt, timeout kết nối/đọc `tcp.timeout.ms` (5000 ms). |
| `AgentManagementService` | Toàn bộ thao tác với agent (thêm/sửa/xóa, connect, pushConfiguration) — GUI chỉ gọi service, không mở socket/SQL. |
| `EmailNotifier` | Gửi email cảnh báo qua SMTP trên **1 luồng** sau hàng đợi có giới hạn (50 phần tử). |
| `MonitorEventBus` | Chuyển sự kiện service → listener (GUI); `CopyOnWriteArrayList`, listener lỗi chỉ bị log và bỏ qua. |
| `MainFrame` + các Panel | Vẽ và nhập liệu trên EDT; mọi callback từ worker đều được `SwingUtilities.invokeLater`; thao tác chặn (connect, push config, load history, gửi mail) chạy trong `SwingWorker`. |

---

## 5. Các loại message

Toàn bộ dữ liệu trên cả hai kênh là **JSON UTF-8**, phiên bản giao thức **`1.0`**
so sánh **bằng đúng** (`ProtocolConstants.PROTOCOL_VERSION`).

### 5.1 UDP — 2 loại

**`TELEMETRY`** — mỗi `metric.interval.ms` (mặc định 1000 ms):

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

**`HEARTBEAT`** — mỗi `heartbeat.interval.ms` (mặc định 5000 ms): cùng trường
thân phận, **không** có trường chỉ số.

Hai loại dùng chung **một bộ đếm sequence** mỗi agent, vì monitor giữ một cửa sổ
chống replay duy nhất bao trùm cả hai.

### 5.2 TCP — 5 loại

| Loại | Hướng | Ý nghĩa |
|---|---|---|
| `PING` / `PONG` | Monitor → Agent | Kiểm tra kết nối điều khiển còn sống. |
| `AGENT_INFO_REQUEST` / `AGENT_INFO_RESPONSE` | Monitor → Agent | Hỏi thông tin máy (OS, CPU, RAM, uptime, chu kỳ hiện hành, `agentVersion = 1.0.0`). |
| `CONFIG_UPDATE` | Monitor → Agent | Đẩy cấu hình; mỗi trường đều **tùy chọn**, `null` = "giữ nguyên". |
| `CONFIG_ACK` | Agent → Monitor | `status` = `SUCCESS` hoặc `ERROR` + lý do, kèm các trường `effective*` (giá trị đang thực chạy). |
| `ERROR` | Agent → Monitor | Không hiểu được yêu cầu. |

Ví dụ `CONFIG_UPDATE`:

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

Range agent chấp nhận (`ConfigUpdateService` + `ProtocolConstants`):

| Trường | Khoảng chấp nhận |
|---|---|
| `metricIntervalMs` | 200 … 3 600 000 ms |
| `heartbeatIntervalMs` | 1 000 … 3 600 000 ms |
| `cpuThreshold`, `memoryThreshold` | > 0 và <= 100 |
| `networkInThresholdMbps`, `networkOutThresholdMbps` | hữu hạn, không âm |

### 5.3 Kiểu cảnh báo (`AlertType`)

8 loại: `CPU_HIGH`, `MEMORY_HIGH`, `NETWORK_IN_HIGH`, `NETWORK_OUT_HIGH`,
`AGENT_OFFLINE`, `AGENT_RECOVERED`, `SECURITY_INVALID_HMAC`,
`SECURITY_REPLAY_PACKET` (2 loại cuối là sự kiện bảo mật, lưu sẵn trạng thái
RESOLVED, giới hạn 1 sự kiện/phút/agent/ lý do và **không gửi email**).

---

## 6. Kiểm tra khác biệt tài liệu vs code

| Điểm | Nguồn tài liệu | Code | Kết luận dùng theo |
|---|---|---|---|
| Chuỗi ký (canonical string) có `sessionId` hay không | `README.md` mục 13 liệt kê **thiếu** `sessionId` | `PacketCanonicalizer.canonicalize()` **có** `sessionId` (đúng như `10-giao-thuc-goc.md` mục 4.1) | **Code**: có `sessionId`. |
| "Cửa sổ bị quên sau khi im lặng 60 giây" | `README.md` mục 14 | `ReplayProtector` **không** có cơ chế nào theo thời gian; chỉ có LRU 4 session/agent (`MAX_SESSIONS_PER_AGENT = 4`) | **Code**: chưa xác định về mốc 60 giây — không tìm thấy trong mã nguồn. |
| Trạng thái `WARNING` của agent | `11-co-so-du-lieu-goc.md` liệt kê trong bảng `status` | Không có code nào gán `AgentStatus.WARNING`; `HeartbeatMonitor` chỉ đếm nó | Giá trị được khai báo nhưng **hiện chưa được set** ở đâu. |

---

## Tài liệu liên quan

- [09-kien-truc-goc.md](09-kien-truc-goc.md) — phân tầng, luồng dữ liệu, mô hình luồng.
- [10-giao-thuc-goc.md](10-giao-thuc-goc.md) — giao thức wire, framing, chuỗi ký, ví dụ.
- [11-co-so-du-lieu-goc.md](11-co-so-du-lieu-goc.md) — bảng, index, retention.
- [04-giao-thuc-tcp-udp.md](04-giao-thuc-tcp-udp.md) — giao thức TCP/UDP, cấu trúc từng message.
- [05-bao-mat-hmac-va-chong-phat-lai.md](05-bao-mat-hmac-va-chong-phat-lai.md) — xác thực HMAC-SHA256 và chống phát lại.
- [06-luu-tru-va-tong-hop.md](06-luu-tru-va-tong-hop.md) — lưu trữ SQLite, tổng hợp và retention.
- [07-canh-bao-va-thong-bao.md](07-canh-bao-va-thong-bao.md) — hệ thống cảnh báo và gửi email.
