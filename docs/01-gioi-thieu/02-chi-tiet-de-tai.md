# Chi tiết đề tài

Đề tài: **Hệ thống Giám sát Server (Server Health Monitoring) từ xa**
Môn: Lập trình mạng máy tính (CMP180) — HUTECH — Nhóm 10, lớp 23DTHA5.

Tài liệu mô tả bài toán, yêu cầu chức năng và phi chức năng, kiểm chứng cách
hệ thống hiện tại đáp ứng 5 yêu cầu bổ sung của giảng viên, các kịch bản sử
dụng chính và hướng mở rộng. Mọi dẫn chứng lấy từ code và tài liệu của dự án.

---

## 1. Bài toán cần giải

**Bối cảnh**: một hệ thống gồm nhiều máy chủ (có thể từ 2 máy trong phòng lab
đến vài chục máy ở nhiều vị trí). Người quản trị cần biết tình trạng của **tất
cả** các máy từ **một nơi duy nhất**, với độ trễ thấp và không phải đăng nhập
từng máy.

**Các thách thức của bài toán**:

| # | Thách thức | Hệ thống xử lý thế nào |
|---|---|---|
| 1 | **Nhiều máy → một trung tâm**: mỗi máy chỉ cần cài một agent nhẹ, trung tâm phải theo dõi đồng thời nhiều agent | Một `monitor-app` nhận dữ liệu từ nhiều agent cùng lúc qua UDP 6000; mỗi agent có định danh (`agentId`) và bí mật riêng |
| 2 | **Theo thời gian thực**: số liệu phải cập nhật liên tục trên màn hình | Agent gửi telemetry mỗi 1 giây, heartbeat mỗi 5 giây; GUI cập nhật qua event bus trên EDT |
| 3 | **Mất kết nối phải phát hiện được**: máy chết thì không gửi gì nữa — hệ thống phải tự nhận ra | `HeartbeatMonitor` quét mỗi 2 giây; quá 15 giây không nhận được gói hợp lệ → trạng thái `OFFLINE` + cảnh báo `AGENT_OFFLINE`; gói hợp lệ kế tiếp → `AGENT_RECOVERED` |
| 4 | **Dữ liệu ngày càng lớn**: 1 mẫu/giây × 4 agent ≈ 345 600 dòng/ngày | Giữ dữ liệu thô 60 phút, gộp thành bucket 5 phút, giữ tổng hợp 30 ngày (xem mục 4) |
| 5 | **Dữ liệu phải tin cậy**: UDP nhận được từ mọi nơi, có thể bị giả mạo hoặc gửi lại | Chữ ký HMAC-SHA256 + timestamp ±30 giây + cửa sổ chống phát lại sequence 64 bit (xem mục 4.1) |
| 6 | **Sự cố phải đến được người quản trị** | Cảnh báo 3 kênh: GUI, database, email SMTP |
| 7 | **Đổi cấu hình không cần đăng nhập từng máy** | Kênh TCP điều khiển: `CONFIG_UPDATE` / `CONFIG_ACK` từ trung tâm xuống agent |

**Giả định phạm vi**: bài toán tập trung vào **giám sát tài nguyên hệ thống**
(CPU, RAM, mạng, kết nối, uptime), không giám sát log hay nội dung ứng dụng
riêng lẻ.

---

## 2. Yêu cầu chức năng

### 2.1 Phía Agent (`agent-app`)

| # | Yêu cầu | Hiện trạng trong dự án |
|---|---|---|
| A1 | Thu thập 6 chỉ số hệ thống | `OshiSystemMetricsCollector` dùng OSHI: CPU %, RAM %, số kết nối, uptime, network in/out bytes/s (`NetworkRateTracker` biến bộ đếm byte thành byte/giây) |
| A2 | Gửi telemetry định kỳ qua UDP đã ký HMAC | `TelemetryScheduler` (mặc định 1000 ms) → `PacketSigner.sign` → `UdpTelemetrySender` gửi tới `monitor.host:6000`; gói > 4096 byte bị từ chối |
| A3 | Gửi heartbeat (gói "còn sống" không kèm chỉ số) | `HeartbeatScheduler` (mặc định 5000 ms) |
| A4 | Nhận cấu hình từ xa qua TCP | `TcpControlServer` (TCP 5000, 1 luồng accept + pool 8 luồng phiên) → `ConfigUpdateService`: kiểm tra **tất cả** trường trước khi áp dụng **bất kỳ** trường nào, trả `CONFIG_ACK` (SUCCESS/ERROR + lý do) |
| A5 | Chạy nền, cấu hình bằng file ngoài jar | Entry point `AgentMain`, đọc `--config` (`.properties`), không in/log bí mật, có shutdown hook |
| A6 | Chịu lỗi khi thu thập chỉ số hỏng | Mọi lỗi collector được bọc, trả giá trị an toàn, cảnh báo tối đa 1 lần/phút |

Các trường cấu hình từ xa (`CONFIG_UPDATE`): `metricIntervalMs`,
`heartbeatIntervalMs`, `cpuThreshold`, `memoryThreshold`,
`networkInThresholdMbps`, `networkOutThresholdMbps` — mỗi trường tùy chọn,
`null` = giữ nguyên.

### 2.2 Phía Monitor (`monitor-app`)

| # | Yêu cầu | Hiện trạng trong dự án |
|---|---|---|
| M1 | Kết nối và theo dõi **nhiều agent** đồng thời | Đăng ký agent qua GUI (`Add Agent`: host, port, secret), lưu vào bảng `agents`; UDP receiver 1 luồng nhận + 4 worker (chọn worker theo nguồn để giữ thứ tự theo từng agent) |
| M2 | Hiển thị realtime | `DashboardPanel` (bảng agent), `ServerDetailPanel`, `TimeSeriesChartPanel` (biểu đồ JFreeChart); mọi callback về UI chạy trên EDT qua `SwingUtilities.invokeLater` |
| M3 | Xác thực gói UDP | `PacketValidator`: shape → phiên bản giao thức → agent đã đăng ký → HMAC (so sánh hằng thời gian) → timestamp ±30 000 ms → chống replay. Gói hỏng **không đi xa hơn**: không cập nhật GUI, không ghi DB, không sinh cảnh báo ngưỡng |
| M4 | Lưu lịch sử | SQLite `data\monitoring.db`: `metric_raw` (mỗi mẫu một dòng), `metric_5m` (tổng hợp), `alerts`, `agents`; index theo (agent, thời điểm) |
| M5 | Tổng hợp & retention | `AggregationService` chạy định kỳ: gộp mẫu thô cũ thành bucket rồi xóa, trong **một transaction** (xem mục 4.2) |
| M6 | Cảnh báo đa kênh | `AlertService` → GUI (`AlertPanel` + event bus), DB (`AlertRepository.insert`), email (`EmailNotifier` trên 1 luồng, hàng đợi 50 phần tử) |
| M7 | Điều khiển agent từ xa | `AgentTcpConnection(Manager)`: giữ tối đa 1 kết nối/agent, tự nối lại khi rớt; `AgentManagementService` phục vụ GUI (GUI không tự mở socket/SQL) |
| M8 | Xem lịch sử 1 giờ → 30 ngày | `HistoryService` chọn nguồn tự động: trong cửa sổ raw → `metric_raw`; cũ hơn → `metric_5m`; fallback `metric_raw` nếu DB mới |

### 2.3 Công cụ mô phỏng (`simulator-app`)

| # | Yêu cầu | Hiện trạng trong dự án |
|---|---|---|
| S1 | Mô phỏng agent thật gửi telemetry hợp lệ | Mode `valid` (`ValidAgentSimulator`) — ký đúng secret |
| S2 | Kiểm thử chống giả mạo chữ ký | Mode `invalid-hmac` (`InvalidHmacSender`) — 3 biến thể gói gian lận phải bị từ chối |
| S3 | Kiểm thử chống gửi lại | Mode `replay` (`ReplayPacketSender`) — gửi lại y hệt một packet đã hợp lệ |
| S4 | Kiểm thử tải với nhiều định danh | Mode `load` (`LoadSimulator`) — mặc định `--agents=20`, `--duration=60`, `--interval=1000` |
| S5 | Chạy nhiều agent giả lập trên **một máy** (tiết kiệm chi phí thực nghiệm) | Kết hợp: demo 4 agent thật cùng máy (cổng TCP 5001–5004, `scripts\run-local-demo.bat`) và simulator phát nhiều định danh cùng lúc về UDP 6000 |

---

## 3. Yêu cầu phi chức năng

| Nhóm | Yêu cầu | Cách hệ thống đáp ứng (dẫn chứng) |
|---|---|---|
| **Bảo mật** | Dữ liệu trên đường dây không giả mạo/sửa đổi được; chống gửi lại | HMAC-SHA256 ký trên chuỗi canonical; so sánh `MessageDigest.isEqual` (hằng thời gian); secret riêng từng agent; timestamp ±30 s; cửa sổ sequence 64 bit theo `(agentId, sessionId)` — `monitoring-common/.../security/*` |
| **Tin cậy** | Mất gói/đứt kết nối không làm sai hệ thống; dữ liệu không mất khi tổng hợp | UDP: gói sau thay gói mất; heartbeat phát hiện OFFLINE; gộp + xóa raw trong một transaction; `INSERT OR IGNORE` + `UNIQUE (agent_id, bucket_start)` idempotent; SQLite WAL + busy_timeout 5000 ms, đúng 1 writer |
| **Hiệu năng** | Chịu được nhiều agent gửi 1 mẫu/giây | Receiver tách 1 luồng `receive()` khỏi 4 worker có hàng đợi (queue 2000, `CallerRunsPolicy` chống tràn bộ nhớ); cảnh báo có hysteresis + rate limit (sự kiện bảo mật tối đa 1/phút/agent/loại) chống email flood |
| **Dễ triển khai** | Cài đặt tối giản | Jar tự chứa (`*-all.jar`) chỉ cần JRE 17+; SQLite tự tạo khi khởi động; cấu hình `.properties` ngoài jar; script `build.bat`, `run-*.bat`, `firewall-*.ps1` |
| **Kiểm thử** | Có bằng chứng hoạt động | 165 test JUnit 5 (theo `README.md` mục 19 và `docs\03-huong-dan-su-dung\08-kiem-thu.md`), gồm test tích hợp mở socket UDP/TCP thật trên loopback; script kiểm thử `run-invalid-hmac-test.bat`, `run-replay-test.bat`, `run-load-test.bat` |

---

## 4. Kiểm chứng 5 yêu cầu bổ sung của giảng viên

Trạng thái: **Đáp ứng** = hệ thống hiện tại có cơ chế này trong code/docs;
**Đáp ứng một phần** = có nhưng cần điều kiện; **Chưa đáp ứng** = không tìm
thấy.

| # | Yêu cầu của giảng viên | Trạng thái | Cách hệ thống hiện tại đáp ứng (dẫn chứng) |
|---|---|---|---|
| 1 | **Bảo mật luồng UDP**: chữ ký HMAC-SHA256 + chống phát lại (sequence + timestamp) | **Đáp ứng** | (a) Ký: `PacketSigner` + `HmacUtil.hmacSha256Hex` (HmacSHA256), ký trên chuỗi canonical chuẩn hóa bởi `PacketCanonicalizer` (13 trường telemetry, `Locale.ROOT`, 4 chữ số thập phân). (b) Kiểm tra: `PacketValidator` — thứ tự shape → phiên bản → agent đã đăng ký → HMAC so sánh hằng thời gian (`MessageDigest.isEqual`) → timestamp trong **±30 000 ms** (`ProtocolConstants.DEFAULT_TIMESTAMP_TOLERANCE_MS`, `ProtocolConstants.java:31`) → replay. (c) Chống phát lại: `ReplayProtector` — cửa sổ bitmap **64 bit** theo `(agentId, sessionId)` (`WINDOW_SIZE = 64`, `ReplayProtector.java:48`), tính theo session nên agent khởi động lại không bị khóa; `sessionId` nằm trong chuỗi canonical nên được ký. (d) Kiểm chứng: `scripts\run-invalid-hmac-test.bat`, `scripts\run-replay-test.bat`, test `ReplayProtectorTest`, `UdpIntegrationTest`. Tài liệu: `docs\02-kien-truc-va-nguyen-ly\05-bao-mat-hmac-va-chong-phat-lai.md`. Gói bị từ chối không ghi DB, không hiện GUI, không sinh cảnh báo ngưỡng; chỉ ghi thành sự kiện bảo mật (tối đa 1/phút/agent/loại, **không gửi email**). |
| 2 | **Data aggregation & retention**: dữ liệu thô 60 phút → bucket 5 phút → giữ tổng hợp 30 ngày (chế độ demo: 60 giây / 10 giây / 300 giây) | **Đáp ứng** | `config\monitor.properties`: `raw.retention.minutes=60`, `aggregation.bucket.minutes=5`, `aggregate.retention.days=30`, `aggregation.interval.seconds=300`. `config\monitor-demo.properties`: `raw.retention.seconds=60`, `aggregation.bucket.seconds=10`, `aggregate.retention.seconds=300` (job 15 s). Triển khai: `AggregationService.runOnce` + `MetricRepository.aggregateAndPrune` — gộp `metric_raw` → `metric_5m` (giữ `sample_count`, trung bình **và** cực đại) rồi xóa raw **trong cùng một transaction**; cutoff được căn xuống biên bucket nên bucket đang dở không bị tóm tắt nửa. Hai profile dùng **cùng một đường code** (không nhánh demo riêng). Bảng: `metric_raw`, `metric_5m` (`UNIQUE (agent_id, bucket_start)` → idempotent). Tài liệu: `docs\02-kien-truc-va-nguyen-ly\06-luu-tru-va-tong-hop.md`. |
| 3 | **Cảnh báo đa kênh**: GUI + cơ sở dữ liệu + Email (SMTP Gmail) | **Đáp ứng** (email cần cấu hình bí mật để bật) | (a) **GUI**: `AlertPanel` + `MonitorEventBus` đẩy sự kiện lên giao diện. (b) **Database**: `AlertRepository.insert` vào bảng `alerts` (8 loại: `CPU_HIGH`, `MEMORY_HIGH`, `NETWORK_IN_HIGH`, `NETWORK_OUT_HIGH`, `AGENT_OFFLINE`, `AGENT_RECOVERED`, `SECURITY_INVALID_HMAC`, `SECURITY_REPLAY_PACKET`). (c) **Email**: `EmailNotifier` → `SmtpMailTransport` — mặc định `smtp.gmail.com:587` (`EmailConfigLoader.DEFAULT_HOST = "smtp.gmail.com"`), STARTTLS bắt buộc (`mail.smtp.starttls.required=true` trong `SmtpMailTransport.java:73`), xác thực bằng **Gmail App Password** đọc từ env (`SMTP_USERNAME`, `SMTP_APP_PASSWORD`, `ALERT_RECIPIENT_EMAIL`) hoặc `config\email-secrets.properties`. **Lưu ý**: theo `README.md` mục 18, mật khẩu chưa điền (placeholder) nên email đang tắt, tab Settings hiện `NOT CONFIGURED`; khi tắt, GUI và DB vẫn chạy bình thường. **Hệ thống dùng EMAIL, không dùng Telegram.** Cơ chế chống spam: mỗi cặp (agent, loại) tối đa 1 sự kiện `OPEN`; hysteresis 2 điểm % (CPU/RAM) và 5% ngưỡng (mạng); sự kiện bảo mật **không** gửi email. Tài liệu: `docs\02-kien-truc-va-nguyen-ly\07-canh-bao-va-thong-bao.md`, `docs\03-huong-dan-su-dung\06-cai-dat-email.md`. |
| 4 | **Giám sát Network In/Out traffic từng máy** | **Đáp ứng** | Mỗi gói `TELEMETRY` chứa `networkInBytesPerSecond` và `networkOutBytesPerSecond` (2 trong 6 chỉ số), tính từ bộ đếm byte tích lũy bởi `NetworkRateTracker` (agent-app). Monitor lưu 2 cột `network_in_bps`, `network_out_bps` trong `metric_raw` và `avg/max_network_in_bps`, `avg/max_network_out_bps` trong `metric_5m`; có ngưỡng riêng `network_in_threshold` / `network_out_threshold` (mặc định 50 Mbps, đơn vị `Mbps` khi so sánh qua `ByteRateFormatter.toMbps`), sinh cảnh báo `NETWORK_IN_HIGH` / `NETWORK_OUT_HIGH`; cấu hình từ xa qua `networkInThresholdMbps` / `networkOutThresholdMbps`. Hiển thị trên dashboard và biểu đồ lịch sử. |
| 5 | **Tối ưu chi phí thực nghiệm**: simulator-app giả lập nhiều agent (cùng máy, nhiều cổng) | **Đáp ứng** | (a) `simulator-app` — 4 mode `valid` / `invalid-hmac` / `replay` / `load`; mode `load` mặc định chạy **20 định danh agent** song song trong 60 giây, interval 1000 ms về cùng UDP 6000 (`--agents=20`, `--duration=60`, `--interval=1000` — `SimulatorMain.printUsage`). (b) Demo nhiều agent **cùng một máy**: `scripts\run-local-demo.bat` mở 1 monitor + 4 agent thật, mỗi agent một cổng TCP riêng 5001–5004 (AGENT-01…04) — khi ra máy riêng thì tất cả dùng 5000. Nhờ vậy không cần mua/dùng nhiều máy vật lý để thử nghiệm. Tài liệu: `docs\04-tai-lieu-class\06-simulator-app.md`, `README.md` mục 10. |

---

## 5. Các kịch bản sử dụng chính

| # | Kịch bản | Cách chạy | Kết quả mong đợi |
|---|---|---|---|
| 1 | **Demo local 4 agent** (một máy) | `scripts\run-local-demo.bat` → mở 5 cửa sổ: 1 monitor + 4 agent (AGENT-01…04, TCP 5001–5004); thêm agent trên tab Dashboard với host `127.0.0.1` | Bảng dashboard có 4 dòng cập nhật mỗi giây; biểu đồ chạy; hạ ngưỡng CPU để thấy cảnh báo |
| 2 | **Đa máy (nhiều máy vật lý)** | Copy `agent-app-1.0.0-all.jar` + `agentNN.properties` (đặt `monitor.host` = IP máy monitor, `agent.tcp.port=5000`) sang từng máy; chạy `scripts\firewall-agent.ps1` (máy agent) và `scripts\firewall-monitor.ps1` (máy monitor) | Agent từ nhiều IP cùng xuất hiện trên một monitor; chi tiết `docs\03-huong-dan-su-dung\05-trien-khai-nhieu-may.md` |
| 3 | **Kiểm thử bảo mật — chữ ký sai** | `scripts\run-invalid-hmac-test.bat` | Dashboard **không** nhảy CPU 100%; không dòng mới trong `metric_raw`; không có `CPU_HIGH` giả; có `SECURITY_INVALID_HMAC` trong log và thẻ Alerts |
| 4 | **Kiểm thử bảo mật — gửi lại** | `scripts\run-replay-test.bat` | Gói #1 ACCEPTED, gói #2 và #3 (trùng y hệt) REJECTED với `SECURITY_REPLAY_PACKET`, gói #4 (sequence mới) ACCEPTED |
| 5 | **Kiểm thử tải** | `scripts\run-load-test.bat` (mode `load`, 20 định danh × 60 giây) | Giao diện không đơ; hàng đợi worker đầy thì chạy `CallerRunsPolicy` (throttle) thay vì tràn bộ nhớ; thống kê gói nhận/từ chối in log |
| 6 | **Kiểm thử tự động** | `scripts\test.bat` | 165 test JUnit 5 chạy qua (theo `README.md` mục 19; kết quả thực tế ghi ở `docs\03-huong-dan-su-dung\09-bao-cao-kiem-thu.md`) |
| 7 | **Xem tổng hợp dữ liệu (demo retention)** | `scripts\run-monitor-demo.bat` (profile demo: raw 60 s, bucket 10 s, aggregate 300 s) | Sau vài phút thấy dữ liệu gộp vào `metric_5m` và bị dọn khỏi `metric_raw` — chứng minh aggregation mà không phải chờ 60 phút |
| 8 | **Trình bày ngắn (12–15 phút)** | Theo `docs\03-huong-dan-su-dung\07-kich-ban-demo.md` | Luồng demo có sẵn cho ca thi |

---

## 6. Hướng mở rộng

Các ý sau **chưa** nằm trong yêu cầu hiện tại của đề tài, chỉ là hướng phát
tiếp hợp lý nếu có thêm thời gian:

1. **Kênh thông báo thứ hai** (ví dụ Telegram bot) — mở rộng interface
   `Notifier` sẵn có (`EmailNotifier`, `NoOpNotifier`, `CompositeNotifier`)
   mà không đụng vào luồng cảnh báo.
2. **Web dashboard** đọc SQLite/đổ dữ liệu ra API — nếu muốn xem từ xa qua
   trình duyệt, thay vì cài ứng dụng Swing.
3. **Mã hóa kênh UDP** (encrypt ngoài sign) hoặc **TLS cho TCP điều khiển** —
   hiện kênh TCP chỉ truyền JSON, HMAC mới phủ gói UDP.
4. **Dashboard tổng hợp nhiều monitor** / cơ chế báo lại (reporting) định kỳ
   qua email (digest hằng ngày) thay vì chỉ cảnh báo khi sự kiện.
5. **Phân quyền người dùng** và ghi audit thao tác cấu hình từ xa.

---

## 7. Điểm chưa chắc định / cần lưu ý khi báo cáo

1. **Email chưa bật thực tế**: code gửi email đã có nhưng `config\
   email-secrets.properties` chưa điền App Password (README mục 18) — khi trình
   bày cần ghi rõ "kênh email đã xây dựng, cần cấu hình bí mật để bật".
2. **Số lượng 165 test** lấy theo `README.md` và `08-kiem-thu.md`; chưa chạy lại
   `scripts\test.bat` trong phiên này để xác nhận — số thực tế nên đối chiếu
   `docs\03-huong-dan-su-dung\09-bao-cao-kiem-thu.md`.
3. **Khác biệt README vs code** đã được ghi nhận trong
   `02-mo-hinh-he-thong.md` mục 6: (a) chuỗi ký trong README mục 13 liệt kê thiếu
   `sessionId` nhưng code **có** (theo code); (b) README mục 14 nói cửa sổ chống
   replay bị quên sau 60 giây im lặng nhưng **không tìm thấy code** này (chỉ có
   LRU 4 session/agent); (c) `AgentStatus.WARNING` được khai báo nhưng chưa thấy
   code gán.
4. **Cổng TCP 5001–5004** chỉ dùng khi 4 agent chung một máy; ở chế độ đa máy
   mọi agent dùng 5000 — đừng trình bày nhầm.

---

## Tài liệu liên quan

- [01-tong-quan-de-tai.md](01-tong-quan-de-tai.md) — giới thiệu tổng quan.
- [Cấu trúc dự án](../02-kien-truc-va-nguyen-ly/01-cau-truc-du-an.md)
- [Mô hình hệ thống](../02-kien-truc-va-nguyen-ly/02-mo-hinh-he-thong.md)
- [Bảo mật HMAC và chống phát lại](../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md)
- [Lưu trữ và tổng hợp](../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md)
- [Cảnh báo và thông báo](../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md)
- [Tài liệu lớp — simulator-app](../04-tai-lieu-class/06-simulator-app.md)
