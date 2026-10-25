# Tài liệu lớp — agent-app

Module `agent-app` là tiến trình (process) chạy trên chính máy được giám sát: nó thu thập 6 chỉ số hệ thống (CPU, RAM, số kết nối TCP, uptime, network in/out), ký HMAC-SHA256 (thư xác thực dùng khóa bí mật chung) và gửi gói telemetry/heartbeat qua UDP cho Monitor; đồng thời mở một cổng TCP để Monitor gửi lệnh PING, yêu cầu thông tin máy và cập nhật cấu hình (CONFIG_UPDATE). Module không có giao diện đồ họa (GUI) — theo Javadoc của `AgentMain`, agent phải "nhỏ và rẻ" (small and cheap) vì chạy chung với hệ thống cần theo dõi.

- Artifact Maven: `vn.edu.hutech.group10:agent-app:1.0.0` (pom cha: `server-health-monitoring`).
- Package gốc: `vn.edu.hutech.group10.monitoring.agent`.
- Phụ thuộc (theo `agent-app\pom.xml`): `monitoring-common`, `oshi-core` (thư viện đọc chỉ số OS/hardware, viết tắt của "Operating System and Hardware Information"), `slf4j-jdk14` (cầu nối SLF4J → java.util.logging).
- Build ra jar gộp `agent-app\target\agent-app-1.0.0-all.jar` (maven-shade-plugin), main class `vn.edu.hutech.group10.monitoring.agent.AgentMain`.
- Chạy ví dụ (theo Javadoc `AgentMain`): `java -jar agent-app-1.0.0-all.jar --config="config\agent01.properties"`.

---

## 1. Bảng tổng quan class

14 file trong `src/main/java` (chưa kể 4 file test):

| # | Class | Package | Vai trò | File nguồn |
|---|-------|---------|---------|------------|
| 1 | `AgentMain` | `agent` | Điểm vào (entry point) console: đọc tham số `--config`, cấu hình log, nạp cấu hình, khởi động runtime, giữ tiến trình sống | `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\AgentMain.java` |
| 2 | `AgentConfig` | `agent.config` | Đối tượng cấu hình runtime của một agent; các trường interval/threshold là `volatile` để thay đổi an toàn khi đang chạy | `...\agent\config\AgentConfig.java` |
| 3 | `AgentConfigLoader` | `agent.config` | Đọc + kiểm tra (validate) file `.properties` thành `AgentConfig` | `...\agent\config\AgentConfigLoader.java` |
| 4 | `AgentRuntime` | `agent.core` | Chủ sở hữu toàn bộ thành phần của agent; định nghĩa thứ tự khởi động/ngừng và rollback khi lỗi | `...\agent\core\AgentRuntime.java` |
| 5 | `SystemMetrics` | `agent.metrics` | DTO bất biến (immutable) chứa 6 chỉ số của một lần lấy mẫu | `...\agent\metrics\SystemMetrics.java` |
| 6 | `SystemMetricsCollector` | `agent.metrics` | Interface nguồn chỉ số hệ thống, tách phần cài khỏi phần dùng để test được | `...\agent\metrics\SystemMetricsCollector.java` |
| 7 | `OshiSystemMetricsCollector` | `agent.metrics` | Hiện thực interface trên OSHI: đọc CPU, RAM, kết nối, uptime, lưu lượng mạng | `...\agent\metrics\OshiSystemMetricsCollector.java` |
| 8 | `NetworkRateTracker` | `agent.metrics` | Chuyển bộ đếm byte tích lũy (tăng dần) của NIC thành tốc độ byte/giây | `...\agent\metrics\NetworkRateTracker.java` |
| 9 | `TcpControlServer` | `agent.network` | Server TCP nhận kết nối điều khiển từ Monitor (1 thread accept + pool 8 session) | `...\agent\network\TcpControlServer.java` |
| 10 | `TcpClientSessionHandler` | `agent.network` | Xử lý trọn vòng đời một kết nối TCP: đọc frame, trả lời PONG / AGENT_INFO_RESPONSE / CONFIG_ACK / ERROR | `...\agent\network\TcpClientSessionHandler.java` |
| 11 | `UdpTelemetrySender` | `agent.network` | Gói JSON đã mã hóa thành datagram UDP gửi đi; đếm số gói thành công/thất bại | `...\agent\network\UdpTelemetrySender.java` |
| 12 | `ConfigUpdateService` | `agent.service` | Validate rồi áp dụng CONFIG_UPDATE vào `AgentConfig`, sinh CONFIG_ACK, báo scheduler xếp lại lịch | `...\agent\service\ConfigUpdateService.java` |
| 13 | `TelemetryScheduler` | `agent.service` | Lấy mẫu chỉ số định kỳ, đóng gói TelemetryPacket, ký HMAC rồi gửi UDP | `...\agent\service\TelemetryScheduler.java` |
| 14 | `HeartbeatScheduler` | `agent.service` | Gói HeartbeatPacket (chỉ nhịp tim, không chỉ số) ký HMAC gửi UDP theo chu kỳ | `...\agent\service\HeartbeatScheduler.java` |

4 file test trong `src/test/java`:

| # | Test class | Kiểm tra cái gì |
|---|-----------|------------------|
| T1 | `agent.config.AgentConfigLoaderTest` | Đọc đủ property, mặc định khi thiếu, bắt lỗi thiếu `agent.id`/`security.sharedSecret`, bắt giá trị ngoài khoảng, phân thập phân theo `Locale.ROOT`, load từ file thật, banner không lộ secret |
| T2 | `agent.metrics.AgentMetricsTest` | Chạy collector OSHI thật trên máy build: chỉ số trong khoảng hợp lý, lặp lại ổn định, `describe()` điền đủ thông tin máy, mẫu thật ký ra HMAC khớp `HmacUtil` |
| T3 | `agent.metrics.NetworkRateTrackerTest` | Mẫu đầu là baseline = 0, rate = delta/thời gian, reset counter không ra số âm, elapsed ≤ 0 lặp rate cũ, counter âm giữ rate cũ, idle = 0, `reset()` |
| T4 | `agent.service.ConfigUpdateServiceTest` | Áp dụng hợp lệ + listener được gọi, field `null` = giữ nguyên, interval quá nhỏ bị từ chối, validate "all-or-nothing", NaN/âm/0% bị từ chối, update rỗng là no-op, message null trả lỗi |

---

## 2. Vòng đời Agent

Luồng chạy từ khi gọi `main` đến khi tắt:

```
java -jar agent-app-1.0.0-all.jar --config="config\agent01.properties"
        |
        v
[1] AgentMain.resolveConfigPath(args)
        |-- có --config=... | --config <path> | arg trần *.properties
        |-- không có  --> AppPaths.home()/config/agent01.properties  (mặc định)
        v
[2] LoggingSetup.configure("agent-" + stem(configPath))
        --> vd: file log "agent01-%g.log" trong thư mục logs/
        v
[3] AgentConfigLoader.load(configPath)
        |-- lỗi IOException / IllegalArgumentException
        |      --> in hướng dẫn dùng ra System.err, log SEVERE, exit(2)
        v
[4] printBanner(config, configPath)      (KHÔNG in sharedSecret)
        v
[5] new AgentRuntime(config, "1.0.0")
     + cài shutdown hook "agent-shutdown" (bấm Ctrl+C / SIGTERM)
     + CountDownLatch(1)
        v
[6] runtime.start()   [synchronized]
        (a) new OshiSystemMetricsCollector()        -> "System metrics collector ready"
        (b) new UdpTelemetrySender(host, udpPort)  -> "UDP telemetry target is host:port"
        (c) new ConfigUpdateService(config, listener)
                listener = { telemetryScheduler.reschedule();
                             heartbeatScheduler.reschedule(); }
        (d) new TcpControlServer(...).start()       -> listen cổng agent.tcp.port
        (e) Executors.newScheduledThreadPool(2, daemon "agent-scheduler-N")
        (f) new TelemetryScheduler(...).start()     -> scheduleAtFixedRate(delay 0)
        (g) new HeartbeatScheduler(...).start()     -> scheduleAtFixedRate(delay = interval)
        |-- nếu bất kỳ bước nào ném IOException/RuntimeException
        |      --> log "Agent startup failed, rolling back", stop(), exit(3)
        v
[7] In "Agent is running. Press Ctrl+C to stop." ; main thread chờ shutdownLatch.await()
        |
        |  --- trong khi chạy, hai vòng lặp song song ---
        |
        +-- tick Telemetry (mỗi metric.interval.ms, mặc định 1000 ms):
        |     collector.collect() -> TelemetryPacket -> PacketSigner.sign()
        |     -> UdpTelemetrySender.send() --> Monitor (UDP 6000)
        |
        +-- tick Heartbeat (mỗi heartbeat.interval.ms, mặc định 5000 ms):
        |     HeartbeatPacket -> PacketSigner.sign() -> sender.send() --> Monitor
        |
        +-- luồng "agent-tcp-accept": accept() -> TcpClientSessionHandler
              (pool 8 thread "agent-tcp-session-N", timeout đọc 120 000 ms)
              PING -> PONG | AGENT_INFO_REQUEST -> AGENT_INFO_RESPONSE
              CONFIG_UPDATE -> ConfigUpdateService.apply() -> CONFIG_ACK
        |
        v
[8] shutdown hook "agent-shutdown":
        runtime.stop() (idempotent - gọi nhiều lần không sao)
          (a) telemetryScheduler.cancel()
          (b) heartbeatScheduler.cancel()
          (c) scheduler.shutdown() ; chờ tối đa 2 giây rồi shutdownNow()
          (d) controlServer.close()  (đóng socket accept, shutdownNow pool, chờ 2 giây)
          (e) sender.close()         (đóng DatagramSocket)
          (f) collector.close()      (xóa tracker)
        --> shutdownLatch.countDown() -> main thread thoát
```

Ghi chú về mã thoát (exit code): `2` = lỗi cấu hình, `3` = lỗi khởi động runtime; Ctrl+C không gọi `System.exit` mà chạy shutdown hook.

---

## 3. Chi tiết từng class

### 3.1 AgentMain

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\AgentMain.java`
- **Vai trò:** điểm vào console của agent; quyết định file cấu hình, khởi tạo log, tạo và điều phối `AgentRuntime`, giữ main thread treo cho đến khi shutdown hook chạy.
- **Thành phần chính:**
  - `AGENT_VERSION = "1.0.0"` — hằng số công khai, được truyền vào `AgentRuntime` và trả về trong AGENT_INFO_RESPONSE.
  - `CONFIG_OPTION = "--config"`, `DEFAULT_CONFIG = "config/agent01.properties"` — tiền tố tùy chọn và đường dẫn mặc định.
  - `main(String[])` — thứ tự: resolve config path → `LoggingSetup.configure("agent-"+stem)` → `AgentConfigLoader.load` → banner → `new AgentRuntime` → cài shutdown hook → `runtime.start()` → `shutdownLatch.await()`.
  - `resolveConfigPath(String[])` (`static`, visibility package-private) — nhận 3 dạng: `--config=path`, `--config path`, hoặc một argument trần kết thúc bằng `.properties`; không có thì ghép `AppPaths.home().resolve("config/agent01.properties")`.
  - `stripQuotes(String)` — bỏ dấu nháy kép bọc ngoài giá trị.
  - `stem(Path)` — lấy tên file không phần mở rộng (dùng để đặt tên file log).
  - `printBanner(AgentConfig, Path)` — in 6 dòng thông tin + đường dẫn config + thư mục log; **cố ý không in shared secret** (bình luận trong code: "The shared secret is intentionally absent from this banner and from the logs").
  - Constructor private — class utility, không thể khởi tạo.
- **Được gọi ở đâu:**
  - `agent-app\pom.xml` — khai báo `<main.class>` cho manifest của maven-jar-plugin và maven-shade-plugin (jar `agent-app-1.0.0-all.jar`).
  - `monitor-app\pom.xml` không tham chiếu; các script `scripts\run-agent0x.bat` chạy jar này (tham chiếu ở mức jar, không phải Java API).
- **Quan hệ:** dùng `AgentConfig`, `AgentConfigLoader`, `AgentRuntime`, `AppPaths`, `LoggingSetup` (2 lớp cuối từ `monitoring-common`).
- **Ghi chú:**
  - Tự bắt lỗi cấu hình và in ví dụ: `java -jar agent-app-1.0.0-all.jar --config="config\agent01.properties"`.
  - Shutdown hook tên `"agent-shutdown"`; nếu main thread bị ngắt (`InterruptedException`) thì đặt lại cờ ngắt và gọi `runtime.stop()` trực tiếp.
  - Bình luận trong code: agent cố ý không có GUI.

### 3.2 AgentConfig

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\config\AgentConfig.java`
- **Vai trò:** giữ toàn bộ cấu hình runtime của một agent; phân tách rõ phần cố định khi khởi động (identity, endpoint, secret) và phần thay đổi được khi đang chạy (interval, threshold).
- **Thành phần chính:**
  - Trường `final` (cố định): `agentId`, `agentName`, `hostname`, `monitorHost`, `monitorUdpPort`, `agentTcpPort`, `sharedSecret`.
  - Trường `volatile` (đọc/ghi từ nhiều thread — thread scheduler đọc, thread TCP ghi): `metricIntervalMs`, `heartbeatIntervalMs`, `cpuThreshold`, `memoryThreshold`, `networkInThresholdMbps`, `networkOutThresholdMbps`; kèm getter/setter cho từng cái.
  - `getSharedSecret()` — Javadoc: "Never log, print or transmit this value."
  - `describeForConsole()` — banner nhiều dòng cho console, gồm agentId/name/hostname/monitor host/udp port/tcp port/metric interval/heartbeat interval; không có secret.
  - `toString()` — dạng `AgentConfig{agentId=..., monitor=host:port, tcpPort=..., metricIntervalMs=..., heartbeatIntervalMs=..., defaultTimestampToleranceMs=30000}`; không có secret.
- **Được gọi ở đâu:**
  - `AgentConfigLoader.fromProperties` — tạo thể (instance).
  - `AgentRuntime` — sở hữu và truyền cho mọi thành phần.
  - `TcpControlServer`, `TcpClientSessionHandler`, `ConfigUpdateService`, `TelemetryScheduler`, `HeartbeatScheduler` — đọc giá trị.
  - Test: `AgentConfigLoaderTest`, `ConfigUpdateServiceTest` (monitor-app: `TcpIntegrationTest` cũng dựng `AgentConfig` qua loader).
- **Quan hệ:** không kế thừa; tham chiếu `ProtocolConstants.DEFAULT_TIMESTAMP_TOLERANCE_MS` trong `toString()`.
- **Ghi chú:**
  - Ngưỡng (threshold) chỉ là bản sao cục bộ: theo ghi chú trong `config\agent01.properties`, Monitor mới là nơi phát cảnh báo; giá trị này được agent trả về trong CONFIG_ACK và bị CONFIG_UPDATE thay đổi.
  - `DEFAULT_TIMESTAMP_TOLERANCE_MS = 30000` (sai lệch timestamp tối đa cho phép khi Monitor kiểm tra gói) được in ra toString để hiển thị, không phải trường của config.

### 3.3 AgentConfigLoader

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\config\AgentConfigLoader.java`
- **Vai trò:** đọc file properties, kiểm tra hợp lệ toàn bộ giá trị **trước khi** trả về `AgentConfig`, để lỗi phát hiện lúc khởi động chứ không phải bên trong scheduler.
- **Thành phần chính (hằng public — tên khóa cấu hình):**
  - `KEY_AGENT_ID = "agent.id"`, `KEY_AGENT_NAME = "agent.name"`, `KEY_MONITOR_HOST = "monitor.host"`, `KEY_MONITOR_UDP_PORT = "monitor.udp.port"`, `KEY_AGENT_TCP_PORT = "agent.tcp.port"`, `KEY_METRIC_INTERVAL = "metric.interval.ms"`, `KEY_HEARTBEAT_INTERVAL = "heartbeat.interval.ms"`, `KEY_SHARED_SECRET = "security.sharedSecret"`, `KEY_CPU_THRESHOLD = "alert.cpu.threshold"`, `KEY_MEMORY_THRESHOLD = "alert.memory.threshold"`, `KEY_NET_IN_THRESHOLD = "alert.network.in.threshold.mbps"`, `KEY_NET_OUT_THRESHOLD = "alert.network.out.threshold.mbps"`.
  - `load(Path)` — ném `IllegalArgumentException` nếu path null, `IOException` nếu file không phải file thường ("Agent configuration file not found: ..."), rồi gọi `fromProperties`.
  - `fromProperties(Properties)` — dựng config; 2 khóa bắt buộc: `agent.id`, `security.sharedSecret` (thiếu → `IllegalArgumentException` "Missing required property '<key>'").
  - `resolveHostname()` — `InetAddress.getLocalHost().getHostName()`, lỗi thì lấy biến môi trường `COMPUTERNAME`, còn trống thì `"UNKNOWN-HOST"`.
  - Helper riêng tư: `requireText`, `text`, `port`, `interval`, `percentage`, `rate`, `parseInt`, `parseLong`, `parseDouble`.
- **Bảng giá trị mặc định và khoảng hợp lệ (lấy từ code):**

  | Khóa | Mặc định khi thiếu | Kiểm tra |
  |------|--------------------|----------|
  | `agent.id` | — (bắt buộc) | không được trống |
  | `security.sharedSecret` | — (bắt buộc) | không được trống |
  | `agent.name` | = `agent.id` | trim |
  | `monitor.host` | `127.0.0.1` | trim |
  | `monitor.udp.port` | `6000` (`DEFAULT_MONITOR_UDP_PORT`) | 1..65535 |
  | `agent.tcp.port` | `5000` (`DEFAULT_AGENT_TCP_PORT`) | 1..65535 |
  | `metric.interval.ms` | `1000` (`DEFAULT_METRIC_INTERVAL_MS`) | 200 (`MIN_METRIC_INTERVAL_MS`) .. 3 600 000 (`MAX_INTERVAL_MS`) |
  | `heartbeat.interval.ms` | `5000` (`DEFAULT_HEARTBEAT_INTERVAL_MS`) | 1000 (`MIN_HEARTBEAT_INTERVAL_MS`) .. 3 600 000 |
  | `alert.cpu.threshold` | `85.0` | percentage 0..100 (`ValidationUtil.isPercentage`) |
  | `alert.memory.threshold` | `85.0` | 0..100 |
  | `alert.network.in.threshold.mbps` | `50.0` | số không âm |
  | `alert.network.out.threshold.mbps` | `50.0` | số không âm |

- **Được gọi ở đâu:**
  - `AgentMain.main` — `AgentConfigLoader.load(configPath)`.
  - `agent-app` tests: `AgentConfigLoaderTest`, `ConfigUpdateServiceTest` (dùng `fromProperties`).
  - `monitor-app` test `TcpIntegrationTest` — dựng `AgentConfig` cho server TCP thật trong test.
- **Quan hệ:** là class utility (`final`, constructor private); trả về `AgentConfig`; dùng `ProtocolConstants`, `ValidationUtil` của `monitoring-common`.
- **Ghi chú:**
  - `parseDouble` làm `raw.trim().toLowerCase(Locale.ROOT)` rồi `Double.parseDouble` — Javadoc: file luôn dùng dấu chấm làm dấu thập phân, bất kể locale hệ thống (được test bằng `Locale.GERMANY` trong `AgentConfigLoaderTest.parsesDecimalsLocaleIndependently`).
  - Giá trị sai khoảng **bị từ chối** (ném exception), không bị "cắt" (clamp) âm thầm — đúng khẳng định trong test `rejectsOutOfRangeValues`.

### 3.4 AgentRuntime

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\core\AgentRuntime.java`
- **Vai trò:** sở hữu mọi thành phần chuyển động (moving part) của agent và thứ tự khởi động/dừng; rollback nếu khởi động dở dang.
- **Thành phần chính:**
  - `SCHEDULER_THREADS = 2` — pool chỉ 2 thread: một cho telemetry tick, một cho heartbeat tick.
  - `config`, `agentVersion`, `codec = new JsonMessageCodec()`, `sequence = new AtomicLong()` — `sequence` **dùng chung** cho cả hai scheduler.
  - `sessionId = UUID.randomUUID().toString()` — định danh một lần chạy; Javadoc: restart sinh giá trị mới, nhờ đó Monitor phân biệt agent vừa restart (đếm lại từ 1) với gói bị phát lại (replay).
  - Các thành phần tạo động: `collector` (`SystemMetricsCollector`), `sender` (`UdpTelemetrySender`), `controlServer` (`TcpControlServer`), `scheduler` (`ScheduledExecutorService`), `telemetryScheduler`, `heartbeatScheduler`; cờ `volatile boolean running`.
  - `start()` (`synchronized`) — thứ tự: collector → sender → `ConfigUpdateService` (listener gọi `reschedule()` cả hai scheduler) → `controlServer.start()` → pool daemon `agent-scheduler-N` → `telemetryScheduler.start()` → `heartbeatScheduler.start()`; mọi lỗi → `LOG SEVERE "Agent startup failed, rolling back"` → `stop()` → ném lại.
  - `stop()` (`synchronized`, idempotent — thứ tự ngược, mỗi tham chiếu được set `null` sau khi đóng; pool scheduler chờ tối đa 2 giây rồi `shutdownNow()`).
  - Getter: `isRunning()`, `getConfig()`, `getTelemetryScheduler()`, `getSender()`, `getSessionId()`.
  - `close()` (do `implements AutoCloseable`) → gọi `stop()`.
- **Được gọi ở đâu:** `AgentMain.main` (tạo, `start()`, và từ shutdown hook `stop()`). Các getter `getTelemetryScheduler()`, `getSender()`, `getSessionId()` hiện **chưa tìm thấy nơi gọi** ngoài class (chưa xác định — nhiều khả năng dành cho test/tương lai).
- **Quan hệ:** `implements AutoCloseable`; phụ thuộc gần như mọi thành phần còn lại của module + `JsonMessageCodec`.
- **Ghi chú:**
  - `ConfigUpdateService` được tạo ngay trước `TcpControlServer` bằng lambda; lambda tham chiếu `telemetryScheduler`/`heartbeatScheduler` mới chỉ được gán **sau** — an toàn vì listener chỉ chạy khi có CONFIG_UPDATE thật sự (lúc đó hai scheduler đã tồn tại).
  - Các thread nền (1 thread accept, tối đa 8 thread session, 2 thread scheduler) đều là daemon, nên JVM thoát được khi main thread kết thúc.

### 3.5 SystemMetrics

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\metrics\SystemMetrics.java`
- **Vai trò:** mẫu (sample) bất biến của đúng 6 chỉ số mà agent báo cáo.
- **Thành phần chính (7 trường `final` + getter):**
  - `timestamp` (long, mili giây) — thời điểm lấy mẫu.
  - `cpuUsage`, `memoryUsage` (double, %).
  - `connectionCount` (int) — số TCP established.
  - `uptimeSeconds` (long).
  - `networkInBytesPerSecond`, `networkOutBytesPerSecond` (double, byte/giây).
  - `toString()` — `Locale.ROOT`: `"cpu=%.1f%% mem=%.1f%% conn=%d uptime=%ds in=%.0fB/s out=%.0fB/s"`.
- **Được gọi ở đâu:** `OshiSystemMetricsCollector.collect()` (tạo), `TelemetryScheduler.tick()` (đọc để điền vào `TelemetryPacket`), test `AgentMetricsTest`.
- **Quan hệ:** class `final`, không kế thừa; không setter.
- **Ghi chú:** không validate trong constructor — việc đảm bảo giá trị hợp lệ thuộc về collector (`ValidationUtil.clampPercentage`).

### 3.6 SystemMetricsCollector (interface)

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\metrics\SystemMetricsCollector.java`
- **Vai trò:** nguồn chỉ số hệ thống, để scheduler test được mà không đụng phần cứng thật (Javadoc của interface).
- **Thành phần chính:**
  - `SystemMetrics collect()` — lấy một mẫu; hợp đồng: **không được ném exception** khi một chỉ số không tồn tại trên nền tảng, phải trả giá trị an toàn.
  - `void describe(AgentInfoMessage)` — điền các trường mô tả máy vào thông điệp AGENT_INFO_RESPONSE.
  - `void close()` (ghi đè của `AutoCloseable.close()`).
- **Được gọi ở đâu:** `AgentRuntime` (khởi tạo thể thể `OshiSystemMetricsCollector`, truyền cho sender/scheduler/TCP server), `TelemetryScheduler.tick()`, `TcpClientSessionHandler.buildAgentInfo()`, test `AgentMetricsTest`, `TcpIntegrationTest` (monitor).
- **Quan hệ:** `extends AutoCloseable`; hiện thực duy nhất trong dự án: `OshiSystemMetricsCollector`.

### 3.7 OshiSystemMetricsCollector

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\metrics\OshiSystemMetricsCollector.java`
- **Vai trò:** đọc 6 chỉ số từ hệ điều hành qua OSHI; mọi đọc đều được bọc try/catch để một chỉ số lỗi không làm sập agent.
- **Thành phần chính:**
  - Hằng: `INTERFACE_REFRESH_MS = 60_000L` (danh sách NIC làm lại mỗi 60 giây để phát hiện NIC mới), `WARN_INTERVAL_MS = 60_000L` (log cảnh báo tối đa mỗi 60 giây, tránh spam).
  - Trường OSHI: `systemInfo`, `hardware`, `operatingSystem`, `processor`, `memory`.
  - `inboundTrackers` / `outboundTrackers` — `Map<String, NetworkRateTracker>` theo **tên interface**, mỗi chiều một tracker.
  - `previousCpuTicks` (baseline CPU), `interfaces`, `interfacesRefreshedAt`, `lastWarnAt`.
  - `collect()` — trả `new SystemMetrics(now, cpu, mem, conn, uptime, in, out)`.
  - `readCpuUsage()` — `getSystemCpuLoadBetweenTicks(previousCpuTicks)*100`, lưu lại ticks cho lần sau, `clampPercentage`; lỗi → `0.0`.
  - `readMemoryUsage()` — `(total - available) * 100 / total`, clamp; `total <= 0` → `0.0`.
  - `readConnectionCount()` — `TCPv4.connectionsEstablished + TCPv6.connectionsEstablished`; giá trị âm → 0, chặn tràn int; lỗi → 0.
  - `readUptimeSeconds()` — `max(0, operatingSystem.getSystemUptime())`.
  - `readNetworkRate(now, inbound)` — nếu quá 60 giây thì `refreshInterfaces`; với mỗi NIC: `updateAttributes()`, lấy `getBytesRecv()`/`getBytesSent()`, cộng dồn rate của từng tracker; `trackers.keySet().retainAll(present)` loại tracker của NIC đã biến mất; lỗi tổng → trả `sumLastRates(trackers)` (tổng rate lần đọc trước).
  - `refreshInterfaces()` — `hardware.getNetworkIFs()` (phiên bản không đối số, Javadoc: bỏ loopback và NIC đang down); lỗi → danh sách rỗng.
  - `describe(AgentInfoMessage)` — `osName = OS family`, `osVersion`, `cpuModel`, `cpuLogicalCores`, `totalMemoryBytes`, `uptimeSeconds`.
  - `warnOccasionally(msg, cause)` — chỉ log WARNING khi đã qua `WARN_INTERVAL_MS`.
  - `close()` — xóa danh sách NIC và 2 map tracker.
- **Được gọi ở đâu:** `AgentRuntime.start()` (tạo và truyền đi), test `AgentMetricsTest`, monitor-app test `TcpIntegrationTest`.
- **Quan hệ:** `implements SystemMetricsCollector`; phụ thuộc `oshi-core`, `AgentInfoMessage`, `ValidationUtil`.
- **Ghi chú:**
  - Rate tính **theo từng interface rồi mới cộng**, không tính trên tổng bộ đếm — lý do trong Javadoc: NIC xuất hiện sẽ làm tổng nhảy vọt bằng toàn bộ lưu lượng từ lúc boot; tính riêng thì NIC mới chỉ tự tạo baseline và báo 0 cho tới mẫu thứ hai.
  - Lỗi từng NIC chỉ log mức FINE và bỏ qua (NIC có thể biến mất giữa lúc liệt kê và lúc đọc).

### 3.8 NetworkRateTracker

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\metrics\NetworkRateTracker.java`
- **Vai trò:** biến bộ đếm byte tăng dần của OS thành tốc độ `rate = (current - previous) / elapsedSeconds`.
- **Thành phần chính:**
  - `previousBytes = -1L` (đánh dấu chưa có baseline), `previousNanos`, `lastRate`.
  - `update(long totalBytes, long nowNanos)` → trả byte/giây, xử lý 5 trường hợp:
    1. `totalBytes < 0` → trả `lastRate` cũ (giữ rate gần nhất).
    2. Chưa có baseline (`previousBytes < 0`) → lưu baseline, trả `0.0` (không phải "mọi lưu lượng từ lúc boot đến trong 1 giây").
    3. `elapsedNanos <= 0` → trả `lastRate` (tránh chia cho 0 / đồng hồ lùi).
    4. `deltaBytes < 0` (counter reset) → re-baseline âm thầm, trả `0.0`.
    5. Bình thường → `delta / (elapsedNanos/1e9)`.
  - `getLastRate()`, `hasBaseline()` (`previousBytes >= 0`), `reset()` (về trạng thái chưa có baseline).
- **Được gọi ở đâu:** `OshiSystemMetricsCollector` (2 map tracker), test `NetworkRateTrackerTest` (8 test case bao phủ đủ các nhánh trên).
- **Quan hệ:** không kế thừa; không phụ thuộc module nào khác.
- **Ghi chú:** thời gian lấy từ `System.nanoTime()` (đồng hồ monotonic — đơn điệu, không bị chỉnh lại bởi đồng hồ hệ thống) nên chỉnh giờ_wall clock không tạo spike.

### 3.9 TcpControlServer

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\network\TcpControlServer.java`
- **Vai trò:** mở cổng TCP cho Monitor kết nối điều khiển; 1 thread accept + pool hạn chế cho các phiên.
- **Thành phần chính:**
  - `MAX_SESSIONS = 8` — pool cố định 8 thread; Javadoc: Monitor giữ một phiên, "vài chục" đủ cho retry và nhiều client.
  - Trường: `config`, `collector`, `configUpdateService`, `codec`, `agentVersion`, `sessionPool`, `acceptThread` (daemon, tên `agent-tcp-accept`), `volatile ServerSocket serverSocket`, `volatile boolean running`.
  - `start()` — `new ServerSocket(config.getAgentTcpPort())`, `running = true`, `acceptThread.start()`, log "TCP control server listening on port N".
  - `acceptLoop()` — `while (running)` → `accept()` → `sessionPool.submit(new TcpClientSessionHandler(...))`; `IOException` khi `running` log WARNING và **vòng lặp tiếp tục** (một client hỏng không làm sập kênh điều khiển); `RuntimeException` (vd: submit bị từ chối khi pool đang tắt) cũng chỉ log.
  - `isRunning()`, `getPort()` (lấy port thật từ socket nếu đã bind).
  - `close()` — `running = false`, đóng socket ngắt `accept()`, `sessionPool.shutdownNow()`, chờ tối đa 2 giây (đủ thì log, không đủ log WARNING "Some control sessions did not stop within 2 seconds").
- **Được gọi ở đâu:** `AgentRuntime.start()`/`stop()`; monitor-app test `TcpIntegrationTest` (chạy server thật để test phía client TCP).
- **Quan hệ:** `implements AutoCloseable`; tạo `TcpClientSessionHandler`; phụ thuộc `AgentConfig`, `SystemMetricsCollector`, `ConfigUpdateService`, `JsonMessageCodec`.
- **Ghi chú:** thread session tên `agent-tcp-session-<n>`, tất cả daemon.

### 3.10 TcpClientSessionHandler

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\network\TcpClientSessionHandler.java`
- **Vai trò:** phục vụ trọn vòng đời một kết nối TCP: đọc frame JSON theo độ dài (framing), chuyển yêu cầu thành phản hồi, mọi lỗi đều bị cô lập trong socket này.
- **Thành phần chính:**
  - `SESSION_READ_TIMEOUT_MS = 120_000` — phiên im lặng quá 2 phút thì bị đóng (`SoTimeout`).
  - `run()` — đặt `SoTimeout` + `TcpNoDelay(true)` (bỏ chặn nhỏ — Nagle algorithm), lặp: `MessageFraming.readFrame(in)` != null → `handle(request)` → `writeFrame(out, response)`; kết thúc bởi `null` (peer đóng), `SocketTimeoutException`, `IOException`, `RuntimeException`; `finally` → `closeQuietly()`.
  - `handle(String)` — `codec.peekType(request)`:
    | Loại vào | Phản hồi |
    |----------|----------|
    | `PING` | `PONG` (JSON `{type, agentId, timestamp}`) |
    | `AGENT_INFO_REQUEST` | `AGENT_INFO_RESPONSE` (`buildAgentInfo()`) |
    | `CONFIG_UPDATE` | deserialize `ConfigUpdateMessage` → `configUpdateService.apply()` → `CONFIG_ACK` |
    | loại khác trong enum | `ERROR` "Message type X is not accepted by an agent" |
    | không nhận diện được | `ERROR` "Unrecognised or malformed control message" |
    | `ProtocolException` | `ERROR` "Malformed X request" |
    | `RuntimeException` | `ERROR` "Agent failed to handle X" |
  - `buildAgentInfo()` — điền agentId/agentName/hostname/metricIntervalMs/heartbeatIntervalMs/agentVersion rồi `collector.describe(info)` (thông tin OS, CPU, RAM).
  - `simple(MessageType)` — map `{type, agentId, timestamp}`; `error(String)` — thêm khóa `message`.
  - `closeQuietly()` — đóng socket, lỗi chỉ log FINE.
- **Được gọi ở đâu:** duy nhất bởi `TcpControlServer.acceptLoop()` (mỗi kết nối một Runnable được submit vào pool).
- **Quan hệ:** `implements Runnable`; phụ thuộc `ConfigUpdateService`, `SystemMetricsCollector`, `JsonMessageCodec`, `MessageFraming`, các model `AgentInfoMessage`/`ConfigUpdateMessage`/`ConfigAckMessage`.
- **Ghi chú:** phía monitor gửi đúng 3 loại này qua `AgentTcpConnection` (`ping()`, `fetchAgentInfo()`, và gửi `CONFIG_UPDATE`); các model/MessageType đều từ `monitoring-common`.

### 3.11 UdpTelemetrySender

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\network\UdpTelemetrySender.java`
- **Vai trò:** mã hóa JSON và gửi datagram UDP tới Monitor; gián đoạn gửi không bao giờ lan vào scheduler.
- **Thành phần chính:**
  - Trường: `monitorHost`, `monitorPort`, `codec`, `socket = new DatagramSocket()`, `volatile InetAddress resolvedAddress` (cache), `LongAdder sentCount` / `failedCount` — Javadoc: hai scheduler dùng chung sender nên bộ đếm phải thread-safe.
  - `send(Object)` → `boolean`:
    1. `codec.toBytes(message)`;
    2. nếu `payload.length > ProtocolConstants.MAX_UDP_PACKET_SIZE` (4096 byte) → log WARNING "Refusing to send an oversized datagram", tăng `failedCount`, trả `false`;
    3. `address()` — resolve lần đầu, các lần sau dùng cache;
    4. `socket.send(...)` → tăng `sentCount`, trả `true`;
    5. mọi `IOException|RuntimeException` → tăng `failedCount`, **đặt `resolvedAddress = null`** (để lần sau resolve lại — Monitor đổi địa chỉ thì bắt được), log WARNING, trả `false`.
  - `getSentCount()`, `getFailedCount()`, `getTarget()` → `"host:port"`.
  - `close()` — đóng socket.
- **Được gọi ở đâu:** `AgentRuntime` (tạo, truyền vào 2 scheduler, `close()` khi stop); `TelemetryScheduler.tick()`, `HeartbeatScheduler.tick()`; getter `getSender()` của `AgentRuntime` chưa thấy nơi gọi ngoài class.
- **Quan hệ:** `implements AutoCloseable`; phụ thuộc `JsonMessageCodec`, `ProtocolConstants`.
- **Ghi chú:** chọn UDP là cố ý (Javadoc): gói nhỏ, gửi mỗi giây, mất gói thì mẫu sau thay thế — chi phí gửi lại (retransmission) còn lớn hơn giá trị của nó.

### 3.12 ConfigUpdateService

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\service\ConfigUpdateService.java`
- **Vai trò:** validate rồi áp dụng `CONFIG_UPDATE`, dựng `CONFIG_ACK` trả về cùng kết nối TCP.
- **Thành phần chính:**
  - Interface lồng `@FunctionalInterface ConfigChangeListener { void onConfigChanged(AgentConfig config); }`.
  - `apply(ConfigUpdateMessage)` (`synchronized`) — thứ tự:
    1. `update == null` → `STATUS_ERROR` "Empty configuration message" + `fillEffective`.
    2. Sao chép `requestId`; `validate(update)` → nếu có lỗi: `STATUS_ERROR`, gộp lỗi bằng `"; "`, log WARNING, **không áp dụng gì** (all-or-nothing).
    3. Với mỗi trường không null → setter vào `AgentConfig` và ghi vào danh sách `applied` (6 trường: `metricIntervalMs`, `heartbeatIntervalMs`, `cpuThreshold`, `memoryThreshold`, `networkInThresholdMbps`, `networkOutThresholdMbps`).
    4. Không trường nào được sửa → SUCCESS "No change requested; current configuration kept" và **không** gọi listener; có sửa → SUCCESS "Applied k=v, ..." , gọi `listener.onConfigChanged(config)`, log INFO.
    5. `fillEffective(ack)` — điền 6 giá trị hiện hành vào ACK.
  - `validate` → danh sách lỗi, dùng: `checkInterval` (min..`MAX_INTERVAL_MS`), `checkPercentage` (finite, `> 0` và `<= 100`), `checkRate` (finite, `>= 0`); field null = bỏ qua ("leave unchanged").
- **Được gọi ở đâu:** `TcpClientSessionHandler.handle()` case `CONFIG_UPDATE`; `AgentRuntime` tạo với listener (listener gọi `telemetryScheduler.reschedule()` + `heartbeatScheduler.reschedule()`); test `ConfigUpdateServiceTest`; monitor-app test `TcpIntegrationTest`.
- **Quan hệ:** giữ `AgentConfig` + `ConfigChangeListener`; phụ thuộc model `ConfigUpdateMessage`/`ConfigAckMessage`, `ProtocolConstants`, `ValidationUtil`.
- **Ghi chú:**
  - Threshold percent `0.0` bị từ chối (test: "a 0% threshold would fire permanently and is refused"); `NaN` bị từ chối qua `isFinite`.
  - Ngưỡng áp dụng giống `AgentConfigLoader` (interval ≥ 200/1000 ms, ≤ 3 600 000 ms).

### 3.13 TelemetryScheduler

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\service\TelemetryScheduler.java`
- **Vai trò:** lấy mẫu và gửi TelemetryPacket đã ký theo chu kỳ cấu hình.
- **Thành phần chính:**
  - Trường: `config`, `collector`, `sender`, `executor`, `sequence` (dùng chung với heartbeat), `sessionId`; `volatile ScheduledFuture<?> task`, `volatile long currentIntervalMs`, `volatile SystemMetrics lastSample`.
  - `start()` → `schedule(config.getMetricIntervalMs())`.
  - `reschedule()` (`synchronized`) — nếu interval mong muốn đã bằng `currentIntervalMs` và task còn sống thì bỏ qua; ngược lại xếp lại lịch và log "Telemetry interval is now N ms".
  - `schedule(long)` — `cancel()` rồi `executor.scheduleAtFixedRate(this::tick, 0L, intervalMs, MILLISECONDS)` (**delay lần đầu = 0**).
  - `tick()` — **không được ném exception** (Javadoc: task thoát exception sẽ bị `ScheduledExecutorService` hủy âm thầm → agent ngừng báo cáo mà không có lỗi hiển hiện); nội dung:
    1. `metrics = collector.collect()`; `lastSample = metrics`;
    2. dựng `TelemetryPacket`: `protocolVersion = "1.0"`, `type = "TELEMETRY"`, `agentId`, `hostname`, `sessionId`, `timestamp = metrics.getTimestamp()`, `sequence = sequence.incrementAndGet()`, 6 chỉ số;
    3. `PacketSigner.sign(packet, config.getSharedSecret())`;
    4. `sender.send(packet)`;
    5. bắt `RuntimeException` → log WARNING "Telemetry tick failed".
  - `getLastSample()`, `getCurrentIntervalMs()`, `cancel()` (`synchronized`, `task.cancel(false)` — không ngắt tick đang chạy).
- **Được gọi ở đâu:** `AgentRuntime.start()` (tạo + `start()`), `AgentRuntime.stop()` (`cancel()`), listener của `ConfigUpdateService` (`reschedule()`). `getLastSample()` chưa thấy nơi gọi ngoài class.
- **Quan hệ:** không kế thừa; dùng `PacketSigner` (monitoring-common) để ký.
- **Ghi chú:** `sequence` là `AtomicLong` **dùng chung** với `HeartbeatScheduler` vì Monitor giữ một bộ đếm chống replay duy nhất cho mỗi agent, phủ cả hai loại gói.

### 3.14 HeartbeatScheduler

- **Định nghĩa:** `agent-app\src\main\java\vn\edu\hutech\group10\monitoring\agent\service\HeartbeatScheduler.java`
- **Vai trò:** gửi HeartbeatPacket đã ký theo chu kỳ.
- **Thành phần chính:** cấu trúc gần giống `TelemetryScheduler`:
  - `start()` → `schedule(config.getHeartbeatIntervalMs())`.
  - `reschedule()` (`synchronized`) — điều kiện bỏ qua giống hệt telemetry.
  - `schedule(long)` — `scheduleAtFixedRate(this::tick, intervalMs, intervalMs, ...)` (**delay lần đầu = interval**, khác telemetry là 0).
  - `tick()` — dựng `HeartbeatPacket`: `protocolVersion`, `type = "HEARTBEAT"`, `agentId`, `hostname`, `sessionId`, `timestamp = System.currentTimeMillis()` (không lấy từ collector), `sequence = sequence.incrementAndGet()`; `PacketSigner.sign` → `sender.send`; bắt `RuntimeException` → log WARNING.
  - `getCurrentIntervalMs()`, `cancel()`.
- **Được gọi ở đâu:** `AgentRuntime.start()`/`stop()` và listener `ConfigUpdateService` (reschedule) — cùng vị trí với `TelemetryScheduler`.
- **Quan hệ:** không kế thừa; dùng chung `UdpTelemetrySender`, `AtomicLong sequence`, `sessionId`.
- **Ghi chú:** lý do tồn tại (Javadoc): telemetry đã chứng minh agent sống, nhưng heartbeat rẻ hơn nhiều và **vẫn đến ngay cả khi thu thập chỉ số gặp trouble** → Monitor phân biệt được "không có metric" với "máy đã chết" (Monitor dùng `heartbeat.timeout` với mặc định `DEFAULT_HEARTBEAT_TIMEOUT_MS = 15 000` ms — xem `MonitorConfigLoader.fromProperties`).

---

## 4. Sơ đồ quan hệ

```
                         (module: agent-app)
                                  |
   java -jar ... --config=...     |
              |                   v
         +----v------+      +------------------+
         | AgentMain |----->| AgentConfigLoader|---- đọc ----> config/agent01.properties
         +----+------+      +--------+---------+
              |                      | new AgentConfig
              | new                  v
              |            +-------------------+
              +----------->|   AgentRuntime    |  implements AutoCloseable
                           +---+---+---+---+---+
                               |   |   |   |
        +----------------------+   |   |   +-----------------------+
        |                          |   |                           |
        v                          v   v                           v
+--------------------+   +---------------------+   +---------------------------+
| OshiSystemMetrics- |   | UdpTelemetrySender  |   | TcpControlServer          |
| Collector          |   | (JSON -> UDP)       |   | (1 accept + pool 8)       |
| implements         |   +----+-----------+----+   +-------------+-------------+
| SystemMetrics-     |        ^           ^                      | mỗi kết nối
| Collector          |        |           |                      v
+----+----------+----+   Telemetry    Heartbeat       +-------------------------+
     |          |        Scheduler    Scheduler        | TcpClientSessionHandler|
     |collect() |        (service)    (service)        | implements Runnable     |
     v          |             |            |           +-----------+-------------+
+------------+  |             |            |                       |
|SystemMetrics| |             |            |                       | CONFIG_UPDATE
| (immutable) | |             |            |                       v
+------------+  |             |            |           +-------------------------+
                |             |            |           | ConfigUpdateService     |
+---------------------+      |            |           |  + ConfigChangeListener |
| NetworkRateTracker  |<-----+            |           +-----------+-------------+
| (1 NIC x 1 chiều)  |   PacketSigner.sign                | onConfigChanged()
+---------------------+   + send                            v
                                                 telemetryScheduler.reschedule()
                                                 heartbeatScheduler.reschedule()

  Phần dùng chung (monitoring-common): JsonMessageCodec, MessageFraming, MessageType,
  ProtocolConstants, PacketSigner/PacketCanonicalizer/HmacUtil, AgentInfoMessage,
  ConfigUpdateMessage, ConfigAckMessage, TelemetryPacket, HeartbeatPacket,
  AppPaths, LoggingSetup, ValidationUtil.

  Phía Monitor (monitor-app): UdpTelemetryReceiver -> TelemetryService -> PacketValidator
  (kiểm tra HMAC + chống replay); AgentTcpConnectionManager/AgentTcpConnection -> PING/
  AGENT_INFO_REQUEST/CONFIG_UPDATE tới TcpControlServer.
```

---

## 5. Test của module

| Test | Đường dẫn | Các case chính |
|------|-----------|----------------|
| `AgentConfigLoaderTest` | `agent-app\src\test\java\vn\edu\hutech\group10\monitoring\agent\config\AgentConfigLoaderTest.java` | `readsEveryProperty`, `appliesDefaults`, `rejectsMissingMandatoryProperties`, `rejectsOutOfRangeValues` (port 70000, interval 10, CPU 150%, port "not-a-port"), `parsesDecimalsLocaleIndependently` (Locale.GERMANY), `loadsFromDisk` (@TempDir), `bannerHidesTheSecret` |
| `AgentMetricsTest` | `...\agent\metrics\AgentMetricsTest.java` | `collectsPlausibleMetrics` (lấy mẫu đầu chỉ để thiết lập baseline), `repeatedCollectionIsStable` (5 lần), `describesTheMachine`, `realSampleProducesAVerifiablePacket` (so HMAC khớp `HmacUtil.hmacSha256Hex(secret, PacketCanonicalizer.canonicalize(packet))`) |
| `NetworkRateTrackerTest` | `...\agent\metrics\NetworkRateTrackerTest.java` | 8 case: baseline đầu, delta/time, scale theo elapsed, counter reset, elapsed ≤ 0, counter âm, idle = 0, reset() |
| `ConfigUpdateServiceTest` | `...\agent\service\ConfigUpdateServiceTest.java` | 7 case: áp dụng + listener, field null giữ nguyên, interval quá nhỏ, all-or-nothing, NaN/âm/0%, update rỗng, message null |

Ngoài ra, `monitor-app\src\test\...\network\TcpIntegrationTest.java` khởi tạo lại `AgentConfigLoader`, `OshiSystemMetricsCollector`, `ConfigUpdateService`, `TcpControlServer` để test phía client TCP của Monitor.

---

## 6. Cấu hình mẫu thật (`config\agent01.properties`)

| Khóa | Giá trị | Ý nghĩa |
|------|---------|---------|
| `agent.id` | `AGENT-01` | Danh tính agent, xuất hiện trong mọi gói |
| `agent.name` | `LOCAL-AGENT-01` | Tên hiển thị |
| `monitor.host` | `127.0.0.1` | Địa chỉ Monitor (demo chạy cùng máy) |
| `monitor.udp.port` | `6000` | Cổng UDP Monitor nghe |
| `agent.tcp.port` | `5001` | Cổng TCP agent nghe (4 agent cùng máy phải khác nhau: 5001..5004; mỗi máy riêng thì đều dùng 5000) |
| `metric.interval.ms` | `1000` | Chu kỳ telemetry |
| `heartbeat.interval.ms` | `5000` | Chu kỳ heartbeat |
| `security.sharedSecret` | `GROUP10_DEMO_SECRET_01` | Khóa bí mật chung để ký HMAC; phải khớp secret đã đăng ký cho `AGENT-01` ở Monitor; mỗi agent một secret riêng để rò rỉ một secret không mạo danh được agent khác |
| `alert.cpu.threshold` | `85.0` | Ngưỡng CPU cục bộ (%) |
| `alert.memory.threshold` | `85.0` | Ngưỡng RAM cục bộ (%) |
| `alert.network.in.threshold.mbps` | `50.0` | Ngưỡng in (Mbps) |
| `alert.network.out.threshold.mbps` | `50.0` | Ngưỡng out (Mbps) |

Các file cùng thư mục: `agent02.properties`, `agent03.properties`, `agent04.properties` (bản sao với id/secret/cổng TCP riêng).

---

## 7. Điểm chưa xác định

- `AgentRuntime.getSender()`, `getTelemetryScheduler()`, `getSessionId()` và `TelemetryScheduler.getLastSample()`: chưa tìm thấy lời gọi ngoài class sở hữu — mục đích dự kiến (test? mở rộng?) chưa xác định.
- `AgentMain.resolveConfigPath` có visibility package-private (Javadoc mô tả 3 dạng tham số) nhưng chưa có test gọi trực tiếp trong 4 test đã đọc — chưa xác định có kế hoạch test riêng hay không.
- Không tìm thấy tài liệu nào của dự án mô tả hành vi khi `monitor.udp.port` của agent trỏ sai cổng (chỉ xác định được code log WARNING và tăng `failedCount`).
