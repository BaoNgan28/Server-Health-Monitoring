# Kiểm thử

Nhóm 10 — Server Health Monitoring System

Tài liệu này bao gồm: mọi kiểm thử tự động, cách chạy chúng, và các kịch bản thủ công mà không kiểm thử tự động nào có thể thay thế. Những gì đã thực sự được thực thi, và những gì chưa, được ghi riêng trong [09-bao-cao-kiem-thu.md](09-bao-cao-kiem-thu.md).

---

## 1. Chạy các kiểm thử tự động

```bat
scripts\test.bat
```

hoặc

```bat
mvn clean test
```

Chi tiết từng test sau đó, bao gồm cả lỗi (failure) nếu có, được ghi vào `<module>\target\surefire-reports\`.

Bộ kiểm thử tuân theo hai nguyên tắc:

* **Không test nào sleep để chờ timeout.** Bộ giám sát heartbeat (heartbeat monitor) và dịch vụ tổng hợp (aggregation service) nhận clock dưới dạng tham số, vì vậy timeout 15 giây và thời gian lưu trữ (retention) một giờ được kiểm thử bằng phép tính số học thay vì chờ đợi thực tế.
* **Không test nào chạm tới database thật.** Mọi test cần database đều tự tạo database trong thư mục tạm của JUnit (JUnit temporary folder), vì vậy `data\monitoring.db` không bao giờ bị test mở tới.

---

## 2. Mỗi test class bao phủ những gì

### monitoring-common

| Class | Phạm vi bao phủ |
|---|---|
| `HmacUtilTest` | một vector known-answer theo RFC 4231, tính đơn định (determinism), nhạy với key, hex chữ thường, so sánh constant-time, từ chối key rỗng |
| `PacketCanonicalizerTest` | thứ tự trường (field order) chính xác, trường signature không bao giờ được ký, số thực (double) độc lập locale (được kiểm tra dưới locale Đức), định dạng bốn chữ số thập phân, chuẩn hoá negative-zero, từ chối giá trị non-finite, xử lý null, và mọi trường được ký đều thực sự ảnh hưởng tới chuỗi |
| `PacketValidatorTest` | gói tin hợp lệ được chấp nhận; chữ ký giả mạo (forged signature), metric bị sửa đổi, sai key, agent không xác định, sai phiên bản giao thức, timestamp quá cũ và timestamp trong tương lai, replay và giá trị ngoài khoảng đều bị từ chối; agent khởi động lại được chấp nhận; gói tin bị từ chối không làm tốn sequence |
| `ReplayProtectorTest` | tiến triển về phía trước (forward progress), gói lặp lại bị từ chối, gói đến muộn được chấp nhận một lần, biên cửa sổ (window edge), bước nhảy lớn, cách ly theo từng agent và từng session, loại bỏ session (session eviction), và tám luồng (thread) đua nhau chấp nhận cùng một sequence đúng một lần |
| `JsonMessageCodecTest` | round trip, UTF-8, lát buffer (buffer slice), `null` nghĩa là "không thay đổi" (unchanged), dò kiểu (type peeking), chịu được dữ liệu rác, và đóng khung TCP (TCP framing): độ dài frame chính xác, các frame liên tiếp, ký tự xuống dòng nhúng bên trong, và từ chối tiền tố độ dài (length prefix) độc hại |

### agent-app

| Class | Phạm vi bao phủ |
|---|---|
| `AgentConfigLoaderTest` | mọi thuộc tính (property) đều được đọc, giá trị mặc định, trường bắt buộc, từ chối giá trị ngoài khoảng, số thập phân độc lập locale, tải từ đĩa, và banner khởi động không bao giờ chứa shared secret |
| `NetworkRateTrackerTest` | mẫu đầu tiên là baseline chứ không phải tốc độ (rate), phép tính delta-over-time, khoảng dưới một giây, bộ đếm bị reset, thời gian trôi bằng không và thời gian trôi âm, bộ đếm không đọc được, liên kết nhàn rỗi (idle link) đọc đúng bằng không, và reset |
| `AgentMetricsTest` | bộ thu thập (collector) OSHI thật trên máy chạy build: mọi metric trong khoảng hợp lệ, thu thập lặp lại ổn định, mô tả máy, và một mẫu thật được đóng thành gói tin (packet) có chữ ký xác minh được |
| `ConfigUpdateServiceTest` | cập nhật hợp lệ được áp dụng và xác nhận (acknowledged), `null` nghĩa là "không thay đổi", từ chối giá trị ngoài khoảng, kiểm tra hợp lệ (validation) theo kiểu tất cả-hoặc-không (all-or-nothing), số non-finite và số âm, message rỗng và null |

### monitor-app

| Class | Phạm vi bao phủ |
|---|---|
| `DatabaseManagerTest` | file và các thư mục được tạo, toàn bộ schema được áp dụng, mở lại không mất dữ liệu, các pragma có hiệu lực, xoá cascade (cascade delete), rollback transaction, sử dụng trước khi khởi tạo, bốn writer đồng thời, đóng idempotent |
| `MetricRepositoryTest` | round-trip mọi trường, lọc theo khoảng (range) và theo agent, đếm bucket, giá trị trung bình và đỉnh (average và peak), gom nhóm theo từng agent và từng bucket, pruning chỉ những gì đã được tổng hợp, tổng hợp idempotent, hết hạn aggregate, lần chạy rỗng |
| `AggregationServiceTest` | mẫu cũ được gộp, chi tiết gần đây được giữ, bucket dở dang được bỏ qua, giữ trung bình và đỉnh, loại bỏ bucket hết hạn, chạy lặp lại không trùng lặp, và cùng một code tạo ra bucket năm phút khi được cấu hình như vậy |
| `AlertServiceTest` | một alert cho mỗi sự cố dù có sáu mươi vi phạm liên tiếp, phục hồi (recovery), dải hysteresis, mở lại, độc lập giữa các metric, chuyển đổi Mbps, offline chỉ báo một lần, phục hồi sẽ resolve, sự kiện bảo mật (security event) bị rate-limit và không bao giờ gửi e-mail, alert đang mở được khôi phục sau khi khởi động lại |
| `HeartbeatMonitorTest` | UNKNOWN cho tới lần liên lạc đầu tiên, ONLINE khi có gói tin, OFFLINE khi quá timeout, biên timeout, không alert trùng lặp trong ba mươi lần quét, phục hồi, không spam phục hồi, agent chưa từng thấy không bị báo là outage, chu kỳ lặp lại, trạng thái được lưu (persisted) |
| `EmailConfigLoaderTest` | file điền đầy đủ sẽ bật e-mail, mỗi placeholder sẽ tắt e-mail, file thiếu, biến môi trường (environment) được ưu tiên, cấu hình chỉ bằng environment, fallback từng phần, địa chỉ malformed, phát hiện template, cổng (port) sai, và mật khẩu không bao giờ xuất hiện trong mô tả (description) |
| `EmailNotifierTest` | tiêu đề (subject) và nội dung (body), gửi bất đồng bộ, lỗi được cô lập và đếm, mật khẩu bị che khỏi thông báo lỗi, message kiểm thử, notifier no-op, và composite vẫn sống sót khi một kênh bị hỏng |
| `MonitorConfigLoaderTest` | giá trị mặc định production, phút và ngày, giây ghi đè lên chúng, từ chối giá trị ngoài khoảng, đường dẫn tương đối được resolve, file thiếu, tải từ đĩa |
| `UdpIntegrationTest` | một **socket UDP thật**: telemetry được chấp nhận end-to-end, heartbeat, gói tin giả mạo không làm thay đổi gì, replay, agent không xác định, timestamp quá cũ, dữ liệu rác không làm chết receiver, một alert từ mười mẫu vi phạm, và một burst 200 gói tin |
| `TcpIntegrationTest` | một **control server của agent thật trên socket thật**: PING/PONG, AGENT_INFO, thay đổi cấu hình được áp dụng và xác nhận, giá trị không hợp lệ bị từ chối, tái sử dụng kết nối, nhiều client, timeout kết nối, và vòng đời của connection manager |

---

## 3. Các kịch bản thủ công

### 3.1 Demo bốn agent trên máy local

```bat
scripts\build.bat
scripts\run-local-demo.bat
```

Đăng ký bốn agent một lần (Dashboard → Add Agent), host `127.0.0.1`, cổng 5001–5004, secret `GROUP10_DEMO_SECRET_01`…`_04`.

Dự kiến trong khoảng mười giây:

* cả bốn dòng đều hiển thị `ONLINE`;
* CPU, RAM, connections, Network IN và Network OUT cập nhật khoảng một lần mỗi giây;
* tab Server Detail vẽ ba biểu đồ (chart) chạy cuộn;
* cửa sổ vẫn phản hồi trong suốt thời gian đó;
* `data\monitoring.db` tăng lên — `SELECT COUNT(*) FROM metric_raw` tăng khoảng bốn dòng mỗi giây.

### 3.2 Cấu hình qua TCP

Chọn AGENT-01 → **Configure** → đặt **CPU Threshold** thành `5.0` → **Send CONFIG_UPDATE**.

Dự kiến: hộp thoại tiêu đề `CONFIG_ACK from AGENT-01` báo `SUCCESS` và các giá trị hiệu lực. Console của agent ghi log `Applied cpuThreshold=5.0`. Đây là bằng chứng cho thấy thiết lập đã đi qua TCP và được chấp nhận, chứ không chỉ bị thay đổi cục bộ.

Sau đó thử một giá trị không hợp lệ — metric interval `5` — và dự kiến `ERROR` kèm `metricIntervalMs must be between 200 and 3600000 ms, got 5`, và không có gì thay đổi.

### 3.3 Cảnh báo CPU mà không cần tải máy

Đọc CPU hiện tại của AGENT-01 từ dashboard, sau đó đặt ngưỡng (threshold) CPU thấp hơn con số đó (Edit Agent, hoặc Configure qua TCP).

Dự kiến: một `CPU_HIGH` mở ra trong tab Alerts và một dòng xuất hiện trong `alerts`. Để yên một phút: **vẫn chỉ một dòng**, chứ không phải một dòng mỗi giây. Nếu e-mail đã được cấu hình, đúng một message sẽ đến.

### 3.4 Offline và phục hồi

Đóng cửa sổ AGENT-02. Chờ hơn 15 giây.

Dự kiến: AGENT-02 chuyển thành `OFFLINE`, một alert `AGENT_OFFLINE` xuất hiện, và số lượng không tăng trong khi nó vẫn down.

Khởi động lại bằng `scripts\run-agent02.bat`.

Dự kiến: nó quay lại `ONLINE` trong một hai giây, outage được đánh dấu `RESOLVED`, và một thông báo `AGENT_RECOVERED` xuất hiện. Agent khởi động lại dùng session id mới, nên không bị nhầm thành replay.

### 3.5 HMAC không hợp lệ

```bat
scripts\run-invalid-hmac-test.bat
```

Gửi ba gói tin giả mạo khai báo CPU 100%: một gói có chữ ký rác, một gói ký bằng sai key, và một gói thật nhưng giá trị CPU bị nâng lên sau khi ký.

Dự kiến: dashboard vẫn hiển thị CPU thật, `SELECT COUNT(*) FROM metric_raw` không tăng thêm ba, không có cảnh báo CPU nào do chúng gây ra, và `SECURITY_INVALID_HMAC` xuất hiện trong tab Alerts và trong `logs\monitor-0.log`.

### 3.6 Replay

```bat
scripts\run-replay-test.bat
```

Gửi một gói tin hợp lệ, cùng gói đó thêm hai lần nữa, rồi một gói mới.

Dự kiến: gói đầu và gói cuối được chấp nhận, hai gói ở giữa bị từ chối với `SECURITY_REPLAY_PACKET`. Agent thật vẫn chạy suốt thời gian đó — simulator có session riêng, nên không thể làm nhiễu cửa sổ (window) của agent.

### 3.7 Tải

```bat
scripts\run-load-test.bat
```

Hai mươi định danh trong sáu mươi giây. Chúng không được đăng ký, nên mọi gói tin đều bị từ chối là `UNKNOWN_AGENT` — đó chính là mục đích: điều cần kiểm tra là receiver vẫn theo kịp và vẫn sống sót dưới một trận flood.

Dự kiến: cửa sổ vẫn phản hồi, bộ đếm received trên thanh trạng thái khớp với con số simulator báo, và số luồng (thread) không tăng. Quan sát trong Task Manager, hoặc bằng:

```bat
jcmd <pid> Thread.print | find /c "java.lang.Thread.State"
```

### 3.8 Tổng hợp

Chạy monitoring client với profile demo:

```bat
scripts\run-monitor-demo.bat
```

Raw retention 60 s, bucket 10 s, job chạy mỗi 15 s. Khởi động các agent và để yên hai ba phút, rồi xem `data\monitoring-demo.db`:

```sql
SELECT COUNT(*) FROM metric_raw;   -- stops growing, roughly the last minute
SELECT COUNT(*) FROM metric_5m;    -- rises as buckets are written
SELECT agent_id, sample_count, ROUND(avg_cpu,1), ROUND(max_cpu,1)
FROM metric_5m ORDER BY bucket_start DESC LIMIT 10;
```

Mỗi bucket nên chứa khoảng mười mẫu, với giá trị lớn nhất (maximum) ít nhất bằng giá trị trung bình (average).

### 3.9 E-mail thực tế

Chỉ thực hiện được khi đã điền Gmail App Password; xem [06-cai-dat-email.md](06-cai-dat-email.md). Nó cố ý **không** nằm trong `mvn test`: các kiểm thử tự động dùng transport giả (stub transport), và không có credential thật nào xuất hiện trong mã nguồn test.

1. Sửa `config\email-secrets.properties`.
2. Khởi động lại, hoặc Settings → Reload Email Configuration.
3. Xác nhận `CONFIGURED`, rồi **Send Test Email**.
4. Kích hoạt `CPU_HIGH`, `AGENT_OFFLINE` và `AGENT_RECOVERED` như trong 3.3 và 3.4 và xác nhận mỗi loại một message.

### 3.10 Đa máy

Xem [05-trien-khai-nhieu-may.md](05-trien-khai-nhieu-may.md). Cần năm máy trên cùng một mạng; không thể thực hiện trên một máy duy nhất, và demo loopback không thay thế được cho nó.

---

## 4. Kiểm tra mạng bằng tay

```bat
ping 192.168.1.10
```

```powershell
Test-NetConnection 192.168.1.11 -Port 5000
```

UDP không có phép thử tương đương, vì không có gì trả lời probe. Hãy xác minh qua ba dấu hiệu có thay đổi: log của agent báo đang gửi, bộ đếm received của monitoring client tăng, và metric xuất hiện trong cửa sổ.
