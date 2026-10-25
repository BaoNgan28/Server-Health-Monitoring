# 07 - Cảnh báo và thông báo

Hệ thống giám sát server từ xa — Group 10 — HUTECH, Lập trình mạng máy tính

Tài liệu mô tả hệ thống cảnh báo và kênh thông báo theo **code**, tham chiếu
chính: `monitoring-common/.../model/{AlertType,AlertSeverity,AlertState,AlertEvent}.java`,
`monitor-app/.../service/{AlertService,HeartbeatMonitor}.java`,
`monitor-app/.../notification/*`, `monitor-app/.../config/{EmailConfig,EmailConfigLoader}.java`,
`monitor-app/.../repository/AlertRepository.java`, `monitor-app/.../ui/AlertPanel.java`.

---

## 1. Các loại cảnh báo

Enum `AlertType` — giá trị trên đường dây và trong cột `alerts.alert_type` chính
là **tên hằng**:

| Giá trị | Nhãn hiển thị (`getDisplayName()`) | Severity | Khi nào |
|---|---|---|---|
| `CPU_HIGH` | `CPU HIGH` | `WARNING` | CPU vượt ngưỡng |
| `MEMORY_HIGH` | `MEMORY HIGH` | `WARNING` | RAM vượt ngưỡng |
| `NETWORK_IN_HIGH` | `NETWORK TRAFFIC HIGH` | `WARNING` | Lưu lượng vào vượt ngưỡng |
| `NETWORK_OUT_HIGH` | `NETWORK TRAFFIC HIGH` | `WARNING` | Lưu lượng ra vượt ngưỡng |
| `AGENT_OFFLINE` | `SERVER OFFLINE` | `CRITICAL` | Không nhận được gói nào quá timeout |
| `AGENT_RECOVERED` | `SERVER ONLINE` | `INFO` | Agent vừa nhận lại được gói hợp lệ |
| `SECURITY_INVALID_HMAC` | `SECURITY INVALID HMAC` | `WARNING` | Chữ ký HMAC không khớp |
| `SECURITY_REPLAY_PACKET` | `SECURITY REPLAY PACKET` | `WARNING` | Sequence đã thấy rồi (gói phát lại) |

**Đúng 8 giá trị** — không có loại nào khác trong enum. Lưu ý
`NETWORK_IN_HIGH` và `NETWORK_OUT_HIGH` **dùng chung nhãn hiển thị**
`NETWORK TRAFFIC HIGH` (phân biệt bằng giá trị enum, không bằng nhãn).

Enum phụ:

- `AlertSeverity`: `INFO`, `WARNING`, `CRITICAL`
- `AlertState`: `OPEN`, `RESOLVED` — ghi thẳng vào cột `alerts.status`

**Lưu ý về `AgentStatus.WARNING`:** enum trạng thái agent có 4 giá trị
`UNKNOWN`, `ONLINE`, `WARNING`, `OFFLINE`, nhưng **không tìm thấy code nào gán
`WARNING`** cho một agent — chỉ có đọc nó ở `HeartbeatMonitor.check` (điều kiện
phòng thủ) và đếm ở `DashboardPanel`. Giá trị này **chưa xác định** trong hiện
tại.

---

## 2. Máy trạng thái cảnh báo: mở / đóng sự kiện

Code: `AlertService`.

**Nguyên tắc cốt lõi: mỗi cặp (agent, loại cảnh báo) hoặc là ĐÓNG, hoặc là có
đúng MỘT sự kiện `OPEN`.**

Khóa trong bộ nhớ: `agentId + "|" + ALERT_TYPE`, lưu trong
`ConcurrentHashMap<String, AlertEvent> openAlerts`.

```
   value vượt ngưỡng và chưa có gì OPEN
        --> mở MỘT sự kiện, ghi DB, đẩy GUI, gửi notifier
             |
   value vẫn trên ngưỡng
        --> KHÔNG XẢY RA GÌ (không mở thêm, không gửi thêm)
             |                     (đây là thứ chống 1 cảnh báo/giây,
             |                      1 email/giây)
   value rớt xuống DƯỚI (ngưỡng - hysteresis)
        --> đóng sự kiện (resolve)
             |
   value lên lại sau đó
        --> một sự kiện HOÀN TOÀN MỚI
```

### 2.1 Mở sự kiện — `evaluateThreshold`

```java
if (threshold <= 0.0d) return;                       // ngưỡng <= 0 => tắt, bỏ qua

String key = key(agent.getAgentId(), type);
AlertEvent open = openAlerts.get(key);

if (value > threshold) {
    if (open == null) {
        openAlert(...);   // chi tiết, ghi DB, eventBus, notifier
    }
    return;                                          // có rồi => không làm gì
}

if (open != null && value <= threshold - Math.max(0.0d, hysteresis)) {
    resolveAlert(key, open, now, "recovered to ...");
}
```

Điều kiện **mở** là `value > threshold` (nghiêm ngặt). Điều kiện **đóng** là
`value <= threshold - hysteresis`.

### 2.2 Hysteresis (dải hồi)

| Loại metric | Hằng | Giá trị | Nguồn |
|---|---|---|---|
| CPU, RAM | `PERCENTAGE_HYSTERESIS` | **2.0 điểm phần trăm** | `AlertService.java:50` |
| Tốc độ mạng (in/out) | `RATE_HYSTERESIS_FRACTION` | **5% của ngưỡng** (`ngưỡng × 0.05`) | `AlertService.java:52` |

```java
evaluateThreshold(..., sample.getCpuUsage(), agent.getCpuThreshold(), "%",
        PERCENTAGE_HYSTERESIS, now);                       // 2.0

double inMbps = ByteRateFormatter.toMbps(sample.getNetworkInBytesPerSecond());
evaluateThreshold(..., inMbps, agent.getNetworkInThresholdMbps(), "Mbps",
        agent.getNetworkInThresholdMbps() * RATE_HYSTERESIS_FRACTION, now);  // 5%
```

**Ví dụ:** CPU ngưỡng 85 % → chỉ đóng cảnh báo khi CPU về **83.0 % hoặc thấp
hơn**. Mạng vào ngưỡng 50 Mbps → chỉ đóng khi về **47.5 Mbps hoặc thấp hơn**.

**Tại sao cần:** nếu đóng đúng ở ngưỡng, một metric **dao động quanh ngưỡng**
sẽ mở và đóng cảnh báo xen kẽ trên từng mẫu (mở / đóng / mở / đóng…), mỗi lần
đều là một email.

**Lưu ý chính xác thuật ngữ:** code dùng **điểm phần trăm tuyệt đối** (2.0),
không phải 2 % **tương đối**. `10-giao-thuc-goc.md`/README viết *"two percentage points"*
— khớp code. `max(0.0, hysteresis)` bảo vệ khi hysteresis âm.

### 2.3 Chuyển đổi Mbps

```java
toMbps(bytesPerSecond) = bytesPerSecond * 8 / 1_000_000;
```

Ngưỡng trong DB và trong agent là **Mbps**, còn packet mang **byte/giây** —
`ByteRateFormatter.toMbps` đổi trước khi so.

### 2.4 Ngưỡng tắt

`threshold <= 0.0` → bỏ qua hoàn toàn loại cảnh báo đó (mặc định DB là 85 % /
85 % / 50 Mbps / 50 Mbps nên mặc định mọi loại đều bật).

### 2.5 Nạp lại khi khởi động

```java
public void load() {                       // gọi 1 lần lúc khởi động (MonitorApplication)
    openAlerts.clear();
    for (AlertEvent event : repository.findAllOpen()) {
        openAlerts.put(key(event.getAgentId(), event.getAlertType()), event);
    }
}
```

Các sự kiện `OPEN` được **tái tạo từ database**, nên sau khi restart client, điều
kiện vẫn đang mở vẫn tiếp tục được theo dõi đúng thay vì mở trùng.

---

## 3. Chống spam

| Cơ chế | Nguồn code |
|---|---|
| Một cặp (agent, loại) chỉ 1 `OPEN` | `openAlerts.containsKey(key)` / `open == null` |
| Giá trị vẫn trên ngưỡng → **không làm gì** | nhánh `return` trong `evaluateThreshold` |
| `raiseOffline` đã có `OPEN` → **return ngay** | `if (openAlerts.containsKey(key)) return;` — "heartbeat monitor không được lặp lại mỗi tick" |
| Sự kiện bảo mật rate limit **60 000 ms** | `SECURITY_EVENT_COOLDOWN_MS` |
| Email gửi qua **hàng đợi có giới hạn** (50 phần tử) | `EmailNotifier.QUEUE_CAPACITY` |

---

## 4. Cơ chế offline 15 s / recovered

### 4.1 Tham số

| Tham số | Giá trị | Nguồn |
|---|---|---|
| `heartbeat.timeout.ms` | **15 000** | `config/monitor.properties` |
| `heartbeat.check.interval.ms` | **2 000** | `config/monitor.properties` |
| Chu kỳ heartbeat agent | 5 000 ms | `config/agent01.properties` |

Với heartbeat 5 s và timeout 15 s, **hai heartbeat bị mất** mới đủ để coi một
agent khỏe mạnh là chết (`10-giao-thuc-goc.md` mục 9).

### 4.2 Đi xuống: `HeartbeatMonitor.check(nowMs)` — periodic sweep

```java
for (AgentRuntimeState state : registry.states()) {
    AgentStatus status = state.getStatus();
    if (status != ONLINE && status != WARNING) continue;   // chỉ agent ĐÃ nghe thấy
    long lastSeen = state.getLastSeen();
    if (lastSeen <= 0L) continue;
    if (nowMs - lastSeen > timeoutMs) {
        registry.setStatus(agentId, OFFLINE);
        alertService.raiseOffline(state.getRecord(), lastSeen, timeoutMs, nowMs);
    }
}
```

- Sweep chạy mỗi 2 000 ms trên thread `monitor-scheduler-*`.
- **Đúng một** sự kiện `AGENT_OFFLINE` bất kể sweep chạy bao nhiêu lần — bởi
  `raiseOffline` tự kiểm tra đã có `OPEN` chưa.
- Agent `UNKNOWN` **không bao giờ** bị coi là chết. Lý do: `lastSeen` đọc từ DB
  chỉ là **lịch sử, không phải bằng chứng**. Nếu không có điều kiện này, mỗi lần
  restart client sẽ gửi email thông báo sập cho **mọi** agent đã đăng ký cùng lúc
  (`HeartbeatMonitor.check` comment).
- Bất kỳ gói hợp lệ nào — telemetry **hoặc** heartbeat — đều làm mới
  `lastSeen` (`markAlive`).

Chi tiết ghi vào sự kiện `AGENT_OFFLINE`:

```java
event.setCurrentValue(lastSeen);   // thời điểm last-seen
event.setThreshold(timeoutMs);     // timeout (ms)
event.setUnit("ms");
// message: "No valid packet received since <TimeUtil.formatTimestamp(lastSeen)>"
```

Body email offline đọc hai trường này thành "Last Seen" và timeout tính bằng giây.

### 4.3 Đi lên: `HeartbeatMonitor.markAlive(agentId, packetTimestamp, nowMs)`

```java
registry.markSeen(agentId, Math.max(packetTimestamp, nowMs));   // đồng hồ máy thu
AgentStatus previous = state.getStatus();
if (previous == ONLINE) return;
registry.setStatus(agentId, ONLINE);
if (previous == OFFLINE) {
    alertService.raiseRecovered(state.getRecord(), nowMs);
}
```

- `Math.max(packetTimestamp, nowMs)`: ghi thời điểm đến theo **đồng hồ máy thu**,
  để agent có đồng hồ hơi chậm không bị coi là stale ngay khi packet vừa đến.
- `raiseRecovered` làm hai việc:
  1. nếu có `AGENT_OFFLINE` đang `OPEN` → **đóng** nó (`"Agent is reachable again"`);
  2. tạo `AGENT_RECOVERED` với severity `INFO`, **lưu sẵn trạng thái `RESOLVED`**
     (`resolvedAt = now`) vì hồi phục là **một khoảnh khắc**, không phải một điều
     kiện đang mở — rồi `repository.insert` + `eventBus.fireAlert` +
     `notifier.sendAlert`.

### 4.4 Thứ tự khi nhận packet

```
  PacketValidator.accepted
        |
        v
  heartbeatMonitor.markAlive     <-- LIVENESS TRƯỚC
        |
        v
  registry.applySample           <-- view trực tiếp + event -> GUI
        |
        v
  metricRepository.insertRaw     <-- 1 dòng metric_raw
        |
        v
  alertService.evaluate          <-- ngưỡng, mở/đóng, email
```

Mục đích: một máy vừa trở lại báo cáo **hồi phục TRƯỚC** khi báo CPU cao
(`TelemetryService.handleTelemetry` comment).

**Lưu ý thất bại trong lưu:** `insertRaw` được bọc try/catch riêng — trục trặc
DB **không** làm mất view trực tiếp và cảnh báo.

---

## 5. Sự kiện bảo mật

| Quy tắc | Code |
|---|---|
| Chỉ `INVALID_HMAC` và `REPLAY` thành sự kiện bảo mật | `TelemetryService.securityTypeOf` |
| **Không gửi email** | `AlertService.recordSecurityEvent` **không** gọi `notifier.sendAlert` |
| Rate limit: tối đa **1 / agent / loại / 60 000 ms** | `SECURITY_EVENT_COOLDOWN_MS = 60_000L`, key `agentId\|TYPE` |
| Lưu sẵn `RESOLVED` | `event.setState(RESOLVED)`, `setResolvedAt(now)`, `setOpenedAt(now)` |
| Severity `WARNING` | `event.setSeverity(WARNING)` |
| Chỉ hiện trên GUI + log | `eventBus.fireAlert(event)` + `LOG.warning("SECURITY ...")` |
| Agent chưa đăng ký (vi phạm khóa ngoại) | nuốt `RuntimeException`, giữ dòng log |

Lý do: **một trận flood packet giả mạo không được biến thành một trận flood
email** (comment trong `AlertService`).

Các lý do khác (`MALFORMED`, `UNKNOWN_AGENT`, `UNSUPPORTED_VERSION`,
`TIMESTAMP_OUT_OF_TOLERANCE`) **không** sinh cảnh báo — chỉ `LOG.warning` +
`eventBus.firePacketRejected`.

---

## 6. Luồng gửi email

### 6.1 Sơ đồ

```
   AlertService.store() / raiseRecovered()
        |
        v
   notifier.sendAlert(event)          Notifier (interface)
        |
        +--> CompositeNotifier  ------ fan-out, mỗi delegate cách ly lỗi
                 |
                 +--> EmailNotifier (khi EmailConfig.isConfigured())
                 |        |  1 thread "email-sender" (daemon)
                 |        |  ArrayBlockingQueue(50)
                 |        v
                 |    EmailMessageBuilder.forAlert(config, event)
                 |        |  subject + body (không có network code)
                 |        v
                 |    SmtpMailTransport.send(message)
                 |        |  jakarta.mail Session + Authenticator
                 |        v
                 |    smtp.gmail.com:587  STARTTLS  ->  người nhận
                 |
                 +--> NoOpNotifier (khi CHƯA cấu hình)  -> chỉ ghi lại lý do
```

### 6.2 `Notifier` interface

```java
sendAlert(AlertEvent)          // gửi một cảnh báo
sendTestMessage()              // gửi mail thử, TRẢ VỀ kết quả đồng bộ (dialog)
isEnabled()                    // kênh có đang bật không
describe()                     // mô tả cho GUI
close()                        // giải phóng
```

`CompositeNotifier` bọc một danh sách delegate: mỗi delegate được **cách ly**
(một kênh ném exception thì các kênh còn lại vẫn nhận cảnh báo). Khi email chưa
cấu hình, `MonitorApplication.buildNotifier` trả
`new CompositeNotifier(new NoOpNotifier(disabledReason))` — **không bao giờ làm
chết quá trình khởi động**.

### 6.3 `EmailNotifier` — nền cho việc gửi

| Hạng mục | Giá trị |
|---|---|
| Hàng đợi | `ArrayBlockingQueue(50)` — `QUEUE_CAPACITY = 50` |
| Thread | `ThreadPoolExecutor(1, 1, ...)` tên `"email-sender"`, daemon |
| Hàng đợi đầy | `RejectedExecutionException` → `droppedCount++` + `LOG.warning("Email notification dropped, the send queue is full: ...")` |
| Đếm | `sentCount`, `failedCount`, `droppedCount` (`LongAdder`) |
| Lỗi gửi | `lastError = safeReason(e)` — **mật khẩu bị thay bằng `***`** trước khi vào log |
| Shutdown | `shutdown()` + `awaitTermination(5 s)` rồi `shutdownNow()` |

**Một email không bao giờ chặn** UDP receiver, Swing EDT hay heartbeat timer;
máy mail chết không làm hàng đợi phình vô hạn — đầy thì **bỏ** với một dòng
warning.

### 6.4 `EmailMessageBuilder` — nội dung

**Chủ đề:**

```java
buildSubject(event) = "[" + subjectTag + "] " + displayName + " - " + agentId
```

| | |
|---|---|
| `AGENT_RECOVERED` | tag = `RECOVERED` → `[RECOVERED] SERVER ONLINE - AGENT-01` |
| còn lại | tag = tên severity (`INFO`/`WARNING`/`CRITICAL`) → `[WARNING] CPU HIGH - AGENT-01` |
| Email thử | `[TEST] Group 10 Server Health Monitoring` |

**Thân mail** (header `GROUP 10 - SERVER HEALTH MONITORING`), ba dạng:

1. `AGENT_OFFLINE` → `CRITICAL ALERT`, gồm Agent, Server, Status `OFFLINE`,
   Reason `"No valid heartbeat received for more than N seconds."`
   (chuyển `threshold` ms → giây), `Last Seen` (đọc `currentValue`),
   `Detected At`.
2. `AGENT_RECOVERED` → `RECOVERY NOTICE`, gồm Agent, Server, Status `ONLINE`,
   Reason `"Valid telemetry is being received again."`, `Detected At`.
3. Các loại còn lại → Alert Type, Severity, Agent, Server,
   `Current <CPU|Memory|Network IN|Network OUT>` (định dạng `%.1f` + đơn vị),
   `Threshold`, `Detected At`.

Lớp này **không chứa network code** nên cách diễn đạt có thể được assert bằng
unit test mà không gửi gì.

### 6.5 `SmtpMailTransport` — giao thức

Thuật ngữ: **SMTP** (Simple Mail Transfer Protocol — giao thức chuyển thư tiêu
chuẩn); **STARTTLS** (bắt đầu kết nối TLS không mã hóa rồi nâng cấp thành mã hóa).

| Thuộc tính | Giá trị | Nguồn |
|---|---|---|
| Host mặc định | `smtp.gmail.com` | `EmailConfigLoader.DEFAULT_HOST` |
| Port mặc định | `587` | `EmailConfigLoader.DEFAULT_PORT` |
| `mail.smtp.auth` | `true` (mặc định) | `smtp.auth` |
| `mail.smtp.starttls.enable` | `true` (mặc định) | `smtp.starttls` |
| `mail.smtp.starttls.required` | `true` khi bật STARTTLS | **từ chối** rơi vào phiên không mã hóa nếu STARTTLS thất bại |
| `mail.smtp.ssl.protocols` | `TLSv1.2 TLSv1.3` | |
| `mail.smtp.connectiontimeout` | 15 000 ms | `CONNECT_TIMEOUT_MS` |
| `mail.smtp.timeout` / `writetimeout` | 20 000 ms | `READ_TIMEOUT_MS` |
| Tên người gửi hiển thị | `Group 10 Server Health Monitoring` | `EmailConfig.SENDER_NAME` |

Constructor **ném exception nếu `EmailConfig` chưa `isConfigured()`** — không
bao giờ tạo transport với credential thiếu.

Thư gửi bằng **Gmail App Password** (mật khẩu ứng dụng), **không bao giờ** dùng
mật khẩu tài khoản (README mục 18).

---

## 7. Thứ tự đọc credential

`EmailConfigLoader.load()` — thứ tự ưu tiên:

```
  1) BIẾN MÔI TRƯỜNG (environment variables)
        SMTP_USERNAME        -> username
        SMTP_APP_PASSWORD    -> app password
        ALERT_RECIPIENT_EMAIL-> recipient
  2) FILE NGOÀI JAR:  config\email-secrets.properties
        smtp.username
        smtp.appPassword
        smtp.recipient
        smtp.host       (mặc định smtp.gmail.com)
        smtp.port       (mặc định 587)
        smtp.starttls   (mặc định true)
        smtp.auth       (mặc định true)
```

```java
String username = firstUsable(value(env, ENV_USERNAME), properties.getProperty(KEY_USERNAME));
```

`firstUsable(preferred, fallback)` chọn giá trị môi trường **nếu nó dùng được**,
ngược lại rơi về file. Env đứng trước để production có thể **tiêm secret không cần
file**, còn demo chỉ cần sửa một file.

File được đọc từ **working directory chứ không từ trong jar** → điền credential
rồi chạy lại, không cần rebuild.

### 7.1 Chưa cấu hình thì sao

Các placeholder nhà máy:

```java
PLACEHOLDER_PASSWORD = "PASTE_GMAIL_APP_PASSWORD_HERE";
PLACEHOLDER_RECIPIENT = "PASTE_RECIPIENT_EMAIL_HERE";
```

`isUsable(value)` coi là **không dùng được** khi: trống, trùng placeholder (không
phân biệt hoa thường), hoặc bắt đầu bằng `PASTE_` / `YOUR_` (không phân biệt hoa
thường).

`findProblem(...)` trả về lý do theo thứ tự:

| Thứ tự | Lý do |
|---|---|
| 1 | `smtp.username is empty` |
| 2 | `smtp.appPassword is empty or still the placeholder` |
| 3 | `smtp.recipient is empty or still the placeholder` |
| 4 | `smtp.username is not a valid e-mail address` |
| 5 | `smtp.recipient is not a valid e-mail address` |

Nếu có vấn đề → `EmailConfig.disabled(host, port, startTls, auth, username, recipient, reason)`:

- `isConfigured() = false`, `appPassword = null`
- `MonitorApplication.buildNotifier` trả **`NoOpNotifier`** → **không có lần
  đăng nhập SMTP nào được thử**
- log một lần: `"Email notifications disabled: credentials not configured (<reason>)"`
- Mọi thứ khác chạy bình thường — đây là **trạng thái bình thường, không phải
  lỗi khởi động**.

### 7.2 Biểu tượng `NOT CONFIGURED` trong UI

| Vị trí | Nhãn |
|---|---|
| `SettingsPanel` — `Email Notification Status:` | `CONFIGURED` (xanh) / `NOT CONFIGURED` (đỏ) |
| `SettingsPanel` — dòng lý do | hiển thị `EmailConfig.getDisabledReason()` |
| `MainFrame` tiêu đề cửa sổ | hậu tố `CONFIGURED` / `NOT CONFIGURED` |
| Nút bấm | **`Reload Email Configuration`** |

**Khác biệt docs/code:** README mục 18 nói *"edit that one file and restart"*.
Code thực ra cho phép **nạp lại không cần restart**: nút `Reload Email
Configuration` gọi `application.reloadEmailConfiguration()` → `EmailConfigLoader.load()`
lại, dựng lại `Notifier` và dựng lại `AlertService` với notifier mới
(`MonitorApplication:161`). **Chọn code**: không cần khởi động lại.

---

## 8. Bảng `alerts` trong DB và trong UI

### 8.1 Trong database

Cột: `id, agent_id, alert_type, severity, message, opened_at, resolved_at,
status, current_value, threshold, unit` — xem mục 2.4 của
[06-luu-tru-va-tong-hop.md](06-luu-tru-va-tong-hop.md) và `schema.sql`.

Ghi (`AlertRepository.insert`):

- `resolved_at` = giá trị nếu `> 0`, ngược lại `setNull` → `NULL` khi còn `OPEN`;
- `status` = `event.getState().name()` → `"OPEN"` hoặc `"RESOLVED"`;
- trả về `id` sinh ra (`Statement.RETURN_GENERATED_KEYS`).

Đóng (`AlertRepository.resolve`):

```sql
UPDATE alerts SET status = ?, resolved_at = ? WHERE id = ?
```

Truy vấn thường dùng:

| Method | Truy vấn |
|---|---|
| `findAllOpen()` | `WHERE status='OPEN' ORDER BY opened_at DESC` |
| `findOpen(agentId, type)` | `WHERE agent_id=? AND alert_type=? AND status='OPEN' ORDER BY opened_at DESC LIMIT 1` |
| `findRecent(limit)` | `ORDER BY opened_at DESC LIMIT ?` |
| `findByAgent(agentId, limit)` | `WHERE agent_id=? ORDER BY opened_at DESC LIMIT ?` |
| `countOpen()` / `count()` | đếm |

Index đường nóng: `idx_alerts_agent_status ON alerts (agent_id, status)`.

### 8.2 Trong UI (thẻ Alerts)

`AlertPanel` — bảng có **6 cột**:

```
   +---------------------+------------+------------------------+----------+-----------+---------------------------+
   | Time                | Agent      | Alert Type             | Severity | State     | Message                   |
   +---------------------+------------+------------------------+----------+-----------+---------------------------+
```

- Nút **Refresh** (`reload()`), checkbox **chỉ hiển thị OPEN** (`openOnly`).
- Panel nạp cảnh báo qua `AlertRepository` (qua service), không tự viết SQL —
  đúng quy tắc phân lớp: **UI không bao giờ mở socket, không viết SQL, không nói
  chuyện SMTP** (09-kien-truc-goc.md mục 2).
- Sự kiện đến từ worker thread và được chuyển sang EDT bằng
  `SwingUtilities.invokeLater` (qua `MonitorEventBus` → `MainFrame`).

---

## 9. Tóm tắt: điều gì được gửi đi đâu

| Loại | Ghi DB | Hiện GUI | Gửi email |
|---|---|---|---|
| `CPU_HIGH`, `MEMORY_HIGH`, `NETWORK_IN_HIGH`, `NETWORK_OUT_HIGH` (mở) | có (`OPEN`) | có | **có** |
| Các loại trên (đóng) | `UPDATE` → `RESOLVED` | có | không (chỉ log) |
| `AGENT_OFFLINE` | có (`OPEN`) | có | **có** |
| `AGENT_RECOVERED` | có (`RESOLVED` ngay) | có | **có** |
| `SECURITY_INVALID_HMAC`, `SECURITY_REPLAY_PACKET` | có (`RESOLVED`, rate limit 60 s) | có | **KHÔNG** |
| Lỗi parse / sai phiên bản / sai timestamp / agent chưa đăng ký | không | chỉ đếm + log | không |

Nếu SMTP lỗi hoặc mạng hỏng: lỗi được ghi kèm **lý do của server** (đã che
credential); cảnh báo **vẫn tới GUI và database**
(09-kien-truc-goc.md mục 6).

---

## Tài liệu liên quan

- [09-kien-truc-goc.md](09-kien-truc-goc.md) — phân lớp, luồng dữ liệu, mô hình thread
- [11-co-so-du-lieu-goc.md](11-co-so-du-lieu-goc.md) — bảng, index, retention, chế độ demo
- [01-cau-truc-du-an.md](01-cau-truc-du-an.md)
- [02-mo-hinh-he-thong.md](02-mo-hinh-he-thong.md)
- [03-cach-thuc-hoat-dong.md](03-cach-thuc-hoat-dong.md)
- [08-mo-hinh-da-luong.md](08-mo-hinh-da-luong.md)
