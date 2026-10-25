# Cấu trúc dự án

Hệ thống giám sát server từ xa (Remote Server Health Monitoring) — Group 10, HUTECH.

Tài liệu mô tả cấu trúc thư mục, vai trò của từng module Maven, cấu trúc package,
công nghệ và cách các module phụ thuộc vào nhau. Mọi số liệu phiên bản được lấy
trực tiếp từ `pom.xml` của dự án.

---

## 1. Hình dung chung

Dự án là một ứng dụng mạng desktop viết bằng Java (không có web server, không có
Spring, không có server database ngoài). Dự án tổ chức theo kiểu **Maven
multi-module** (nhiều module trong một dự án Maven): một `pom.xml` cha khai báo
bốn module con, mỗi module con khai báo `<parent>` trỏ về pom cha.

| Hạng mục | Giá trị (từ `pom.xml` cha) |
|---|---|
| `groupId` | `vn.edu.hutech.group10` |
| `artifactId` | `server-health-monitoring` |
| `version` | `1.0.0` |
| `packaging` | `pom` |
| Số module | 4: `monitoring-common`, `agent-app`, `monitor-app`, `simulator-app` |
| Java | `maven.compiler.release = 17` (biên dịch với `--release 17`) |
| Encoding | UTF-8 |

---

## 2. Sơ đồ thư mục gốc

```
ServerHealthMonitoring\
|
+-- pom.xml                     POM cha: khai báo 4 module, quản lý phiên bản thư viện
+-- README.md                   Tài liệu tổng quan (tiếng Anh)
+-- .gitignore                  Loại file nhạy cảm (vd. config/email-secrets.properties)
|
+-- config\                     Cấu hình ngoài, sửa được không cần biên dịch lại
|     monitor.properties          monitor-app bản production (retention 60 phút)
|     monitor-demo.properties     monitor-app bản demo (retention nén lại)
|     email-secrets.properties    thông tin SMTP (bị .gitignore, chưa điền)
|     email-secrets.properties.example   mẫu để sao chép
|     agent01.properties ... agent04.properties   cấu hình từng agent
|
+-- data\                       Database SQLite, tự tạo khi khởi động lần đầu
|     monitoring.db               bản production
|     monitoring-demo.db          bản demo (tự tạo khi chạy profile demo)
|
+-- logs\                       File log xoay vòng (java.util.logging)
|     monitor-0.log, agent-agentNN-0.log, simulator-0.log ...
|
+-- docs\                       Tài liệu dự án (có README.md làm chỉ mục)
|     01-gioi-thieu\              01-tong-quan-de-tai.md, 02-chi-tiet-de-tai.md
|     02-kien-truc-va-nguyen-ly\  09-kien-truc-goc.md, 10-giao-thuc-goc.md, 11-co-so-du-lieu-goc.md,
|                                 01..08-*.md (bộ tài liệu lý thuyết)
|     03-huong-dan-su-dung\       01..03-*.md + EMAIL_SETUP, NETBEANS_SETUP,
|                                 TESTING, TEST_REPORT, DEMO_SCRIPT,
|                                 MULTI_MACHINE_SETUP, TROUBLESHOOTING
|     04-tai-lieu-class\          00..06-*.md (tài liệu chi tiết từng class)
|     05-tu-khoa\                 01-tu-khoa.md (bảng thuật ngữ)
|     06-huong-dan-script\        01-cac-file-bat.md (hướng dẫn script)
|
+-- scripts\                    Script build / test / chạy / firewall
|     build.bat, test.bat, find-maven.bat
|     run-monitor.bat, run-monitor-demo.bat, run-agentNN.bat, run-local-demo.bat
|     run-invalid-hmac-test.bat, run-replay-test.bat, run-load-test.bat
|     firewall-agent.ps1, firewall-monitor.ps1
|
+-- release\                    Bản đóng gói phát hành
|     ServerHealthMonitoring-v1.0.0.zip
|     ServerHealthMonitoring-v1.0.0\  (3 jar *-all.jar + config + docs + scripts)
|
+-- src\test\java\              Thư mục test cấp gốc — hiện KHÔNG chứa file nào
|                               (chỉ còn cấu trúc thư mục rỗng)
|
+-- monitoring-common\          Module dùng chung: model, giao thức, bảo mật, util
+-- agent-app\                  Agent chạy trên máy được giám sát
+-- monitor-app\                Client giám sát (Swing GUI)
+-- simulator-app\              Trình sinh lưu lượng kiểm thử
```

Lưu ý: các thư mục `target\` bên trong từng module là sản phẩm của Maven (biên
dịch, đóng gói) và không nằm trong nguồn dự án.

### Giải thích từng mục gốc

| Thư mục / file | Vai trò |
|---|---|
| `pom.xml` | POM cha: liệt kê `<modules>`, khai báo `dependencyManagement` (một nơi duy nhất quyết định phiên bản thư viện), khai báo `pluginManagement` (compiler, surefire, shade, jar). |
| `README.md` | Tổng quan dự án, stack công nghệ, cách build/chạy, demo 4 agent. |
| `config\` | Cấu hình kiểu `.properties` đặt **ngoài** jar: thay đổi cổng, chu kỳ, ngưỡng, bí mật... chỉ cần sửa file và khởi động lại, không cần build lại. |
| `data\` | Chứa file SQLite (`monitoring.db` / `monitoring-demo.db`). Database được tạo kèm schema ngay lần chạy đầu tiên, không cần chuẩn bị tay. |
| `logs\` | Log ghi ra file theo cơ chế xoay vòng: 5 file × 2 MiB cho mỗi tên (`LoggingSetup`, `FileHandler(path, 2*1024*1024, 5, true)`), cộng thêm log ra console. |
| `docs\` | Tài liệu dự án, chia đánh số theo chủ đề (01 giới thiệu, 02 kiến trúc, 03 hướng dẫn...). |
| `scripts\` | Script Windows: build, test, chạy monitor/agent/demo, chạy các kịch bản kiểm thử bảo mật (invalid HMAC, replay, load) và script mở firewall PowerShell. |
| `release\` | Bản đóng gói: file zip và thư mục giải nén chứa 3 jar `*-all.jar` (jar tự chứa toàn bộ dependency), `config\`, `docs\`, `scripts\`. |
| `src\test\java\` | Cấu trúc thư mục test cấp gốc còn sót lại, **không chứa file Java nào**; toàn bộ test nằm trong `src/test/java` của từng module. |

---

## 3. Vai trò của từng module

| Module | ArtifactId | Vai trò | Main class |
|---|---|---|---|
| Dùng chung | `monitoring-common` | Model dữ liệu, giao thức wire (framing, codec, MessageType), bảo mật (HMAC, canonicalizer, validator, chống replay), tiện ích (AppPaths, LoggingSetup, TimeUtil, ValidationUtil). Không có `main`. | — |
| Agent | `agent-app` | Chương trình console chạy trên **máy được giám sát**: thu thập 6 chỉ số hệ thống qua OSHI, ký HMAC, gửi UDP tới monitor, đồng thời mở TCP server để nhận lệnh cấu hình từ xa. | `vn.edu.hutech.group10.monitoring.agent.AgentMain` |
| Client giám sát | `monitor-app` | Ứng dụng Swing desktop: nhận UDP (telemetry/heartbeat), xác thực, lưu SQLite, vẽ bảng/biểu đồ, sinh cảnh báo (GUI, DB, Email), gửi lệnh cấu hình qua TCP. | `vn.edu.hutech.group10.monitoring.monitor.MonitoringMain` |
| Mô phỏng | `simulator-app` | Trình sinh lưu lượng kiểm thử: 4 chế độ `valid`, `invalid-hmac`, `replay`, `load` (mặc định 20 định danh, 60 giây, interval 1000 ms). | `vn.edu.hutech.group10.monitoring.simulator.SimulatorMain` |

---

## 4. Cấu trúc package của từng module

Gốc package chung: `vn.edu.hutech.group10.monitoring`.

### 4.1 `monitoring-common`

```
monitoring-common/src/main/java/vn/edu/hutech/group10/monitoring/common/
+-- model/          TelemetryPacket, HeartbeatPacket, ConfigUpdateMessage,
|                   ConfigAckMessage, AgentInfoMessage, AlertEvent,
|                   AlertType, AlertSeverity, AlertState, AgentStatus
+-- protocol/       JsonMessageCodec (Gson), MessageFraming (4-byte length prefix),
|                   MessageType, ProtocolConstants, ProtocolException
+-- security/       PacketSigner, PacketValidator, PacketCanonicalizer,
|                   HmacUtil, ReplayProtector, SecretProvider, ValidationResult
+-- util/           AppPaths, LoggingSetup, TimeUtil, ValidationUtil,
                    ByteRateFormatter
```

### 4.2 `agent-app`

```
agent-app/src/main/java/vn/edu/hutech/group10/monitoring/agent/
+-- AgentMain.java            entry point, parse --config, banner, shutdown hook
+-- config/                   AgentConfig (volatile cho field mutable),
|                             AgentConfigLoader (đọc + kiểm tra range)
+-- core/                     AgentRuntime (vòng đời: start/stop, thứ tự khởi động)
+-- metrics/                  SystemMetrics, SystemMetricsCollector (interface),
|                             OshiSystemMetricsCollector, NetworkRateTracker
+-- network/                  UdpTelemetrySender, TcpControlServer,
                              TcpClientSessionHandler
+-- service/                  TelemetryScheduler, HeartbeatScheduler,
                              ConfigUpdateService
```

### 4.3 `monitor-app`

```
monitor-app/src/main/java/vn/edu/hutech/group10/monitoring/monitor/
+-- MonitoringMain.java       entry point
+-- config/                   MonitorConfig(+Loader), EmailConfig(+Loader)
+-- core/                     MonitorApplication (nơi "wiring" toàn bộ)
+-- model/                    AgentRecord, AgentRuntimeState, AgentSnapshot,
|                             MetricSample, MetricAggregate, HistoryPoint
+-- network/                  UdpTelemetryReceiver, AgentTcpConnection,
|                             AgentTcpConnectionManager
+-- repository/               DatabaseManager, AgentRepository, MetricRepository,
|                             AlertRepository, DataAccessException
+-- service/                  TelemetryService, AgentRegistry, HeartbeatMonitor,
|                             AlertService, AggregationService, HistoryService,
|                             AgentManagementService, MonitorEventBus(+Listener)
+-- notification/             Notifier, EmailNotifier, NoOpNotifier,
|                             CompositeNotifier, SmtpMailTransport, MailTransport,
|                             EmailMessage(+Builder), NotificationResult
+-- ui/                       MainFrame, DashboardPanel, ServerDetailPanel,
                              HistoryPanel, AlertPanel, SettingsPanel,
                              ConfigureAgentDialog, AgentDialog, AgentTableModel,
                              TimeSeriesChartPanel, UiConstants
```

Tài nguyên: `monitor-app/src/main/resources/db/schema.sql` (schema SQLite).

### 4.4 `simulator-app`

```
simulator-app/src/main/java/vn/edu/hutech/group10/monitoring/simulator/
+-- SimulatorMain.java        chọn mode theo tham số dòng đầu tiên
+-- SimulatorOptions.java     --host, --port, --agent, --secret, --agents,
|                             --duration, --interval, --agent-prefix
+-- PacketFactory.java        tạo packet đúng chuẩn để ký
+-- ValidAgentSimulator.java  chế độ "valid"
+-- InvalidHmacSender.java    chế độ "invalid-hmac"
+-- ReplayPacketSender.java   chế độ "replay"
+-- LoadSimulator.java        chế độ "load"
+-- SimulatorUdpSender.java   gửi UDP dùng chung
```

### 4.5 Test

Mỗi module có `src/test/java` với JUnit 5. `monitor-app` có test tích hợp mở
socket thật trên loopback (`UdpIntegrationTest`, `TcpIntegrationTest`).

---

## 5. Công nghệ và phiên bản thư viện (lấy từ `pom.xml`)

### 5.1 Thư viện runtime

| Hạng mục | Thư viện | Phiên bản | Thuộc module |
|---|---|---|---|
| Ngôn ngữ | Java (`maven.compiler.release`) | 17 | tất cả |
| JSON | `com.google.code.gson:gson` | 2.11.0 | monitoring-common |
| Chỉ số hệ thống | `com.github.oshi:oshi-core` | 6.6.5 | agent-app |
| Database | `org.xerial:sqlite-jdbc` | 3.46.1.3 | monitor-app |
| Biểu đồ | `org.jfree:jfreechart` | 1.5.5 | monitor-app |
| Email (SMTP) | `com.sun.mail:jakarta.mail` | 2.0.1 | monitor-app |
| Logging binding | `org.slf4j:slf4j-jdk14` | 2.0.13 | agent-app, monitor-app |
| GUI | Java Swing (JDK) | kèm JDK 17 | monitor-app |
| Test | `org.junit.jupiter:junit-jupiter` | 5.10.2 | tất cả (scope `test`) |

Ghi chú: README ghi `SQLite via org.xerial:sqlite-jdbc 3.46`; `pom.xml` khai báo
chính xác `3.46.1.3` — dùng giá trị từ `pom.xml`.

### 5.2 Plugin Maven (trong `pluginManagement` của POM cha)

| Plugin | Phiên bản |
|---|---|
| `maven-compiler-plugin` | 3.13.0 |
| `maven-surefire-plugin` | 3.2.5 |
| `maven-shade-plugin` | 3.5.3 |
| `maven-jar-plugin` | 3.4.1 |

Ba module ứng dụng (`agent-app`, `monitor-app`, `simulator-app`) đều cấu hình
`maven-shade-plugin` với `shadedClassifierName = all`, tạo ra jar tự chứa:

```
agent-app\target\agent-app-1.0.0-all.jar
monitor-app\target\monitor-app-1.0.0-all.jar
simulator-app\target\simulator-app-1.0.0-all.jar
```

---

## 6. Các module phụ thuộc vào nhau như thế nào

```
                    +--------------------+
                    |  monitoring-common |   (chỉ phụ thuộc gson)
                    +---------+----------+
                              |
        +---------------------+---------------------+
        |                     |                     |
        v                     v                     v
+---------------+     +--------------+      +----------------+
|   agent-app   |     |  monitor-app |      | simulator-app  |
| (common, oshi,|     | (common,     |      | (common)       |
|  slf4j-jdk14) |     |  sqlite,     |      +----------------+
+---------------+     |  jfreechart, |
                      |  jakarta.mail,|
                      |  slf4j-jdk14) |
                      +------+--------+
                             |
                             | scope = test
                             v
                      +--------------+
                      |   agent-app  |
                      +--------------+
```

Bảng phụ thuộc khai báo trong `pom.xml` từng module:

| Module | Phụ thuộc compile | Phụ thuộc test |
|---|---|---|
| `monitoring-common` | `gson` | `junit-jupiter` (kế thừa từ POM cha) |
| `agent-app` | `monitoring-common`, `oshi-core`, `slf4j-jdk14` | `junit-jupiter` |
| `monitor-app` | `monitoring-common`, `sqlite-jdbc`, `jfreechart`, `jakarta.mail`, `slf4j-jdk14` | `junit-jupiter`, **`agent-app`** |
| `simulator-app` | `monitoring-common` | `junit-jupiter` |

Hai điểm quan trọng:

1. `monitoring-common` là **module gốc của đồ thị phụ thuộc**: cả ba module ứng
   dụng đều phụ thuộc vào nó, nên model packet, giao thức, HMAC dùng chung một
   bộ mã duy nhất cho cả hai phía — hai bên không thể "lệch chuẩn" giao thức.
2. `monitor-app` chỉ phụ thuộc `agent-app` với **scope `test`** (nhận xét trong
   `monitor-app/pom.xml`): các test tích hợp chạy một agent thật qua socket
   thật, không có mã trong `src/main` nào của monitor-app được phép gọi agent-app.

Phụ thuộc phiên bản được quản lý tập trung: module con khai báo groupId +
artifactId, **không** khai báo version — version nằm trong
`<dependencyManagement>` của POM cha.

---

## 7. Cách cấu hình tìm thư mục gốc

Tất cả module dùng chung `AppPaths` (`monitoring-common`):

- Thư mục gốc = giá trị hệ thống `-Dmonitoring.home` nếu có, ngược lại là thư
  mục làm việc hiện hành (working directory).
- Từ đó suy ra `config\`, `data\`, `logs\`.
- Nhờ vậy bản trong `release\` có thể giải nén ở bất kỳ đâu mà không phải sửa
  lại đường dẫn hay build lại.

---

## Tài liệu liên quan

- [09-kien-truc-goc.md](09-kien-truc-goc.md) — phân tầng, luồng dữ liệu, mô hình luồng.
- [10-giao-thuc-goc.md](10-giao-thuc-goc.md) — giao thức wire, framing, chuỗi ký, ví dụ.
- [11-co-so-du-lieu-goc.md](11-co-so-du-lieu-goc.md) — bảng, index, retention.
- [04-giao-thuc-tcp-udp.md](04-giao-thuc-tcp-udp.md) — giao thức TCP/UDP, cấu trúc từng message.
- [05-bao-mat-hmac-va-chong-phat-lai.md](05-bao-mat-hmac-va-chong-phat-lai.md) — xác thực HMAC-SHA256 và chống phát lại.
- [06-luu-tru-va-tong-hop.md](06-luu-tru-va-tong-hop.md) — lưu trữ SQLite, tổng hợp và retention.
- [07-canh-bao-va-thong-bao.md](07-canh-bao-va-thong-bao.md) — hệ thống cảnh báo và gửi email.
