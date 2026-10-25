# 05 - Bảo mật HMAC và chống phát lại (replay)

Hệ thống giám sát server từ xa — Group 10 — HUTECH, Lập trình mạng máy tính

Tài liệu mô tả cơ chế xác thực gói UDP theo **code**, tham chiếu chính:
`monitoring-common/.../security/*`, `monitor-app/.../service/TelemetryService.java`,
`config/monitor.properties`, `scripts/run-invalid-hmac-test.bat`,
`scripts/run-replay-test.bat`.

---

## 1. HMAC-SHA256 là gì?

**HMAC** (Hash-based Message Authentication Code — mã xác thực thông điệp dựa
trên hàm băm) là cách ghép một **khóa bí mật chung** (shared secret) với một
thông điệp rồi băm, cho ra một chuỗi cố định dùng để chứng minh hai điều cùng
lúc:

1. **Tính toàn vẹn** (integrity): thông điệp không bị sửa trên đường đi.
2. **Tính xác thực** (authentication): người gửi **có** đúng khóa bí mật đó.

**SHA-256** là hàm băm cho ra 256 bit (32 byte = 64 ký tự hex). Công thức HMAC
theo chuẩn RFC 2104:

```
HMAC(K, m) = H( (K' ⊕ opad) || H( (K' ⊕ ipad) || m ) )
```

Trong đó `H` là SHA-256, `opad`/`ipad` là hai hằng số xung đột (padding) 64 byte.
Việc bọc hai lần (double hashing) này bảo vệ HMAC khỏi cuộc tấn công mở rộng
chiều của hàm băm (length-extension attack) — điều mà cách viết đơn giản
`SHA256(key || message)` không chống được.

Trong dự án (`HmacUtil.hmacSha256Hex`):

```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
return toHex(mac.doFinal(data.getBytes(UTF_8)));   // hex CHỮ THƯỜNG
```

- Khóa lấy từ `secret.getBytes(StandardCharsets.UTF_8)`.
- Chuỗi ký (`data`) lấy từ `data.getBytes(StandardCharsets.UTF_8)`.
- Kết quả là hex **chữ thường** (`0123456789abcdef`), độ dài 64 ký tự.
- Secret rỗng/null → `IllegalArgumentException`; JRE thiếu HmacSHA256 →
  `IllegalStateException`.

**Tại sao không ký trực tiếp JSON?** Hai bộ mã hóa (encoder) có thể sắp thứ tự
trường khác nhau hoặc định dạng số khác nhau trong khi mô tả **cùng một** mẫu
đo. Chữ ký khi đó phụ thuộc vào **trình mã hóa** chứ không phải vào **dữ liệu**
— một packet hợp lệ có thể bị từ chối chỉ vì máy gửi dùng cách định dạng khác.
Vì vậy gói được chuẩn hóa về **chuỗi canonical** trước khi ký.

---

## 2. Chuỗi canonical (chuỗi chuẩn hóa)

Code: `monitoring-common/.../security/PacketCanonicalizer.java`.

### 2.1 Các trường và thứ tự

**Telemetry** — các trường nối bằng ký tự `|` (SEPArator = `'|'`):

```
protocolVersion | type | agentId | hostname | sessionId | timestamp | sequence |
cpuUsage | memoryUsage | connectionCount | uptimeSeconds |
networkInBytesPerSecond | networkOutBytesPerSecond
```

(13 trường, không có `hmac`.)

**Heartbeat** — chuỗi ngắn hơn:

```
protocolVersion | type | agentId | hostname | sessionId | timestamp | sequence
```

(7 trường, không có `hmac`.)

### 2.2 Quy tắc bắt buộc cả hai phía phải áp dụng giống hệt

| Quy tắc | Code |
|---|---|
| Trường `hmac` **không bao giờ** nằm trong chuỗi nó tự ký | `canonicalize` không append `hmac` |
| `double` hiển thị bằng **`Locale.ROOT`** và **đúng 4 chữ số thập phân** | `String.format(Locale.ROOT, "%.4f", ...)` |
| `-0.0` được chuẩn hóa về `0.0000` | `double normalised = value == 0.0d ? 0.0d : value;` — trong Java `-0.0d == 0.0d` là **đúng**, nên nhánh này nuốt dấu trừ |
| Chuỗi `null` thành chuỗi rỗng, **không** thành văn bản `"null"` | `nullSafe(String)` trả `""` |
| Số không hữu hạn (NaN, vô cực) bị **từ chối** thay vì ký thành `"NaN"` | `formatDouble` ném `IllegalArgumentException` khi `Double.isNaN/isInfinite` |
| Số nguyên (`timestamp`, `sequence`, `connectionCount`, `uptimeSeconds`) viết nguyên văn | `sb.append(packet.getTimestamp())` — kiểu `long`, không qua `%.4f` |

**Vì sao `Locale.ROOT` quan trọng:** `String.format` mặc định dùng locale của
JVM. Máy có locale dùng **dấu phẩy** làm dấu thập phân (việt Nam, Đức, Pháp…)
sẽ in `23,5000` thay vì `23.5000` → chữ ký hai bên không khớp → mọi packet bị
từ chối. Ép `Locale.ROOT` (locale trung tính, dùng dấu chấm) loại bỏ phụ thuộc
vị trí địa lý.

**Vì sao đúng 4 chữ số thập phân:** nếu để nguyên độ chính xác của `double`, hai
máy có thể biểu diễn khác nhau ở chữ số xa (rounding). Ép một format cố định làm
chuỗi ký **tuyệt đối xác định** (deterministic).

### 2.3 Ví dụ chuỗi canonical thật

Telemetry (đã xuống dòng cho dễ đọc; chuỗi thật **không** có xuống dòng):

```
1.0|TELEMETRY|AGENT-01|SRV-HANOI-01|0f0b9a61-3a3b-4a64-9a3d-1d0c2b5f7e11|1790668331245|417|23.5000|61.2500|42|143284|12345.5000|6789.2500
```

Heartbeat:

```
1.0|HEARTBEAT|AGENT-01|SRV-HANOI-01|0f0b9a61-3a3b-4a64-9a3d-1d0c2b5f7e11|1790668336245|418
```

Chữ ký:

```
hmac = lowercase_hex( HmacSHA256( sharedSecret, canonicalString ) )
```

Bên gửi gọi `PacketSigner.sign(packet, secret)` (agent và simulator dùng chung
lớp này, nên **cách ký giống hệt cách kiểm tra**).

---

## 3. So sánh constant-time (so sánh không phụ thuộc thời gian)

```java
public static boolean constantTimeEquals(String expected, String actual) {
    if (expected == null || actual == null) return false;
    byte[] a = expected.toLowerCase(Locale.ROOT).getBytes(UTF_8);
    byte[] b = actual.toLowerCase(Locale.ROOT).getBytes(UTF_8);
    return MessageDigest.isEqual(a, b);
}
```

**Vấn đề của so sánh thông thường:** `expected.equals(actual)` trả kết quả ngay
tại **ký tự sai đầu tiên**. Kẻ tấn công đo **thời gian phản hồi** (timing
attack) để đoán từng ký tự một: phản hồi nhanh hơn nghĩa là ký tự đầu khớp.

**`MessageDigest.isEqual`** duyệt toàn bộ hai mảng bất kể có khác ở đâu, nên
thời gian **không tiết lộ** có bao nhiêu ký tự đầu khớp
(`HmacUtil` javadoc; `10-giao-thuc-goc.md` mục 4.2).

Chi tiết phụ:

- Hai phía đều hạ chữ thường bằng `Locale.ROOT` trước khi so → chữ ký hex viết
  hoa ở bên gửi vẫn được chấp nhận (so sánh **không phân biệt hoa thường**).
- `null` (bất kỳ bên nào) → `false`, không so sánh gì cả.

---

## 4. Quy trình xác thực từng bước khi nhận gói

Lớp `PacketValidator` — "cửa ngõ duy nhất" mọi gói UDP đi qua. **Kiểm tra chạy
theo đúng thứ tự này, thất bại đầu tiên làm dừng phần còn lại**
(`PacketValidator` javadoc + `10-giao-thuc-goc.md` mục 5).

```
  DatagramPacket
      |
      v
  [1] Hình dạng & khoảng giá trị (shape)
      |  - sessionId không được trống
      |  - sequence >= 0
      |  - cpuUsage, memoryUsage, netIn, netOut phải HỮU HẠN (không NaN/Inf)
      |  - cpuUsage, memoryUsage trong 0..100
      |  - connectionCount, uptimeSeconds >= 0
      |  - networkIn/Out >= 0
      |  (heartbeat chỉ kiểm: sequence >= 0, sessionId không trống)
      v
  [2] Phiên bản giao thức
      |  ProtocolConstants.PROTOCOL_VERSION.equals(protocolVersion)
      |  => lỗi UNSUPPORTED_VERSION
      v
  [3] Agent có biết không (known agent)
      |  - agentId không trống
      |  - secretProvider.secretFor(agentId) không trống
      |  => nếu không: UNKNOWN_AGENT — GÓI BỊ BỎ, KHÔNG TÍNH CHỮ KÝ
      |     (một agentId chưa đăng ký không có secret để kiểm tra)
      v
  [4] Chữ ký (signature)
      |  canonical = PacketCanonicalizer.canonicalize(packet)
      |  expected  = HmacUtil.hmacSha256Hex(secret, canonical)
      |  constantTimeEquals(expected, packet.getHmac())
      |  => lỗi INVALID_HMAC
      v
  [5] Tính mới của timestamp
      |  |nowMs - packet.timestamp| <= timestampToleranceMs (30 000 ms)
      |  => lỗi TIMESTAMP_OUT_OF_TOLERANCE  (kiểm tra CẢ HAI HƯỚNG)
      v
  [6] Chống phát lại (replay)
      |  replayProtector.accept(agentId, sessionId, sequence)
      |  => lỗi REPLAY
      v
    ACCEPTED -> HeartbeatMonitor.markAlive -> AgentRegistry.applySample
              -> MetricRepository.insertRaw -> AlertService.evaluate -> GUI
```

Điểm mấu chốt: **một gói bị từ chối không chạy bất cứ thứ gì phía sau** — không
cập nhật dashboard, không ghi dòng `metric_raw`, không sinh cảnh báo. Nó chỉ được
đếm, ghi log và ghi thành **sự kiện bảo mật**. Nhờ vậy một datagram giả mạo
khẳng định CPU 100% không thể hiện trên bảng điều khiển và không thể kích hoạt
cảnh báo CPU giả (`TelemetryService` javadoc).

Lưu ý thứ tự giữa **liveness (sống chết) và ngưỡng**: `markAlive` được gọi
**trước** `registry.applySample` và `alertService.evaluate`, nên một máy vừa
trở lại báo cáo `AGENT_RECOVERED` **trước** khi báo CPU cao
(`TelemetryService.handleTelemetry`).

**Kết quả kiểm tra** (`ValidationResult.Reason`):

| Reason | Nghĩa | Trở thành sự kiện bảo mật? |
|---|---|---|
| `ACCEPTED` | hợp lệ | — |
| `MALFORMED` | JSON sai, thiếu trường hoặc ngoài khoảng | không (chỉ log) |
| `UNKNOWN_AGENT` | agentId chưa đăng ký | không (chỉ log) |
| `UNSUPPORTED_VERSION` | phiên bản giao thức khác | không (chỉ log) |
| `INVALID_HMAC` | chữ ký không khớp | **có** → `SECURITY_INVALID_HMAC` |
| `TIMESTAMP_OUT_OF_TOLERANCE` | lệch đồng hồ quá 30 s | không (chỉ log) |
| `REPLAY` | sequence đã thấy rồi | **có** → `SECURITY_REPLAY_PACKET` |

Chỉ hai loại `INVALID_HMAC` và `REPLAY` được coi là sự kiện bảo mật
(`TelemetryService.securityTypeOf`).

---

## 5. Chống phát lại (replay protection)

Code: `monitoring-common/.../security/ReplayProtector.java`.

### 5.1 Cửa sổ sequence 64 bit

Mỗi agent đánh số packet của mình trong một **session**. Monitor giữ, theo từng
bộ `(agentId, sessionId)`:

- `highest`: sequence cao nhất đã chấp nhận;
- `seen`: một **bitmap 64 bit** — bit `i` được đặt khi sequence `highest - i`
  đã thấy.

Đây là cấu trúc mà **IPsec** sử dụng. `WINDOW_SIZE = 64`.

| Tình huống | Kết quả |
|---|---|
| sequence mới hơn mọi thứ đã thấy | **chấp nhận**, cửa sổ trượt tới (shift bitmask) |
| sequence nằm trong cửa sổ, bit chưa đặt | **chấp nhận** — chỉ đến muộn thôi |
| sequence nằm trong cửa sổ, bit đã đặt | **TỪ CHỐI**, lặp lại (replay) |
| sequence cũ hơn cửa sổ (age >= 64) | **TỪ CHỐI** |

Chi tiết thuật toán trong `accept(...)`:

```java
if (sequence > window.highest) {
    long shift = sequence - window.highest;
    window.seen = shift >= WINDOW_SIZE ? 1L : (window.seen << shift) | 1L;
    window.highest = sequence;                    // -> chấp nhận
}
long age = window.highest - sequence;
if (age >= WINDOW_SIZE) return false;            // quá cũ -> từ chối
long bit = 1L << age;
if ((window.seen & bit) != 0L) return false;     // đã thấy -> từ chối
window.seen |= bit;                              // packet đến muộn thật -> chấp nhận
```

**Tại sao là cửa sổ chứ không phải "phải lớn hơn gói trước"?** UDP không hứa
thứ tự: một packet có thể vượt packet khác trên mạng, và một receiver chia việc
xử lý cho nhiều thread có thể **xong sai thứ tự**. Yêu cầu sequence tăng nghiêm
ngặt sẽ vứt bỏ packet thật chỉ đến trễ. Hơn nữa, hàng đợi của receiver có chính
sách `CallerRunsPolicy` khi đầy — việc chạy chệch thứ tự lại càng dễ xảy ra
(`UdpTelemetryReceiver` javadoc).

### 5.2 Tại sao cửa sổ tính theo **session** chứ không theo agent

Một agent sau khi khởi động lại bắt đầu đếm lại từ 1. Đối với một cửa sổ duy
nhất theo agent, điều này **không phân biệt được với replay** → máy vừa khởi động
sẽ ngồi đó `OFFLINE` mãi mà không có lý do.

- Mỗi lần chạy agent sinh `sessionId = UUID.randomUUID().toString()`
  (`AgentRuntime:48`) và `sessionId` **nằm trong chuỗi canonical**, tức được
  **ký** — kẻ tấn công không thể giả mạo session.
- Cửa sổ mới mở cho session mới, session cũ giữ cửa sổ riêng
  (`MAX_SESSIONS_PER_AGENT = 4`, dùng `LinkedHashMap` access-ordered với
  `removeEldestEntry` → bỏ session cũ nhất sau 4 session).
- Capture gói từ session cũ replay vào session mới **vẫn rơi vào cửa sổ của
  session cũ**, nơi bit đã đặt → **vẫn bị từ chối**.
- Nhờ tách session, **hai người gửi không làm nhiễu nhau**: simulator dùng
  session riêng (`PacketFactory.SESSION_ID = "SIM-" + UUID.randomUUID()`), nên
  packet của nó với sequence lớn không đẩy cửa sổ của agent thật ra ngoài phạm vi.

### 5.3 Timestamp ±30 giây

| Tham số | Giá trị | Nguồn |
|---|---|---|
| `ProtocolConstants.DEFAULT_TIMESTAMP_TOLERANCE_MS` | `30000` | `ProtocolConstants.java:31` |
| `packet.timestamp.tolerance.ms` | `30000` | `config/monitor.properties` |
| Nạp vào validator | `new PacketValidator(registry, new ReplayProtector(), config.getTimestampToleranceMs())` | `MonitorApplication.java:110` |

```java
long drift = Math.abs(nowMs - timestamp);
if (drift > timestampToleranceMs) reject(TIMESTAMP_OUT_OF_TOLERANCE);
```

Đo cả hai chiều — packet "tương lai" (đồng hồ máy gửi nhanh) cũng bị từ chối
y như packet "quá khứ".

**Rủi ro còn lại** của mọi scheme kết hợp timestamp + sequence: một capture được
replay **trong** dung cho phép 30 giây tới một receiver **chưa bao giờ thấy gói
gốc**. Khoảng dung 30 giây chính là thứ giới hạn rủi ro đó
(`10-giao-thuc-goc.md` mục 6).

**Lưu ý khác biệt docs/code:** README mục 14 viết *"An agent that has been
completely silent for 60 seconds has its window forgotten"* (một agent im lặng
60 giây sẽ bị quên cửa sổ). **Không có code nào làm điều này** — `ReplayProtector`
không có bất kỳ hết hạn theo thời gian (time-based expiry) nào; cửa sổ chỉ bị
quên qua `reset(agentId)` / `resetAll()` (gọi khi xóa agent khỏi registry) hoặc
khi session cũ bị đẩy ra khỏi `MAX_SESSIONS_PER_AGENT`. **Chọn code.**

---

## 6. Lưu giữ bí mật (secret ở đâu?)

### 6.1 Secret dùng ký UDP

| Bên | Vị trí | Khóa cấu hình |
|---|---|---|
| Agent | file properties của agent, ngoài jar | `security.sharedSecret` |
| Monitor | cột `shared_secret` trong bảng `agents` của SQLite | — (nhập qua dialog) |

Ví dụ `config/agent01.properties`:

```properties
# Shared secret used to sign every UDP packet. It must match the secret
# registered for this agent on the monitoring client. Each agent has its own, so
# one leaked secret cannot be used to impersonate the others.
security.sharedSecret=GROUP10_DEMO_SECRET_01
```

**Mỗi agent có secret RIÊNG** — secret rò rỉ không thể dùng để giả mạo agent khác
(`10-giao-thuc-goc.md` mục 4.2).

**Cách secret được tra cứu khi nhận gói:** interface chức năng (functional
interface) `SecretProvider`:

```java
@FunctionalInterface
public interface SecretProvider {
    /** @return secret của agentId, hoặc null khi agent chưa đăng ký */
    String secretFor(String agentId);
}
```

Monitor cắm `AgentRegistry implements SecretProvider` — backing bởi bảng
`agents`. Agent không đăng ký phải trả `null` để gói bị bỏ, **chứ không** được
kiểm tra against một khóa mặc định nào đó.

**Các quy tắc giữ bí mật (từ `09-kien-truc-goc.md` mục 7 và code):**

- Secret không bao giờ được in khi khởi động, không nằm trong log, không gửi qua
  đường dây, không hiện trong bảng agent trên GUI.
- `TelemetryPacket.toString()` **không** in `hmac`; không lớp nào in secret.
- Dialog sửa secret dùng `JPasswordField` và xóa mảng ký tự đã đọc.
- Agent in định danh, endpoint và chu kỳ khi khởi động, nhưng **không bao giờ**
  in/log/truyền secret (README mục 9).

### 6.2 Secret dùng cho SMTP (xem thêm `07-canh-bao-va-thong-bao.md`)

App Password của Gmail đọc lúc runtime từ env hoặc `config/email-secrets.properties`,
chỉ nằm trong `EmailConfig`, và bị che (`replace(password, "***")`) khỏi mọi
thông báo lỗi SMTP trước khi thông báo đó tới log hay dialog.

---

## 7. Kịch bản kiểm thử

### 7.1 `scripts\run-invalid-hmac-test.bat`

```bat
java -Dmonitoring.home="%CD%" -jar simulator-app\target\simulator-app-1.0.0-all.jar ^
     invalid-hmac --host=127.0.0.1 --port=6000 --agent=AGENT-01
```

Lớp `InvalidHmacSender` gửi **3 biến thể**, mỗi biến thể sẽ gây báo động giả nếu
monitor tin vào payload:

| # | Gói gửi | Tại sao nó phải bị từ chối |
|---|---|---|
| 1 | CPU 100%, RAM 100%, chữ ký rác `0000...deadbeef` | chữ ký không khớp |
| 2 | CPU 100%, RAM 100%, ký bằng khóa sai `ATTACKER_GUESSED_SECRET` | secret sai → chữ ký khác |
| 3 | Packet thật, ký tử tế, **rồi đổi CPU thành 100 sau khi đã ký** | chữ ký bao trùm CPU → sửa payload làm hỏng chữ ký |

Simulator chạy với **session id riêng** (bắt đầu từ sequence 1) nên không làm
nhiễu cửa sổ của agent thật.

**Kết quả mong đợi trên monitor:**

- dashboard vẫn hiện số CPU thật, không phải 100%;
- không có dòng mới trong `metric_raw` cho các gói này;
- không có cảnh báo `CPU_HIGH` do chúng gây ra;
- `SECURITY_INVALID_HMAC` xuất hiện trong `logs\monitor-0.log` **và** trong thẻ
  Alerts.

### 7.2 `scripts\run-replay-test.bat`

```bat
java -Dmonitoring.home="%CD%" -jar simulator-app\target\simulator-app-1.0.0-all.jar ^
     replay --host=127.0.0.1 --port=6000 --agent=AGENT-01 --secret=GROUP10_DEMO_SECRET_01
```

Lớp `ReplayPacketSender` gửi **4 bước** (mỗi bước cách nhau 1000 ms):

| Bước | Gói | Kết quả mong đợi |
|---|---|---|
| 1/4 | packet gốc, sequence N (ký thật) | **ACCEPTED** |
| 2/4 | **cùng packet y nguyên** lần thứ hai | **REJECTED** — `SECURITY_REPLAY_PACKET` |
| 3/4 | lần thứ ba | **REJECTED** — `SECURITY_REPLAY_PACKET` |
| 4/4 | packet mới với sequence N+1 | **ACCEPTED** |

Đây là cuộc tấn công mà **chỉ chữ ký không thể ngăn**: chữ ký vẫn thật, chỉ có
sequence phân biệt lần gửi thứ hai với lần đầu.

### 7.3 Kịch bản khác

```bat
scripts\run-load-test.bat      # tải / flood — kiểm tra hàng đợi có bị chặn đúng không
scripts\test.bat               # 165 test tự động, có integration test chạy socket UDP/TCP thật trên loopback
```

---

## 8. Sự kiện bảo mật trong hệ thống cảnh báo

### 8.1 Hai loại

Enum `AlertType` (`monitoring-common/.../model/AlertType.java`):

| Giá trị | Nhãn hiển thị |
|---|---|
| `SECURITY_INVALID_HMAC` | `SECURITY INVALID HMAC` |
| `SECURITY_REPLAY_PACKET` | `SECURITY REPLAY PACKET` |

### 8.2 Quy tắc ghi (`AlertService.recordSecurityEvent`)

| Quy tắc | Chi tiết code |
|---|---|
| **Rate limit (chống spam)** | Tối đa **1 sự kiện / agent / loại / 60 000 ms** — hằng `SECURITY_EVENT_COOLDOWN_MS = 60_000L`; nếu `now - last < 60_000` thì bỏ qua |
| **Trạng thái khi lưu** | Ghi **đã đóng sẵn**: `state = RESOLVED`, `resolvedAt = openedAt = now` — vì đây là một **sự kiện** đã qua, không phải một điều kiện đang mở |
| **Mức nghiêm trọng** | `AlertSeverity.WARNING` |
| **Không gửi email** | `recordSecurityEvent` **không** gọi `notifier.sendAlert` — chỉ `repository.insert` + `LOG.warning` + `eventBus.fireAlert` (GUI). Trận flood packet giả mạo **không được biến thành trận flood mail** |
| **Agent chưa đăng ký** | `repository.insert` có thể hỏng vì khóa ngoại (`agents` không có dòng đó) → nuốt RuntimeException, giữ dòng log `Security event for <id> could not be stored` |
| **Không chặn luồng xử lý** | Mọi thứ nằm trong `reject()` của `TelemetryService`, tiếp theo là `eventBus.firePacketRejected` |

Lưu ý: `AGENT_RECOVERED` và cảnh báo ngưỡng thì **có** gọi
`notifier.sendAlert` — chỉ sự kiện bảo mật là không.

### 8.3 Bộ đếm và sự kiện GUI

```java
private void reject(String agentId, String detail, Reason reason, long now) {
    rejectedCount.increment();                  // LongAdder
    if (agentId != null) registry.countRejected(agentId);   // hiển thị trên bảng agent
    ... recordSecurityEvent(...) hoặc LOG.warning(...)
    eventBus.firePacketRejected(agentId, detail);           // đẩy lên GUI
}
```

---

## 9. Tóm tắt lớp phủ bảo mật

```
  +--------------------------------------------------------------+
  |  Chữ ký HMAC-SHA256      -> gói đúng không bị sửa, đúng người |
  |  Chuỗi canonical         -> chữ ký phụ thuộc DỮ LIỆU, không   |
  |                             phụ thuộc trình mã hóa            |
  |  Locale.ROOT + %.4f      -> không phụ thuộc locale máy gửi    |
  |  MessageDigest.isEqual   -> không rò rỉ timing                |
  |  Secret riêng / agent    -> rò rỉ 1 secret không giả mạo được |
  |                             agent khác                        |
  |  Timestamp ±30 s         -> gói cũ bị vứt, bound rủi ro       |
  |  Cửa sổ sequence 64 bit  -> gói lặp bị từ chối, gói trễ vẫn   |
  |                             được nhận                          |
  |  sessionId ký trong gói  -> agent khởi động lại không bị khóa, |
  |                             giả session không được              |
  |  Gói bị từ chối -> không DB, không GUI, không cảnh báo ngưỡng  |
  +--------------------------------------------------------------+
```

---

## Tài liệu liên quan

- [09-kien-truc-goc.md](09-kien-truc-goc.md) — phân lớp, luồng dữ liệu, mô hình thread
- [11-co-so-du-lieu-goc.md](11-co-so-du-lieu-goc.md) — bảng, index, retention, chế độ demo
- [01-cau-truc-du-an.md](01-cau-truc-du-an.md)
- [02-mo-hinh-he-thong.md](02-mo-hinh-he-thong.md)
- [03-cach-thuc-hoat-dong.md](03-cach-thuc-hoat-dong.md)
- [08-mo-hinh-da-luong.md](08-mo-hinh-da-luong.md)
