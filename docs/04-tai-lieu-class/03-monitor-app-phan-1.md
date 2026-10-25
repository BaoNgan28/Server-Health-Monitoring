# Tài liệu lớp — monitor-app (Phần 1: Cấu hình, lõi, model, mạng, thông báo)

`monitor-app` là ứng dụng Java Swing desktop (ứng dụng chạy trên cửa sổ hệ điều hành bằng Swing — thư viện giao diện đồ hoạ của Java) nhận telemetry (dữ liệu đo từ xa) UDP từ các Agent, kiểm tra HMAC (Hash-based Message Authentication Code — mã xác thực thông điệp dựa trên hàm băm, dùng để xác nhận gói tin không bị giả mạo), lưu vào SQLite, hiển thị dashboard (bảng điều khiển tổng quan) và cảnh báo qua GUI / database / email.

Tài liệu về các lớp của `monitor-app` được chia làm 3 phần:

- **Phần 1 (tài liệu này)**: cấu hình (config), lõi (core), model (mô hình dữ liệu), mạng (network), thông báo (notification).
- **Phần 2**: repository (lớp truy cập dữ liệu SQLite) và service (lớp nghiệp vụ).
- **Phần 3**: giao diện người dùng (UI — các panel Swing).

Nguồn tham chiếu dùng xuyên suốt tài liệu: `config/monitor.properties` (hồ sơ production), `config/monitor-demo.properties` (hồ sơ demo), `monitor-app/src/main/resources/db/schema.sql`, `monitor-app/pom.xml`. Các chú thích Javadoc trong code được giữ nguyên ý nghĩa và diễn đạt lại bằng tiếng Việt.

## 1. Bảng tổng quan class (phần 1)

| # | Class | Package | Vai trò | File nguồn |
|---|-------|---------|---------|------------|
| 1 | `MonitoringMain` | `vn.edu.hutech.group10.monitoring.monitor` | Entry point (điểm vào) của ứng dụng: đọc tham số dòng lệnh, nạp cấu hình, khởi động lõi, mở cửa sổ GUI | `monitor-app/src/main/java/vn/edu/hutech/group10/monitoring/monitor/MonitoringMain.java` |
| 2 | `MonitorConfig` | `...monitor.config` | Object cấu hình bất biến (immutable — không đổi sau khi tạo) của monitor, mọi thời lượng đã quy về mili giây | `.../monitor/config/MonitorConfig.java` |
| 3 | `MonitorConfigLoader` | `...monitor.config` | Đọc file `monitor.properties` thành `MonitorConfig`, có kiểm tra khoảng giá trị và giá trị mặc định | `.../monitor/config/MonitorConfigLoader.java` |
| 4 | `EmailConfig` | `...monitor.config` | Cấu hình SMTP cho email cảnh báo, kèm cờ `configured` và lý do tắt email | `.../monitor/config/EmailConfig.java` |
| 5 | `EmailConfigLoader` | `...monitor.config` | Dựng `EmailConfig` từ biến môi trường (environment variable) hoặc file `config/email-secrets.properties` | `.../monitor/config/EmailConfigLoader.java` |
| 6 | `MonitorApplication` | `...monitor.core` | Lớp lõi: ghép nối toàn bộ ứng dụng, quản lý vòng đời khởi động/dừng, ép phân tầng (layering) giữa GUI và hạ tầng | `.../monitor/core/MonitorApplication.java` |
| 7 | `AgentRecord` | `...monitor.model` | Bản ghi một Agent đã đăng ký, tương ứng dòng trong bảng `agents` | `.../monitor/model/AgentRecord.java` |
| 8 | `AgentRuntimeState` | `...monitor.model` | Trạng thái "sống" của một Agent: record + số liệu từ gói tin được chấp nhận gần nhất | `.../monitor/model/AgentRuntimeState.java` |
| 9 | `AgentSnapshot` | `...monitor.model` | Ảnh chụp bất biến của một Agent, thứ duy nhất GUI được cầm | `.../monitor/model/AgentSnapshot.java` |
| 10 | `MetricSample` | `...monitor.model` | Một mẫu telemetry đã được chấp nhận, tương ứng một dòng bảng `metric_raw` | `.../monitor/model/MetricSample.java` |
| 11 | `MetricAggregate` | `...monitor.model` | Một "bucket" (khung giờ gộp) thống kê, tương ứng một dòng bảng `metric_5m` | `.../monitor/model/MetricAggregate.java` |
| 12 | `HistoryPoint` | `...monitor.model` | Một điểm trên biểu đồ lịch sử, hợp nhất mẫu thô và bucket gộp về cùng một hình dạng | `.../monitor/model/HistoryPoint.java` |
| 13 | `UdpTelemetryReceiver` | `...monitor.network` | Lắng nghe cổng UDP, nhận gói telemetry và chuyển cho worker xử lý theo đúng thứ tự từng Agent | `.../monitor/network/UdpTelemetryReceiver.java` |
| 14 | `AgentTcpConnection` | `...monitor.network` | Một kết nối TCP điều khiển (control channel) tới một Agent: PING, yêu cầu thông tin, đẩy cấu hình | `.../monitor/network/AgentTcpConnection.java` |
| 15 | `AgentTcpConnectionManager` | `...monitor.network` | Giữ tối đa một kết nối TCP cho mỗi Agent, cách ly lỗi giữa các Agent | `.../monitor/network/AgentTcpConnectionManager.java` |
| 16 | `Notifier` | `...monitor.notification` | Interface (giao diện) kênh gửi cảnh báo từ xa | `.../monitor/notification/Notifier.java` |
| 17 | `CompositeNotifier` | `...monitor.notification` | Gửi một cảnh báo tới nhiều kênh, cách ly kênh hỏng | `.../monitor/notification/CompositeNotifier.java` |
| 18 | `NoOpNotifier` | `...monitor.notification` | Kênh "không làm gì" (no-operation) thay cho email khi chưa có thông tin đăng nhập | `.../monitor/notification/NoOpNotifier.java` |
| 19 | `EmailNotifier` | `...monitor.notification` | Gửi email cảnh báo qua SMTP trên một luồng nền với hàng đợi có giới hạn | `.../monitor/notification/EmailNotifier.java` |
| 20 | `NotificationResult` | `...monitor.notification` | Kết quả một lần gửi (thành công/thất bại + thông điệp cho hộp thoại) | `.../monitor/notification/NotificationResult.java` |
| 21 | `EmailMessage` | `...monitor.notification` | Email văn bản thuần đã sẵn sàng để chuyển cho tầng vận chuyển | `.../monitor/notification/EmailMessage.java` |
| 22 | `EmailMessageBuilder` | `...monitor.notification` | Dựng tiêu đề và nội dung mọi email cảnh báo/email kiểm tra, thuần tính toán, không mạng | `.../monitor/notification/EmailMessageBuilder.java` |
| 23 | `MailTransport` | `...monitor.notification` | Interface phần việc thật sự nói chuyện với máy chủ SMTP | `.../monitor/notification/MailTransport.java` |
| 24 | `SmtpMailTransport` | `...monitor.notification` | Triển khai `MailTransport` thật bằng Jakarta Mail, STARTTLS, có timeout | `.../monitor/notification/SmtpMailTransport.java` |

Ghi chú về đường dẫn: các file nằm dưới `monitor-app/src/main/java/vn/edu/hutech/group10/monitoring/monitor/`; bảng trên viết tắt phần đầu của đường dẫn, chỉ giữ `.../monitor/...`.

## 2. Chi tiết từng class (nhóm theo package)

### 2.1 Entry point (package `vn.edu.hutech.group10.monitoring.monitor`)

#### MonitoringMain

- **Định nghĩa**: class `public final` có hàm tạo private (không thể kế thừa hay khởi tạo), chứa `public static void main(String[] args)`. Hằng khai báo: `MONITOR_VERSION = "1.0.0"`, `CONFIG_OPTION = "--config"`, `DEFAULT_CONFIG = "config/monitor.properties"`.
- **Vai trò**: điểm vào của ứng dụng. Thứ tự trong `main()`:
  1. `resolveConfigPath(args)` — xác định file cấu hình;
  2. `LoggingSetup.configure("monitor")` (lớp helper của module `monitoring-common`) — thiết lập ghi log;
  3. `MonitorConfigLoader.load(configPath)` — nạp cấu hình; lỗi `IllegalArgumentException` → `fail(...)` và thoát;
  4. `printBanner(config, configPath)` — in thông tin ra console;
  5. `new MonitorApplication(config).start()` — khởi động lõi; `IOException`/`RuntimeException` → `fail(...)` và thoát;
  6. đăng ký shutdown hook (hook chạy khi JVM thoát) tên `monitor-shutdown-hook` gọi `application::close`;
  7. `SwingUtilities.invokeLater(...)` — trên EDT (Event Dispatch Thread — luồng duy nhất của Swing xử lý sự kiện giao diện): `applyLookAndFeel()` rồi `new MainFrame(application).setVisible(true)`.
- **Thành phần chính**:
  - `resolveConfigPath(String[] args)` — `static`, phạm vi package: chấp nhận 3 kiểu tham số: `--config=path`, `--config path` (đối số rời), và một path trần kết thúc bằng `.properties`; không có thì mặc định `AppPaths.home().resolve("config/monitor.properties")`. Helper `stripQuotes` bỏ dấu nháy kép bọc ngoài giá trị.
  - `applyLookAndFeel()` — thử đặt look and feel (giao diện) của hệ thống qua `UIManager.getSystemLookAndFeelClassName()`; mọi ngoại lệ đều bị bắt và chỉ log ở mức `FINE`, giữ giao diện mặc định.
  - `printBanner(...)` — in dấu phân cách, tên phiên bản, `Profile`, đường dẫn tuyệt đối của file cấu hình, `UDP Listen Port`, `Heartbeat Timeout`, `Database`, `Log Directory`.
  - `fail(String headline, Exception e)` — ghi log mức `SEVERE`, in ra `System.err`, hiện hộp thoại `JOptionPane` qua `SwingUtilities.invokeAndWait` (môi trường không có giao diện được bắt riêng, chỉ in console), rồi `System.exit(1)`.
- **Được gọi ở đâu**: JVM khi chạy `java -jar monitor-app-1.0.0-all.jar` (theo Javadoc trong file); `monitor-app/pom.xml` dòng 16 khai báo `<main.class>vn.edu.hutech.group10.monitoring.monitor.MonitoringMain</main.class>` và được `maven-jar-plugin` + `maven-shade-plugin` dùng làm Main-Class của jar. Không lớp Java nào khác gọi `main()`; `resolveConfigPath` chỉ được dùng ngay trong class này — chưa thấy test gọi tới.
- **Ghi chú**: Javadoc nêu rõ mọi thứ được khởi động *trước khi* cửa sổ hiện ra, để cổng bị chiếm hay database không đọc được báo qua hộp thoại thay vì một màn hình trống.

### 2.2 Nhóm config (package `...monitor.config`)

#### MonitorConfig

- **Định nghĩa**: class công khai, 12 trường đều `final`, một hàm tạo công khai nhận đủ 12 tham số, toàn bộ là getter (không có setter) + `toString()`.
- **Vai trò**: giữ cài đặt của ứng dụng monitor đã được chuẩn hoá về mili giây. Javadoc nêu rõ production và demo chỉ khác *con số* nằm trong object này, không khác mã sử dụng: hồ sơ demo với bucket 10 giây chạy đúng đường gộp dữ liệu (aggregation) như production với bucket 5 phút.
- **Thành phần chính** (trường → getter):
  - `udpPort` → `getUdpPort()` — cổng UDP lắng nghe telemetry;
  - `heartbeatTimeoutMs` → `getHeartbeatTimeoutMs()` — hết chừng này mà không nhận được gì thì Agent bị coi là OFFLINE;
  - `heartbeatCheckIntervalMs` → `getHeartbeatCheckIntervalMs()` — chu kỳ quét liveness (tính "còn sống");
  - `timestampToleranceMs` → `getTimestampToleranceMs()` — độ lệch thời gian cho phép của gói tin so với đồng hồ máy này;
  - `databasePath` → `getDatabasePath()` — đường dẫn file SQLite;
  - `rawRetentionMs` → `getRawRetentionMs()` — thời gian giữ mẫu thô trước khi gộp vào bucket;
  - `aggregationBucketMs` → `getAggregationBucketMs()` — chiều rộng một bucket gộp;
  - `aggregateRetentionMs` → `getAggregateRetentionMs()` — thời gian giữ các bucket đã gộp;
  - `aggregationIntervalMs` → `getAggregationIntervalMs()` — chu kỳ chạy job gộp;
  - `chartWindowSamples` → `getChartWindowSamples()` — số điểm tối đa mỗi chuỗi dữ liệu trên biểu đồ;
  - `tcpTimeoutMs` → `getTcpTimeoutMs()` — timeout kết nối/đọc của kênh TCP điều khiển;
  - `profileName` → `getProfileName()` — `production` hoặc `demo`, hiển thị trên tiêu đề cửa sổ.
- **Được gọi ở đâu**:
  - `MonitorConfigLoader.fromProperties(...)` là nơi duy nhất `new MonitorConfig(...)` trong code chính (dòng 111); test dựng trực tiếp trong `TestSupport` (dòng 57, 64);
  - `MonitorApplication.start()`: `getDatabasePath()`, `getHeartbeatTimeoutMs()`, `getHeartbeatCheckIntervalMs()`, `getTimestampToleranceMs()`, `getUdpPort()`, `getTcpTimeoutMs()`, `getProfileName()`;
  - `AgentTcpConnectionManager` (nhận config qua ctor, dùng `getTcpTimeoutMs()`); `AggregationService` (`getAggregationIntervalMs()`, `getRawRetentionMs()`, `getAggregationBucketMs()`, `getAggregateRetentionMs()`); `HistoryService` (`getRawRetentionMs()`);
  - UI: `MainFrame` (`getProfileName()` ở tiêu đề, `getUdpPort()` ở thanh trạng thái), `SettingsPanel` (hiển thị Profile, UDP Listen Port, Heartbeat Timeout, Timestamp Tolerance, Raw Retention, Aggregation Bucket, Aggregate Retention, Database), `ServerDetailPanel` (`getChartWindowSamples()`);
  - Test: `MonitorConfigLoaderTest`, `TcpIntegrationTest` (tạo config để dựng `AgentTcpConnectionManager`).
- **Ghi chú**: các getter có chú thích Javadoc giải thích ý nghĩa nghiệp vụ (raw retention, bucket width...). `toString()` in profile, udpPort, heartbeatTimeoutMs, database, rawRetentionMs, bucketMs, aggregateRetentionMs — không in bí mật gì.

#### MonitorConfigLoader

- **Định nghĩa**: class `final` với hàm tạo private (tiện ích tĩnh — static utility), toàn bộ phương thức `static`.
- **Vai trò**: đọc `monitor.properties` (hoặc `monitor-demo.properties`) thành `MonitorConfig`. Thời lượng có thể khai báo theo giây, phút hoặc ngày; loader đều quy về mili giây và **giá trị theo giây thắng** khi cả hai dạng cùng có — chính cơ chế này cho phép hồ sơ demo rút thời gian giữ dữ liệu thô từ 1 giờ xuống 1 phút mà không sửa một dòng mã gộp dữ liệu nào.
- **Thành phần chính**:
  - Hằng khoá (key)properties: `monitor.udp.port`, `heartbeat.timeout.ms`, `heartbeat.check.interval.ms`, `packet.timestamp.tolerance.ms`, `database.path`, `raw.retention.seconds`, `raw.retention.minutes`, `aggregation.bucket.seconds`, `aggregation.bucket.minutes`, `aggregate.retention.seconds`, `aggregate.retention.days`, `aggregation.interval.seconds`, `chart.window.samples`, `tcp.timeout.ms`, `profile.name`.
  - Hằng `MIN_CHART_WINDOW = 60`, `MAX_CHART_WINDOW = 300`.
  - `load(Path path)`: file không tồn tại/không đọc được → **không phải lỗi**, log cảnh báo và dùng mặc định để ứng dụng luôn khởi động được; đọc được → log `Loaded monitor configuration from <path>`.
  - `fromProperties(Properties)`: xây dựng config với kiểm tra khoảng:
    - `udpPort`: mặc định `6000` (`ProtocolConstants.DEFAULT_MONITOR_UDP_PORT`), khoảng cho phép (range) 1–65535;
    - `heartbeatTimeout`: mặc định `15000` ms, range 1000–3600000 (`ProtocolConstants.MAX_INTERVAL_MS`);
    - `heartbeatCheck`: mặc định `2000` ms, range 200–3600000;
    - `tolerance`: mặc định `30000` ms, range 1000–3600000;
    - `databasePath`: mặc định `"data/monitoring.db"`, đường dẫn tương đối được nối vào `AppPaths.home()` (thư mục gốc dự án, có thể override bằng hệ thống property `monitoring.home`) rồi `normalize()`;
    - `rawRetention`: mặc định `3600000` ms (1 giờ);
    - `bucket`: mặc định `300000` ms (5 phút);
    - `aggregateRetention`: mặc định `2592000000` ms (30 ngày);
    - `aggregationInterval`: mặc định suy ra từ bucket theo công thức `max(10000 ms, min(bucket, 300000 ms))` rồi chia 1000 để đọc ra giá trị giây; đọc khoá `aggregation.interval.seconds` với khoảng 5–3600 giây, rồi nhân 1000 ra mili giây;
    - `chartWindow`: mặc định `120`, range 60–300;
    - `tcpTimeout`: mặc định `5000` ms (`ProtocolConstants.DEFAULT_TCP_TIMEOUT_MS`), range 500–60000;
    - `profileName`: mặc định `"production"`.
  - Hàm private `duration(properties, fineKey, fineUnitMs, coarseKey, coarseUnitMs, fallbackMs)` — đọc khoá chi tiết (giây) trước, khoá thô (phút/ngày) sau, không có thì dùng mặc định; `positive(...)`, `intValue(...)`, `longValue(...)`, `parseLong(...)`, `text(...)` — các hàm kiểm tra đều ném `IllegalArgumentException` với thông điệp nêu tên khoá và giá trị.
- **Được gọi ở đâu**: `MonitoringMain.main()` (dòng 49, `MonitorConfigLoader.load(configPath)`); test `MonitorConfigLoaderTest` gồm các ca: `appliesProductionDefaults`, `readsProductionUnits`, `secondsOverrideCoarseUnits`, `rejectsOutOfRangeValues`, `resolvesRelativeDatabasePath`, `missingFileUsesDefaults`, `readsFileFromDisk`.
- **Ghi chú**: giá trị cấu hình thật trong `config/monitor.properties`: `profile.name=production`, `monitor.udp.port=6000`, `tcp.timeout.ms=5000`, `heartbeat.timeout.ms=15000`, `heartbeat.check.interval.ms=2000`, `packet.timestamp.tolerance.ms=30000`, `database.path=data/monitoring.db`, `raw.retention.minutes=60`, `aggregation.bucket.minutes=5`, `aggregate.retention.days=30`, `aggregation.interval.seconds=300`, `chart.window.samples=120`. Bản demo `monitor-demo.properties` khác đúng: `profile.name=demo`, `database.path=data/monitoring-demo.db`, `raw.retention.seconds=60`, `aggregation.bucket.seconds=10`, `aggregate.retention.seconds=300`, `aggregation.interval.seconds=15`.

#### EmailConfig

- **Định nghĩa**: class có hàm tạo **private**, chỉ được tạo qua hai factory tĩnh: `configured(...)` (đủ thông tin, `configured = true`, `disabledReason = null`) và `disabled(...)` (thiếu thông tin, `configured = false`, `appPassword = null`, giữ `disabledReason`).
- **Vai trò**: giữ cài đặt SMTP (Simple Mail Transfer Protocol — giao thức chuẩn gửi thư điện tử) cho email cảnh báo. Javadoc nêu: phần còn lại của ứng dụng chỉ hỏi `isConfigured()`; khi là `false`, kênh notifier bị tắt và không bao giờ thử đăng nhập — nhờ vậy dự án khi checkout với thông tin điền sẵn là placeholder (giá trị mẫu chừa trống) vẫn chạy bình thường. Mật khẩu chỉ nằm trong object này, không bao giờ được ghi log, không hiện trên GUI, không rời khỏi tiến trình trừ trong lúc bắt tay SMTP.
- **Thành phần chính**:
  - Hằng `SENDER_NAME = "Group 10 Server Health Monitoring"` — tên hiển thị người gửi (getter `getSenderName()` trả giá trị này);
  - trường: `host`, `port`, `startTls`, `auth`, `username`, `appPassword`, `recipient`, `configured`, `disabledReason`;
  - getter tương ứng; `getAppPassword()` có chú thích "Never log or display this value";
  - `describeServer()` → ví dụ `smtp.gmail.com:587 STARTTLS` (hoặc ` plain` khi tắt STARTTLS);
  - `toString()` — in server, username, recipient, configured; **không** in mật khẩu.
- **Được gọi ở đâu**: `EmailConfigLoader.load(...)` là nơi tạo; `MonitorApplication` giữ trường `emailConfig` và trả qua `getEmailConfig()`; `EmailNotifier` và `SmtpMailTransport` (trường `config`); `EmailMessageBuilder.forAlert/forTest/buildTestBody`; UI: `SettingsPanel.refresh()` và `onSendTestEmail()` đọc trạng thái hiển thị, `MainFrame.updateStatusBar()` hiện `email: CONFIGURED / NOT CONFIGURED`; test: `EmailConfigLoaderTest`, `EmailNotifierTest` (factory `configured("smtp.gmail.com", 587, true, true, ...)`).
- **Ghi chú**: trạng thái "chưa cấu hình" là trạng thái bình thường, không phải lỗi khởi động.

#### EmailConfigLoader

- **Định nghĩa**: class `final`, hàm tạo private, các phương thức `static`.
- **Vai trò**: dựng `EmailConfig` theo thứ tự ưu tiên: (1) biến môi trường `SMTP_USERNAME`, `SMTP_APP_PASSWORD`, `ALERT_RECIPIENT_EMAIL`; (2) file ngoài `config/email-secrets.properties`. File được đọc từ thư mục làm việc chứ không từ trong jar, để có thể điền thông tin rồi khởi động lại mà không cần build lại. Khi thiếu thông tin, còn là placeholder, hoặc không giống địa chỉ email thì kết quả là cấu hình `disabled` kèm lý do.
- **Thành phần chính**:
  - Hằng: `DEFAULT_FILE_NAME = "email-secrets.properties"`; `ENV_USERNAME/ENV_APP_PASSWORD/ENV_RECIPIENT`; khoá property `smtp.username`, `smtp.appPassword`, `smtp.recipient`, `smtp.host`, `smtp.port`, `smtp.starttls`, `smtp.auth`; `DEFAULT_HOST = "smtp.gmail.com"`, `DEFAULT_PORT = 587`; placeholder `PLACEHOLDER_PASSWORD = "PASTE_GMAIL_APP_PASSWORD_HERE"`, `PLACEHOLDER_RECIPIENT = "PASTE_RECIPIENT_EMAIL_HERE"`; `DISABLED_LOG_MESSAGE = "Email notifications disabled: credentials not configured"`.
  - `load()` — nạp mặc định từ `AppPaths.configFile("email-secrets.properties")` với `System::getenv`.
  - `load(Path file, Function<String,String> env)` — bản nhận tham số (dùng trong test). Đọc host/port/starttls/auth từ file (port sai hoặc ngoài 1–65535 → log cảnh báo và dùng 587); username/password/recipient lấy env trước, file sau (`firstUsable`).
  - `findProblem(username, password, recipient)` — trả về lý do tắt email: `smtp.username is empty`, `smtp.appPassword is empty or still the placeholder`, `smtp.recipient is empty or still the placeholder`, `smtp.username is not a valid e-mail address`, `smtp.recipient is not a valid e-mail address`, hoặc `null` khi tất cả đều hợp lệ.
  - `isUsable(String)` — public: rỗng → false; bằng placeholder hoặc bắt đầu bằng `PASTE_`/`YOUR_` (không phân biệt hoa thường) → false.
  - Khi có vấn đề: log `DISABLED_LOG_MESSAGE + " (problem)"` rồi trả `EmailConfig.disabled(...)`; khi ổn: log `Email notifications enabled for recipient ... through host:port` rồi trả `EmailConfig.configured(...)`.
- **Được gọi ở đâu**: `MonitorApplication.start()` (dòng 98) và `MonitorApplication.reloadEmailConfiguration()` (dòng 162); `MonitorApplication.buildNotifier` đọc hằng `DISABLED_LOG_MESSAGE`; `SettingsPanel` tham chiếu `EmailConfigLoader.DEFAULT_FILE_NAME` để hiện đường dẫn file bí mật cho người vận hành; test `EmailConfigLoaderTest` (gọi `load(file, env::get)` với 11 ca, gồm `environmentWinsOverFile`, `placeholdersDisableEmail`, `detectsTemplateValues`, `badPortFallsBack`, `descriptionHidesThePassword`).
- **Ghi chú**: khi đọc file lỗi, code cố tình **không** đưa nội dung file vào thông điệp log (xem `readFile`).

### 2.3 Nhóm core (package `...monitor.core`)

#### MonitorApplication

- **Định nghĩa**: class công khai `implements AutoCloseable` (tự động đóng được, dùng trong try-with-resources). Hằng `SCHEDULER_THREADS = 2` — "một luồng cho lượt quét heartbeat (nhịp tim — dấu hiệu còn sống), một luồng cho job gộp dữ liệu".
- **Vai trò**: ghép nối (wiring) toàn bộ ứng dụng monitor và sở hữu vòng đời của nó. Javadoc nêu hai điểm: (1) phân tầng được ép ngay khi dựng — GUI chỉ được trao cho service, chỉ service mới giữ repository (lớp truy cập DB), socket và notifier; không phần nào của giao diện với tới `DatagramSocket`, câu lệnh JDBC hay phiên SMTP; (2) thứ tự khởi động là database → registry → trạng thái cảnh báo → notifier → service → socket → timer, thứ tự dừng là chiều ngược lại và chạy hai lần vẫn an toàn.
- **Thành phần chính**:
  - Trường: `config`, `codec` (`JsonMessageCodec` của `monitoring-common`), `eventBus` (`MonitorEventBus`), `database` (`DatabaseManager`), `agentRepository`/`metricRepository`/`alertRepository`, `emailConfig`, `notifier`, `registry`, `alertService`, `heartbeatMonitor`, `telemetryService`, `aggregationService`, `historyService`, `agentManagementService`, `connectionManager`, `udpReceiver`, `scheduler`, `volatile boolean running`.
  - `start() throws IOException` — `synchronized`, đã chạy thì trả về ngay. Thứ tự cụ thể trong code:
    1. `new DatabaseManager(config.getDatabasePath())` + `initialize()`;
    2. `new AgentRepository(database)`, `new MetricRepository(database)`, `new AlertRepository(database)`;
    3. `emailConfig = EmailConfigLoader.load()`; `notifier = buildNotifier(emailConfig)`;
    4. `registry = new AgentRegistry(agentRepository, eventBus)` + `load()`;
    5. `alertService = new AlertService(alertRepository, notifier, eventBus)` + `load()`;
    6. `heartbeatMonitor = new HeartbeatMonitor(registry, alertService, getHeartbeatTimeoutMs(), getHeartbeatCheckIntervalMs())`;
    7. `PacketValidator validator = new PacketValidator(registry, new ReplayProtector(), config.getTimestampToleranceMs())` — `ReplayProtector` chống gói tin bị **replay** (phát lại), `PacketValidator` kiểm tra HMAC/chuẩn hoá/giá trị của gói;
    8. `telemetryService = new TelemetryService(codec, validator, registry, metricRepository, alertService, heartbeatMonitor, eventBus)`;
    9. `connectionManager = new AgentTcpConnectionManager(codec, config)`;
    10. `agentManagementService = new AgentManagementService(agentRepository, registry, alertService, connectionManager)`;
    11. `historyService = new HistoryService(metricRepository, config)`; `aggregationService = new AggregationService(metricRepository, config, eventBus)`;
    12. `udpReceiver = new UdpTelemetryReceiver(config.getUdpPort(), telemetryService)` + `start()`;
    13. tạo `ScheduledExecutorService` 2 luồng daemon tên `monitor-scheduler-1`, `monitor-scheduler-2`; `heartbeatMonitor.start(scheduler)`; `aggregationService.start(scheduler)`;
    14. `running = true`, log `Monitoring client started with profile '<tên>'`.
    - Bất kỳ `IOException`/`RuntimeException` nào → log `SEVERE ... rolling back`, gọi `close()`, rồi ném lại (không để socket/tệp bị treo).
  - `buildNotifier(EmailConfig)` — riêng tư: cấu hình ổn → `new CompositeNotifier(EmailNotifier.withSmtp(email))`; chưa cấu hình → log `DISABLED_LOG_MESSAGE` và `new CompositeNotifier(new NoOpNotifier(email.getDisabledReason()))`. Javadoc: thiếu thông tin đăng nhập không bao giờ làm hỏng việc khởi động.
  - `reloadEmailConfiguration()` — `synchronized`, nạp lại từ file secrets, dựng lại notifier, **dựng lại luôn `AlertService`** với notifier mới (vì `AlertService` giữ tham chiếu notifier cũ), `close()` notifier cũ, trả `EmailConfig` mới. Nhờ đó người vận hành điền file rồi bấm nút reload là email bật được mà không cần khởi động lại ứng dụng.
  - `close()` — `synchronized`, chiều ngược `start()`: `aggregationService.stop()`, `heartbeatMonitor.stop()`, `scheduler.shutdown` (chờ 2 giây rồi `shutdownNow()`), `udpReceiver.close()`, `connectionManager.close()`, `registry.persistLastSeen()` (chỉ lỗi `RuntimeException` được nuốt và log WARNING), `notifier.close()`, `database.close()`; mỗi bước đều kiểm tra `null` nên chạy lại lần hai vẫn an toàn; cuối set `running = false`.
  - Hàng loạt getter cho UI: `isRunning()`, `getConfig()`, `getEventBus()`, `getRegistry()`, `getAgentManagementService()`, `getHistoryService()`, `getAlertService()`, `getAlertRepository()`, `getAggregationService()`, `getTelemetryService()`, `getUdpReceiver()`, `getEmailConfig()`, `getNotifier()`.
- **Được gọi ở đâu**:
  - `MonitoringMain`: `new MonitorApplication(config)` → `start()` → shutdown hook `application::close` → truyền vào `new MainFrame(application)`;
  - UI nhận qua ctor (constructor injection — tiêm qua hàm tạo): `MainFrame`, `DashboardPanel`, `HistoryPanel`, `AlertPanel`, `SettingsPanel`, `ServerDetailPanel`;
  - `MainFrame.shutdown()` gọi `application.close()` trên luồng `monitor-shutdown` rồi `System.exit(0)`;
  - `SettingsPanel` gọi `getNotifier().sendTestMessage()` và `reloadEmailConfiguration()` (trên `SwingWorker`, không trên EDT).
- **Ghi chú**: không có test riêng cho lớp này trong repo (chưa xác định việc kiểm thử trực tiếp `MonitorApplication`); phần lớn hành vi của nó được phủ gián tiếp qua test của các lớp con phía dưới (repository, service, network, notification).

### 2.4 Nhóm model (package `...monitor.model`)

#### AgentRecord

- **Định nghĩa**: POJO (Plain Old Java Object — object Java thuần, có getter/setter) ánh xạ bảng `agents`; hàm tạo không tham số + getter/setter đầy đủ.
- **Vai trò**: bản ghi một Agent đã đăng ký — cách liên lạc với nó, bí mật mà các gói tin của nó được ký, và ngưỡng (threshold) để chấm số liệu.
- **Thành phần chính**:
  - Danh tính/kết nối: `id` (long, khóa chính), `agentId`, `name`, `host`, `tcpPort`;
  - `sharedSecret` — bí mật chung để ký/kiểm tra HMAC; getter có chú thích "Never shown in the agent table or in any log line" (không bao giờ hiện trong bảng Agent hay dòng log);
  - Ngưỡng: `cpuThreshold`, `memoryThreshold`, `networkInThresholdMbps`, `networkOutThresholdMbps` (đơn vị Mbps — Megabit mỗi giây);
  - Trạng thái: `status` (`AgentStatus`, mặc định `AgentStatus.UNKNOWN`), `lastSeen`, `createdAt`, `updatedAt` (epoch millis);
  - `copy()` — trả về bản sao cùng giá trị, "used so the GUI cannot mutate stored state" (để GUI không sửa được trạng thái đang lưu);
  - `toString()` — chỉ in `agentId`, `name`, `host:tcpPort`, `status`; không in `sharedSecret`.
- **Được gọi ở đâu** (trong monitor-app):
  - `AgentRepository` (`findAll`, `findByAgentId`, `insert`, `update`, hàm private `map(ResultSet)`) — đọc/ghi bảng `agents`;
  - `AgentRegistry` (`load()` duyệt `repository.findAll()`, `register`, `updateRecord`);
  - `AgentManagementService` (`addAgent`, `updateAgent`, `connect`, `pushConfiguration`, `applyLocally`, `validate`);
  - `AlertService` (`evaluate`, `raiseOffline`, `raiseRecovered`, `openAlert`, `newEvent`);
  - `AgentTcpConnectionManager.connect/ensureConnected` (đọc `getHost`, `getTcpPort`, `getAgentId`);
  - `AgentRuntimeState` và `AgentSnapshot` (giữ/copy bản ghi);
  - UI: `AgentDialog.showAddDialog/showEditDialog` (trả về `AgentRecord`), `ConfigureAgentDialog.show(Window, AgentRecord)`, `DashboardPanel` (thêm/sửa/xóa/kết nối/push cấu hình);
  - Test: `TestSupport.agent(...)`, `DatabaseManagerTest`, `AlertServiceTest`, `HeartbeatMonitorTest`, `UdpIntegrationTest`.
- **Ghi chú**: giá trị mặc định trong `schema.sql`: `cpu_threshold = 85.0`, `memory_threshold = 85.0`, `network_in_threshold = 50.0`, `network_out_threshold = 50.0`, `status = 'UNKNOWN'`, `last_seen = 0`; ràng buộc `UNIQUE (agent_id)` và `FOREIGN KEY ... ON DELETE CASCADE`.

#### AgentRuntimeState

- **Định nghĩa**: class công khai, mỗi trường thay đổi được đều `volatile` (đảm bảo mọi luồng thấy giá trị mới nhất); `acceptedPackets`/`rejectedPackets` là `LongAdder` (bộ đếm nguyên tử tối ưu cho nhiều luồng ghi cùng lúc).
- **Vai trò**: "góc nhìn sống" của một Agent: record đã lưu + bất cứ thứ gì gói tin được chấp nhận gần nhất báo về. Javadoc nêu: được luồng sự kiện Swing đọc, luồng nhận UDP và timer heartbeat viết, nên mọi trường mutable đều `volatile`; `snapshot()` trao cho GUI một bản sao bất biến thay vì chính object này.
- **Thành phần chính**:
  - `volatile AgentRecord record`; `volatile AgentStatus status` (mặc định `UNKNOWN`, ctor lấy từ record nếu record có); `volatile long lastSeen`;
  - số liệu mới nhất: `cpuUsage`, `memoryUsage`, `connectionCount`, `uptimeSeconds`, `networkInBytesPerSecond`, `networkOutBytesPerSecond`, `lastMetricTimestamp` (đều `volatile`);
  - `LongAdder acceptedPackets`, `rejectedPackets`; `volatile boolean tcpConnected`;
  - ctor `AgentRuntimeState(AgentRecord record)`;
  - `applySample(MetricSample)` — chép số của một mẫu được chấp nhận vào góc nhìn sống;
  - `countAccepted()` / `countRejected()` — tăng bộ đếm;
  - `snapshot()` — "Immutable copy for the GUI, taken without holding any lock" (bản sao bất biến, đọc không cần giữ khoá).
- **Được gọi ở đâu**: chỉ trong package service — `AgentRegistry` là chủ sở hữu duy nhất: tạo ở `load()` (dòng 53) và `register()` (dòng 97), đọc qua `find`, `states()`, và cập nhật qua `applySample`, `setStatus`, `setLastSeen`, `setTcpConnected`...; `TelemetryService` lấy state qua `registry.find(packet.getAgentId())` (dòng 126). Không lớp UI nào thao tác trực tiếp.

#### AgentSnapshot

- **Định nghĩa**: class với toàn bộ trường `final`, hàm tạo công khai 13 tham số, chỉ có getter.
- **Vai trò**: ảnh chụp bất biến về một Agent tại một thời điểm, thứ duy nhất được trao cho GUI. Javadoc: GUI không bao giờ chạm `AgentRuntimeState` trực tiếp, nên bảng đang vẽ lại không thể thấy một nửa của lần ghi do luồng UDP vừa thực hiện (tránh đọc dữ liệu nửa vời/tainted read).
- **Thành phần chính**: `record` (`AgentRecord` — đã là bản `copy()`), `status`, `lastSeen`, `cpuUsage`, `memoryUsage`, `connectionCount`, `uptimeSeconds`, `networkInBytesPerSecond`, `networkOutBytesPerSecond`, `lastMetricTimestamp`, `acceptedPackets`, `rejectedPackets`, `tcpConnected`; getter cho tất cả, delegate một số getter sang `record` (`getAgentId`, `getName`, `getHost`, `getTcpPort`); `hasMetrics()` — true khi `lastMetricTimestamp > 0` (đã nhận ít nhất một mẫu).
- **Được gọi ở đâu**:
  - Tạo: `AgentRuntimeState.snapshot()`;
  - `AgentRegistry.snapshots()` (danh sách sắp theo `getAgentId`) và `AgentRegistry.snapshot(agentId)`;
  - Sự kiện: `MonitorEventBus.fireAgentUpdated(AgentSnapshot)` → `MonitorEventListener.onAgentUpdated` → `MainFrame.UiEventListener` đẩy về EDT → `DashboardPanel.applyUpdate(snapshot)` và `ServerDetailPanel.applyUpdate(snapshot)`;
  - `AgentTableModel` (giữ `List<AgentSnapshot>`, `setRows`/`updateRow`/`getRow`); `HistoryPanel` (duyệt `registry().snapshots()`); `ServerDetailPanel.showSnapshot`.
- **Ghi chú**: test `UdpIntegrationTest` khẳng định giá trị qua snapshot, ví dụ `assertEquals(17, snapshot.getConnectionCount())`.

#### MetricSample

- **Định nghĩa**: class bất biến (chỉ `final` fields, ctor, getter), một factory tĩnh.
- **Vai trò**: một mẫu telemetry đã được chấp nhận, tương ứng một dòng bảng `metric_raw`.
- **Thành phần chính**: `agentId`, `timestamp` (epoch millis), `cpuUsage`, `memoryUsage`, `connectionCount`, `uptimeSeconds`, `networkInBytesPerSecond`, `networkOutBytesPerSecond`; factory `static MetricSample from(TelemetryPacket packet)` — "Builds a sample from a packet that has already passed validation" (dựng mẫu từ gói tin đã qua kiểm định); `toString()` in agentId, timestamp, cpu, memory.
- **Được gọi ở đâu**:
  - `TelemetryService` dòng 112: `MetricSample sample = MetricSample.from(packet);` — trung tâm của đường xử lý gói tin;
  - `MetricRepository.insertRaw`, `insertRawBatch`, `findRaw`, `bindRaw` (ghi/đọc bảng `metric_raw`);
  - `AgentRegistry.applySample` → `AgentRuntimeState.applySample` (cập nhật góc nhìn sống);
  - `AlertService.evaluate(AgentRecord, MetricSample)` — chấm ngưỡng cảnh báo;
  - `HistoryService.fromSamples(...)` — chuyển sang `HistoryPoint`;
  - Test: `TestSupport.sample(...)`, `MetricRepositoryTest`, `AggregationServiceTest`, `AlertServiceTest`.

#### MetricAggregate

- **Định nghĩa**: class bất biến, ctor 12 tham số.
- **Vai trò**: một bucket gộp, như được lưu trong bảng `metric_5m`.
- **Thành phần chính**: `agentId`; `bucketStart` (epoch millis của thời điểm bắt đầu bucket — "Epoch millis of the first moment covered by this bucket"); `sampleCount` (số mẫu trong bucket); các thống kê trung bình/đỉnh: `avgCpu`/`maxCpu`, `avgMemory`/`maxMemory`, `avgConnections`, `avgNetworkInBps`/`maxNetworkInBps`, `avgNetworkOutBps`/`maxNetworkOutBps`; getter tất cả; `toString()` in agentId, bucketStart, samples, avgCpu, maxCpu.
- **Được gọi ở đâu**: `MetricRepository.findAggregates(...)` và `mapAggregate(ResultSet)` (đọc bảng `metric_5m`); `HistoryService.fromAggregates(...)` (dòng 116) chuyển thành `HistoryPoint`; test `MetricRepositoryTest` (`aggregatesCorrectly`, `separatesBucketsAndAgents`, ...) và `AggregationServiceTest` (đọc kết quả gộp). **Trong code chính**, `AggregationService` không thao tác trực tiếp trên class này mà đi qua `MetricRepository` (đường gộp nằm ở repository, xem Phần 2).

#### HistoryPoint

- **Định nghĩa**: class bất biến, ctor 7 tham số.
- **Vai trò**: một điểm trên biểu đồ lịch sử. Javadoc: mẫu thô và bucket gộp đều được ánh xạ về cùng hình dạng này, nên panel lịch sử vẽ khoảng 1 giờ và khoảng 30 ngày bằng đúng một đoạn mã, không cần biết số lấy từ bảng nào.
- **Thành phần chính**: `timestamp`, `cpuUsage`, `memoryUsage`, `connectionCount` (kiểu `double`), `networkInBytesPerSecond`, `networkOutBytesPerSecond`, `boolean aggregated` — "True when this point is a bucket average rather than a single sample" (true nếu điểm này là trung bình của bucket thay vì một mẫu đơn lẻ); getter tất cả.
- **Được gọi ở đâu**: `HistoryService.fromSamples(...)` (dòng 108) và `fromAggregates(...)` (dòng 116) là hai nơi `new`; `HistoryService.load(...)` trả `List<HistoryPoint>` cho UI; `HistoryPanel` nhận qua `SwingWorker` (dòng 118–120) rồi `plot(List<HistoryPoint>)` (dòng 143).

### 2.5 Nhóm network (package `...monitor.network`)

#### UdpTelemetryReceiver

- **Định nghĩa**: class `implements AutoCloseable`. Hằng: `WORKER_COUNT = 4`, `QUEUE_CAPACITY = 2000`, `SOCKET_RECEIVE_BUFFER = 1024 * 1024` (1 MB, theo chú thích "Asking the kernel for a larger buffer reduces loss during a burst" — xin kernel một bộ đệm lớn hơn để bớt mất gói khi lưu lượng bùng nổ).
- **Vai trò**: lắng nghe cổng UDP của monitor, nhận gói telemetry của Agent. Javadoc nêu ba ý quan trọng:
  - *Một luồng chỉ làm việc `receive()`*, toàn bộ phần kiểm định/lưu/cảnh báo chạy trên worker — vì khi vòng lặp socket bận, bộ đệm kernel đầy lên và datagram (gói UDP) bị mất.
  - *Thứ tự*: mỗi worker là **một luồng riêng với hàng đợi riêng**, và một datagram luôn được đưa cho worker chọn theo địa chỉ+cổng của người gửi. Agent gửi từ một socket nên mọi packet của cùng một Agent tới đúng một worker và xử lý theo thứ tự đến. Pool dùng chung sẽ để packet 40 vượt packet 39, khi đó bước kiểm tra replay (chống phát lại) lại từ chối packet tốt.
  - *Back pressure (áp lực ngược)*: hàng đợi có giới hạn và chính sách từ chối là `CallerRunsPolicy` — chạy việc ngay trên luồng nhận, nhờ đó "throttle" (hạn lưu lượng) một đợt bùng nổ thay vì hàng đợi phình vô hạn. Trường hợp này có thể làm lệch thứ tự tương đối, đó cũng là lý do bước chống replay chấp nhận một cửa sổ packet trễ thay vì đòi chuỗi số thứ tự tăng liên tục.
- **Thành phần chính**:
  - Trường: `port`, `telemetryService` (`TelemetryService`), `List<ThreadPoolExecutor> workers` (4 phần tử), `receiveThread` (tên `udp-receiver`, daemon), `LongAdder receivedCount`, `volatile DatagramSocket socket`, `volatile boolean running`;
  - ctor: dựng 4 `ThreadPoolExecutor(1, 1, ...)` với `ArrayBlockingQueue<>(2000)`, thread factory đặt tên `udp-worker-0..3` (daemon), `CallerRunsPolicy`;
  - `start() throws SocketException` — `new DatagramSocket(port)`, thử `setReceiveBufferSize(1MB)` (thất bại chỉ log `FINE`), set `running = true`, `receiveThread.start()`, log `UDP telemetry receiver listening on port <cổng>`;
  - `receiveLoop()` — buffer `ProtocolConstants.MAX_UDP_PACKET_SIZE = 4096` byte; mỗi lần nhận: `receivedCount++`, chép payload ra mảng mới (`Arrays.copyOfRange`), dựng `source = "<ip>:<port>"`, giao `workerFor(source).execute(() -> process(payload, source))`; `IOException` khi còn `running` → log WARNING;
  - `workerFor(String source)` — `Math.floorMod(source.hashCode(), WORKER_COUNT)`;
  - `process(...)` — gọi `telemetryService.handleDatagram(payload, 0, payload.length, source)`, bắt `RuntimeException` và log;
  - getter: `isRunning()`, `getPort()` (trả cổng đã bind), `getReceivedCount()` ("How many datagrams reached the socket, valid or not"), `getQueueDepth()` (tổng phần tử đang chờ trong mọi hàng đợi worker);
  - `close()` — set `running = false`, **đóng socket để gỡ blocked của `receive()`**, `shutdown()` từng worker, chờ 2 giây rồi `shutdownNow()`.
- **Được gọi ở đâu**:
  - `MonitorApplication.start()` (dòng 123–124: `new UdpTelemetryReceiver(config.getUdpPort(), telemetryService)` + `start()`), `close()` (dòng 198), getter `getUdpReceiver()`;
  - `MainFrame.updateStatusBar()` dùng `getReceivedCount()` để hiện thanh trạng thái `packets: <n> received, <n> accepted, <n> rejected` (phần accepted/rejected lấy từ `TelemetryService`);
  - test `UdpIntegrationTest`: `new UdpTelemetryReceiver(0, telemetryService)` (cổng 0 = OS chọn cổng trống), các ca `acceptsSignedTelemetry`, `acceptsHeartbeat`, `rejectsForgedPacket`, `rejectsReplay`, `rejectsUnknownAgent`, `rejectsStaleTimestamp`, `survivesGarbage`, `raisesAlertFromRealTraffic`, `handlesABurst`.
- **Ghi chú**: `getQueueDepth()` **không thấy nơi gọi** trong toàn bộ mã hiện tại (chưa xác định mục đích dự kiến — có thể dành cho giám sát/giao diện).

#### AgentTcpConnection

- **Định nghĩa**: class `implements AutoCloseable`, mọi phương thức công khai đều `synchronized`.
- **Vai trò**: một kết nối TCP điều khiển từ monitor tới một Agent. Javadoc: dùng TCP vì thay đổi cấu hình không được phép bị mất im lặng như datagram — `CONFIG_ACK` (acknowledgement — xác nhận) của Agent là bằng chứng nó đã tới và được chấp nhận. Khung tin có tiền tố độ dài (length-prefixed, xem `MessageFraming`). Mỗi lời gọi là một request kèm reply đúng trên cùng socket nên instance được `synchronized`: hai thao tác GUI không thể đan xen hai cuộc hội thoại trên một kết nối. Timeout kết nối và đọc luôn được đặt, để máy đã biến mất thất bại trong vài giây thay vì treo vĩnh viễn một worker. Lớp này **không bao giờ được gọi từ EDT của Swing**.
- **Thành phần chính**:
  - Trường: `agentId`, `host`, `port`, `timeoutMs`, `codec` (`JsonMessageCodec`), `socket`, `in` (`DataInputStream`), `out` (`DataOutputStream`);
  - `connect()` — no-op nếu đã mở; `candidate.connect(new InetSocketAddress(host, port), timeoutMs)`, `setSoTimeout(timeoutMs)`, `setTcpNoDelay(true)` (bỏ tắt nghẽn Nagle); thất bại thì đóng ứng viên rồi ném lại;
  - `isConnected()` — `socket != null && socket.isConnected() && !socket.isClosed()`;
  - `ping()` — gửi `{type: PING, timestamp}` và trả về true nếu reply là `PONG`;
  - `requestInfo()` — gửi `AGENT_INFO_REQUEST`, trả `AgentInfoMessage` (ném `IOException` nếu loại phản hồi sai);
  - `sendConfigUpdate(ConfigUpdateMessage)` — set `type = CONFIG_UPDATE`, sinh `requestId = UUID.randomUUID()` nếu chưa có, gửi, bắt buộc phản hồi là `CONFIG_ACK` (sai → `IOException` nêu rõ loại thực nhận), log `CONFIG_ACK from <agent>: <status> - <message>`, trả `ConfigAckMessage`;
  - `exchange(String request)` — private: `MessageFraming.writeFrame(out, request)` rồi `MessageFraming.readFrame(in)`; null nghĩa là Agent đã đóng kết nối;
  - `endpoint()` → `host:port`; `close()` — đóng socket, set 3 trường về null, log.
- **Được gọi ở đâu**: chỉ được `new` trong `AgentTcpConnectionManager.connect(...)` (dòng 43); do đó gián tiếp qua `AgentManagementService.connect()` (PING rồi `requestInfo`) và `AgentManagementService.pushConfiguration()` (`sendConfigUpdate`); test `TcpIntegrationTest` gọi trực tiếp: `pingIsAnswered`, `agentInfoIsReturned`, `configUpdateIsAppliedAndAcknowledged`, `invalidConfigUpdateIsRefused`, `connectingToNothingFails`, `usingAClosedConnectionFails`.

#### AgentTcpConnectionManager

- **Định nghĩa**: class `implements AutoCloseable`, dùng `ConcurrentHashMap<String, AgentTcpConnection>` (băm đồng thời — an toàn khi nhiều luồng).
- **Vai trò**: giữ **tối đa một** kết nối điều khiển cho mỗi Agent và đảm bảo Agent lỗi chỉ tự làm hại mình. Javadoc: kết nối/ngắt/mọi lời gọi đều được bọc để máy không tới được sinh ra thông điệp cho người vận hành thay vì ngoại lệ thoát ra GUI; đóng manager thì đóng mọi kết nối.
- **Thành phần chính**:
  - ctor `AgentTcpConnectionManager(JsonMessageCodec codec, MonitorConfig config)`;
  - `connect(AgentRecord)` — `disconnect(...)` kết nối cũ trước, `new AgentTcpConnection(agentId, host, tcpPort, config.getTcpTimeoutMs(), codec)`, `connection.connect()`; IOException thì `connection.close()` rồi ném lại; thành công thì put vào map;
  - `get(String agentId)` → `Optional<AgentTcpConnection>` (trả `Optional.empty()` nếu không có hoặc đã ngắt);
  - `isConnected(String)`, `disconnect(String)` (remove + close);
  - `ensureConnected(AgentRecord)` — có kết nối đang mở thì dùng, không thì log `Reconnecting the control channel to <id>` và `connect(...)`;
  - `getConnectionCount()` — số kết nối đang mở;
  - `close()` — đóng từng cái (bắt `RuntimeException`, log FINE), `clear()`, log `All TCP control connections closed`.
- **Được gọi ở đâu**: `MonitorApplication.start()` (dòng 116) tạo, `close()` (dòng 202) đóng; `AgentManagementService` giữ trường và dùng `connect`, `disconnect`, `isConnected`, `ensureConnected`; test `TcpIntegrationTest` dùng `new AgentTcpConnectionManager(codec, config)` với ca `connectionManagerLifecycle`, `severalConnectionsAreServed` (kiểm `getConnectionCount()`).
- **Ghi chú**: `getConnectionCount()` trong mã chính chưa thấy nơi gọi — chỉ test dùng (chưa xác định).

### 2.6 Nhóm notification (package `...monitor.notification`)

#### Notifier

- **Định nghĩa**: `public interface Notifier extends AutoCloseable`.
- **Vai trò**: kênh gửi cảnh báo từ xa. Javadoc: triển khai **không bao giờ được chặn (block) người gọi** — `AlertService` gọi interface này từ đường nhận UDP và từ timer heartbeat.
- **Thành phần chính**: `void sendAlert(AlertEvent event)` — "Failures are handled inside and never thrown" (lỗi tự xử lý bên trong, không ném ra ngoài); `NotificationResult sendTestMessage()` — gửi thông điệp kiểm tra cố định để người vận hành chứng minh kênh hoạt động mà không cần chờ sự cố thật; trả về `NotificationResult`, theo Javadoc "không bao giờ là `null`"; `boolean isEnabled()` — false khi kênh chưa cấu hình (GUI hiện điều này); `String describe()` — một dòng mô tả kênh cho màn hình cài đặt; `void close()`.
- **Được gọi ở đâu**: `AlertService` giữ trường `private final Notifier notifier` (ctor dòng 64) và gọi `sendAlert` khi mở cảnh báo; `MonitorApplication` tạo và sở hữu (phương thức `buildNotifier`), trả qua `getNotifier()`; `SettingsPanel.onSendTestEmail()` gọi `application.getNotifier().sendTestMessage()` trên `SwingWorker`.
- **Triển khai**: `EmailNotifier`, `NoOpNotifier`, `CompositeNotifier` (code chính); `RecordingNotifier` (test — ghi lại các cảnh báo nhận được, có `setEnabled`).

#### CompositeNotifier

- **Định nghĩa**: `public class CompositeNotifier implements Notifier`.
- **Vai trò**: "fan out" (phân phối) một cảnh báo ra nhiều kênh. Javadoc: mỗi delegate được cách ly — kênh nào ném ngoại lệ thì được log, các kênh còn lại vẫn nhận cảnh báo, nên một notifier hỏng không thể làm câm các kênh khác.
- **Thành phần chính**:
  - ctor varargs `CompositeNotifier(Notifier...)` và `CompositeNotifier(List<Notifier>)`, đều bỏ qua phần tử `null`;
  - `sendAlert` — lặp từng delegate, bắt `RuntimeException`, log WARNING dạng `Notifier <TênLớp> failed to handle an alert`;
  - `sendTestMessage()` — rỗng → `NotificationResult.failure("No notification channel is configured.")`; không rỗng → gửi qua mọi delegate, "thành công khi ít nhất một kênh thành công"; gom thông điệp thất bại, nếu không kênh nào thành công thì trả `failure` với các thông điệp nối bằng `System.lineSeparator()`;
  - `isEnabled()` — true khi **bất kỳ** delegate nào bật; `describe()` — nối các `describe()` bằng `"; "`, rỗng → `"No notification channel"`; `getDelegates()` — danh sách bất biến; `close()` — đóng từng delegate, cách ly `RuntimeException`.
- **Được gọi ở đâu**: `MonitorApplication.buildNotifier(...)` — luôn luôn bọc trong `CompositeNotifier` (dòng 151 với `EmailNotifier.withSmtp(email)`, dòng 154 với `new NoOpNotifier(...)`); test `EmailNotifierTest`: `compositeIsolatesFailures`, `emptyCompositeReportsFailure`.

#### NoOpNotifier

- **Định nghĩa**: `public class NoOpNotifier implements Notifier`. ("No-op" = no operation — không làm gì.)
- **Vai trò**: thế chỗ kênh email khi chưa cấu hình thông tin đăng nhập. Javadoc: cảnh báo vẫn tới GUI và database, chỉ kênh từ xa là im; mỗi lần cảnh báo bị "nuốt" đều để lại một dòng log, nhờ đó demo cho thấy engine cảnh báo đã kích dù không gửi email nào.
- **Thành phần chính**: ctor `NoOpNotifier(String reason)` (reason null → `"credentials not configured"`); `volatile long suppressedCount`; `sendAlert` — `suppressedCount++`, log INFO `Email notification suppressed (<reason>) for <alertType> on <agentId>`; `sendTestMessage()` → `NotificationResult.failure("Email notification is not configured. " + reason)`; `isEnabled()` → false; `describe()` → `"Email disabled: " + reason`; `getSuppressedCount()` — số cảnh báo bị bỏ qua; `close()` — không giải phóng gì.
- **Được gọi ở đâu**: `MonitorApplication.buildNotifier` khi `email.isConfigured() == false`; test `EmailNotifierTest.noOpNotifierIsSilent` (kiểm `getSuppressedCount() == 2`). `getSuppressedCount()` trong code chính chưa thấy nơi gọi (chưa xác định).

#### EmailNotifier

- **Định nghĩa**: `public class EmailNotifier implements Notifier`. Hằng `QUEUE_CAPACITY = 50` — "Đủ để hấp thụ một đợt bùng nổ cảnh báo, nhỏ đủ để giới hạn bộ nhớ".
- **Vai trò**: gửi email cảnh báo qua SMTP. Javadoc: việc gửi chạy trên **một luồng worker** sau hàng đợi có giới hạn, nên email không bao giờ chặn luồng nhận UDP, EDT của Swing hay timer heartbeat; và mail server ngừng trả lời không làm hàng đợi phình vô hạn — đầy thì những thông điệp sau bị loại với một cảnh báo thay vì ăn hết bộ nhớ. Lỗi SMTP được log đúng lý do của server, mật khẩu không bao giờ tới dòng log.
- **Thành phần chính**:
  - Trường: `config` (`EmailConfig`), `transport` (`MailTransport`), `executor` (`ThreadPoolExecutor(1, 1, ...)` với `ArrayBlockingQueue<>(50)`, thread tên `email-sender`, daemon); `LongAdder sentCount/failedCount/droppedCount`; `volatile String lastError`;
  - ctor hai tham số — ném `IllegalArgumentException` nếu `config` hoặc `transport` là null; factory `static EmailNotifier withSmtp(EmailConfig)` → `new EmailNotifier(config, new SmtpMailTransport(config))`;
  - `sendAlert(AlertEvent)` — null thì bỏ qua; `EmailMessageBuilder.forAlert(config, event)` rồi `submit(message, "alert <type> for <agentId>")`; `RejectedExecutionException` (hàng đợi đầy) → `droppedCount++` + log warning `Email notification dropped, the send queue is full: ...`;
  - `deliver(...)` — `transport.send(message)`; thành công → `sentCount++` + log INFO; thất bại → `failedCount++`, `lastError = safeReason(e)`, log WARNING;
  - `safeReason(Exception)` — lấy message của exception (rỗng thì lấy tên lớp), rồi **thay mọi chỗ xuất hiện app password bằng `***`**;
  - `sendTestMessage()` — gửi **đồng bộ** (không qua hàng đợi) và trả kết quả, vì màn hình cài đặt hiện kết quả trong hộp thoại; thành công → `NotificationResult.success("Test email sent successfully.")`, thất bại → `failure("Test email could not be sent." + NL + "Reason: " + lastError)`;
  - `isEnabled()` → `config.isConfigured()`; `describe()` → `"Email to <recipient> through <transport.describe()>"`;
  - getter thống kê: `getSentCount()`, `getFailedCount()`, `getDroppedCount()`, `getLastError()`;
  - `close()` — `executor.shutdown()`, chờ 5 giây rồi `shutdownNow()`, sau đó `transport.close()` (bắt mọi `Exception`, log FINE).
- **Được gọi ở đâu**: `MonitorApplication.buildNotifier` qua `EmailNotifier.withSmtp(email)`; test `EmailNotifierTest` dùng ctor 2 tham số với `StubTransport implements MailTransport`: `sendsAnAlert` (kiểm đúng tiêu đề `[WARNING] CPU HIGH - AGENT-01`, người gửi/nhận, nội dung chứa `25.4 %` và `5.0 %`), `deliveryIsAsynchronous` (20 email xếp hàng < 1000 ms), `transportFailureIsContained`, `failureMessageHidesThePassword`, `sendsTheTestMessage`.

#### NotificationResult

- **Định nghĩa**: class bất biến với hàm tạo private và hai factory tĩnh `success(String)` / `failure(String)`.
- **Vai trò**: kết quả một lần gửi. Javadoc: thông điệp được viết cho hộp thoại, giải thích bước tiếp theo và **không bao giờ lặp lại thông tin đăng nhập**.
- **Thành phần chính**: `boolean success`, `String message`; `isSuccess()`, `getMessage()`; `toString()` → `"OK: <msg>"` hoặc `"FAILED: <msg>"`.
- **Được gọi ở đâu**: kiểu trả về của `Notifier.sendTestMessage()`; `EmailNotifier` (`success`/`failure` ở `sendTestMessage`), `CompositeNotifier` (đúc kết quả tổng hợp), `NoOpNotifier` (luôn `failure`); `SettingsPanel` (dòng 207–220: hiện `result.getMessage()` với icon INFO/ERROR theo `isSuccess()`); test `RecordingNotifier` trả `success("recorded")` / `failure("disabled")`, cùng `EmailNotifierTest`.

#### EmailMessage

- **Định nghĩa**: class bất biến, hàm tạo nhận 5 tham số.
- **Vai trò**: email văn bản thuần đã sẵn sàng để đưa cho `MailTransport`.
- **Thành phần chính**: `fromAddress`, `fromName`, `toAddress`, `subject`, `body` (đều `final` + getter); `toString()` chỉ in `to` và `subject` (không in nội dung).
- **Được gọi ở đâu**: `EmailMessageBuilder.forAlert(...)` / `forTest(...)` là hai nơi `new`; `EmailNotifier.submit/deliver/sendTestMessage` nhận và truyền đi; `MailTransport.send(EmailMessage)`; `SmtpMailTransport.send(...)` đọc để dựng `MimeMessage`; test `EmailNotifierTest.StubTransport` ghi lại `List<EmailMessage>` để assertion.

#### EmailMessageBuilder

- **Định nghĩa**: class `final` với hàm tạo private (toàn bộ static). Hằng `HEADER = "GROUP 10 - SERVER HEALTH MONITORING"`, `NL = System.lineSeparator()`.
- **Vai trò**: dựng tiêu đề và nội dung **mọi** email cảnh báo và email kiểm tra. Javadoc: tách hoàn toàn khỏi mã mạng để có thể assertion chính xác từng chữ trong unit test mà không gửi gì.
- **Thành phần chính**:
  - `buildSubject(AlertEvent)` — dạng `[TAG] <tên loại cảnh báo> - <agentId>`, ví dụ `[WARNING] CPU HIGH - AGENT-01`;
  - `subjectTag(...)` — nếu `alertType == AGENT_RECOVERED` → `"RECOVERED"`; nếu không, lấy tên mức nghiêm trọng (`severity.name()`), null → `"INFO"`;
  - `buildBody(AlertEvent)` — bắt đầu bằng `HEADER`, rồi theo `switch (alertType)`:
    - `AGENT_OFFLINE`: mục `CRITICAL ALERT`, gồm Agent, Server, Status `OFFLINE`, Reason `"No valid heartbeat received for more than <giây> giây"` (chuyển `threshold` từ ms sang giây), `Last Seen` định dạng từ `currentValue`;
    - `AGENT_RECOVERED`: mục `RECOVERY NOTICE`, Status `ONLINE`, Reason `"Valid telemetry is being received again."`;
    - mặc định: Alert Type, Severity, Agent, Server, `Current <nhãn>: <giá trị> <đơn vị>`, `Threshold: <giá trị> <đơn vị>`;
    - cuối mọi nhánh: `Detected At:` định dạng từ `openedAt`.
  - `metricLabel(AlertType)` — `CPU_HIGH` → `"CPU"`, `MEMORY_HIGH` → `"Memory"`, `NETWORK_IN_HIGH` → `"Network IN"`, `NETWORK_OUT_HIGH` → `"Network OUT"`, khác → `"Value"`;
  - `formatValue(double, unit)` — `%.1f` (một chữ số thập phân) + đơn vị nếu có; `formatSeconds(millis)` — `%.0f`; `nullSafe(...)` — null → `"-"`;
  - `buildTestSubject()` → `"[TEST] Group 10 Server Health Monitoring"`; `buildTestBody(config, nowMillis)` — gồm `HEADER`, `This is a test notification.`, `SMTP email notification is configured successfully.`, Sender, Recipient, Time;
  - `forAlert(config, event)` / `forTest(config, nowMillis)` — lắp `EmailMessage` với `fromAddress = config.getUsername()`, `fromName = config.getSenderName()`, `toAddress = config.getRecipient()`.
- **Được gọi ở đâu**: `EmailNotifier.sendAlert` (dòng 70, `EmailMessageBuilder.forAlert`) và `sendTestMessage` (dòng 103, `forTest`). Chưa thấy test riêng cho lớp này (chưa xác định); nội dung chữ được kiểm gián tiếp trong `EmailNotifierTest.sendsAnAlert` (`assertEquals("[WARNING] CPU HIGH - AGENT-01", message.getSubject())`, body chứa `25.4 %`, `5.0 %`).

#### MailTransport

- **Định nghĩa**: `public interface MailTransport extends AutoCloseable`.
- **Vai trò**: phần việc thật sự nói chuyện với máy chủ SMTP. Javadoc: tách khỏi `EmailNotifier` để phần còn lại của notifier (tiêu đề, nội dung, xếp hàng, xử lý lỗi) kiểm thử được với một bản stub (mô phỏng), không cần đăng nhập Gmail thật và không cần mật khẩu thật trong mã test.
- **Thành phần chính**: `void send(EmailMessage message) throws Exception` — ném khi server từ chối hoặc không tới được; `String describe()` — một dòng tên server cho màn hình cài đặt; `default void close()` — mặc định không làm gì ("SMTP connections are per send" — mỗi lần gửi là một kết nối SMTP riêng).
- **Được gọi ở đâu**: `EmailNotifier` giữ trường `transport` và gọi `send`/`describe`/`close`; triển khai thật `SmtpMailTransport` (tạo qua `EmailNotifier.withSmtp`); triển khai trong test `EmailNotifierTest.StubTransport` (ghi `sent`, có cờ `shouldFail`).

#### SmtpMailTransport

- **Định nghĩa**: `public class SmtpMailTransport implements MailTransport`, dùng thư viện `jakarta.mail` (phụ thuộc `com.sun.mail:jakarta.mail` trong `monitor-app/pom.xml`). Hằng `CONNECT_TIMEOUT_MS = 15_000`, `READ_TIMEOUT_MS = 20_000`.
- **Vai trò**: gửi mail qua máy chủ SMTP với STARTTLS (bắt buộc mã hoá kênh sau khi bắt tay — cách Gmail yêu cầu trên cổng 587). Javadoc: app password được đọc từ `EmailConfig` **tại thời điểm gửi** và đưa thẳng cho authenticator; không bao giờ ghi vào log, file cấu hình hay thông điệp exception.
- **Thành phần chính**:
  - ctor `SmtpMailTransport(EmailConfig)` — ném `IllegalArgumentException` nếu config null hoặc `!config.isConfigured()`;
  - `send(EmailMessage)` — `Session.getInstance(buildProperties(), Authenticator trả về PasswordAuthentication(username, appPassword))`; dựng `MimeMessage`: `setFrom` (địa chỉ + tên hiển thị, UTF-8), `setRecipients(TO, ...)`, `setSubject(..., UTF-8)`, `setText(..., UTF-8)`, `setSentDate(new Date())`; `Transport.send(mime)`;
  - `buildProperties()` — `mail.smtp.host`, `mail.smtp.port`, `mail.smtp.auth`, `mail.smtp.starttls.enable`; khi bật STARTTLS thì thêm `mail.smtp.starttls.required = "true"` ("Từ chối rơi về phiên không mã hoá nếu STARTTLS thất bại"); `mail.smtp.ssl.protocols = "TLSv1.2 TLSv1.3"`; `mail.smtp.connectiontimeout = 15000`, `mail.smtp.timeout = 20000`, `mail.smtp.writetimeout = 20000` (không có chúng, sự cố mạng sẽ treo luồng gửi vô hạn);
  - `describe()` → `config.describeServer()`.
- **Được gọi ở đâu**: chỉ trong `EmailNotifier.withSmtp(config)` (dòng 62) — tức gián tiếp từ `MonitorApplication.buildNotifier` khi email đã cấu hình. Không có test riêng (đúng chủ đích: test dùng stub `MailTransport` thay vì login thật).

## 3. Sơ đồ quan hệ trong phần 1 (ASCII)

```
                         java -jar monitor-app-1.0.0-all.jar
                                        |
                                        v
                              +-------------------+
                              |  MonitoringMain   |  entry point
                              |  (resolveConfig,  |
                              |   banner, fail)   |
                              +---------+---------+
                                        |
              +-------------------------+--------------------------+
              |                                                    |
              v                                                    v
   MonitorConfigLoader  --load(Path)-->  MonitorConfig      MonitorApplication (core)
   (monitor.properties)                 (12 field final)      |  start(): DB -> repos ->
                                                               |  email -> registry ->
                                                               |  services -> sockets ->
                                                               |  scheduler(2 luồng)
                                                               |
   +-------------------- notification --------------------------------+
   |  EmailConfigLoader --load()--> EmailConfig                       |
   |       |  (env SMTP_* / config/email-secrets.properties)          |
   |       v                                                          |
   |  isConfigured()? ---false---> CompositeNotifier(NoOpNotifier)    |
   |       | true                                                     |
   |       v                                                          |
   |  CompositeNotifier(EmailNotifier.withSmtp(config)) <-------------+
   |       |                (MonitorApplication.buildNotifier)        |
   |       | sendAlert(AlertEvent) -- gọi bởi AlertService            |
   |       v                                                          |
   |  EmailMessageBuilder --forAlert/forTest--> EmailMessage          |
   |                                                 |                |
   |                MailTransport (interface) <------+                |
   |                       ^                                           |
   |                       |                                           |
   |                SmtpMailTransport (jakarta.mail, STARTTLS 587)     |
   +-------------------------------------------------------------------+
                                                               v
   +------------------- network ----------------+    +--------------------+
   | UdpTelemetryReceiver (4 worker, queue 2000)|    | AgentRegistry      |
   |   port 6000, buffer 1 MB, packet 4096 B    |-->|  Map<id,           |
   +-------------------+------------------------+    |  AgentRuntimeState>|
                           | handleDatagram()        +---------+----------+
                           v                                   | snapshot()
                    TelemetryService                           v
                    (HMAC/replay/timestamp)          AgentSnapshot -----> GUI (EDT)
                           |                                   ^
              +------------+-------------+                     |
              |            |             |                     |
              v            v             v                     |
        MetricSample   AlertService  HeartbeatMonitor --+------+
        (metric_raw)   (Notifier)    (timeout/offline)  |
              |                                         |
              v                                         |
        MetricAggregate (metric_5m) <--- AggregationService
              |
              v
        HistoryPoint <--- HistoryService <--- UI HistoryPanel

   +---------------- network (điều khiển) --------------+
   | AgentManagementService                             |
   |      | ensureConnected / connect / pushConfiguration
   |      v                                             |
   | AgentTcpConnectionManager -- 1 kết nối / Agent -> |
   |      AgentTcpConnection (PING / AGENT_INFO /       |
   |                           CONFIG_UPDATE + ACK)     |
   +----------------------------------------------------+

   model dùng chung:
     AgentRecord (bảng agents) --> AgentRuntimeState --> AgentSnapshot
     MetricSample (metric_raw)  --> MetricAggregate (metric_5m) --> HistoryPoint
```

Chú thích sơ đồ: mũi `-->` là quan hệ gọi/sở hữu theo hướng khởi động và dữ liệu; nhóm `notification` nằm dưới `MonitorApplication` vì chỉ core mới dựng notifier và chỉ `AlertService` mới gọi `sendAlert`.
