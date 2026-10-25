# Tài liệu lớp — monitoring-common

`monitoring-common` là module Maven dùng chung (shared module) của dự án ServerHealthMonitoring, chứa model message, giao thức (protocol), lớp bảo mật (security) và tiện ích (util) mà cả ba module ứng dụng — `agent-app`, `monitor-app`, `simulator-app` — đều kế thừa qua dependency Maven. Module này định nghĩa "hợp đồng" chung: mọi gói tin UDP/TCP đều là JSON của các model ở đây, mọi gói UDP đều đi qua lớp ký/kiểm tra HMAC ở đây. Xem thêm tổng quan (00-tong-quan-class.md).

- Package gốc: `vn.edu.hutech.group10.monitoring.common`
- Artifact: `monitoring-common` (parent `server-health-monitoring` 1.0.0), dependency duy nhất là `gson` (thư viện JSON của Google).
- Số class trong main: 27 (model 10, protocol 5, security 7, util 5).

## 1. Bảng tổng quan class

| # | Class | Package | Vai trò (1 dòng) | File nguồn |
|---|-------|---------|------------------|------------|
| 1 | `TelemetryPacket` | model | Gói tin chỉ số (CPU, RAM, mạng...) agent gửi qua UDP | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/TelemetryPacket.java` |
| 2 | `HeartbeatPacket` | model | Gói tin "còn sống" (heartbeat) nhẹ, agent gửi định kỳ qua UDP | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/HeartbeatPacket.java` |
| 3 | `AgentInfoMessage` | model | Thông tin mô tả agent và máy chủ (OS, CPU, RAM...) qua TCP | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AgentInfoMessage.java` |
| 4 | `ConfigUpdateMessage` | model | Thông điệp đổi cấu hình Monitor đẩy xuống agent qua TCP | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/ConfigUpdateMessage.java` |
| 5 | `ConfigAckMessage` | model | Agent phản hồi kết quả áp dụng cấu hình (thành công/lỗi + giá trị thực dùng) | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/ConfigAckMessage.java` |
| 6 | `AlertEvent` | model | Một lần xảy ra cảnh báo: mở (OPEN) và đóng (RESOLVED) | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AlertEvent.java` |
| 7 | `AlertType` | model | Enum loại cảnh báo, kèm nhãn hiển thị cho GUI/e-mail | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AlertType.java` |
| 8 | `AlertSeverity` | model | Enum mức nghiêm trọng của cảnh báo (INFO/WARNING/CRITICAL) | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AlertSeverity.java` |
| 9 | `AlertState` | model | Enum trạng thái vòng đời cảnh báo (OPEN/RESOLVED) | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AlertState.java` |
| 10 | `AgentStatus` | model | Enum trạng thái agent quan sát được (UNKNOWN/ONLINE/WARNING/OFFLINE) | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AgentStatus.java` |
| 11 | `ProtocolConstants` | protocol | Tập hằng số giao thức: phiên bản, cổng, giới hạn kích thước, interval mặc định | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/protocol/ProtocolConstants.java` |
| 12 | `MessageType` | protocol | Enum 9 loại message của cả hai kênh UDP và TCP | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/protocol/MessageType.java` |
| 13 | `MessageFraming` | protocol | Khung hóa TCP: tiền tố độ dài 4 byte + payload UTF-8 | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/protocol/MessageFraming.java` |
| 14 | `JsonMessageCodec` | protocol | Mã hóa/giải mã JSON (Gson) và đọc trước trường `type` | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/protocol/JsonMessageCodec.java` |
| 15 | `ProtocolException` | protocol | Exception runtime khi không mã hóa/giải mã/khung hóa được message | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/protocol/ProtocolException.java` |
| 16 | `HmacUtil` | security | Tính HmacSHA256 dạng hex và so sánh constant-time (không lộ thời điểm) | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/HmacUtil.java` |
| 17 | `PacketCanonicalizer` | security | Dựng chuỗi chuẩn (canonical string) cố định thứ tự trường để ký | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/PacketCanonicalizer.java` |
| 18 | `PacketSigner` | security | Ký (đóng dấu) gói Telemetry/Heartbeat bằng HMAC trước khi gửi | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/PacketSigner.java` |
| 19 | `PacketValidator` | security | Cổng kiểm tra duy nhất mọi gói UDP đi vào Monitor | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/PacketValidator.java` |
| 20 | `ReplayProtector` | security | Chống phát lại (replay) bằng cửa sổ trượt sequence theo từng phiên | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/ReplayProtector.java` |
| 21 | `SecretProvider` | security | Interface cung cấp secret (khoá bí mật chung) theo agentId | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/SecretProvider.java` |
| 22 | `ValidationResult` | security | Kết quả bất biến "chấp nhận/từ chối" + lý do, trả về từ `PacketValidator` | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/ValidationResult.java` |
| 23 | `ValidationUtil` | util | Kiểm tra khoảng/giá trị: isBlank, isPercentage, inRange, requireText... | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/util/ValidationUtil.java` |
| 24 | `TimeUtil` | util | Định dạng timestamp epoch millis, uptime, duration, khung giờ (bucket) | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/util/TimeUtil.java` |
| 25 | `ByteRateFormatter` | util | Định dạng lưu lượng byte/giây và đổi giữa B/s ↔ Mbps | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/util/ByteRateFormatter.java` |
| 26 | `LoggingSetup` | util | Cấu hình `java.util.logging` 1 lần: console + file xoay vòng trong `logs/` | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/util/LoggingSetup.java` |
| 27 | `AppPaths` | util | Định vị đường dẫn `config/`, `data/`, `logs/` theo working directory hoặc `-Dmonitoring.home` | `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/util/AppPaths.java` |

## 2. Các package

### 2.1 `model` (10 class)

Chứa dữ liệu trao đổi giữa các tiến trình (POJO thuần, Gson tự động map trường JSON ↔ field Java theo tên):

- **Gói UDP realtime:** `TelemetryPacket`, `HeartbeatPacket` — cả hai đều có `protocolVersion`, `type`, `agentId`, `hostname`, `sessionId`, `timestamp` (epoch millis — mốc thời gian Unix tính bằng mili giây), `sequence`, `hmac`.
- **Gói điều khiển TCP:** `AgentInfoMessage`, `ConfigUpdateMessage`, `ConfigAckMessage` — đều có trường `type` làm bộ phân biệt (discriminator) cho `MessageType`.
- **Domain cảnh báo của Monitor:** `AlertEvent` + 3 enum `AlertType`, `AlertSeverity`, `AlertState`, và enum trạng thái agent `AgentStatus`.

Hợp đồng với module khác: `agent-app` và `simulator-app` **tạo và ký** các gói model; `monitor-app` **giải mã, kiểm tra, lưu và hiển thị** chúng. Model không có logic nghiệp vụ (trừ `equals/hashCode/toString` và `ConfigAckMessage.isSuccess()`).

### 2.2 `protocol` (5 class)

Định nghĩa "dây dẫn" (wire format) của hệ thống:

- `ProtocolConstants` — mọi giá trị mặc định và giới hạn dùng chung (cổng 6000/5000, dung lượng packet, tolerance...).
- `MessageType` — 9 loại message; giá trị trên đường dây chính là tên hằng (ví dụ `"TELEMETRY"`), JSON do đó luôn đọc được khi demo.
- `JsonMessageCodec` — chuyển object ↔ JSON UTF-8; `peekType` đọc riêng trường `type` để chọn lớp đích trước khi parse đầy đủ.
- `MessageFraming` — kênh TCP không có ranh giới message nên mỗi frame là **tiền tố độ dài 4 byte big-endian (4 byte, giá trị dài nhất 1 MiB) + đúng ngần ấy byte JSON UTF-8**.
- `ProtocolException` — lỗi protocol ném ra dưới dạng runtime exception để không làm hỏng vòng lặp nhận gói.

Hợp đồng: mọi module dùng chung codec/framing này để hai bên nói cùng một kiểu JSON; `ProtocolConstants.CHARSET` (UTF-8) là mã hoá áp dụng cho mọi JSON trên cả hai kênh.

### 2.3 `security` (7 class)

Cơ chế xác thực gói UDP, chia thành tầng:

1. `PacketCanonicalizer` — dựng chuỗi chuẩn (canonical string: chuỗi có thứ tự trường cố định, dùng để ký) từ packet.
2. `HmacUtil` — tính HmacSHA256 (HMAC: mã xác thực dựa trên hàm băm, dùng khoá bí mật chung) trên chuỗi chuẩn.
3. `PacketSigner` — phía gửi: tính HMAC rồi gán vào trường `hmac`.
4. `SecretProvider` — trả về secret theo `agentId` (agent không đăng ký → `null` → bị bỏ).
5. `ReplayProtector` — chặn gói đã thấy (tấn công replay: gửi lại gói thu được) bằng cửa sổ trượt sequence theo từng `sessionId`.
6. `PacketValidator` — ghép tất cả thành một chuỗi kiểm tra cố định thứ tự.
7. `ValidationResult` — kết quả trả ra cho tầng gọi.

Hợp đồng: `agent-app`/`simulator-app` chỉ cần `PacketSigner.sign(...)`; `monitor-app` chỉ cần `PacketValidator.validate(...)` rồi đọc `ValidationResult`. Không module nào tự so sánh HMAC riêng.

### 2.4 `util` (5 class)

Tiện ích không phụ thuộc giao thức: kiểm tra dữ liệu (`ValidationUtil`), định dạng thời gian (`TimeUtil`), định dạng lưu lượng mạng (`ByteRateFormatter`), cấu hình log (`LoggingSetup`), đường dẫn thư mục (`AppPaths`). Được dùng cả trong module này (ví dụ `PacketValidator` gọi `ValidationUtil`, `LoggingSetup` gọi `AppPaths`) và ở cả ba module ứng dụng.

### 2.5 Lớp test của module

Nằm trong `monitoring-common/src/test/java/...`:

| File test | Lớp được test chính | Số case (đếm theo method) |
|---|---|---|
| `security/ReplayProtectorTest.java` | `ReplayProtector` | 15 (muộn trong cửa sổ, nhảy lớn, restart agent, evict session, concurrency...) |
| `security/PacketValidatorTest.java` | `PacketValidator` | 14 (chữ ký giả, sai khoá, agent lạ, version lạ, timestamp lệch, replay, giá trị vô lý...) |
| `security/PacketCanonicalizerTest.java` | `PacketCanonicalizer` | 7 (thứ tự trường, double độc lập locale, null→rỗng, mọi trường đều ảnh hưởng chữ ký) |
| `security/HmacUtilTest.java` | `HmacUtil` | 5 (vector chuẩn RFC, hex thường, so sánh constant-time) |
| `protocol/JsonMessageCodecTest.java` | `JsonMessageCodec` + `MessageFraming` | 11 (round-trip UTF-8, peekType, JSON hỏng, frame liền kề, độ dài vô lý...) |
| `TestPackets.java` | (helper, không phải test) | builder gói tin với hằng `AGENT_ID="AGENT-01"`, `SECRET="UNIT_TEST_SECRET_01"`, `SESSION="TEST-SESSION-1"` |

Ngoài ra các lớp còn lại được test gián tiếp qua test của `agent-app` (vd. `AgentMetricsTest` gọi `HmacUtil` + `PacketCanonicalizer` + `PacketSigner`) và `monitor-app` (vd. `UdpIntegrationTest` dựng `PacketValidator` + `ReplayProtector`).

## 3. Chi tiết từng class

### 3.1 TelemetryPacket

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/TelemetryPacket.java`
- **Vai trò:** Gói tin chỉ số thời gian thực mà agent gửi sang Monitor qua UDP; là object trung tâm của cả hệ thống.
- **Thành phần chính:**
  - `protocolVersion`, `type` — phiên bản giao thức và loại message (`"TELEMETRY"`).
  - `agentId`, `hostname` — định danh agent và tên máy.
  - `sessionId` — định danh "một lần chạy" của agent; mỗi lần agent khởi động lại sinh giá trị mới, nhờ đó Monitor phân biệt được agent restart đếm lại từ 1 với gói bị phát lại (replay).
  - `timestamp` — epoch millis; `sequence` — số tăng nghiêm ngặt theo từng agent, nền tảng của chống replay.
  - `cpuUsage`, `memoryUsage` (phần trăm `double`), `connectionCount` (`int`), `uptimeSeconds`, `networkInBytesPerSecond`, `networkOutBytesPerSecond`.
  - `hmac` — chữ ký HmacSHA256 của chuỗi chuẩn của **mọi trường còn lại**; trường `hmac` không bao giờ nằm trong chuỗi được ký.
  - `equals/hashCode` so đủ 14 trường (gồm cả `hmac`); `toString` in tóm tắt, **không** in `hmac`.
- **Được gọi ở đâu:**
  - `TelemetryScheduler` (agent-app) — dựng packet mỗi `metricIntervalMs`, đặt `PROTOCOL_VERSION`, `MessageType.TELEMETRY.wireName()`, ký bằng `PacketSigner.sign(packet, config.getSharedSecret())`.
  - `TelemetryService`, `MetricSample` (monitor-app) — `codec.fromJson(json, TelemetryPacket.class)` rồi `validator.validate(packet, now)`, sau đó `MetricSample.from(packet)` để lưu DB.
  - `PacketFactory` (simulator-app) — dựng packet ngẫu nhiên CPU 10–50%, RAM 30–70%, dùng cho 3 chế độ mô phỏng.
  - `PacketCanonicalizer`, `PacketSigner`, `PacketValidator` (monitoring-common) — đọc từng trường để ký/kiểm tra.
  - Test: `TestPackets`, `JsonMessageCodecTest` (monitoring-common), `UdpIntegrationTest` (monitor-app), `AgentMetricsTest` (agent-app).
- **Quan hệ:** được Gson serialize/deserialize; là đối tượng của `PacketCanonicalizer.canonicalize(...)`, `PacketSigner.sign(...)`, `PacketValidator.validate(...)`; `MetricSample.from(TelemetryPacket)` (monitor-app) chuyển tiếp.
- **Ghi chú:** mọi timestamp là epoch millis; giá trị ngoài biên (số âm `sequence`, CPU > 100, NaN/Infinity) sẽ bị `PacketValidator` từ chối với lý do `MALFORMED`.

### 3.2 HeartbeatPacket

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/HeartbeatPacket.java`
- **Vai trò:** Gói nhẹ báo agent còn sống, gửi theo lịch riêng qua UDP; ký như `TelemetryPacket` nhưng trên chuỗi chuẩn ngắn hơn.
- **Thành phần chính:** `protocolVersion`, `type` (`"HEARTBEAT"`), `agentId`, `hostname`, `sessionId`, `timestamp`, `sequence`, `hmac`. Không có trường chỉ số.
- **Được gọi ở đâu:**
  - `HeartbeatScheduler` (agent-app) — dựng + `PacketSigner.sign(...)` theo `heartbeatIntervalMs`.
  - `TelemetryService.handleHeartbeat` (monitor-app) — parse, `validator.validate(...)`, `heartbeatMonitor.markAlive(...)`.
  - `PacketFactory.signedHeartbeat(...)` (simulator-app).
  - `PacketCanonicalizer`, `PacketSigner`, `PacketValidator` (monitoring-common); test: `TestPackets`, `UdpIntegrationTest`.
- **Quan hệ:** song song cấu trúc với `TelemetryPacket`; cùng đi qua `PacketValidator` và `ReplayProtector`.
- **Ghi chú:** `PacketValidator` kiểm tra heartbeat nhẹ hơn telemetry (bỏ bước kiểm tra shape chỉ số) nhưng vẫn đủ thứ tự: shape cơ bản → version/agent → HMAC → timestamp → replay.

### 3.3 AgentInfoMessage

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AgentInfoMessage.java`
- **Vai trò:** Mô tả agent + máy chạy nó, làm payload của message TCP `AGENT_INFO_RESPONSE`.
- **Thành phần chính:** `type`, `agentId`, `agentName`, `hostname`, `osName`, `osVersion`, `cpuModel`, `cpuLogicalCores`, `totalMemoryBytes`, `uptimeSeconds`, `metricIntervalMs`, `heartbeatIntervalMs`, `agentVersion`.
- **Được gọi ở đâu:**
  - `SystemMetricsCollector.describe(AgentInfoMessage)` (agent-app) — interface; `OshiSystemMetricsCollector.describe(...)` (agent-app) điền dữ liệu từ OSHI (thư viện đọc thông tin phần cứng).
  - `TcpClientSessionHandler.buildAgentInfo()` (agent-app) — dựng message trả lời `AGENT_INFO_REQUEST`.
  - `AgentTcpConnection` (monitor-app) — `codec.peekType(response) != AGENT_INFO_RESPONSE` thì hỏng, ngược lại decode; `AgentManagementService`, `DashboardPanel` (monitor-app) — lưu và hiển thị (RAM tổng qua `ByteRateFormatter.formatBytes`).
  - Test: `TcpIntegrationTest` (monitor-app), `AgentMetricsTest` (agent-app).
- **Quan hệ:** đi kèm `MessageType.AGENT_INFO_REQUEST`/`AGENT_INFO_RESPONSE`; được `JsonMessageCodec` mã hoá.
- **Ghi chú:** không có trường `hmac` — kênh TCP không dùng chữ ký UDP; độ tin cậy đến từ việc kiểm tra `type` trước khi tin nội dung.

### 3.4 ConfigUpdateMessage

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/ConfigUpdateMessage.java`
- **Vai trò:** Thông điệp Monitor đẩy xuống agent qua TCP để đổi cấu hình (`MessageType.CONFIG_UPDATE`).
- **Thành phần chính:**
  - `type`, `requestId` — `requestId` để ghép cặp yêu cầu/phản hồi.
  - `metricIntervalMs`, `heartbeatIntervalMs` (kiểu `Long`), `cpuThreshold`, `memoryThreshold` (kiểu `Double`), `networkInThresholdMbps`, `networkOutThresholdMbps` (kiểu `Double`).
- **Được gọi ở đâu:**
  - `ConfigureAgentDialog` (monitor-app) — form điền giá trị rồi gửi đi.
  - `AgentManagementService` (monitor-app) — dựng message, `AgentTcpConnection` (monitor-app) — `update.setType(MessageType.CONFIG_UPDATE.wireName())` rồi gửi/đọc phản hồi.
  - `TcpClientSessionHandler` (agent-app) — `codec.fromJson(request, ConfigUpdateMessage.class)`; `ConfigUpdateService.apply/validate` (agent-app) — kiểm tra khoảng giá trị.
  - Test: `ConfigUpdateServiceTest` (agent-app), `TcpIntegrationTest`, `JsonMessageCodecTest` (case `partialConfigUpdateKeepsNulls`).
- **Quan hệ:** cặp với `ConfigAckMessage`; dùng `MessageType.CONFIG_UPDATE`.
- **Ghi chú:** mọi setting đều là kiểu **boxed** (kiểu đóng hộp `Long`/`Double` thay vì `long`/`double`) nên `null` nghĩa là "không đổi" — Monitor có thể gửi cập nhật một phần (partial update). Gson giữ nguyên các trường `null` nhờ `serializeNulls()`.

### 3.5 ConfigAckMessage

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/ConfigAckMessage.java`
- **Vai trò:** Agent trả lời `ConfigUpdateMessage` (`MessageType.CONFIG_ACK`): chấp nhận hay từ chối, kèm giá trị thực có hiệu lực.
- **Thành phần chính:**
  - Hằng `STATUS_SUCCESS = "SUCCESS"`, `STATUS_ERROR = "ERROR"`; `isSuccess()` — `STATUS_SUCCESS.equals(status)`.
  - `type`, `requestId`, `status`, `message` (giải thích cài đặt nào bị từ chối và vì sao).
  - 6 trường `effective*`: `effectiveMetricIntervalMs`, `effectiveHeartbeatIntervalMs`, `effectiveCpuThreshold`, `effectiveMemoryThreshold`, `effectiveNetworkInThresholdMbps`, `effectiveNetworkOutThresholdMbps` — cấu hình đang thực chạy trên agent sau khi áp dụng.
- **Được gọi ở đâu:**
  - `ConfigUpdateService.apply(ConfigUpdateMessage)` (agent-app) — `new ConfigAckMessage()`, đặt `STATUS_SUCCESS`/`STATUS_ERROR`, `fillEffective(ack)` điền 6 giá trị `effective*`.
  - `TcpClientSessionHandler` (agent-app) — gửi ack ra `MessageFraming`.
  - `AgentTcpConnection` (monitor-app) — `codec.peekType(response) == CONFIG_ACK`; `AgentManagementService` (monitor-app) — xử lý kết quả; `DashboardPanel` (monitor-app) — hiển thị thông báo.
  - Test: `ConfigUpdateServiceTest` (agent-app), `TcpIntegrationTest`, `JsonMessageCodecTest`.
- **Quan hệ:** phản xạ lại `ConfigUpdateMessage` qua `requestId`; dùng `MessageType.CONFIG_ACK`.
- **Ghi chú:** `requestId` trong ack phải khớp yêu cầu để nối kết cặp request/response.

### 3.6 AlertEvent

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AlertEvent.java`
- **Vai trò:** Một lần xảy ra cảnh báo: mở khi điều kiện vi phạm lần đầu (`AlertState.OPEN`), đóng khi điều kiện hết (`AlertState.RESOLVED`).
- **Thành phần chính:**
  - `id`; `agentId`, `agentName`; `alertType`, `severity`, `state`; `message`.
  - `openedAt` (epoch millis); `resolvedAt` — **bằng 0 khi còn mở**.
  - `currentValue` (giá trị đo làm trip cảnh báo, vô nghĩa với event offline), `threshold`, `unit`.
  - Constructor 6 tham số đặt `state = AlertState.OPEN` ngay khi tạo.
  - `equals/hashCode` chỉ dùng `id`, `agentId`, `alertType`, `openedAt`.
- **Được gọi ở đâu (tất cả monitor-app):**
  - `AlertService` — tạo event khi điều kiện trip, đổi sang `RESOLVED` khi hết; `AlertRepository` — lưu/đọc DB.
  - Hệ thống thông báo: `Notifier` (interface), `EmailNotifier`, `EmailMessageBuilder`, `CompositeNotifier`, `NoOpNotifier`.
  - Sự kiện & UI: `MonitorEventBus`, `MonitorEventListener`, `AlertPanel`, `MainFrame`.
  - Test: `AlertServiceTest`, `EmailNotifierTest`, `RecordingNotifier`.
- **Quan hệ:** chứa 3 enum `AlertType`/`AlertSeverity`/`AlertState`; là định danh của notifier và event bus.
- **Ghi chú:** vi phạm lặp lại trên alert đang OPEN **không** tạo event mới (theo Javadoc của class).

### 3.7 AlertType

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AlertType.java`
- **Vai trò:** Enum 8 loại cảnh báo; mỗi hằng có nhãn hiển thị (display name) dùng trong tiêu đề e-mail và GUI.
- **Thành phần chính:** `displayName` + `getDisplayName()`; các hằng và nhãn:
  - `CPU_HIGH` → `"CPU HIGH"`, `MEMORY_HIGH` → `"MEMORY HIGH"`.
  - `NETWORK_IN_HIGH`, `NETWORK_OUT_HIGH` → cùng `"NETWORK TRAFFIC HIGH"`.
  - `AGENT_OFFLINE` → `"SERVER OFFLINE"`, `AGENT_RECOVERED` → `"SERVER ONLINE"`.
  - `SECURITY_INVALID_HMAC` → `"SECURITY INVALID HMAC"`, `SECURITY_REPLAY_PACKET` → `"SECURITY REPLAY PACKET"`.
- **Được gọi ở đâu:**
  - `AlertService`, `AlertRepository` (monitor-app) — gán và lưu loại cảnh báo.
  - `TelemetryService.securityTypeOf(ValidationResult.Reason)` (monitor-app) — chỉ `INVALID_HMAC` → `SECURITY_INVALID_HMAC` và `REPLAY` → `SECURITY_REPLAY_PACKET` được coi là sự kiện bảo mật.
  - `HeartbeatMonitor` (monitor-app) — `AGENT_OFFLINE`/`AGENT_RECOVERED` khi timeout/khôi phục.
  - `EmailMessageBuilder` (monitor-app) — dùng display name; test: `UdpIntegrationTest`, `HeartbeatMonitorTest`.
- **Quan hệ:** là field của `AlertEvent`; xuất hiện trong chuỗi Reason → AlertType của `TelemetryService`.
- **Ghi chú:** `NETWORK_IN_HIGH` và `NETWORK_OUT_HIGH` dùng chung một nhãn hiển thị, nên trên GUI/e-mail chỉ thấy `"NETWORK TRAFFIC HIGH"`.

### 3.8 AlertSeverity

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AlertSeverity.java`
- **Vai trò:** Enum mức nghiêm trọng: `INFO`, `WARNING`, `CRITICAL`.
- **Thành phần chính:** không có field/method.
- **Được gọi ở đâu:** `AlertService`, `AlertRepository`, `EmailMessageBuilder` (monitor-app) — gán/lưu/màu sắc–nội dung e-mail; test `EmailNotifierTest`.
- **Quan hệ:** field của `AlertEvent`.
- **Ghi chú:** chưa tìm thấy trong code chỗ nào phân nhánh theo severity ngoài việc lưu/hiển thị (chưa xác định đầy đủ).

### 3.9 AlertState

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AlertState.java`
- **Vai trò:** Vòng đời cảnh báo: `OPEN` (đang mở) → `RESOLVED` (đã xử lý/xong).
- **Thành phần chính:** 2 hằng enum.
- **Được gọi ở đâu:** `AlertService` (đóng event khi điều kiện hết), `AlertRepository` (lưu trạng thái), `AlertPanel` (lọc/hiển thị); test `AlertServiceTest`.
- **Quan hệ:** field của `AlertEvent`; được constructor của `AlertEvent` đặt mặc định `OPEN`.
- **Ghi chú:** theo Javadoc, alert giữ `OPEN` cho tới khi điều kiện phát cảnh báo hết.

### 3.10 AgentStatus

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/model/AgentStatus.java`
- **Vai trò:** Trạng thái agent mà Monitor quan sát được: `UNKNOWN`, `ONLINE`, `WARNING`, `OFFLINE`.
- **Thành phần chính:** 4 hằng enum.
- **Được gọi ở đâu (monitor-app):** `AgentRecord`, `AgentSnapshot`, `AgentRuntimeState` (model nội bộ), `AgentRegistry`, `HeartbeatMonitor`, `AgentRepository` (lưu/đọc DB), UI: `AgentTableModel`, `DashboardPanel`, `UiConstants` (màu theo trạng thái); test: `TestSupport`, `DatabaseManagerTest`, `UdpIntegrationTest`, `HeartbeatMonitorTest`.
- **Quan hệ:** dùng bởi các model của monitor-app; không module nào khác dùng.
- **Ghi chú:** `WARNING` dùng để gắn trạng thái khi chỉ số vượt ngưỡng (giả định từ tên hằng; chi tiết đặt trong monitor-app — chưa xác định tại đây).

### 3.11 ProtocolConstants

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/protocol/ProtocolConstants.java`
- **Vai trò:** Class `final` không có constructor (utility class hằng số): mọi giá trị mặc định và giới hạn của giao thức.
- **Thành phần chính (giá trị lấy từ code):**

  | Hằng | Giá trị | Ý nghĩa |
  |---|---|---|
  | `PROTOCOL_VERSION` | `"1.0"` | Bump mỗi khi canonical string/framing đổi không tương thích |
  | `CHARSET` | `StandardCharsets.UTF_8` | JSON trên cả hai kênh đều UTF-8 |
  | `HMAC_ALGORITHM` | `"HmacSHA256"` | Thuật toán ký |
  | `DEFAULT_MONITOR_UDP_PORT` | `6000` | Cổng UDP Monitor lắng nghe mặc định |
  | `DEFAULT_AGENT_TCP_PORT` | `5000` | Cổng TCP agent nghe mặc định |
  | `DEFAULT_METRIC_INTERVAL_MS` | `1000` | Nhịp gửi chỉ số (1 giây) |
  | `DEFAULT_HEARTBEAT_INTERVAL_MS` | `5000` | Nhịp heartbeat (5 giây) |
  | `DEFAULT_HEARTBEAT_TIMEOUT_MS` | `15000` | Hết 15 giây không heartbeat → coi như offline |
  | `DEFAULT_TIMESTAMP_TOLERANCE_MS` | `30000` | Dung sai lệch đồng hồ gói tin ±30 giây |
  | `MIN_METRIC_INTERVAL_MS` | `200` | Interval nhỏ nhất agent nhận qua `CONFIG_UPDATE` |
  | `MIN_HEARTBEAT_INTERVAL_MS` | `1000` | Heartbeat interval nhỏ nhất agent nhận |
  | `MAX_INTERVAL_MS` | `3600_000` (1 giờ) | Cả hai interval tối đa |
  | `MAX_UDP_PACKET_SIZE` | `4096` | Buffer nhận datagram (JSON chỉ số < 1 KiB) |
  | `MAX_TCP_FRAME_SIZE` | `1024 * 1024` (1 MiB) | Trần frame TCP, tránh prefix độ dài sai làm hết RAM |
  | `DEFAULT_TCP_TIMEOUT_MS` | `5000` | Timeout connect/read TCP mặc định |

- **Được gọi ở đâu:**
  - agent-app: `AgentConfigLoader` (mặc định port/interval, khung `[MIN, MAX]`), `AgentConfig` (in `DEFAULT_TIMESTAMP_TOLERANCE_MS` trong `toString`), `TelemetryScheduler`/`HeartbeatScheduler` (đặt `PROTOCOL_VERSION`), `ConfigUpdateService` (validate `MIN_*`/`MAX_INTERVAL_MS`), `UdpTelemetrySender` (từ chối payload > `MAX_UDP_PACKET_SIZE`).
  - monitor-app: `MonitorConfigLoader` (mặc định port 6000, heartbeat timeout, tcp timeout), `UdpTelemetryReceiver` (buffer `MAX_UDP_PACKET_SIZE`), `AgentDialog` (điền `DEFAULT_AGENT_TCP_PORT`); bên trong common: `PacketValidator`, `MessageFraming`, `HmacUtil`.
  - simulator-app: `SimulatorOptions` (mặc định port/interval), `PacketFactory` (`PROTOCOL_VERSION`).
- **Quan hệ:** phụ thuộc `java.nio.charset`; được `JsonMessageCodec`/`MessageFraming`/`PacketValidator`/`HmacUtil` tham chiếu trực tiếp.
- **Ghi chú:** `CHARSET` khai báo nhưng **chưa thấy nơi sử dụng** trong repo (code dùng `StandardCharsets.UTF_8` trực tiếp) — chưa xác định có plan dùng sau không.

### 3.12 MessageType

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/protocol/MessageType.java`
- **Vai trò:** Enum 9 loại message của cả hai kênh; wire value (giá trị trên đường dây) chính là tên hằng.
- **Thành phần chính:**
  - UDP: `TELEMETRY`, `HEARTBEAT`.
  - TCP: `PING`, `PONG`, `AGENT_INFO_REQUEST`, `AGENT_INFO_RESPONSE`, `CONFIG_UPDATE`, `CONFIG_ACK`, `ERROR`.
  - `wireName()` — trả `name()`; `fromWire(String)` — **trả về `null` thay vì ném lỗi** khi giá trị lạ, để gói hỏng không giết vòng lặp nhận.
- **Được gọi ở đâu:**
  - agent-app: `TelemetryScheduler` (`TELEMETRY`), `HeartbeatScheduler` (`HEARTBEAT`), `ConfigUpdateService` (`CONFIG_ACK`), `TcpClientSessionHandler` (`PONG`, `AGENT_INFO_RESPONSE`, `ERROR`; đọc loại qua `codec.peekType(request)`).
  - monitor-app: `AgentTcpConnection` (`PING`/`PONG`, `AGENT_INFO_REQUEST`/`AGENT_INFO_RESPONSE`, `CONFIG_UPDATE`/`CONFIG_ACK`), `TelemetryService` (`codec.peekType(json)` rồi `switch` chỉ nhận `TELEMETRY`/`HEARTBEAT`, các loại còn lại bị `reject`).
  - simulator-app: `PacketFactory` (`TELEMETRY`, `HEARTBEAT`).
  - Test: `JsonMessageCodecTest`, `TestPackets`, `UdpIntegrationTest`, `AgentMetricsTest`.
- **Quan hệ:** được `JsonMessageCodec.peekType(...)` trả về; `JsonMessageCodec` gọi `MessageType.fromWire(...)`.
- **Ghi chú:** mặc định của `switch` trong `TelemetryService` từ chối mọi loại không phải TELEMETRY/HEARTBEAT qua UDP ("message type ... is not accepted over UDP").

### 3.13 MessageFraming

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/protocol/MessageFraming.java`
- **Vai trò:** Khung hóa kênh TCP: mỗi frame = **4 byte big-endian (độ dài, lớn nhất 1 MiB)** + đúng ngần ấy byte JSON UTF-8.
- **Thành phần chính:**
  - `writeFrame(DataOutputStream, String json)` — ném `ProtocolException` nếu `json == null` hoặc payload > `MAX_TCP_FRAME_SIZE`; `synchronized (out)` quanh `writeInt + write + flush` (an toàn khi nhiều thread ghi chung stream).
  - `readFrame(DataInputStream)` — trả JSON string; trả `null` khi peer đóng kết nối sạch giữa 2 frame (`EOFException` khi đọc prefix); `""` nếu độ dài 0; ném `ProtocolException` nếu prefix `< 0` hoặc `> MAX_TCP_FRAME_SIZE`; dùng `readFully` để đọc đủ payload.
- **Được gọi ở đâu:**
  - `TcpClientSessionHandler` (agent-app) — `while ((request = MessageFraming.readFrame(in)) != null) { ... MessageFraming.writeFrame(out, response); }`.
  - `AgentTcpConnection` (monitor-app) — `MessageFraming.writeFrame(out, request)` rồi `readFrame(in)` cho các lệnh PING/AGENT_INFO/CONFIG.
  - Test: `JsonMessageCodecTest` (4 case framing: round-trip, 2 message liền nhau, JSON có newline, độ dài vô lý).
- **Quan hệ:** phụ thuộc `ProtocolConstants.MAX_TCP_FRAME_SIZE`, ném `ProtocolException`.
- **Ghi chú:** cố ý **không** đọc theo dòng (line-oriented) vì JSON có thể chứa newline; TCP là byte stream không có ranh giới message nên `read()` có thể trả nửa frame hoặc nhiều frame — `readFully` giải quyết điều này.

### 3.14 JsonMessageCodec

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/protocol/JsonMessageCodec.java`
- **Vai trò:** Biến message ↔ JSON UTF-8 compact (Gson) và đọc trước trường `type`.
- **Thành phần chính:**
  - Constructor tạo `Gson` với `serializeNulls()` (giữ trường `null` — cần cho partial update) + `disableHtmlEscaping()`.
  - `toJson(Object)` — ném `ProtocolException` nếu `message == null` hoặc Gson lỗi; `toBytes(Object)` — JSON bytes UTF-8 cho datagram/frame.
  - `fromJson(String, Class<T>)` — ném `ProtocolException` khi payload rỗng, decode ra `null`, hoặc `JsonSyntaxException`; `fromBytes(byte[], int, int, Class<T>)` và `fromBytes(byte[], Class<T>)` — bản bọc ByteBuffer/mảng.
  - `peekType(String)` / `peekType(byte[], int, int)` — chỉ đọc phần tử `type`, trả `MessageType` hoặc `null` khi payload không phải JSON object / không có `type` dùng được / JSON hỏng (bắt `JsonSyntaxException`, `IllegalStateException`, `UnsupportedOperationException`).
  - Hằng riêng `TYPE_FIELD = "type"`.
- **Được gọi ở đâu:**
  - agent-app: `AgentRuntime` (`new JsonMessageCodec()`), `TcpControlServer`, `TcpClientSessionHandler`, `UdpTelemetrySender`.
  - monitor-app: `MonitorApplication` (codec part của `TelemetryService`/`AgentTcpConnectionManager`), `TelemetryService`, `AgentTcpConnection`, `AgentTcpConnectionManager`.
  - simulator-app: `SimulatorUdpSender` (`new JsonMessageCodec()`).
  - Test: `JsonMessageCodecTest`.
- **Quan hệ:** instance **không trạng thái** (stateless), Gson thread-safe nên một codec dùng chung cho UDP receiver, TCP session và scheduler; phụ thuộc `MessageType`, ném `ProtocolException`.
- **Ghi chú:** `peekType` cho phép chọn đúng class trước khi parse đầy đủ — nhờ vậy Monitor không cần parse cả gói để biết loại.

### 3.15 ProtocolException

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/protocol/ProtocolException.java`
- **Vai trò:** Exception runtime (`extends RuntimeException`) khi không encode/decode/frame được message; `serialVersionUID = 1`.
- **Thành phần chính:** 2 constructor: `(String message)`, `(String message, Throwable cause)`.
- **Được gọi ở đâu:**
  - **Ném ra bởi** `JsonMessageCodec` (toàn bộ nhánh lỗi decode/encode) và `MessageFraming` (payload null, frame quá lớn, prefix sai).
  - **Bắt tại** `TcpClientSessionHandler` (agent-app, dòng ~109) và `TelemetryService` (monitor-app, dòng ~93 — chuyển thành gói bị từ chối, không làm sập vòng lặp nhận).
  - Test: `JsonMessageCodecTest` (`assertThrows(ProtocolException.class, ...)` với frame độ dài vô lý).
- **Quan hệ:** extended by nobody trong repo; là unchecked exception nên các method không cần `throws`.
- **Ghi chú:** lựa chọn unchecked để một gói hỏng không làm đứt vòng lặp xử lý — caller bắt và trả về phản hồi `ERROR`/log cảnh báo.

### 3.16 HmacUtil

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/HmacUtil.java`
- **Vai trò:** Helper HmacSHA256 (HMAC: mã xác thực khoá bí mật chung) cho gói UDP.
- **Thành phần chính:**
  - `hmacSha256Hex(String secret, String data)` — trả digest **hex chữ thường** (bảng `HEX = "0123456789abcdef"`); ném `IllegalArgumentException` khi secret rỗng/null hoặc data null; ném `IllegalStateException` khi JRE thiếu HmacSHA256.
  - `constantTimeEquals(String expected, String actual)` — so sánh **không rò rỉ thời gian khớp ký tự** (dùng `MessageDigest.isEqual`), **không phân biệt hoa thường** (đều `toLowerCase(Locale.ROOT)`), `null` không bao giờ khớp (kể cả `null` vs `null`).
  - `toHex(byte[])` — private.
- **Được gọi ở đâu:**
  - `PacketSigner.sign(...)` và `PacketValidator.validate(...)` (monitoring-common) — tính và so sánh chữ ký.
  - `AgentMetricsTest` (agent-app) — khẳng định chữ ký bằng `HmacUtil.hmacSha256Hex(secret, PacketCanonicalizer.canonicalize(packet))`.
  - Test: `HmacUtilTest` (vector chuẩn `"Jefe"` / `"what do ya want for nothing?"`, digest hex thường, case-insensitive, null-reject).
- **Quan hệ:** dùng `ProtocolConstants.HMAC_ALGORITHM`; gọi `PacketCanonicalizer` (gián tiếp, qua caller); là "thanh toán" cuối của `PacketSigner`/`PacketValidator`.
- **Ghi chú:** ký trên **chuỗi chuẩn**, không ký trên JSON thô, vì hai encoder có thể sắp thứ tự trường/định dạng số khác nhau cho cùng một mẫu số liệu.

### 3.17 PacketCanonicalizer

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/PacketCanonicalizer.java`
- **Vai trò:** Dựng chuỗi chuẩn (canonical string) cố định để hai phía cùng ký trên cùng một chuỗi.
- **Thành phần chính:**
  - Hằng `SEPARATOR = '|'`, `DOUBLE_FORMAT = "%.4f"`.
  - `canonicalize(TelemetryPacket)` — thứ tự:
    `protocolVersion|type|agentId|hostname|sessionId|timestamp|sequence|cpuUsage|memoryUsage|connectionCount|uptimeSeconds|networkInBytesPerSecond|networkOutBytesPerSecond` (13 trường, **không có `hmac`**).
  - `canonicalize(HeartbeatPacket)` — `protocolVersion|type|agentId|hostname|sessionId|timestamp|sequence` (7 trường).
  - `formatDouble(double)` — `String.format(Locale.ROOT, "%.4f", ...)`, **ném `IllegalArgumentException` với NaN/Infinity**, chuẩn hoá `-0.0` thành `0.0` (tránh sinh `-0.0000`).
  - `nullSafe(String)` — private: `null` → chuỗi rỗng (không phải chữ `"null"`).
- **Được gọi ở đâu:**
  - `PacketSigner.sign(...)` và `PacketValidator.validate(...)` (monitoring-common).
  - `AgentMetricsTest` (agent-app) — tái tạo chuỗi chuẩn để đối chiếu chữ ký.
  - Test: `PacketCanonicalizerTest` (thứ tự trường, double độc lập locale: `0.0`→`"0.0000"`, `-0.0`→`"0.0000"`, `99.99994`→`"99.9999"`, `12345.5`→`"12345.5000"`; null→rỗng; mọi trường đều ảnh hưởng chữ ký).
- **Quan hệ:** phụ thuộc `TelemetryPacket`, `HeartbeatPacket`; là input của `HmacUtil.hmacSha256Hex`.
- **Ghi chú:** `Locale.ROOT` bảo đảm máy dùng dấu phẩy thập phân cũng sinh cùng byte với máy dùng dấu chấm; agent và Monitor **bắt buộc** áp dụng đúng một luật, nếu không mọi gói sẽ bị từ chối.

### 3.18 PacketSigner

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/PacketSigner.java`
- **Vai trò:** Đóng dấu (gán `hmac`) cho gói outbound; class `final` chỉ có method static.
- **Thành phần chính:** `sign(TelemetryPacket, String secret)` và `sign(HeartbeatPacket, String secret)` — đều `packet.setHmac(HmacUtil.hmacSha256Hex(secret, PacketCanonicalizer.canonicalize(packet)))`.
- **Được gọi ở đâu:**
  - agent-app: `TelemetryScheduler` (`PacketSigner.sign(packet, config.getSharedSecret())`), `HeartbeatScheduler` (tương tự).
  - simulator-app: `PacketFactory.signedTelemetry/signedHeartbeat`, `InvalidHmacSender` (ký với `WRONG_SECRET = "ATTACKER_GUESSED_SECRET"` để mô phỏng tấn công).
  - Test: `TestPackets`, `PacketValidatorTest` (monitoring-common), `UdpIntegrationTest` (monitor-app), `AgentMetricsTest` (agent-app).
- **Quan hệ:** composition `PacketCanonicalizer` + `HmacUtil`; là "phía gửi" đối xứng với `PacketValidator`.
- **Ghi chú:** ký **trước** khi gửi; mọi chỉnh sửa payload sau khi ký sẽ làm HMAC lệch → bị `PacketValidator` bắt (đúng kịch bản gói "tampered" trong `InvalidHmacSender`).

### 3.19 PacketValidator

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/PacketValidator.java`
- **Vai trò:** Cổng kiểm tra duy nhất mọi gói UDP vào Monitor; không có GUI/DB/alert engine nào thấy gói mà class này không chấp nhận.
- **Thành phần chính:**
  - Field: `secretProvider`, `replayProtector`, `timestampToleranceMs`.
  - 2 constructor: `(SecretProvider, ReplayProtector)` → dùng `DEFAULT_TIMESTAMP_TOLERANCE_MS` (30000 ms); `(SecretProvider, ReplayProtector, long tolerance)` (reject null ngay trong constructor).
  - `validate(TelemetryPacket, long nowMs)` — thứ tự kiểm tra, thất bại đầu tiên dừng lại:
    1. shape/range (`checkTelemetryShape`): `sessionId` không rỗng, `sequence >= 0`, chỉ số hữu hạn (finite), CPU/MEMORY ∈ [0..100], `connectionCount`/`uptimeSeconds`/tốc độ mạng không âm;
    2. `checkVersionAndAgent`: `protocolVersion == "1.0"`, `agentId` không rỗng, secret lấy được từ `SecretProvider` không rỗng;
    3. HMAC: canonical → `HmacUtil.hmacSha256Hex` → `constantTimeEquals` với `packet.getHmac()`;
    4. `checkTimestamp`: `|now - timestamp| <= tolerance`;
    5. `replayProtector.accept(agentId, sessionId, sequence)`.
  - `validate(HeartbeatPacket, long nowMs)` — cùng thứ tự với shape nhẹ hơn.
  - `getTimestampToleranceMs()`.
- **Được gọi ở đâu:**
  - `MonitorApplication` (monitor-app) — `new PacketValidator(registry, new ReplayProtector(), config.getTimestampToleranceMs())`.
  - `TelemetryService` (monitor-app) — `validator.validate(packet, now)` cho cả telemetry (dòng ~105) và heartbeat (~134); kết quả `ValidationResult` quyết định accept/reject.
  - Test: `PacketValidatorTest` (monitoring-common, 14 case), `UdpIntegrationTest` (monitor-app).
- **Quan hệ:** **depends on** `SecretProvider`, `ReplayProtector`, `PacketCanonicalizer`, `HmacUtil`, `ValidationUtil`, `ProtocolConstants`; trả về `ValidationResult`.
- **Ghi chú:** gói bị từ chối **không tiêu sequence** trong `ReplayProtector` (chỉ gọi `accept()` sau khi HMAC và timestamp đã qua) — đúng case test `rejectedPacketsDoNotConsumeSequence`.

### 3.20 ReplayProtector

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/ReplayProtector.java`
- **Vai trò:** Từ chối gói đã thấy trước đó — cơ chế chống phát lại (replay: kẻ tấn công gửi lại captured packet).
- **Thành phần chính:**
  - Hằng `WINDOW_SIZE = 64` (mức trễ tối đa còn chấp nhận), `MAX_SESSIONS_PER_AGENT = 4` (số phiên giữ cho mỗi agent), hằng private `NO_SESSION = ""`.
  - Lớp private `Window { long highest; long seen; }` — bit `i` set nghĩa là sequence `highest - i` đã thấy (cùng hình dạng với IPsec — giao thức bảo mật IP).
  - Lớp private `AgentSessions extends LinkedHashMap` với `accessOrder = true` và `removeEldestEntry` khi `size() > MAX_SESSIONS_PER_AGENT` (evict phiên cũ nhất).
  - `accept(String agentId, String sessionId, long sequence)` — trả `false` khi `agentId` null/rỗng hoặc `sequence < 0`; logic: phiên mới → chấp nhận; `sequence > highest` → dịch bit (nhảy ≥ 64 thì reset cửa sổ); trễ `age < 64` và bit chưa set → chấp nhận + set bit; đã set hoặc `age >= 64` → từ chối. Toàn bộ nằm trong `ConcurrentHashMap.compute(...)` nên quyết định + cập nhật **không thể xen kẽ** giữa các thread.
  - `lastSequenceOf(agentId, sessionId)` (trả `-1` nếu chưa thấy), `sessionCountOf`, `reset(agentId)`, `resetAll()`, `trackedAgentCount()`.
- **Được gọi ở đâu:**
  - `MonitorApplication` (monitor-app) — `new ReplayProtector()` đưa vào `PacketValidator`.
  - Test: `UdpIntegrationTest` (monitor-app), `ReplayProtectorTest` (monitoring-common, 15 case: packet trễ trong cửa sổ, nhảy lớn, agent restart, session cũ vẫn bị từ chối, session không can thiệp lẫn nhau, evict, `missingSessionIdFallsBackToOneWindow`, atomicity đa luồng).
  - Các method `reset/resetAll/lastSequenceOf/sessionCountOf/trackedAgentCount`: **chưa thấy nơi gọi ngoài test của chính nó** — chưa xác định (agent registry delete có thể dự định dùng `reset`, nhưng chưa có trong code đã grep).
- **Quan hệ:** là dependency của `PacketValidator`; độc lập với model (chỉ nhận chuỗi/`long`).
- **Ghi chú (theo Javadoc):** cửa sổ theo `(agentId, sessionId)` chứ không theo agent, vì agent restart đếm lại từ 1 sẽ bị coi là replay nếu dùng cửa sổ chung; mỗi phiên có cửa sổ riêng, gói replay từ phiên cũ vẫn rơi vào bit đã set của phiên cũ → bị từ chối; `sessionId = null` coi như một phiên không tên duy nhất cho người gửi cũ. Thread-safe cho nhiều worker UDP.

### 3.21 SecretProvider

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/SecretProvider.java`
- **Vai trò:** Interface `@FunctionalInterface` cung cấp secret (khoá bí mật chung) theo `agentId`.
- **Thành phần chính:** `String secretFor(String agentId)` — trả secret, hoặc `null` khi agent chưa đăng ký.
- **Được gọi ở đâu:**
  - `AgentRegistry implements SecretProvider` (monitor-app) — backing bởi bảng `agents` trong DB; agent không đăng ký phải trả `null` để gói bị bỏ, **không** kiểm tra bằng khoá mặc định.
  - `PacketValidator` (monitoring-common) — `secretProvider.secretFor(agentId)` ở cả 3 chỗ (validate telemetry, heartbeat, `checkVersionAndAgent`).
- **Quan hệ:** được `PacketValidator` inject qua constructor; hiện thực duy nhất tìm thấy là `AgentRegistry`.
- **Ghi chú:** pattern Dependency Inversion — monitor không cần `PacketValidator` biết về DB.

### 3.22 ValidationResult

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/security/ValidationResult.java`
- **Vai trò:** Kết quả bất biến (immutable — không sửa được sau khi tạo) của một lần kiểm tra gói UDP.
- **Thành phần chính:**
  - Enum lồng `Reason`: `ACCEPTED`, `MALFORMED` (JSON sai / thiếu trường / ngoài khoảng), `UNKNOWN_AGENT` (agent chưa đăng ký nên không có secret), `UNSUPPORTED_VERSION`, `INVALID_HMAC` (chữ ký lệch), `TIMESTAMP_OUT_OF_TOLERANCE` (lệch đồng hồ quá dung sai), `REPLAY` (sequence lặp).
  - Constructor **private**; factory static: `accepted()`, `rejected(Reason, String detail)`.
  - `isAccepted()`, `getReason()`, `getDetail()`, `toString()` dạng `REASON (detail)`.
- **Được gọi ở đâu:**
  - `PacketValidator` (monitoring-common) — là kiểu trả về của 2 overload `validate(...)`.
  - `TelemetryService` (monitor-app) — `result.isAccepted()`, `result.getDetail()`, `result.getReason()`; `securityTypeOf(Reason)` chỉ map `INVALID_HMAC`/`REPLAY` thành cảnh báo bảo mật, còn lại chỉ log.
  - Test: `PacketValidatorTest` (monitoring-common).
- **Quan hệ:** output của `PacketValidator`; input (sau `getReason()`) của alert pipeline trong `TelemetryService`.
- **Ghi chú:** mỗi lý do từ chối kèm `detail` mô tả (vd. `"sequence 7 was already seen in session X"`) — dùng trực tiếp cho log/ cảnh báo.

### 3.23 ValidationUtil

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/util/ValidationUtil.java`
- **Vai trò:** Các kiểm tra khoảng/hình dạng nhỏ dùng chung; class `final` chỉ static.
- **Thành phần chính:**
  - `isBlank(String)` — null hoặc sau `trim()` rỗng; `isFinite(double)` — không NaN/Infinity; `isPercentage(double)` — hữu hạn và trong `[0..100]`.
  - `clampPercentage(double)` — ép về `0..100`, input không hữu hạn → `0.0`.
  - `isNonNegative(double)`; `inRange(long, long min, long max)` (hai đầu đều bao gồm).
  - `requireText(String, String name)` — ném `IllegalArgumentException` nếu blank, trả về giá trị đã `trim()`.
  - `looksLikeEmail(String)` — heuristic đủ chuẩn để thử gửi SMTP: đúng một `@`, không ở đầu/cuối, domain có `.`, không bắt đầu/kết thúc bằng `.`, không chứa khoảng trắng.
- **Được gọi ở đâu:**
  - agent-app: `AgentConfigLoader` (đọc mọi giá trị cấu hình: `isBlank`, `inRange`, `isPercentage`, `isNonNegative`), `ConfigUpdateService` (`inRange`, `isFinite` với `MIN_METRIC_INTERVAL_MS`/`MIN_HEARTBEAT_INTERVAL_MS`/`MAX_INTERVAL_MS`), `OshiSystemMetricsCollector` (`clampPercentage` cho CPU/RAM%).
  - monitor-app: `MonitorConfigLoader`, `EmailConfigLoader` (`looksLikeEmail` cho username/recipient), `AgentManagementService` (`requireText` cho Agent ID/Name/Host/Shared Secret, `isFinite`, `isNonNegative`).
  - Trong chính module: `PacketValidator`, `AppPaths`, `ByteRateFormatter`, `LoggingSetup`; test `AgentMetricsTest` (agent-app).
- **Quan hệ:** được `PacketValidator` dùng để kiểm tra shape; không phụ thuộc class nào khác trong module.
- **Ghi chú:** `clampPercentage` là nơi duy nhất "sửa" dữ liệu (ngoài logic kiểm tra) — OSHI có thể trả load âm/vượt 100.

### 3.24 TimeUtil

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/util/TimeUtil.java`
- **Vai trò:** Định dạng epoch millis và độ dài thời gian cho GUI/log/e-mail; class `final` chỉ static.
- **Thành phần chính:**
  - Formatter: `TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss"`, `TIME_ONLY_FORMAT = "HH:mm:ss"` (đều `Locale.ROOT`).
  - `formatTimestamp(long)` — chuỗi theo múi hệ thống; `<= 0` → `"-"`.
  - `formatTimeOnly(long)` — chỉ giờ:phút:giây (trục biểu đồ, log).
  - `formatUptime(long seconds)` — dạng `3d 04h 17m 09s`; nếu không có ngày → `04h 17m 09s`; không có giờ → `17m 09s`; `< 0` → `"-"`.
  - `formatDuration(long millis)` — `< 1000` → `"N ms"`, còn lại gọi `formatUptime(millis/1000)`.
  - `bucketStart(long epochMillis, long bucketMillis)` — đầu khung giờ chứa mốc này, canh theo epoch bằng `Math.floorDiv` (mọi tiến trình cùng tính ra kết quả); `bucketMillis <= 0` → ném `IllegalArgumentException`.
- **Được gọi ở đâu (monitor-app):** `AlertPanel` (`formatTimestamp(openedAt)`, `formatTimeOnly(resolvedAt)`), `MainFrame` (`formatTimeOnly`), `AgentTableModel` (`formatTimestamp(lastSeen)`), `ServerDetailPanel` (`formatTimestamp`, `formatUptime`), `EmailMessageBuilder` (3 chỗ `formatTimestamp`), `AlertService` (`formatTimestamp` trong message offline), `AggregationService` (`bucketStart` cắt dữ liệu raw theo khung giờ).
- **Ghi chú:** `formatDuration` **chưa thấy nơi gọi** ngoài định nghĩa — chưa xác định.

### 3.25 ByteRateFormatter

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/util/ByteRateFormatter.java`
- **Vai trò:** Định dạng lưu lượng mạng cho GUI/e-mail và đổi giữa B/s (đơn vị wire) ↔ Mbps (đơn vị ngưỡng cấu hình).
- **Thành phần chính:**
  - Hằng `BITS_PER_BYTE = 8.0`, `BITS_PER_MEGABIT = 1_000_000.0`.
  - `formatRate(double bytesPerSecond)` — đơn vị **thập phân** (1 kB/s = 1000 B/s, đúng thiết bị mạng báo): `< 1000` → `"%.0f B/s"`; `< 1e6` → `"%.2f kB/s"`; `< 1e9` → `"%.2f MB/s"`; ngược lại `"%.2f GB/s"`; không hữu hạn hoặc âm → `"-"`.
  - `formatMbps(double)` — `"%.3f Mbps"` (vd. `9.87 Mbps`); không hợp lệ → `"-"`.
  - `toMbps(double)` = `bytesPerSecond * 8 / 1_000_000`; `toBytesPerSecond(double)` = đảo lại.
  - `formatBytes(long)` — đơn vị **nhị phân** (1024) cho dung lượng bộ nhớ: `B`, `KiB`, `MiB`, `GiB`, `TiB`; `unit == 0` → `"%.0f %s"` còn lại `"%.2f %s"`; `< 0` → `"-"`.
- **Được gọi ở đâu (monitor-app):** `AgentTableModel.formatRate` (cột in/out), `ServerDetailPanel.formatRate`, `DashboardPanel.formatBytes(totalMemoryBytes)`, `AlertService.toMbps` (so sánh `networkIn/OutThresholdMbps` với mẫu số liệu).
- **Ghi chú:** `formatMbps` **chưa thấy nơi gọi** ngoài định nghĩa — chưa xác định. Phân biệt đơn vị thập phân (tốc độ) vs nhị phân (bộ nhớ) là chủ đích theo Javadoc.

### 3.26 LoggingSetup

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/util/LoggingSetup.java`
- **Vai trò:** Cấu hình `java.util.logging` **một lần cho mỗi tiến trình**, ghi ra cả console lẫn file xoay vòng trong `logs/`.
- **Thành phần chính:**
  - `configure(String baseName)` — `static synchronized`, cờ `configured` đảm bảo idempotent (gọi lần 2 không làm gì); xoá mọi handler mặc định của root logger, đặt level `INFO`.
  - Thêm `ConsoleHandler` (INFO) + `FileHandler(logs/<baseName>-%g.log, 2*1024*1024 byte, 5 file, append=true)` — 5 file × 2 MiB, nối tiếp qua các lần khởi động lại; `baseName` rỗng → `"application"`.
  - Nếu tạo file lỗi (`IOException`/`SecurityException`) chỉ `WARNING` "File logging disabled" — không chặn ứng dụng.
  - Hạ logger `oshi` và `com.sun.mail` xuống `WARNING` (OSHI rất nhiều log ở mức FINE).
  - Class lồng private `SingleLineFormatter` — 1 record/1 dòng: `yyyy-MM-dd HH:mm:ss.SSS [LEVEL] shortLoggerName - message`, rồi in stack trace thụt lề nếu có `thrown`; tên logger cắt ngắn lấy phần sau dấu `.` cuối.
- **Được gọi ở đâu:** `AgentMain` (agent-app) — `LoggingSetup.configure("agent-" + stem(configPath))`; `MonitoringMain` (monitor-app) — `configure("monitor")`; `SimulatorMain` (simulator-app) — `configure("simulator")`.
- **Quan hệ:** dùng `AppPaths.ensureDirectory(AppPaths.logsDir())` và `ValidationUtil.isBlank`.
- **Ghi chú:** OSHI và các thư viện log qua SLF4J, binding `slf4j-jdk14` chuyển vào đây nên một cấu hình phủ hết; **không nơi nào truyền secret vào logger** (theo Javadoc trỏ tới `docs/02-kien-truc-va-nguyen-ly/09-kien-truc-goc.md`).

### 3.27 AppPaths

- **Định nghĩa:** `monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/util/AppPaths.java`
- **Vai trò:** Định vị thư mục `config/`, `data/`, `logs/` tương đối working directory để thư mục release copy đi đâu cũng chạy được.
- **Thành phần chính:**
  - Hằng `HOME_PROPERTY = "monitoring.home"` — system property override.
  - `home()` — `-Dmonitoring.home` (nếu set, `trim()` rồi `toAbsolutePath().normalize()`), ngược lại working directory hiện tại.
  - `configDir()`, `dataDir()`, `logsDir()` — `home().resolve(<tên>)`; `configFile(String)` — `configDir().resolve(fileName)`.
  - `ensureDirectory(Path)` — tạo thư mục nếu chưa có (`Files.createDirectories`), trả về chính nó; ném `IOException`.
- **Được gọi ở đâu:**
  - agent-app: `AgentMain` (`AppPaths.home().resolve(DEFAULT_CONFIG)`, `AppPaths.logsDir()` in lúc banner).
  - monitor-app: `MonitoringMain` (tương tự), `MonitorConfigLoader` (tường định vị file config tương đối), `EmailConfigLoader` (`AppPaths.configFile(DEFAULT_FILE_NAME)`), `DatabaseManager` (`ensureDirectory(parent)` cho file DB), `SettingsPanel` (hiển thị `logsDir()`, mở `configFile(...)`).
  - Trong module: `LoggingSetup` (tạo thư mục `logs/`).
- **Quan hệ:** dùng `ValidationUtil.isBlank` để kiểm tra giá trị override.
- **Ghi chú:** `HOME_PROPERTY` là cơ chế batch script dùng khi khởi động từ ngoài thư mục gốc dự án.

## 4. Mối quan hệ giữa các class (sơ đồ ASCII đơn giản)

```
                       KÊNH UDP (JSON, không tin cậy)
 agent-app / simulator-app ───────────────────────────────────────> monitor-app
 ┌────────────────────────────────┐            ┌───────────────────────────────────┐
 │ TelemetryScheduler             │            │ UdpTelemetryReceiver (buf 4096 B) │
 │ HeartbeatScheduler             │  datagram  │   │                               │
 │ PacketFactory (simulator)      │ ─────────> │   v                               │
 │   │  dựng TelemetryPacket /    │            │ TelemetryService                 │
 │   │  HeartbeatPacket            │            │   │ JsonMessageCodec.peekType()  │
 │   v                            │            │   v                              │
 │ PacketSigner ──> PacketCanonicalizer        │ PacketValidator ──┬─ SecretProvider│
 │      │                 │        │            │   │               │  (AgentRegistry)
 │      └────> HmacUtil ◄─┘        │            │   │               ├─ PacketCanonicalizer
 │                                 │            │   │               │        └─ HmacUtil
 └────────────────────────────────┘            │   │               ├─ ReplayProtector
                                               │   │               └─ ValidationUtil
                                               │   v
                                               │ ValidationResult.Reason
                                               │   └─> AlertType (SECURITY_*)
                                               v
                                        MetricSample / AlertService / HeartbeatMonitor


                      KÊNH TCP (frame: 4 byte độ dài + JSON UTF-8)
 monitor-app ─────────────────────────────────────────────────────> agent-app
 ┌────────────────────────────────┐      ┌─────────────────────────────────────┐
 │ AgentTcpConnection             │      │ TcpControlServer /                  │
 │   MessageFraming.write/read    │ <──> │ TcpClientSessionHandler             │
 │   JsonMessageCodec.peekType    │      │   MessageFraming + JsonMessageCodec │
 │   MessageType: PING/PONG,      │      │   MessageType: PONG,                │
 │     AGENT_INFO_*, CONFIG_*     │      │     AGENT_INFO_RESPONSE, CONFIG_ACK,│
 │   AgentInfoMessage,            │      │     ERROR                           │
 │   ConfigUpdateMessage,         │      │   AgentInfoMessage (điền bởi        │
 │   ConfigAckMessage             │      │     OshiSystemMetricsCollector)     │
 └────────────────────────────────┘      │   ConfigUpdateService ─> ConfigAck   │
                                         └─────────────────────────────────────┘

 Lớp nền (dùng bởi cả ba nhánh trên):
   ProtocolConstants ── giá trị mặc định/giới hạn ──> mọi nơi
   ProtocolException  <── JsonMessageCodec / MessageFraming ném ra
                         ── TelemetryService / TcpClientSessionHandler bắt
   ValidationUtil ── PacketValidator, AppPaths, ByteRateFormatter, LoggingSetup,
                     AgentConfigLoader, MonitorConfigLoader, EmailConfigLoader...
   TimeUtil / ByteRateFormatter ── UI + e-mail của monitor-app
   LoggingSetup ──> AgentMain / MonitoringMain / SimulatorMain (gọi 1 lần)
   AppPaths     ──> LoggingSetup + config loader + DatabaseManager + SettingsPanel

 Enum model (chủ yếu monitor-app):
   AlertEvent ──< AlertType, AlertSeverity, AlertState   (AlertService, AlertRepository,
                                                          Notifier, UI)
   AgentStatus ──> AgentRecord/AgentSnapshot/AgentRuntimeState, AgentRegistry,
                   HeartbeatMonitor, AgentRepository, AgentTableModel/DashboardPanel
```

Ghi chú đọc sơ đồ: mũi `──>` là hướng sử dụng (caller → callee); UDP là kênh không tin cậy nên mọi gói đều qua `PacketValidator`, còn TCP dùng `MessageFraming` để biết ranh giới message và chỉ tin nội dung sau khi đã đối chiếu trường `type`.
