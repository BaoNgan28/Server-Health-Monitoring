# Báo cáo kiểm thử

Nhóm 10 — Server Health Monitoring System — phiên bản 1.0.0

Mọi nội dung dưới đây đều đã được thực thi. Chỗ nào **không** được thực thi sẽ ghi rõ và nêu lý do; không có gì được ghi là pass chỉ vì code trông có vẻ đúng.

---

## 1. Môi trường

| | |
|---|---|
| Hệ điều hành | Windows 11 Pro, 10.0.26200 |
| JDK dùng để build | Oracle JDK 22.0.1 (2024-04-16) |
| Bytecode target | `--release 17`, vì vậy output chạy được trên Java 17+ |
| Maven | Apache Maven 3.9.6, bản copy đi kèm Apache NetBeans 22 |
| Thư mục gốc project (Project root) | `F:\Project LTM\ServerHealthMonitoring` |
| Số máy hiện có | **một** — xem mục 9 |

---

## 2. Build

```
mvn clean package
```

| | |
|---|---|
| Kết quả | **BUILD SUCCESS** |
| Tổng thời gian | 24.5 s |
| monitoring-common | SUCCESS |
| agent-app | SUCCESS |
| monitor-app | SUCCESS |
| simulator-app | SUCCESS |

Các artifact đã tạo:

```
monitor-app\target\monitor-app-1.0.0-all.jar        17.1 MB
agent-app\target\agent-app-1.0.0-all.jar             4.9 MB
simulator-app\target\simulator-app-1.0.0-all.jar     0.4 MB
```

---

## 3. Kiểm thử tự động

```
mvn clean test
```

| Module | Số test | Failures | Errors | Skipped |
|---|---|---|---|---|
| monitoring-common | 50 | 0 | 0 | 0 |
| agent-app | 26 | 0 | 0 | 0 |
| monitor-app | 96 | 0 | 0 | 0 |
| **Total** | **172** | **0** | **0** | **0** |

Theo từng class:

| Class | Số test | Kết quả |
|---|---|---|
| `HmacUtilTest` | 5 | PASS |
| `PacketCanonicalizerTest` | 7 | PASS |
| `PacketValidatorTest` | 14 | PASS |
| `ReplayProtectorTest` | 13 | PASS |
| `JsonMessageCodecTest` | 11 | PASS |
| `AgentConfigLoaderTest` | 7 | PASS |
| `NetworkRateTrackerTest` | 8 | PASS |
| `AgentMetricsTest` | 4 | PASS |
| `ConfigUpdateServiceTest` | 7 | PASS |
| `EmailConfigLoaderTest` | 11 | PASS |
| `MonitorConfigLoaderTest` | 7 | PASS |
| `DatabaseManagerTest` | 10 | PASS |
| `MetricRepositoryTest` | 9 | PASS |
| `AggregationServiceTest` | 8 | PASS |
| `AlertServiceTest` | 14 | PASS |
| `HeartbeatMonitorTest` | 11 | PASS |
| `EmailNotifierTest` | 8 | PASS |
| `UdpIntegrationTest` | 9 | PASS |
| `TcpIntegrationTest` | 9 | PASS |

Không test nào bị vô hiệu hoá (disabled), bỏ qua (ignored) hay skip.

---

## 4. Chạy end-to-end trên máy local

Các tiến trình thật trên một máy: backend giám sát (monitoring backend) cộng bốn tiến trình agent thật, mỗi tiến trình khởi động từ `agent-app-1.0.0-all.jar` với file properties riêng, trong 110 giây. Lớp Swing là phần duy nhất không được lần chạy này kiểm tra; nó được bao phủ riêng trong mục 8.

**Kết quả: PASS.**

```
udp received : 469
udp accepted : 464
udp rejected : 5
open alerts  : 1
live threads : 8

metric_raw rows  : 389
  AGENT-01 : 107
  AGENT-02 :  77      (lower: it was stopped for ~26 s on purpose)
  AGENT-03 : 103
  AGENT-04 : 102
alerts rows      : 5
```

Năm gói tin bị từ chối chính là những gói mà hai simulator bảo mật đã gửi; không có gói tin thật nào của agent bị từ chối. Khoảng chênh giữa 464 accepted và 389 dòng lưu trữ là các heartbeat, vốn không mang metric.

| Kiểm tra (Check) | Kết quả (Result) | Bằng chứng (Evidence) |
|---|---|---|
| Bốn agent đạt ONLINE | PASS | cả bốn đều `ONLINE` từ giây thứ 10 trở đi |
| CPU thật | PASS | dao động 3.9 – 57.1 % theo tải của máy |
| Bộ nhớ (memory) thật | PASS | 51.8 – 55.2 % |
| Số connection thật | PASS | 37 – 41 kết nối TCP established |
| Uptime thật | PASS | 143 724 s, tăng một mỗi giây |
| Tốc độ Network IN | PASS | 6.8 – 2260 kB/s, bám theo lưu lượng thật |
| Tốc độ Network OUT | PASS | 1.6 – 85 kB/s |
| Ghi database | PASS | 389 dòng trên bốn agent |
| Không tăng luồng (thread) | PASS | 8 luồng sống (live thread) lúc kết thúc |

### 4.1 Điều khiển qua TCP — PASS

Một `CONFIG_UPDATE` đã được đẩy tới AGENT-01 qua TCP giữa lần chạy:

```
TCP CONFIG_ACK status=SUCCESS
  message=Applied metricIntervalMs=1000, cpuThreshold=2.0
  effectiveCpuThreshold=2.0  effectiveMetricIntervalMs=1000
```

Log của chính agent xác nhận nó đã áp dụng thay đổi, vì vậy đây là round trip thật, không phải cập nhật UI cục bộ. Việc từ chối giá trị không hợp lệ được bao phủ bởi `TcpIntegrationTest` và bởi `ConfigUpdateServiceTest`.

### 4.2 Cảnh báo CPU — PASS

AGENT-01 được đăng ký với ngưỡng (threshold) CPU 1 % để nhánh cảnh báo (alert path) kích hoạt mà không cần tải máy.

```
ALERT CPU_HIGH AGENT-01 OPEN  CPU HIGH is 55.7 %, above the threshold of 1.0 %
```

**Một** dòng duy nhất, từ khoảng 100 mẫu liên tiếp vượt ngưỡng. Việc chặn trùng lặp (duplicate suppression) được xác nhận dưới 60 vi phạm liên tiếp trong `AlertServiceTest`.

### 4.3 Offline và phục hồi — PASS

AGENT-02 bị tắt lúc t≈30 s và khởi động lại lúc t≈56 s.

```
ALERT AGENT_OFFLINE   AGENT-02 OPEN      No valid packet received since 15:09:57
ALERT AGENT_OFFLINE   AGENT-02 RESOLVED
ALERT AGENT_RECOVERED AGENT-02 RESOLVED  Agent is online again
```

Một sự kiện offline duy nhất cho toàn bộ thời gian outage, và phục hồi được phát hiện trong vòng một giây sau khi khởi động lại dù agent khởi động lại đếm lại từ sequence 1.

### 4.4 HMAC không hợp lệ — PASS

`simulator-app ... invalid-hmac` đã gửi ba gói tin giả mạo khai báo CPU 100 %.

```
SECURITY SECURITY_INVALID_HMAC from AGENT-01: signature mismatch for agent AGENT-01
```

CPU hiển thị của AGENT-01 không bị ảnh hưởng, không dòng nào được ghi cho chúng, và không cảnh báo CPU nào do chúng gây ra.

### 4.5 Replay — PASS

`simulator-app ... replay` đã gửi một gói tin hợp lệ, rồi cùng gói đó thêm hai lần nữa, rồi một gói mới.

```
SECURITY SECURITY_REPLAY_PACKET from AGENT-01:
    sequence 1 was already seen in session SIM-bcb734b9-...
```

Gói đầu và gói cuối được chấp nhận, hai gói ở giữa bị từ chối. AGENT-01 thật vẫn chạy suốt thời gian đó.

---

## 5. Tổng hợp (Aggregation) — PASS

Được thực thi với profile demo (`config\monitor-demo.properties`: raw giữ 60 s, bucket 10 giây, aggregate giữ 300 s, job mỗi 15 s) và bốn agent thật, trong 150 giây.

```
Aggregation scheduled every 15000 ms (raw kept 60000 ms, bucket 10000 ms,
                                      aggregates kept 300000 ms)
Aggregation wrote 3 bucket(s) ...
Aggregation wrote 8 bucket(s) ...
Aggregation wrote 4 bucket(s) ...
Aggregation wrote 8 bucket(s) ...
Aggregation wrote 4 bucket(s) ...
Aggregation wrote 8 bucket(s) ...

udp received : 692   accepted : 692   rejected : 0

metric_raw rows : 257   (64–65 per agent: the last ~60 s, as configured)
metric_5m rows  :  35
```

Dữ liệu raw ngừng tăng ở ngưỡng retention trong khi các bucket tích luỹ dần — đúng hành vi mong muốn. Thời gian production (một giờ, bucket năm phút, ba mươi ngày) chạy qua cùng một đường code và được bao phủ bởi `AggregationServiceTest`, vốn dùng timestamp giả lập (fabricated timestamp) thay vì chờ cả giờ.

---

## 6. Kiểm thử tải (Load test) — PASS

```
simulator-app load --agents=20 --duration=60
```

| | |
|---|---|
| Số định danh (Identities) | 20 |
| Gói đã gửi | 1 201 |
| Lỗi gửi | 0 |
| Datagram client nhận được | 1 211 |
| Bị từ chối là UNKNOWN_AGENT | 1 201 |
| Được chấp nhận (Accepted) | 10 (a separate, registered agent running at the time) |
| **Datagram mất** | **0** |
| Luồng sống lúc kết thúc | 6 |

Mọi identity tải đều bị từ chối vì không có identity nào được đăng ký — đó là hành vi đúng và cũng là mục đích của bài kiểm tra. Điều được kiểm tra là receiver vẫn theo kịp dưới flood, từ chối sạch sẽ, không mất gì, và không tăng luồng. Nó đã làm được cả bốn.

---

## 7. Cơ sở dữ liệu (Database) — PASS

Được xác minh bởi `DatabaseManagerTest`, `MetricRepositoryTest` và các lần chạy ở trên:

| Kiểm tra | Kết quả |
|---|---|
| Database tự tạo khi khởi động lần đầu | PASS |
| Áp dụng đầy đủ schema, cả bốn bảng và bốn index | PASS |
| Mở lại database hiện có vẫn giữ dữ liệu | PASS |
| `foreign_keys=ON`, `journal_mode=WAL` có hiệu lực | PASS |
| Xoá agent sẽ cascade tới metric và alert | PASS |
| Transaction rollback khi thất bại | PASS |
| Bốn writer đồng thời, 100 insert, không mất mát hay lỗi | PASS |
| Agent được lưu (persisted) qua các lần khởi động lại | PASS |
| Metric raw được lưu | PASS |
| Lịch sử từ raw và từ aggregate | PASS |
| Alert được insert và resolve | PASS |

---

## 8. Client Swing — PASS, có một phần không tự động hoá

`monitor-app-1.0.0-all.jar` được khởi động như một ứng dụng desktop thông thường.

| Kiểm tra | Kết quả |
|---|---|
| Khởi động không lỗi | PASS |
| Tải cấu hình và tạo/mở database | PASS |
| Bind UDP 6000 | PASS |
| Tải các agent đã đăng ký và các alert đang mở | PASS |
| Khởi động timer heartbeat và aggregation | PASS |
| Báo `Email notifications disabled: credentials not configured` và tiếp tục chạy | PASS |
| Tiến trình vẫn sống và phản hồi | PASS |

```
Profile             : production
UDP Listen Port     : 6000
Database            : F:\...\data\monitoring.db
Email notifications disabled: credentials not configured
                     (smtp.appPassword is empty or still the placeholder)
Loaded 4 agent(s) from the database
UDP telemetry receiver listening on port 6000
Monitoring client started with profile 'production'
```

**KHÔNG TỰ ĐỘNG HOÁ:** việc kiểm tra bằng mắt cửa sổ đã render — hình thức biểu đồ (chart), màu sắc, bố cục cột — không được ghi lại bằng chương trình. Logic của các panel được bao phủ bởi các test mức service mà chúng gọi, nhưng việc xác nhận biểu đồ *trông* đúng là bước thủ công (xem mục 3.1 trong `08-kiem-thu.md`).

---

## 9. Chưa thực thi

### 9.1 Gửi Gmail thực tế (Live Gmail delivery) — **NOT EXECUTED**

**Lý do:** Gmail App Password và địa chỉ nhận trong `config\email-secrets.properties` vẫn là placeholder đi kèm (`PASTE_GMAIL_APP_PASSWORD_HERE`, `PASTE_RECIPIENT_EMAIL_HERE`). Không có mật khẩu nào bị đoán hay bịa ra, và không có phiên SMTP thực tế nào được thử.

Đây **không phải lỗi của project**. Đây là hành vi thiết kế cho hệ thống chưa cấu hình: e-mail bị tắt một cách an toàn, log nêu lý do, tab Settings hiển thị `NOT CONFIGURED`, và mọi thứ khác chạy bình thường.

Những gì **đã** được kiểm thử, và đã pass:

| Kiểm tra | Kết quả |
|---|---|
| Dòng tiêu đề (subject line) cho cả sáu loại message | PASS |
| Nội dung (body), bao gồm giá trị và ngưỡng (threshold) | PASS |
| Gửi bất đồng bộ (asynchronous) và không chặn caller | PASS (20 alerts queued in under a second) |
| Lỗi SMTP được cô lập, đếm, và không bao giờ ném cho caller | PASS |
| Mật khẩu bị che khỏi mọi message lỗi | PASS |
| Placeholder để e-mail tắt, kèm lý do được ghi lại | PASS |
| Biến môi trường (environment variable) ghi đè lên file | PASS |
| Notifier no-op báo đang tắt và không gửi gì | PASS |
| Composite vẫn sống sót khi một kênh bị hỏng | PASS |

Để hoàn tất kiểm tra này, làm theo `06-cai-dat-email.md`: điền hai giá trị, khởi động lại hoặc nhấn **Reload Email Configuration**, rồi **Send Test Email**.

### 9.2 Triển khai đa máy vật lý — **NOT EXECUTED**

**Lý do:** chỉ có một máy. Bốn agent và client đều chạy trên đó qua giao diện loopback.

Lần chạy loopback **không** thay thế được: nó không kiểm tra đường mạng thật, firewall thật, hay hai máy có clock lệch nhau. Nó được báo cáo ở đây đúng như những gì nó là.

Điều mà lần chạy này chứng minh và không chứng minh:

* **Đã chứng minh:** protocol, chữ ký (signing), đóng khung (framing), database, engine cảnh báo (alert engine) và logic liveness đều hoạt động trên bốn tiến trình hệ điều hành độc lập.
* **Chưa chứng minh:** rằng lưu lượng vượt qua mạng vật lý và tường lửa Windows trên một LAN cụ thể.

Mọi thứ cần cho lần chạy đa máy đã được chuẩn bị: `agent.tcp.port` có thể là 5000 trên mọi máy, các script firewall đã được viết, và chỉ cần chép jar agent cộng một file properties. Xem `05-trien-khai-nhieu-may.md`.

### 9.3 Retention production dài hạn — **NOT EXECUTED**

**Lý do:** việc xác nhận cửa sổ raw 60 phút và cửa sổ aggregate 30 ngày trong thời gian thực sẽ mất 30 ngày.

Cả hai được xác minh thay bằng cách lái cùng code với timestamp giả lập (`AggregationServiceTest`), và cùng đường code đó đã được xác minh end-to-end trong thời gian thực bằng profile demo nén (mục 5).

---

## 10. Lỗi đã phát hiện và sửa trong quá trình kiểm thử

Được ghi lại vì chính các test đã phát hiện ra chúng.

| # | Lỗi (Defect) | Phát hiện bởi | Cách sửa (Fix) |
|---|---|---|---|
| 1 | bộ đếm `volatile long` tăng từ bốn luồng worker bị mất cập nhật | `UdpIntegrationTest` timeout khi đếm | `LongAdder` cho mọi counter dùng chung giữa các thread |
| 2 | pool worker dùng chung để gói 40 vượt gói 39, nên kiểm tra replay từ chối nhầm gói thật (23 trên 200 trong một burst) | `UdpIntegrationTest.handlesABurst` | một worker đơn luồng cho mỗi sender, chọn theo địa chỉ nguồn, để thứ tự của một sender được giữ; cộng thêm sliding window thay vì sequence đơn điệu nghiêm ngặt |
| 3 | agent khởi động lại đếm lại từ 1 trông như replay và tiếp tục OFFLINE thêm ~34 s | lần chạy bốn agent trên máy local | session id cho mỗi lần chạy của agent, được bao phủ bởi chữ ký; mỗi session có cửa sổ replay riêng |
| 4 | số sequence lớn của simulator đẩy cửa sổ (window) của agent thật ra ngoài khoảng và đánh sập nó thành offline | lần chạy bốn agent trên máy local | simulator có session id riêng, nên các sender không thể gây nhiễu lẫn nhau |
| 5 | cộng bộ đếm byte trên danh sách interface thay đổi tạo ra spike ~570 Mbps giả và alert mạng sai | lần chạy bốn agent trên máy local | tốc độ (rate) đo theo từng interface rồi mới cộng lại, để adapter xuất hiện không thể trông như burst |
| 6 | khởi động lại client làm dấy alert OFFLINE cho mọi agent, từ giá trị `last_seen` tải ra khỏi database | khởi động client Swing | chỉ agent mà client này thực sự đã nghe thấy mới được tuyên bố là down |

Mỗi bản sửa đều có test hồi quy (regression test).

---

## 11. Quét chất lượng code

| Kiểm tra | Kết quả |
|---|---|
| `TODO`, `FIXME`, `XXX`, `HACK` | none |
| `UnsupportedOperationException` ném ra như stub | none (hai chỗ xuất hiện là mệnh đề `catch` hợp lệ) |
| Khối `catch` rỗng | none — mọi chỗ đều log hoặc xử lý |
| `System.out` / `System.err` ngoài ba class `main` và simulator console | none |
| Mật khẩu hard-code trong mã nguồn Java | none |
| Test bị vô hiệu hoá hay bỏ qua | none |
| Credential placeholder trong `config\email-secrets.properties` | có mặt **theo thiết kế**, và đã được ghi tài liệu |

---

## 12. Tóm tắt

| Hạng mục | Kết quả |
|---|---|
| Build | PASS |
| Kiểm thử unit và integration (172) | PASS |
| Telemetry UDP | PASS |
| Điều khiển TCP và CONFIG_ACK | PASS |
| Lưu trữ SQLite, schema, cascade | PASS |
| Xác thực HMAC | PASS |
| Bảo vệ replay | PASS |
| Heartbeat, offline và phục hồi | PASS |
| Engine cảnh báo và chặn trùng lặp | PASS |
| Tổng hợp và retention | PASS |
| Chạy local bốn agent | PASS |
| Kiểm thử tải hai mươi định danh | PASS |
| Khởi động và vận hành client Swing | PASS (hình thức không tự động hoá) |
| Triển khai e-mail | PASS |
| **Gửi Gmail thực tế** | **NOT EXECUTED** — credential placeholder |
| **Chạy đa máy vật lý** | **NOT EXECUTED** — chỉ có một máy |
| **Retention 30 ngày trong thời gian thực** | **NOT EXECUTED** — đã xác minh bằng clock giả lập và profile demo |
