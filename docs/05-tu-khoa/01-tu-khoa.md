# Từ vựng (Glossary)

Từ khóa của dự án **Server Health Monitoring** (nhóm 10), giải thích theo đúng code và tài liệu
hiện có. Mỗi mục gồm cách đọc, nhóm, định nghĩa ngắn (kèm class/hằng số/cấu hình thật) và liên kết
tài liệu liên quan. Dùng Ctrl + F để tra cứu nhanh.

## Thống kê

### Theo nhóm

| Nhóm | Số entry |
| --- | --- |
| Giao thức & mạng | 20 |
| Bảo mật | 17 |
| Giám sát & cảnh báo | 12 |
| Lưu trữ | 13 |
| Cấu hình & build | 9 |
| Kiến trúc & đa luồng | 13 |
| UI & thông báo | 10 |
| Khái niệm dự án | 12 |
| **Tổng** | **106** |

### Theo chữ cái

| Chữ cái | A | B | C | D | E | F | G | H | I | J | L | M | N | O | P | R | S | T | U | V | W |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Số entry | 14 | 4 | 8 | 3 | 3 | 2 | 1 | 6 | 2 | 4 | 1 | 12 | 2 | 3 | 7 | 5 | 13 | 8 | 4 | 2 | 2 |

## Mục lục

- [A](#a)
- [B](#b)
- [C](#c)
- [D](#d)
- [E](#e)
- [F](#f)
- [G](#g)
- [H](#h)
- [I](#i)
- [J](#j)
- [L](#l)
- [M](#m)
- [N](#n)
- [O](#o)
- [P](#p)
- [R](#r)
- [S](#s)
- [T](#t)
- [U](#u)
- [V](#v)
- [W](#w)

## A

### Agent
**Đọc:** "ây-jent" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Chương trình chạy trên máy cần giám sát, thu thập chỉ số hệ thống rồi gửi về
monitoring client. Mỗi agent có `agent.id`, cổng TCP riêng (mặc định 5000), gửi telemetry UDP
mỗi 1000 ms và heartbeat UDP mỗi 5000 ms tới cổng 6000 của client. Cấu hình nằm ở
`config\agent01.properties` ... `agent04.properties`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/02-mo-hinh-he-thong.md

### AgentMain
**Đọc:** "ây-jent-mein" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Lớp `main` của module `agent-app` (`agent\src\main\java\...\agent\AgentMain.java`).
Đọc file cấu hình qua `AgentConfigLoader`, khởi tạo `AgentRuntime` rồi đăng ký shutdown hook
tên `agent-shutdown` để dừng có trật tự.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

### AgentRegistry
**Đọc:** "ây-jent-re-jis-tri" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Bộ nhớ trong (in-memory) của monitoring client giữ danh sách agent đã đăng ký
(thông tin, secret, ngưỡng cảnh báo), dùng chung cho luồng UDP nhận gói và luồng giao diện.
Truy cập được từ nhiều luồng nên các thao tác ghi được bảo vệ bằng khóa `synchronized`.
**Xem thêm:** ../04-tai-lieu-class/03-monitor-app-phan-1.md

### AgentRuntime
**Đọc:** "ây-jent-ran-taim" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Lõi vòng đời phía agent: sở hữu `ScheduledExecutorService` với
`SCHEDULER_THREADS = 2` (`AgentRuntime.java:36`) để chạy lịch thu thập metric và gửi
heartbeat, đồng thời điều phối `UdpTelemetrySender` và `TcpControlServer`.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

### AgentTcpConnection
**Đọc:** "ây-jent-ti-si pi-kep-necn" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Đại diện một kết nối TCP điều khiển đang mở ở phía client: đặt
`candidate.setSoTimeout(timeoutMs)` (`AgentTcpConnection.java:66`) với `tcp.timeout.ms=5000`,
theo dõi hoạt động cuối cùng để phát hiện kết nối chết và gửi lại yêu cầu qua kênh này.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md

### agents (bảng)
**Đọc:** "ây-jents" · **Nhóm:** Lưu trữ
**Nghĩa:** Bảng trong SQLite (`data\monitoring.db`) với 14 cột, mỗi dòng là một agent mà
client biết tới: định danh, hostname, địa chỉ, cổng, trạng thái, ngưỡng cảnh báo, secret
đã che. Được `AgentRepository` đọc/ghi và nạp lại khi khởi động.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

### AggregationService
**Đọc:** "a-gri-gei-sen ser-vis" · **Nhóm:** Lưu trữ
**Nghĩa:** Job nền tổng hợp dữ liệu thô vào bucket 5 phút: `runOnce(nowMs)` gộp `metric_raw`
thành `metric_5m` rồi xóa dòng cũ theo retention. Chạy định kỳ qua
`aggregation.interval.seconds=300` (profile demo: 15 giây), idempotent nên chạy lại không
tạo dữ liệu sai.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

### alerts (bảng)
**Đọc:** "a-lerts" · **Nhóm:** Lưu trữ
**Nghĩa:** Bảng lưu cảnh báo: mỗi dòng có agent, loại (`AlertType`), mức
(`AlertSeverity`), giá trị đo, ngưỡng, thời điểm mở và trạng thái `AlertState`
(`OPEN`/`RESOLVED`). Có chỉ mục `idx_alerts_opened_at` và `idx_alerts_agent_status`,
do `AlertRepository` quản lý.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

### AlertService
**Đọc:** "a-lert ser-vis" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Dịch vụ đánh giá ngưỡng và mở/đóng cảnh báo: `evaluateThreshold` áp dụng
dải tự hồi (hysteresis) `PERCENTAGE_HYSTERESIS = 2.0` điểm phần trăm và
`RATE_HYSTERESIS_FRACTION = 0.05` cho mạng; `recordSecurityEvent` có chống spam với
`SECURITY_EVENT_COOLDOWN_MS = 60_000`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md

### AlertSeverity
**Đọc:** "a-lert si-vie-ri-ti" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Enum mức độ cảnh báo gồm `INFO`, `WARNING`, `CRITICAL`, quyết định màu sắc
trong bảng Alerts và mức ưu tiên của email thông báo.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### AlertType
**Đọc:** "a-lert tai-p" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Enum 8 loại cảnh báo trong `AlertType.java`: `CPU_HIGH`, `MEMORY_HIGH`,
`NETWORK_IN_HIGH`, `NETWORK_OUT_HIGH`, `AGENT_OFFLINE`, `AGENT_RECOVERED`,
`SECURITY_INVALID_HMAC`, `SECURITY_REPLAY_PACKET`; mỗi loại có `displayName` dùng cho
chủ đề email và giao diện.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### App Password
**Đọc:** "áp pas-uord" · **Nhóm:** Cấu hình & build
**Nghĩa:** Mật khẩu ứng dụng Gmail dùng cho SMTP (thay cho mật khẩu thật), đặt trong
`config\email-secrets.properties` qua khóa `smtp.appPassword` hoặc biến môi trường
`SMTP_APP_PASSWORD`. Giá trị placeholder `PASTE_GMAIL_APP_PASSWORD_HERE` bị chặn không gửi
mail; đọc thêm qua biến `safeReason` để không lộ mật khẩu khi báo lỗi.
**Xem thêm:** ../04-tai-lieu-class/03-monitor-app-phan-1.md

### AppPaths
**Đọc:** "áp pads" · **Nhóm:** Cấu hình & build
**Nghĩa:** Lớp định vị thư mục gốc của ứng dụng (nơi chứa `config`, `data`, `logs`) — đọc
`-Dmonitoring.home` nếu được truyền, nếu không thì suy ra từ thư mục làm việc hiện hành,
để mọi module đọc cùng một bộ file cấu hình.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### AtomicLong & LongAdder
**Đọc:** "a-tomic long và long a-der" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Biến nguyên tử trong `java.util.concurrent.atomic` dùng để đếm số gói nhận, số
gói bị từ chối... từ nhiều luồng mà không cần khóa; `LongAdder` phù hợp khi nhiều luồng
cùng ghi liên tục (đếm ở luồng nhận UDP).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/08-mo-hinh-da-luong.md

## B

### Blocking queue (ArrayBlockingQueue)
**Đọc:** "blo-king kyu" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Hàng đợi có giới hạn dung lượng chặn luồng sản xuất khi đầy. Dự án dùng
`ArrayBlockingQueue(2000)` cho luồng nhận UDP (`UdpTelemetryReceiver.QUEUE_CAPACITY = 2000`)
và `ArrayBlockingQueue(50)` cho luồng gửi email (`EmailNotifier.QUEUE_CAPACITY = 50`).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/08-mo-hinh-da-luong.md

### Buffer (bộ đệm datagram)
**Đọc:** "ba-fer" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Mảng byte nhận gói UDP: `byte[] buffer = new byte[ProtocolConstants.MAX_UDP_PACKET_SIZE]`
(`UdpTelemetryReceiver.java:96`), kích thước tối đa 4096 byte; gói lớn hơn bị bỏ qua ngay
ở người gửi (`UdpTelemetrySender` kiểm tra trước khi `socket.send`).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md

### Build
**Đọc:** "bi-lt" · **Nhóm:** Cấu hình & build
**Nghĩa:** Biên dịch toàn dự án bằng Maven (`build.bat` / `mvn clean package`), sinh JAR từng
module trong `target\` và bản đóng gói trong `release\`. POM cha đặt `groupId`
`vn.edu.hutech.group10`, `artifactId` `server-health-monitoring`, `version` `1.0.0`,
Java release 17; plugin `maven-shade-plugin` 3.5.3 tạo JAR `*-all.jar`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/01-cau-truc-du-an.md

### ByteRateFormatter
**Đọc:** "bai-reit-for-ma-ter" · **Nhóm:** UI & thông báo
**Nghĩa:** Lớp tiện ích trong `monitoring-common` chuyển số byte/giây thành chuỗi dễ đọc
(B/s, KB/s, MB/s) để hiển thị cho cột mạng trong bảng Dashboard và biểu đồ.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

## C

### CallerRunsPolicy
**Đọc:** "co-ler ranz po-li-si" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Chính sách từ chối của `ThreadPoolExecutor`: khi hàng đợi đầy, luồng gọi enqueue
tự chạy task đó, ép luồng nhận UDP chậm lại thay vì vứt gói. Áp dụng cho hàng đợi 2000 phần
tử của `UdpTelemetryReceiver` (`UdpTelemetryReceiver.java:74`).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/08-mo-hinh-da-luong.md

### canonical string
**Đọc:** "ca-no-ni-cal string" · **Nhóm:** Bảo mật
**Nghĩa:** Chuỗi chuẩn hóa được ký HMAC, các trường xếp thứ tự cố định, tách bằng `'|'`.
Telemetry 13 trường: `protocolVersion|type|agentId|hostname|sessionId|timestamp|sequence|
cpuUsage|memoryUsage|connectionCount|uptimeSeconds|networkInBytesPerSecond|
networkOutBytesPerSecond`; heartbeat 7 trường (không có số liệu). Hai phia phải dựng chuỗi
giống hệt nhau (`PacketCanonicalizer`).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md

### client–server
**Đọc:** "khai-lent ser-ver" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Mô hình của hệ thống: monitoring client là "server" nghe UDP 6000 và là "client"
kết nối TCP tới agent; agent mở TCP server ở cổng 5000 cho kênh điều khiển. Cùng một tiến
trình vừa là client vừa là server tùy kênh.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/02-mo-hinh-he-thong.md

### CompositeNotifier
**Đọc:** "com-po-zit no-ti-fai-er" · **Nhóm:** UI & thông báo
**Nghĩa:** `Notifier` ghép nhiều notifier lại và gọi lần lượt, cho phép gửi email thật
(`EmailNotifier`) cùng notifier ghi nhận trong kiểm thử (`RecordingNotifier`) hoặc
`NoOpNotifier` khi chưa cấu hình SMTP.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md

### ConcurrentHashMap
**Đọc:** "con-cu-rent hash-map" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Map an toàn luồng thay cho `synchronized` toàn bộ: dùng cho bản đồ agent
(`AgentRegistry`), cửa sổ replay theo phiên (`ReplayProtector.agents`) và các bộ đệm trạng
thái đọc-ghi từ nhiều luồng.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/08-mo-hinh-da-luong.md

### CONFIG_UPDATE / CONFIG_ACK
**Đọc:** "con-fig ap-deit / con-fig ei-si-kyu" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Cặp message TCP điều khiển: client gửi `CONFIG_UPDATE` (ngưỡng cảnh báo, khoảng
gửi...), agent kiểm tra hợp lệ rồi trả `CONFIG_ACK` xác nhận; nếu không hợp lệ trả `ERROR`.
Một yêu cầu – một đáp trả, nên `requestId` của `CONFIG_ACK` phải khớp.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md

### constant-time
**Đọc:** "cons-tent taim" · **Nhóm:** Bảo mật
**Nghĩa:** So sánh chuỗi không phụ thuộc thời gian chạy để kẻ tấn công không dò được HMAC
đúng qua thời gian phản hồi. Dự án dùng `MessageDigest.isEqual` (so sánh nhị phân) thay vì
`String.equals`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md

### cooldown
**Đọc:** "ku-l-daun" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Khoảng thời gian tối thiểu giữa hai lần ghi cùng một loại sự kiện bảo mật, chống
spam: `SECURITY_EVENT_COOLDOWN_MS = 60_000` (60 giây) trong `AlertService.recordSecurityEvent`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md

## D

### Dashboard (tab)
**Đọc:** "dash-bord" · **Nhóm:** UI & thông báo
**Nghĩa:** Tab đầu của giao diện (`DashboardPanel`): bảng danh sách agent với trạng thái,
CPU, RAM, mạng; dùng `AgentTableModel` và làm mới theo sự kiện từ `MonitorEventBus`.
**Xem thêm:** ../04-tai-lieu-class/05-monitor-app-phan-3.md

### Datagram (gói UDP)
**Đọc:** "da-te-gram" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Đơn vị truyền độc lập của UDP, không đảm bảo tới nơi hay đúng thứ tự. Mỗi gói
telemetry/heartbeat là một datagram JSON có chữ ký HMAC, tối đa 4096 byte
(`MAX_UDP_PACKET_SIZE`), gửi từ `UdpTelemetrySender` đến cổng 6000.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md

### DatabaseManager
**Đọc:** "de-ta-beis ma-ni-jer" · **Nhóm:** Lưu trữ
**Nghĩa:** Lớp mở và cấu hình kết nối SQLite (`data\monitoring.db`, bản demo:
`data\monitoring-demo.db`): bật `journal_mode=WAL`, `busy_timeout=5000`,
`foreign_keys=ON`, `synchronous=NORMAL`, rồi đọc file schema `.sql` để tạo bảng.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

## E

### EmailConfig
**Đọc:** "i-meil kon-fig" · **Nhóm:** Cấu hình & build
**Nghĩa:** Cấu hình SMTP: host `smtp.gmail.com`, cổng 587, tên đăng nhập, mật khẩu ứng dụng
và người nhận. Nạp từ `config\email-secrets.properties` (hoặc biến môi trường
`SMTP_USERNAME`, `SMTP_APP_PASSWORD`, `ALERT_RECIPIENT_EMAIL`); placeholder
`PASTE_GMAIL_APP_PASSWORD_HERE` coi như chưa cấu hình.
**Xem thêm:** ../04-tai-lieu-class/03-monitor-app-phan-1.md

### EmailNotifier
**Đọc:** "i-meil no-ti-fai-er" · **Nhóm:** UI & thông báo
**Nghĩa:** Gửi email cảnh báo qua một luồng nền riêng tên `email-sender`
(`ThreadPoolExecutor(1, 1, ...)`, hàng đợi 50, daemon). Nội dung do
`EmailMessageBuilder` dựng, gửi qua `SmtpMailTransport`; đầy hàng đợi thì rơi vào
chính sách mặc định `AbortPolicy` (từ chối có kiểm soát).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md

### ExecutorService
**Đọc:** "eg-ze-kyu-tor ser-vis" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Giao diện quản lý luồng của Java: `newScheduledThreadPool(2)` cho lịch gửi của
agent (`AgentRuntime.SCHEDULER_THREADS = 2`) và lịch job của client
(`MonitorApplication.SCHEDULER_THREADS = 2`); `newFixedThreadPool(8)` cho phiên TCP ở agent
(`TcpControlServer.MAX_SESSIONS = 8`).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/08-mo-hinh-da-luong.md

## F

### Firewall (script)
**Đọc:** "fai-o-ol" · **Nhóm:** Cấu hình & build
**Nghĩa:** Kịch bản PowerShell mở port cho Windows: `scripts\firewall-monitor.ps1` cho máy
chạy client (UDP 6000), `scripts\firewall-agent.ps1` cho máy chạy agent (TCP 5000).
Chú ý profile của rule (Private/Public) — sai profile thì gói vẫn bị chặn.
**Xem thêm:** ../03-huong-dan-su-dung/03-cau-hinh-he-thong.md

### Foreign key (khóa ngoại)
**Đọc:** "fo-rin ki" · **Nhóm:** Lưu trữ
**Nghĩa:** Ràng buộc tham chiếu: các bảng `metric_raw`, `metric_5m`, `alerts` trỏ về
`agents(agent_id)`, nên SQLite chặn ghi "mồ côi" khi `foreign_keys=ON`; xóa agent sẽ liên
quan tới dữ liệu con.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

## G

### Gson
**Đọc:** "gon" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Thư viện `com.google.code.gson:gson` 2.11.0 (POM cha) để tuần tự hóa message JSON;
`JsonMessageCodec` dựng `Gson`/`JsonParser` mã hóa và giải mã thân packet trong khung TCP
và gói UDP.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

## H

### heartbeat
**Đọc:** "hart-bit" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Gói nhỏ agent gửi mỗi 5000 ms (`DEFAULT_HEARTBEAT_INTERVAL_MS`) để báo "tôi còn
sống"; không nhận được gì trong 15000 ms (`heartbeat.timeout.ms`) thì client gắn OFFLINE.
Cùng một kênh UDP với telemetry nhưng type riêng và chỉ có 7 trường canonical.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/03-cach-thuc-hoat-dong.md

### HeartbeatMonitor
**Đọc:** "hart-bit mo-ni-tor" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Theo dõi độ sống còn phía client: `markAlive(agentId, packetTimestamp, nowMs)`
cập nhật lần nhận cuối, `check(nowMs)` quét định kỳ (`heartbeat.check.interval.ms=2000`)
để hạ OFFLINE hoặc báo `AGENT_RECOVERED` khi hoạt động trở lại.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md

### HeartbeatScheduler
**Đọc:** "hart-bit sce-du-le-r" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Lập lịch phía agent gửi `HEARTBEAT` UDP theo `heartbeat.interval` trong
`config\agent0X.properties` (mặc định 5000 ms, tối thiểu 1000 ms theo
`MIN_HEARTBEAT_INTERVAL_MS`), chạy trên scheduler 2 luồng của `AgentRuntime`.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

### hex
**Đọc:** "heks" · **Nhóm:** Bảo mật
**Nghĩa:** Chuỗi 16 ký tự số và a–f để biểu diễn byte. HMAC luôn xuất hex **chữ thường**
64 ký tự: bảng `HEX = "0123456789abcdef"` trong `HmacUtil.toHex`; hai đầu so sánh đều
hạ chữ thường với `Locale.ROOT` trước khi so.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md

### HmacSHA256
**Đọc:** "et-si-et-mát-she-a-256" · **Nhóm:** Bảo mật
**Nghĩa:** Thuật toán ký khóa bí mật chung `HmacSHA256` (`ProtocolConstants.HMAC_ALGORITHM`)
— mỗi agent có secret riêng trong `config\agent0X.properties`; `HmacUtil.hmacSha256Hex`
ký canonical string thành hex 64 ký tự, phía client dùng `PacketValidator` để kiểm tra.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md

### hysteresis
**Đọc:** "hi-ste-re-sis" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Dải tự hồi để cảnh báo không chớp nháy: với phần trăm, giá trị phải tụt xuống
dưới ngưỡng trừ `PERCENTAGE_HYSTERESIS = 2.0` điểm mới đóng; với mạng thì trừ
`RATE_HYSTERESIS_FRACTION = 0.05` (5%) của ngưỡng Mbps.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md

## I

### Index (chỉ mục)
**Đọc:** "in-deks" · **Nhóm:** Lưu trữ
**Nghĩa:** Cấu trúc chỉ mục phụ giúp truy vấn theo agent/thời gian nhanh hơn: `idx_metric_raw_agent_time`,
`idx_metric_5m_agent_bucket`, `idx_alerts_agent_status`, `idx_alerts_opened_at` — thiếu
chỉ mục thì truy History/Alerts quét toàn bảng.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

### Invalid HMAC (kịch bản)
**Đọc:** "in-va-lid et-si-et-mát" · **Nhóm:** Bảo mật
**Nghĩa:** Kịch bản kiểm thử gói bị làm giả: `simulator-app` mode `invalid-hmac`
(`InvalidHmacSender`) gửi payload chữ ký sai để xác minh client từ chối và ghi cảnh báo
`SECURITY_INVALID_HMAC`; chạy qua `scripts\run-invalid-hmac-test.bat`.
**Xem thêm:** ../04-tai-lieu-class/06-simulator-app.md

## J

### Jakarta Mail
**Đọc:** "dja-kar-ta meil" · **Nhóm:** UI & thông báo
**Nghĩa:** Thư viện gửi mail `com.sun.mail:jakarta.mail` 2.0.1 (biến
`jakarta.mail.version` trong POM cha); `SmtpMailTransport` dựng `Session` SMTP với
STARTTLS để gửi email cảnh báo.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md

### JDBC (sqlite-jdbc)
**Đọc:** "et-dei-bi-si" · **Nhóm:** Lưu trữ
**Nghĩa:** API kết nối SQL chuẩn của Java; driver `org.xerial:sqlite-jdbc` 3.46.1.3 cho
phần SQLite, nạp qua `jdbc:sqlite:` bởi `DatabaseManager`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

### JSON
**Đọc:** "dzei-soan" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Định dạng thân message: gói UDP là một object JSON duy nhất; TCP cũng truyền
JSON nhưng bọc trong khung dài 4 byte prefix. `JsonMessageCodec` là lớp biên dịch/giải mã
duy nhất, nhờ đó thêm trường không phá vỡ parser bên kia.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md

### JUnit 5
**Đọc:** "dzei-ju-nit 5" · **Nhóm:** Cấu hình & build
**Nghĩa:** Framework kiểm thử `org.junit.jupiter:junit-jupiter` 5.10.2; 165 test trong 3
module (`monitoring-common`, `agent-app`, `monitor-app`), chạy bằng `test.bat` /
`mvn test` (plugin surefire 3.2.5).
**Xem thêm:** ../03-huong-dan-su-dung/08-kiem-thu.md

## L

### Load test (mô hình tải)
**Đọc:** "lo-d test" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Chế độ `load` của simulator (`LoadSimulator`) bắn hàng loạt gói hợp lệ để đo
hàng đợi UDP và độ trễ xử lý; mặc định 20 agent, gửi mỗi 1000 ms trong 60 s, chạy qua
`scripts\run-load-test.bat`.
**Xem thêm:** ../04-tai-lieu-class/06-simulator-app.md

## M

### Maven
**Đọc:** "me-ven" · **Nhóm:** Cấu hình & build
**Nghĩa:** Công cụ build Java quản lý 4 module (`monitoring-common`, `agent-app`,
`monitor-app`, `simulator-app`); `find-maven.bat` tự tìm Maven khi `mvn` không có trong
`PATH`. POM cha giữ version các thư viện qua property: `gson.version=2.11.0`,
`oshi.version=6.6.5`, `sqlite.version=3.46.1.3`.
**Xem thêm:** ../03-huong-dan-su-dung/01-cai-dat-va-build.md

### MessageDigest.isEqual
**Đọc:** "me-sen dai-dzest iz-i-kol" · **Nhóm:** Bảo mật
**Nghĩa:** Phương thức so sánh 2 mảng byte không rò rỉ thời gian, dùng để đối chiếu HMAC:
`MessageDigest.isEqual(a, b)` với `a`, `b` là hex chữ thường đã hạ bằng `Locale.ROOT`
(`HmacUtil`), thay cho `String.equals` bị dò được qua timing.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md

### MessageFraming
**Đọc:** "me-sen frei-ming" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Khung tin TCP: 4 byte đầu là độ dài big-endian của payload JSON, rồi đến thân
message; giới hạn `MAX_TCP_FRAME_SIZE = 1024 * 1024` (1 MB). Framing đọc theo khung chứ
không đọc theo dòng vì JSON có thể chứa xuống dòng.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md

### MessageType
**Đọc:** "me-sen tai-p" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Enum kiểu message: UDP có `TELEMETRY`, `HEARTBEAT`; TCP có `PING`, `PONG`,
`AGENT_INFO_REQUEST`, `AGENT_INFO_RESPONSE`, `CONFIG_UPDATE`, `CONFIG_ACK`, `ERROR`.
Field `type` nằm trong canonical string nên đổi type là đổi chữ ký.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### metric
**Đọc:** "me-trik" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Một mẫu chỉ số của một agent tại một thời điểm: CPU %, RAM %, số kết nối, uptime
(giây), mạng vào/ra (byte/s). Đến từ `TelemetryPacket`, lưu thành dòng `metric_raw` rồi gộp
thành bucket `metric_5m`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

### metric_5m
**Đọc:** "me-trik 5-min" · **Nhóm:** Lưu trữ
**Nghĩa:** Bảng khung 5 phút: mỗi agent mỗi `bucket_start` đúng 1 dòng (UNIQUE
`agent_id, bucket_start`), chứa giá trị trung bình/max của khoảng; do `AggregationService`
tạo, là nguồn trả lời truy vấn History khoảng dài.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

### metric_raw
**Đọc:** "me-trik ro" · **Nhóm:** Lưu trữ
**Nghĩa:** Bảng lưu nguyên xi mỗi gói telemetry được chấp nhận (1 dòng = 1 packet), có
chỉ mục `idx_metric_raw_agent_time`; giữ `raw.retention.minutes=60` (profile demo:
60 giây) rồi bị aggregation xóa.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

### Monitor
**Đọc:** "mo-ni-tor" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Monitoring client — tiến trình Swing nhận dữ liệu UDP, quản lý agent, sinh cảnh
báo và hiển thị giao diện. Chạy bằng `run-monitor.bat` (profile production) hoặc
`run-monitor-demo.bat` (`--config=config\monitor-demo.properties`).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/02-mo-hinh-he-thong.md

### MonitorApplication
**Đọc:** "mo-ni-tor a-pli-kei-sen" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Lõi ứng dụng client: giữ `MonitorConfig`, scheduler 2 luồng
(`SCHEDULER_THREADS = 2`) cho job aggregation/retention, các service và
`MonitorEventBus`; `start()` mở DB và socket trước khi hiện cửa sổ.
**Xem thêm:** ../04-tai-lieu-class/03-monitor-app-phan-1.md

### MonitorEventBus
**Đọc:** "mo-ni-tor i-vent bas" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Trung tâm phát sự kiện nội bộ (agent đổi trạng thái, metric mới, cảnh báo mới)
cho các panel Swing đăng ký qua `MonitorEventListener`; danh sách listener dùng
`CopyOnWriteArrayList` để thêm/bớt khi giao diện đang chạy không sợ race.
**Xem thêm:** ../04-tai-lieu-class/03-monitor-app-phan-1.md

### monitoring.home
**Đọc:** "mo-ni-ting houm" · **Nhóm:** Cấu hình & build
**Nghĩa:** Hệ thống property `-Dmonitoring.home=<thư mục gốc>` chỉ nơi chứa `config\`,
`data\`, `logs\`; nếu không truyền thì `AppPaths` suy ra từ thư mục làm việc hiện hành —
đây là lý do phải chạy với working directory là thư mục dự án.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### MonitoringMain
**Đọc:** "mo-ni-ting me-in" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Lớp `main` của monitor-app: nhận `--config` (mặc định
`config/monitor.properties`), cấu hình logging, nạp `MonitorConfig`, khởi động
`MonitorApplication` rồi mới hiện `MainFrame`; lỗi cấu hình/cổng báo bằng dialog.
**Xem thêm:** ../04-tai-lieu-class/03-monitor-app-phan-1.md

## N

### NetworkRateTracker
**Đọc:** "net-urek reit tra-cker" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Ở agent, đọc tổng byte vào/ra của card mạng rồi lấy hiệu so với lần trước chia
thời gian ra byte/giây — biến counter tích lũy thành `networkInBytesPerSecond` /
`networkOutBytesPerSecond` trong packet.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

### NoOpNotifier
**Đọc:** "no-op no-ti-fai-er" · **Nhóm:** UI & thông báo
**Nghĩa:** Notifier "không làm gì", dùng khi email chưa cấu hình (thiếu credential hoặc
còn placeholder) để hệ thống chạy ổn định mà không báo lỗi; UI hiện trạng
`NOT CONFIGURED` thay vì im lặng.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md

## O

### Observer / Listener
**Đọc:** "ob-zerv-er / lis-te-r" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Mẫu quan sát một-nhiều: subject báo cho danh sách observer khi trạng thái đổi.
Dự án dùng `MonitorEventListener` (listener của `MonitorEventBus`) để panel cập nhật khi
không trực tiếp gọi luồng khác.
**Xem thêm:** ../04-tai-lieu-class/03-monitor-app-phan-1.md

### OFFLINE / ONLINE
**Đọc:** "of-lain / on-lain" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Trạng thái sống còn: `ONLINE` khi còn nhận được packet, `OFFLINE` khi quá
`heartbeat.timeout.ms=15000` không thấy gì, quét mỗi `heartbeat.check.interval.ms=2000`;
chuyển OFFLINE sinh `AGENT_OFFLINE`, quay lại sinh `AGENT_RECOVERED` (enum `AgentStatus`
còn `UNKNOWN` khi chưa biết gì về agent).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md

### OSHI
**Đọc:** "o-si" · **Nhóm:** Khái niệm dự án
**Nghĩa:** "Operating System and Hardware Information" — thư viện `com.github.oshi:oshi-core`
6.6.5; `OshiSystemMetricsCollector` (thông qua interface `SystemMetricsCollector`) đọc CPU,
RAM, đĩa, mạng của máy; có thể hiện thông báo registry Perflib vô hại trên Windows.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

## P

### PacketCanonicalizer
**Đọc:** "pa-ket ca-no-ni-cal-i-zer" · **Nhóm:** Bảo mật
**Nghĩa:** Lớp dựng canonical string: ghép các trường theo thứ tự cố định, tách bằng `'|'`,
định dạng số bằng `Locale.ROOT %.4f`, loại NaN/Infinite — cả packet ký và packet kiểm tra
phải dùng chung một bản dựng.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### PacketSigner
**Đọc:** "pa-ket sai-ner" · **Nhóm:** Bảo mật
**Nghĩa:** Bên gửi: dựng canonical rồi gọi `HmacUtil.hmacSha256Hex(secret, data)` gắn vào
field `hmac` của packet. Secret lấy từ `SecretProvider` (file cấu hình agent, mỗi agent
một secret).
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### PacketValidator
**Đọc:** "pa-ket va-li-dei-ter" · **Nhóm:** Bảo mật
**Nghĩa:** Bên nhận, kiểm tra theo thứ tự: canonical đúng → HMAC khớp (constant-time) →
timestamp trong ±30000 ms (`packet.timestamp.tolerance.ms`) → sequence chưa dùng
(`ReplayProtector`). Sai bước nào từ chối bước đó và log lý do.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md

### PING / PONG
**Đọc:** "ping / pong" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Cặp keepalive TCP: client gửi `PING`, agent trả `PONG` — dùng để kiểm tra kết
điều khiển còn sống và đo độ trễ, theo quy tắc một yêu cầu một đáp trả với `requestId`
khớp nhau.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md

### pom.xml
**Đọc:** "pom eks-em-el" · **Nhóm:** Cấu hình & build
**Nghĩa:** POM Maven: POM cha khai `groupId` `vn.edu.hutech.group10`,
`artifactId` `server-health-monitoring`, `version` `1.0.0`, `maven.compiler.release=17`,
các module khai `<parent>` trỏ về POM cha; shade plugin 3.5.3 tạo JAR `*-all.jar`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/01-cau-truc-du-an.md

### Profile (production / demo)
**Đọc:** "pro-fail" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Hồ sơ chạy: `config\monitor.properties` (production, DB `data\monitoring.db`,
raw giữ 60 phút, bucket 5 phút) và `config\monitor-demo.properties` (demo, DB
`data\monitoring-demo.db`, raw 60 giây, bucket 10 giây để thấy aggregation nhanh); chỉ số
khác biệt, code chung.
**Xem thêm:** ../03-huong-dan-su-dung/03-cau-hinh-he-thong.md

### ProtocolConstants
**Đọc:** "pro-to-kol kon-stents" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Lớp hằng số giao thức chung: `PROTOCOL_VERSION="1.0"`, `HMAC_ALGORITHM`,
`DEFAULT_MONITOR_UDP_PORT=6000`, `DEFAULT_AGENT_TCP_PORT=5000`,
`DEFAULT_METRIC_INTERVAL_MS=1000`, `DEFAULT_HEARTBEAT_INTERVAL_MS=5000`,
`DEFAULT_HEARTBEAT_TIMEOUT_MS=15000`, `DEFAULT_TIMESTAMP_TOLERANCE_MS=30000`,
`MAX_UDP_PACKET_SIZE=4096`, `DEFAULT_TCP_TIMEOUT_MS=5000`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/10-giao-thuc-goc.md

## R

### Replay (chống phát lại)
**Đọc:** "ri-plei" · **Nhóm:** Bảo mật
**Nghĩa:** Kẻ tấn công bắt gói hợp lệ rồi gửi lại. Dự án chống bằng 3 lớp: timestamp
±30000 ms, `sequence` đơn điệu trong cửa sổ 64 bit, và `ReplayProtector` đánh dấu bit từng
sequence đã dùng; gói lặp bị từ chối và ghi `SECURITY_REPLAY_PACKET`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md

### ReplayProtector
**Đọc:** "ri-plei pro-tek-ter" · **Nhóm:** Bảo mật
**Nghĩa:** Lớp giữ cửa sổ sequence theo `(agentId, sessionId)`: `WINDOW_SIZE = 64` bit,
`MAX_SESSIONS_PER_AGENT = 4` phiên gần nhất (session cũ nhất bị loại khi tràn). Session mới
mở cửa sổ mới nên restart agent không bị khóa, gói giả mạo không đẩy được cửa sổ thật ra
ngoài; lưu trong `ConcurrentHashMap`.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### Repository
**Đọc:** "re-po-zi-to-ri" · **Nhóm:** Lưu trữ
**Nghĩa:** Lớp truy cập bảng: `AgentRepository` (findAll, insert, update, updateStatus,
delete), `MetricRepository` (insertRaw, insertRawBatch, findRaw, findAggregates,
aggregateAndPrune), `AlertRepository` (insert, resolve, findOpen, findRecent). Tất cả đi
qua `DatabaseManager`.
**Xem thêm:** ../04-tai-lieu-class/04-monitor-app-phan-2.md

### Retention
**Đọc:** "ri-ten-sen" · **Nhóm:** Lưu trữ
**Nghĩa:** Thời gian giữ dữ liệu, đọc từ cấu hình: production
`raw.retention.minutes=60`, `aggregate.retention.days=30`; demo `raw.retention.seconds=60`,
`aggregate.retention.seconds=300`. Dòng raw hết hạn bị xóa khi aggregation chạy.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

### Rollup (tổng hợp)
**Đọc:** "ro-lap" · **Nhóm:** Lưu trữ
**Nghĩa:** Gộp nhiều dòng chi tiết thành một dòng tổng hợp theo khoảng (bucket). Xảy ra
trong `AggregationService.runOnce` / `MetricRepository.aggregateAndPrune`: raw → bucket
`aggregation.bucket.minutes=5`, idempotent nên chạy lại không nhân đôi dữ liệu.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

## S

### Secret (khóa bí mật chung)
**Đọc:** "si-cret" · **Nhóm:** Bảo mật
**Nghĩa:** Chuỗi ký HMAC, khai trong agent qua `security.sharedSecret` (ví dụ
`GROUP10_DEMO_SECRET_01` trong `config\agent01.properties`) và phải khớp với secret đã
đăng ký cho agent đó ở client; mỗi agent một secret để lộ secret này không giả mạo được
agent khác. Không commit file secret thật.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md

### SecretProvider
**Đọc:** "si-cret pro-vai-der" · **Nhóm:** Bảo mật
**Nghĩa:** Interface một phương thức `String secretFor(String agentId)`, tách nơi cất secret
khỏi logic ký/kiểm tra — phía client tra secret theo agentId, phía agent trả secret của
mình; nhờ đó thay nguồn secret không sửa code bảo mật.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### Sequence (số thứ tự gói)
**Đọc:** "si-kwens" · **Nhóm:** Bảo mật
**Nghĩa:** Số đếm tăng dần gắn vào mỗi gói, nằm trong canonical string nên được ký kèm.
Telemetry và heartbeat dùng chung một bộ đếm; giá trị đã nhận được đánh dấu trong cửa sổ
`ReplayProtector`, gói có sequence cũ ngoài cửa sổ 64 hoặc đã dùng đều bị loại.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md

### Session (sessionId)
**Đọc:** "se-sen" · **Nhóm:** Bảo mật
**Nghĩa:** Định danh phiên gửi, sinh UUID mỗi lần agent khởi động; bao phủ bởi chữ ký.
Cửa sổ replay lưu theo `(agentId, sessionId)` nên restart tạo cửa sổ mới, còn gói replay từ
phiên cũ vẫn rơi vào cửa sổ cũ và bị từ chối. Simulator dùng session `SIM-` + UUID riêng.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### Server Detail (tab)
**Đọc:** "ser-ver di-teil" · **Nhóm:** UI & thông báo
**Nghĩa:** Tab thứ hai (`ServerDetailPanel`): chi tiết một agent đang chọn — chỉ số hiện
tại, biểu đồ thời gian thực, nút `Connect` / `Configure` mở kênh TCP điều khiển tới agent.
**Xem thêm:** ../04-tai-lieu-class/05-monitor-app-phan-3.md

### Settings (tab)
**Đọc:** "se-tins" · **Nhóm:** UI & thông báo
**Nghĩa:** Tab thứ năm (`SettingsPanel`): cấu hình ứng dụng client (ngưỡng mặc định, cấu
hình email...) và hiển thị trạng thái gửi mail (`NOT CONFIGURED` khi thiếu credential).
**Xem thêm:** ../04-tai-lieu-class/05-monitor-app-phan-3.md

### Simulator (công cụ mô phỏng)
**Đọc:** "si-miu-lei-ter" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Module `simulator-app` giả lập agent để kiểm thử không cần nhiều máy; 4 mode:
`valid`, `invalid-hmac`, `replay`, `load`. Tham số `--agents=20`, `--duration=60` (giây),
`--interval=1000` (ms); entry point `SimulatorMain`.
**Xem thêm:** ../04-tai-lieu-class/06-simulator-app.md

### SmtpMailTransport
**Đọc:** "es-ti-em-pi meil trans-por" · **Nhóm:** UI & thông báo
**Nghĩa:** Lớp gửi mail thật, cài đặt SMTP `smtp.gmail.com:587` với STARTTLS, auth bằng
tên đăng nhập + App Password; nằm sau interface `MailTransport` nên thay bằng transport giả
được khi test (`RecordingNotifier` ghi nhận thay vì gửi).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md

### Socket timeout (SoTimeout)
**Đọc:** "so-ket tai-maut" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Thời gian chờ đọc tối đa trên socket, tính bằng mili giây: client đặt
`setSoTimeout(SESSION_READ_TIMEOUT_MS = 120000)` cho phiên TCP
(`TcpClientSessionHandler.java:64`), và `candidate.setSoTimeout(timeoutMs)` với
`tcp.timeout.ms=5000` khi kết nối tới agent (`AgentTcpConnection.java:66`).
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

### StartTLS
**Đọc:** "start ti-es-el" · **Nhóm:** Bảo mật
**Nghĩa:** Dòng lệnh nâng kết nối SMTP thường lên TLS (cổng 587) trước khi gửi
đăng nhập/nội dung — `SmtpMailTransport` bật nó để email không đi plaintext trên mạng.
**Xem thêm:** ../03-huong-dan-su-dung/06-cai-dat-email.md

### synchronized
**Đọc:** "sing-cro-naized" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Khóa cơ sở của Java: chỉ một luồng vào vùng nhớ được bảo vệ. Dùng cho các
phương thức trạng thái của agent (`TelemetryScheduler.start()`), registry và các cặp
đọc-ghi cần nhất quán; chi phí thấp hơn `Lock` khi vùng giữ ngắn.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/08-mo-hinh-da-luong.md

### SystemMetrics
**Đọc:** "sis-tem me-triks" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Model thuần dữ liệu một lần đo: CPU %, RAM %, số kết nối, uptime giây, mạng
vào/ra byte/giây — kết quả cuối cùng của `SystemMetricsCollector` trước khi đóng gói thành
`TelemetryPacket`.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

### SystemMetricsCollector
**Đọc:** "sis-tem me-triks ka-lek-ter" · **Nhóm:** Khái niệm dự án
**Nghĩa:** Interface thu thập chỉ số; triển khai thật là `OshiSystemMetricsCollector` (dùng
OSHI). Việc tách interface cho phép testinject kết quả giả, không phụ thuộc phần cứng máy
chạy test.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

## T

### TCP
**Đọc:** "ti-si-pi" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Giao thức kết nối có hướng, tin cậy, dùng cho kênh điều khiển: agent mở server
cổng 5000 (`DEFAULT_AGENT_TCP_PORT`), client kết nối tới để `PING`, đọc thông tin agent
và đẩy `CONFIG_UPDATE`; timeout `tcp.timeout.ms=5000`, khung tối đa 1 MB.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md

### TcpClientSessionHandler
**Đọc:** "ti-si pai-lan se-sen han-dler" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Xử lý phía agent cho mỗi kết nối TCP đến: đọc khung, giải mã JSON, trả lời
`PONG` / `AGENT_INFO_RESPONSE` / `CONFIG_ACK`, với `setSoTimeout(SESSION_READ_TIMEOUT_MS=120000)`;
mỗi phiên chạy trên pool `newFixedThreadPool(MAX_SESSIONS = 8)`.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

### TcpControlServer
**Đọc:** "ti-si kon-trol ser-ver" · **Nhóm:** Giao thức & mạng
**Nghĩa:** `ServerSocket` lắng nghe cổng TCP của agent (mặc định 5000, demo local
5001–5004 cho 4 agent cùng máy), chấp nhận kết nối và giao mỗi kết nối cho
`TcpClientSessionHandler` qua pool 8 luồng.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

### TelemetryPacket
**Đọc:** "te-le-me-tri pa-ket" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Model gói telemetry UDP gồm 13 trường canonical (protocolVersion, type, agentId,
hostname, sessionId, timestamp, sequence + cpu, memory, connectionCount, uptime, netIn,
netOut) và `hmac`; `PacketFactory` của simulator cũng dựng đúng cấu trúc này.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### TelemetryScheduler
**Đọc:** "te-le-me-tri sce-du-le-r" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Lập lịch phía agent gửi telemetry theo `metric.interval.ms` (mặc định 1000,
tối thiểu `MIN_METRIC_INTERVAL_MS = 200`); nút chạy `synchronized start()`, giữ
`volatile long currentIntervalMs` để đổi interval mà không cần dừng tiến trình, và nuông
lỗi để scheduled task không bị hủy ngầm.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

### TelemetryService
**Đọc:** "te-le-me-tri ser-vis" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Đường nhận phía client: `handleDatagram(...)` giải mã → `PacketValidator` kiểm
tra → lưu `metric_raw` → `AlertService.evaluate(...)` mở/đóng cảnh báo; thống kê
`getAcceptedCount()` / `getRejectedCount()`; gói sai HMAC hoặc replay gọi
`alertService.recordSecurityEvent(...)` .
**Xem thêm:** ../04-tai-lieu-class/04-monitor-app-phan-2.md

### Threshold (ngưỡng cảnh báo)
**Đọc:** "tres-hold" · **Nhóm:** Giám sát & cảnh báo
**Nghĩa:** Mức mở cảnh báo, khai trong agent (`alert.cpu.threshold=85.0`,
`alert.memory.threshold=85.0`, `alert.network.in.threshold.mbps=50.0`,
`alert.network.out.threshold.mbps=50.0`) và đổi được từ xa qua `CONFIG_UPDATE`; giá trị
nhân đôi làm ngưỡng đóng (có hysteresis).
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md

### TimeSeriesChartPanel
**Đọc:** "tai-m si-riez cart pa-nel" · **Nhóm:** UI & thông báo
**Nghĩa:** Panel biểu đồ JFreeChart theo chuỗi thời gian, số điểm mỗi series giới hạn bằng
`maximumItemCount` (cấu hình `chart.window.samples=120`, cho phép 60–300) để điểm cũ trôi
ra ngoài thay vì biểu đồ phình vô hạn; `addOrUpdate` tránh trùng thời điểm.
**Xem thêm:** ../04-tai-lieu-class/05-monitor-app-phan-3.md

## U

### UDP
**Đọc:** "iu-di-pi" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Giao thức không hướng, không tin cậy, nhẹ — chọn cho kênh telemetry/heartbeat:
mỗi gói tự chứa đủ thông tin, mất một gói thì gói sau thay thế. Client nghe cổng 6000
(`DEFAULT_MONITOR_UDP_PORT`), packet tối đa 4096 byte.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md

### UdpTelemetryReceiver
**Đọc:** "iu-di-pi te-le-me-tri ri-si-ve-r" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Luồng nhận UDP của client: đệm `byte[MAX_UDP_PACKET_SIZE = 4096]`, đưa vào
hàng đợi `ArrayBlockingQueue(QUEUE_CAPACITY = 2000)` rồi 4 worker xử lý
(`WORKER_COUNT = 4`); hàng đợi đầy áp dụng `CallerRunsPolicy` để không mất gói.
**Xem thêm:** ../04-tai-lieu-class/04-monitor-app-phan-2.md

### UdpTelemetrySender
**Đọc:** "iu-di-pi te-le-me-tri sen-der" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Bên gửi UDP: kiểm tra payload không vượt `MAX_UDP_PACKET_SIZE` rồi
`socket.send(new DatagramPacket(...))` tới `monitor.host:monitor.udp.port`; dùng chung
bộ đếm sequence với `HeartbeatScheduler`.
**Xem thêm:** ../04-tai-lieu-class/02-agent-app.md

### UTF-8
**Đọc:** "iu-ti-fu-eit" · **Nhóm:** Giao thức & mạng
**Nghĩa:** Bảng mã byte cho mọi message: `ProtocolConstants.CHARSET =
StandardCharsets.UTF_8`; canonical string cũng được lấy byte UTF-8 trước khi ký
(`data.getBytes(StandardCharsets.UTF_8)` trong `HmacUtil`), nên cả hai phía phải cùng mã
hóa mới khớp chữ ký.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/10-giao-thuc-goc.md

## V

### ValidationUtil
**Đọc:** "va-li-dei-sen yu-til" · **Nhóm:** Bảo mật
**Nghĩa:** Các kiểm tra input dùng chung: `isBlank`, `isFinite`, `isPercentage`,
`clampPercentage`, `isNonNegative`, `inRange`, `looksLikeEmail`; trả về `ValidationResult`
thay vì ném exception để tầng gọi tự quyết định cách báo lỗi.
**Xem thêm:** ../04-tai-lieu-class/01-monitoring-common.md

### volatile
**Đọc:** "vo-la-til" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** Biến được đọc/ghi từ nhiều luồng mà luôn thấy giá trị mới nhất (nhưng không
tự atomic): `AgentConfig.metricIntervalMs`, `cpuThreshold`, `AgentRuntime.running`,
`TcpControlServer.serverSocket`, `TelemetryScheduler.task` đều là `volatile`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/08-mo-hinh-da-luong.md

## W

### WAL (Write-Ahead Logging)
**Đọc:** "uol" · **Nhóm:** Lưu trữ
**Nghĩa:** Chế độ ghi trước của SQLite, bật trong `DatabaseManager` cùng
`busy_timeout=5000`, `foreign_keys=ON`, `synchronous=NORMAL`: reader không chặn writer,
giảm lỗi `database is locked` khi nhiều luồng cùng truy cập một file `.db`.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md

### Worker (luồng nhận UDP)
**Đọc:** "uer-ker" · **Nhóm:** Kiến trúc & đa luồng
**Nghĩa:** 4 luồng nền (`WORKER_COUNT = 4`) trong `UdpTelemetryReceiver` mỗi luồng có
`ThreadPoolExecutor(1, 1, ...)` riêng, kéo gói từ hàng đợi 2000 phần tử và chạy
`TelemetryService.handleDatagram` — cách ly lỗi từng gói, đầy hàng đợi thì caller tự chạy.
**Xem thêm:** ../02-kien-truc-va-nguyen-ly/08-mo-hinh-da-luong.md

