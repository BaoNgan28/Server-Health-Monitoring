# Tổng quan các class trong hệ thống

Dự án ServerHealthMonitoring là hệ thống giám sát server từ xa, xây dựng theo Maven multi-module với Java 17, gồm 4 module: `monitoring-common` (module dùng chung), `agent-app` (tiến trình chạy trên máy được giám sát), `monitor-app` (ứng dụng Swing phía người theo dõi) và `simulator-app` (công cụ tạo lưu lượng kiểm thử). Toàn bộ class chính của hệ thống được mô tả trong 6 file tài liệu trong thư mục này, tổng cộng **100 class** (kiểm lại đúng theo bảng tổng quan của từng file: monitoring-common 27 + agent-app 14 + monitor-app phần 1: 24 + monitor-app phần 2: 14 + monitor-app phần 3: 13 + simulator-app 8 = 100).

Lưu ý về cách đếm: 13 class của `monitor-app` phần 3 nằm trong 11 file nguồn — 2 class là class lồng nhau (`MainFrame.UiEventListener` và `AlertPanel.AlertTableModel`) được tính riêng theo bảng của file `05-monitor-app-phan-3.md`. Nếu chỉ đếm theo file nguồn thì tổng sẽ là 98; tài liệu này theo cách đếm class (100) như đã nêu.

## 1. Bảng phân bổ theo module/package

| Module | Package (viết tắt, gốc `vn.edu.hutech.group10.monitoring...`) | Số class | File tài liệu | Vai trò |
|---|---|---|---|---|
| monitoring-common | `.common.model` | 10 | [01-monitoring-common.md](01-monitoring-common.md) | Model message (POJO) trao đổi giữa các tiến trình: gói UDP, gói điều khiển TCP, domain cảnh báo |
| monitoring-common | `.common.protocol` | 5 | [01-monitoring-common.md](01-monitoring-common.md) | "Dây dẫn" chung: hằng số giao thức, loại message, mã hóa JSON, khung hóa TCP, exception protocol |
| monitoring-common | `.common.security` | 7 | [01-monitoring-common.md](01-monitoring-common.md) | Ký và kiểm tra HMAC-SHA256, chuỗi chuẩn, chống replay, cung cấp secret |
| monitoring-common | `.common.util` | 5 | [01-monitoring-common.md](01-monitoring-common.md) | Tiện ích kiểm tra dữ liệu, định dạng thời gian/lưu lượng, cấu hình log, đường dẫn thư mục |
| agent-app | `.agent` (điểm vào) | 1 | [02-agent-app.md](02-agent-app.md) | Entry point console `AgentMain` |
| agent-app | `.agent.config` | 2 | [02-agent-app.md](02-agent-app.md) | Object cấu hình runtime và loader đọc file `.properties` có kiểm tra |
| agent-app | `.agent.core` | 1 | [02-agent-app.md](02-agent-app.md) | `AgentRuntime` sở hữu toàn bộ thành phần, thứ tự khởi động/dừng và rollback |
| agent-app | `.agent.metrics` | 4 | [02-agent-app.md](02-agent-app.md) | Thu thập 6 chỉ số hệ thống qua OSHI, DTO mẫu chỉ số, tính tốc độ mạng |
| agent-app | `.agent.network` | 3 | [02-agent-app.md](02-agent-app.md) | Gửi UDP telemetry/heartbeat, server TCP điều khiển, xử lý từng phiên TCP |
| agent-app | `.agent.service` | 3 | [02-agent-app.md](02-agent-app.md) | Lập lịch gửi telemetry/heartbeat và áp dụng cập nhật cấu hình |
| monitor-app | `.monitor` (điểm vào) | 1 | [03-monitor-app-phan-1.md](03-monitor-app-phan-1.md) | Entry point `MonitoringMain` |
| monitor-app | `.monitor.config` | 4 | [03-monitor-app-phan-1.md](03-monitor-app-phan-1.md) | Cấu hình monitor (production/demo) và cấu hình SMTP email |
| monitor-app | `.monitor.core` | 1 | [03-monitor-app-phan-1.md](03-monitor-app-phan-1.md) | `MonitorApplication` — composition root, ghép nối và vòng đời toàn ứng dụng |
| monitor-app | `.monitor.model` | 6 | [03-monitor-app-phan-1.md](03-monitor-app-phan-1.md) | Model nội bộ: record agent, runtime state, snapshot, mẫu metric, bucket gộp, điểm biểu đồ |
| monitor-app | `.monitor.network` | 3 | [03-monitor-app-phan-1.md](03-monitor-app-phan-1.md) | Nhận UDP telemetry, kết nối TCP điều khiển tới từng agent |
| monitor-app | `.monitor.notification` | 9 | [03-monitor-app-phan-1.md](03-monitor-app-phan-1.md) | Kênh gửi cảnh báo: interface Notifier, email SMTP, email message, kết quả gửi |
| monitor-app | `.monitor.repository` | 5 | [04-monitor-app-phan-2.md](04-monitor-app-phan-2.md) | Tầng SQLite: quản lý kết nối/schema và 3 repository agents/alerts/metrics |
| monitor-app | `.monitor.service` | 9 | [04-monitor-app-phan-2.md](04-monitor-app-phan-2.md) | Nghiệp vụ: xử lý telemetry, heartbeat, cảnh báo, gộp dữ liệu, lịch sử, quản lý agent, event bus |
| monitor-app | `.monitor.ui` | 13 | [05-monitor-app-phan-3.md](05-monitor-app-phan-3.md) | Giao diện Swing: 5 tab chính, 2 hộp thoại, TableModel, hằng số màu/phông (11 file, 13 class gồm 2 class lồng) |
| simulator-app | `.simulator` | 8 | [06-simulator-app.md](06-simulator-app.md) | 4 mode mô phỏng (valid / invalid-hmac / replay / load) + options, factory gói, sender UDP |
| **Tổng** | | **100** | | |

## 2. Bảng tất cả class (100 dòng)

Sắp theo module (`monitoring-common` → `agent-app` → `monitor-app` → `simulator-app`), trong mỗi module sắp theo alphabetical.

| # | Class | Module | Vai trò (1 câu) | File tài liệu |
|---|-------|--------|-----------------|---------------|
| 1 | `AgentInfoMessage` | monitoring-common | Thông tin mô tả agent và máy chủ (OS, CPU, RAM...) gửi qua TCP (`AGENT_INFO_RESPONSE`). | [01](01-monitoring-common.md) |
| 2 | `AgentStatus` | monitoring-common | Enum trạng thái agent quan sát được: `UNKNOWN`/`ONLINE`/`WARNING`/`OFFLINE`. | [01](01-monitoring-common.md) |
| 3 | `AlertEvent` | monitoring-common | Một lần xảy ra cảnh báo: mở (`OPEN`) khi vi phạm lần đầu, đóng (`RESOLVED`) khi hết. | [01](01-monitoring-common.md) |
| 4 | `AlertSeverity` | monitoring-common | Enum mức nghiêm trọng của cảnh báo (`INFO`/`WARNING`/`CRITICAL`). | [01](01-monitoring-common.md) |
| 5 | `AlertState` | monitoring-common | Enum trạng thái vòng đời cảnh báo (`OPEN`/`RESOLVED`). | [01](01-monitoring-common.md) |
| 6 | `AlertType` | monitoring-common | Enum 8 loại cảnh báo, kèm nhãn hiển thị cho GUI/e-mail. | [01](01-monitoring-common.md) |
| 7 | `AppPaths` | monitoring-common | Định vị đường dẫn `config/`, `data/`, `logs/` theo working directory hoặc `-Dmonitoring.home`. | [01](01-monitoring-common.md) |
| 8 | `ByteRateFormatter` | monitoring-common | Định dạng lưu lượng byte/giây và đổi giữa B/s ↔ Mbps. | [01](01-monitoring-common.md) |
| 9 | `ConfigAckMessage` | monitoring-common | Agent phản hồi kết quả áp dụng cấu hình (thành công/lỗi + giá trị thực có hiệu lực). | [01](01-monitoring-common.md) |
| 10 | `ConfigUpdateMessage` | monitoring-common | Thông điệp đổi cấu hình Monitor đẩy xuống agent qua TCP; mọi trường đều optional (boxed type). | [01](01-monitoring-common.md) |
| 11 | `HeartbeatPacket` | monitoring-common | Gói tin "còn sống" (heartbeat) nhẹ, agent gửi định kỳ qua UDP. | [01](01-monitoring-common.md) |
| 12 | `HmacUtil` | monitoring-common | Tính HmacSHA256 dạng hex chữ thường và so sánh constant-time (không lộ thời điểm khớp ký tự). | [01](01-monitoring-common.md) |
| 13 | `JsonMessageCodec` | monitoring-common | Mã hóa/giải mã JSON (Gson) và đọc trước trường `type` (`peekType`) để chọn lớp đích. | [01](01-monitoring-common.md) |
| 14 | `LoggingSetup` | monitoring-common | Cấu hình `java.util.logging` 1 lần: console + file xoay vòng trong `logs/`. | [01](01-monitoring-common.md) |
| 15 | `MessageFraming` | monitoring-common | Khung hóa TCP: tiền tố độ dài 4 byte big-endian + payload JSON UTF-8. | [01](01-monitoring-common.md) |
| 16 | `MessageType` | monitoring-common | Enum 9 loại message của cả hai kênh UDP và TCP, wire value là tên hằng. | [01](01-monitoring-common.md) |
| 17 | `PacketCanonicalizer` | monitoring-common | Dựng chuỗi chuẩn (canonical string) cố định thứ tự trường để hai phía ký trên cùng chuỗi. | [01](01-monitoring-common.md) |
| 18 | `PacketSigner` | monitoring-common | Ký (đóng dấu) gói Telemetry/Heartbeat bằng HMAC trước khi gửi — phía gửi. | [01](01-monitoring-common.md) |
| 19 | `PacketValidator` | monitoring-common | Cổng kiểm tra duy nhất mọi gói UDP đi vào Monitor (shape → version/agent → HMAC → timestamp → replay). | [01](01-monitoring-common.md) |
| 20 | `ProtocolConstants` | monitoring-common | Tập hằng số giao thức: phiên bản, cổng 6000/5000, giới hạn kích thước, interval mặc định. | [01](01-monitoring-common.md) |
| 21 | `ProtocolException` | monitoring-common | Exception runtime khi không mã hóa/giải mã/khung hóa được message. | [01](01-monitoring-common.md) |
| 22 | `ReplayProtector` | monitoring-common | Chống phát lại (replay) bằng cửa sổ trượt sequence 64 bit theo từng `(agentId, sessionId)`. | [01](01-monitoring-common.md) |
| 23 | `SecretProvider` | monitoring-common | Interface cung cấp secret (khoá bí mật chung) theo `agentId`; agent chưa đăng ký trả `null`. | [01](01-monitoring-common.md) |
| 24 | `TelemetryPacket` | monitoring-common | Gói tin chỉ số (CPU, RAM, kết nối, uptime, mạng...) agent gửi qua UDP — object trung tâm của hệ thống. | [01](01-monitoring-common.md) |
| 25 | `TimeUtil` | monitoring-common | Định dạng timestamp epoch millis, uptime, duration, khung giờ (bucket). | [01](01-monitoring-common.md) |
| 26 | `ValidationResult` | monitoring-common | Kết quả bất biến "chấp nhận/từ chối" kèm `Reason` và chi tiết, trả về từ `PacketValidator`. | [01](01-monitoring-common.md) |
| 27 | `ValidationUtil` | monitoring-common | Kiểm tra khoảng/giá trị dùng chung: `isBlank`, `isPercentage`, `inRange`, `requireText`... | [01](01-monitoring-common.md) |
| 28 | `AgentConfig` | agent-app | Đối tượng cấu hình runtime của agent; các trường interval/threshold là `volatile` để thay đổi an toàn khi chạy. | [02](02-agent-app.md) |
| 29 | `AgentConfigLoader` | agent-app | Đọc + kiểm tra (validate) file `.properties` thành `AgentConfig`, lỗi phát hiện ngay lúc khởi động. | [02](02-agent-app.md) |
| 30 | `AgentMain` | agent-app | Điểm vào console của agent: đọc `--config`, cấu hình log, nạp cấu hình, khởi động runtime, giữ tiến trình sống. | [02](02-agent-app.md) |
| 31 | `AgentRuntime` | agent-app | Chủ sở hữu toàn bộ thành phần của agent; định nghĩa thứ tự khởi động/ngừng và rollback khi lỗi. | [02](02-agent-app.md) |
| 32 | `ConfigUpdateService` | agent-app | Validate rồi áp dụng `CONFIG_UPDATE` vào `AgentConfig` (all-or-nothing), sinh `CONFIG_ACK`, báo scheduler xếp lại lịch. | [02](02-agent-app.md) |
| 33 | `HeartbeatScheduler` | agent-app | Gói `HeartbeatPacket` (chỉ nhịp tim, không chỉ số) ký HMAC gửi UDP theo chu kỳ. | [02](02-agent-app.md) |
| 34 | `NetworkRateTracker` | agent-app | Chuyển bộ đếm byte tích lũy của NIC thành tốc độ byte/giây, xử lý cả baseline và counter reset. | [02](02-agent-app.md) |
| 35 | `OshiSystemMetricsCollector` | agent-app | Hiện thực interface trên OSHI: đọc CPU, RAM, kết nối, uptime, lưu lượng mạng, mọi đọc đều bọc try/catch. | [02](02-agent-app.md) |
| 36 | `SystemMetrics` | agent-app | DTO bất biến chứa đúng 6 chỉ số của một lần lấy mẫu. | [02](02-agent-app.md) |
| 37 | `SystemMetricsCollector` | agent-app | Interface nguồn chỉ số hệ thống (`collect`/`describe`/`close`), tách phần cài khỏi phần dùng để test được. | [02](02-agent-app.md) |
| 38 | `TcpClientSessionHandler` | agent-app | Xử lý trọn vòng đời một kết nối TCP: đọc frame, trả lời PONG / AGENT_INFO_RESPONSE / CONFIG_ACK / ERROR. | [02](02-agent-app.md) |
| 39 | `TcpControlServer` | agent-app | Server TCP nhận kết nối điều khiển từ Monitor (1 thread accept + pool 8 session). | [02](02-agent-app.md) |
| 40 | `TelemetryScheduler` | agent-app | Lấy mẫu chỉ số định kỳ, đóng gói `TelemetryPacket`, ký HMAC rồi gửi UDP. | [02](02-agent-app.md) |
| 41 | `UdpTelemetrySender` | agent-app | Gói JSON đã mã hóa thành datagram UDP gửi đi; đếm số gói thành công/thất bại, cách ly lỗi mạng khỏi scheduler. | [02](02-agent-app.md) |
| 42 | `AgentDialog` | monitor-app | Hộp thoại modal thêm/sửa agent (9 trường, validate qua service). | [05](05-monitor-app-phan-3.md) |
| 43 | `AgentManagementService` | monitor-app | Toàn bộ thao tác màn hình quản lý agent (thêm/sửa/xoá/kênh TCP/push cấu hình) tách khỏi GUI. | [04](04-monitor-app-phan-2.md) |
| 44 | `AgentRecord` | monitor-app | Bản ghi một Agent đã đăng ký, tương ứng dòng trong bảng `agents`. | [03](03-monitor-app-phan-1.md) |
| 45 | `AgentRegistry` | monitor-app | Bản đồ in-memory các agent, đồng bộ với bảng `agents`; kiêm `SecretProvider` cung cấp bí mật chung để kiểm tra chữ ký. | [04](04-monitor-app-phan-2.md) |
| 46 | `AgentRepository` | monitor-app | Đọc/ghi bảng `agents`, lớp duy nhất biết câu SQL bảng này. | [04](04-monitor-app-phan-2.md) |
| 47 | `AgentRuntimeState` | monitor-app | Trạng thái "sống" của một Agent: record + số liệu từ gói tin được chấp nhận gần nhất. | [03](03-monitor-app-phan-1.md) |
| 48 | `AgentSnapshot` | monitor-app | Ảnh chụp bất biến của một Agent, thứ duy nhất GUI được cầm. | [03](03-monitor-app-phan-1.md) |
| 49 | `AgentTcpConnection` | monitor-app | Một kết nối TCP điều khiển tới một Agent: PING, yêu cầu thông tin, đẩy cấu hình. | [03](03-monitor-app-phan-1.md) |
| 50 | `AgentTcpConnectionManager` | monitor-app | Giữ tối đa một kết nối TCP cho mỗi Agent, cách ly lỗi giữa các Agent. | [03](03-monitor-app-phan-1.md) |
| 51 | `AgentTableModel` | monitor-app | `TableModel` của bảng Dashboard, mỗi hàng là một `AgentSnapshot` bất biến. | [05](05-monitor-app-phan-3.md) |
| 52 | `AlertPanel` | monitor-app | Tab Alerts: bảng lịch sử cảnh báo, lọc "chỉ cảnh báo đang mở". | [05](05-monitor-app-phan-3.md) |
| 53 | `AlertPanel.AlertTableModel` | monitor-app | `TableModel` nội bộ (class lồng trong `AlertPanel`) cho bảng cảnh báo. | [05](05-monitor-app-phan-3.md) |
| 54 | `AlertRepository` | monitor-app | Đọc/ghi bảng `alerts`: lưu sự kiện cảnh báo và trả danh sách cho GUI/`AlertService`. | [04](04-monitor-app-phan-2.md) |
| 55 | `AlertService` | monitor-app | Máy trạng thái cảnh báo: mở/đóng theo ngưỡng, hysteresis, cảnh báo offline/hồi phục, sự kiện bảo mật. | [04](04-monitor-app-phan-2.md) |
| 56 | `AggregationService` | monitor-app | Job định kỳ gộp mẫu cũ thành bucket (`metric_5m`) và xoá dữ liệu quá hạn lưu trữ (retention). | [04](04-monitor-app-phan-2.md) |
| 57 | `CompositeNotifier` | monitor-app | Gửi một cảnh báo tới nhiều kênh, cách ly kênh hỏng. | [03](03-monitor-app-phan-1.md) |
| 58 | `ConfigureAgentDialog` | monitor-app | Hộp thoại modal thu thập cấu hình gửi `CONFIG_UPDATE` cho agent qua TCP. | [05](05-monitor-app-phan-3.md) |
| 59 | `DashboardPanel` | monitor-app | Tab Dashboard: 4 thẻ tổng quan, bảng agent trực tiếp, 6 nút quản lý. | [05](05-monitor-app-phan-3.md) |
| 60 | `DataAccessException` | monitor-app | Exception runtime bọc `SQLException`, cách ly JDBC khỏi service/GUI. | [04](04-monitor-app-phan-2.md) |
| 61 | `DatabaseManager` | monitor-app | Sở hữu kết nối SQLite duy nhất, áp pragma + `db/schema.sql`, cung cấp `execute`/`inTransaction` kèm khoá và retry. | [04](04-monitor-app-phan-2.md) |
| 62 | `EmailConfig` | monitor-app | Cấu hình SMTP cho email cảnh báo, kèm cờ `configured` và lý do tắt email. | [03](03-monitor-app-phan-1.md) |
| 63 | `EmailConfigLoader` | monitor-app | Dựng `EmailConfig` từ biến môi trường hoặc file `config/email-secrets.properties`. | [03](03-monitor-app-phan-1.md) |
| 64 | `EmailMessage` | monitor-app | Email văn bản thuần đã sẵn sàng để chuyển cho tầng vận chuyển. | [03](03-monitor-app-phan-1.md) |
| 65 | `EmailMessageBuilder` | monitor-app | Dựng tiêu đề và nội dung mọi email cảnh báo/email kiểm tra, thuần tính toán, không mạng. | [03](03-monitor-app-phan-1.md) |
| 66 | `EmailNotifier` | monitor-app | Gửi email cảnh báo qua SMTP trên một luồng nền với hàng đợi có giới hạn. | [03](03-monitor-app-phan-1.md) |
| 67 | `HeartbeatMonitor` | monitor-app | Quét định kỳ để chuyển ONLINE → OFFLINE và xử lý hồi phục qua `markAlive`. | [04](04-monitor-app-phan-2.md) |
| 68 | `HistoryPanel` | monitor-app | Tab History: biểu đồ lịch sử theo khoảng thời gian người dùng chọn. | [05](05-monitor-app-phan-3.md) |
| 69 | `HistoryPoint` | monitor-app | Một điểm trên biểu đồ lịch sử, hợp nhất mẫu thô và bucket gộp về cùng một hình dạng. | [03](03-monitor-app-phan-1.md) |
| 70 | `HistoryService` | monitor-app | Trả lời truy vấn lịch sử, tự chọn bảng `metric_raw` hay `metric_5m`. | [04](04-monitor-app-phan-2.md) |
| 71 | `MailTransport` | monitor-app | Interface phần việc thật sự nói chuyện với máy chủ SMTP. | [03](03-monitor-app-phan-1.md) |
| 72 | `MainFrame` | monitor-app | Cửa sổ chính `JFrame`: 5 tab, thanh trạng thái, đăng ký lắng nghe sự kiện. | [05](05-monitor-app-phan-3.md) |
| 73 | `MainFrame.UiEventListener` | monitor-app | Class lồng trong `MainFrame`, hiện thực `MonitorEventListener`: chuyển sự kiện từ service sang EDT và rải về các panel. | [05](05-monitor-app-phan-3.md) |
| 74 | `MetricAggregate` | monitor-app | Một "bucket" (khung giờ gộp) thống kê, tương ứng một dòng bảng `metric_5m`. | [03](03-monitor-app-phan-1.md) |
| 75 | `MetricRepository` | monitor-app | Đọc/ghi bảng `metric_raw` và `metric_5m`, gộp + dọn dữ liệu cũ trong một transaction. | [04](04-monitor-app-phan-2.md) |
| 76 | `MetricSample` | monitor-app | Một mẫu telemetry đã được chấp nhận, tương ứng một dòng bảng `metric_raw`. | [03](03-monitor-app-phan-1.md) |
| 77 | `MonitorApplication` | monitor-app | Lớp lõi (composition root): ghép nối toàn bộ ứng dụng, quản lý vòng đời khởi động/dừng, ép phân tầng giữa GUI và hạ tầng. | [03](03-monitor-app-phan-1.md) |
| 78 | `MonitorConfig` | monitor-app | Object cấu hình bất biến của monitor, mọi thời lượng đã quy về mili giây. | [03](03-monitor-app-phan-1.md) |
| 79 | `MonitorConfigLoader` | monitor-app | Đọc file `monitor.properties` thành `MonitorConfig`, có kiểm tra khoảng và giá trị mặc định; thiếu file vẫn chạy được. | [03](03-monitor-app-phan-1.md) |
| 80 | `MonitorEventBus` | monitor-app | Hàng đợi phát sự kiện (`CopyOnWriteArrayList`) từ service sang listener, cách ly lỗi từng listener. | [04](04-monitor-app-phan-2.md) |
| 81 | `MonitorEventListener` | monitor-app | Interface 5 callback mặc định, nơi GUI lắng nghe sự kiện. | [04](04-monitor-app-phan-2.md) |
| 82 | `MonitoringMain` | monitor-app | Entry point của ứng dụng: đọc tham số dòng lệnh, nạp cấu hình, khởi động lõi, mở cửa sổ GUI. | [03](03-monitor-app-phan-1.md) |
| 83 | `NoOpNotifier` | monitor-app | Kênh "không làm gì" (no-operation) thay cho email khi chưa có thông tin đăng nhập SMTP. | [03](03-monitor-app-phan-1.md) |
| 84 | `NotificationResult` | monitor-app | Kết quả một lần gửi thông báo (thành công/thất bại + thông điệp cho hộp thoại). | [03](03-monitor-app-phan-1.md) |
| 85 | `Notifier` | monitor-app | Interface (giao diện) kênh gửi cảnh báo từ xa. | [03](03-monitor-app-phan-1.md) |
| 86 | `ServerDetailPanel` | monitor-app | Tab Server Detail: chọn agent, 11 ô số liệu, 3 biểu đồ thời gian thực. | [05](05-monitor-app-phan-3.md) |
| 87 | `SettingsPanel` | monitor-app | Tab Settings: trạng thái cấu hình email + thông tin runtime (chỉ đọc). | [05](05-monitor-app-phan-3.md) |
| 88 | `SmtpMailTransport` | monitor-app | Triển khai `MailTransport` thật bằng Jakarta Mail, STARTTLS, có timeout. | [03](03-monitor-app-phan-1.md) |
| 89 | `TelemetryService` | monitor-app | Đường đi duy nhất của mọi Datagram UDP: giải mã → kiểm tra → đăng ký → lưu DB → đánh giá cảnh báo → báo GUI. | [04](04-monitor-app-phan-2.md) |
| 90 | `TimeSeriesChartPanel` | monitor-app | Component biểu đồ thời gian (time series) bọc JFreeChart, có giới hạn số điểm. | [05](05-monitor-app-phan-3.md) |
| 91 | `UiConstants` | monitor-app | Hằng số màu, phông chữ dùng chung + `colorOf(AgentStatus)` — nguồn màu trạng thái duy nhất. | [05](05-monitor-app-phan-3.md) |
| 92 | `UdpTelemetryReceiver` | monitor-app | Lắng nghe cổng UDP, nhận gói telemetry và chuyển cho worker xử lý theo đúng thứ tự từng Agent. | [03](03-monitor-app-phan-1.md) |
| 93 | `InvalidHmacSender` | simulator-app | Mode `invalid-hmac`: 3 kiểu gói gian lận chữ ký phải bị Monitor từ chối. | [06](06-simulator-app.md) |
| 94 | `LoadSimulator` | simulator-app | Mode `load`: nhiều danh tính agent cùng lúc để thử tải phía Monitor. | [06](06-simulator-app.md) |
| 95 | `PacketFactory` | simulator-app | Dựng gói Telemetry/Heartbeat "có vẻ thật", có hoặc chưa ký HMAC. | [06](06-simulator-app.md) |
| 96 | `ReplayPacketSender` | simulator-app | Mode `replay`: gửi một gói hợp lệ rồi gửi lại y hệt (phát lại) để Monitor phải từ chối. | [06](06-simulator-app.md) |
| 97 | `SimulatorMain` | simulator-app | Điểm vào console: đọc mode (`valid`/`invalid-hmac`/`replay`/`load`), phân luồng tới class chế độ tương ứng, in usage. | [06](06-simulator-app.md) |
| 98 | `SimulatorOptions` | simulator-app | Tùy chọn dòng lệnh dùng chung cho cả 4 mode; tự có giá trị mặc định để chạy được ngay. | [06](06-simulator-app.md) |
| 99 | `SimulatorUdpSender` | simulator-app | Người gửi UDP tối giản: JSON hóa và gửi datagram, đếm số gói đã gửi. | [06](06-simulator-app.md) |
| 100 | `ValidAgentSimulator` | simulator-app | Mode `valid`: gửi telemetry + heartbeat ký đúng secret (trường hợp đối chứng). | [06](06-simulator-app.md) |

## 3. Sơ đồ kiến trúc phân tầng

Vai trò của từng nhóm class theo 5 lớp, từ dưới lên (dữ liệu đi từ agent đến màn hình người dùng):

```
 LỚP 1 — THU THẬP (tạo ra chỉ số)          [agent-app/metrics + simulator-app]
 +-------------------------------+        +--------------------------------------+
 | OshiSystemMetricsCollector    |        | PacketFactory (dựng gói giả lập)     |
 |   implements SystemMetrics-   |        | ValidAgentSimulator / InvalidHmac-   |
 |   Collector -> SystemMetrics  |        | Sender / ReplayPacketSender /        |
 | NetworkRateTracker (tốc độ    |        | LoadSimulator                        |
 |   mạng theo từng NIC)         |        | (simulator-app — mô phỏng agent)     |
 +---------------+---------------+        +------------------+-------------------+
                 |                                        |
                 v                                        v
 LỚP 2 — TRUYỀN TẢI (UDP realtime / TCP điều khiển)   [2 module network + common/protocol]
 Gửi đi : TelemetryScheduler, HeartbeatScheduler (agent)
          -> UdpTelemetrySender | SimulatorUdpSender ------> UDP 6000
 Nhận vào: UdpTelemetryReceiver (monitor, 1 thread receive + worker theo agent)
 TCP agent  : TcpControlServer -> TcpClientSessionHandler (PONG/AGENT_INFO/CONFIG_ACK)
 TCP monitor: AgentTcpConnectionManager -> AgentTcpConnection (PING/AGENT_INFO/CONFIG_UPDATE)
 Khung chung: JsonMessageCodec, MessageFraming, MessageType, ProtocolConstants,
              ProtocolException, các model packet/message (monitoring-common)
                 |
                 v
 LỚP 3 — XÁC THỰC (chỉ gói UDP hợp lệ đi tiếp)        [monitoring-common/security]
 Phía gửi  : PacketSigner = PacketCanonicalizer (chuỗi chuẩn) + HmacUtil (ký)
 Phía nhận : PacketValidator (5 bước: shape -> version/agent -> HMAC -> timestamp -> replay)
               + SecretProvider (hiện thực: AgentRegistry) + ReplayProtector
               -> ValidationResult (ACCEPTED / Reason + detail)
                 |
                 v
 LỚP 4 — LƯU TRỮ & CẢNH BÁO                            [monitor-app/repository + service]
 TelemetryService (đường đi duy nhất của gói được chấp nhận)
   -> MetricRepository (metric_raw) / AlertService (máy trạng thái ngưỡng + hysteresis)
 HeartbeatMonitor (ONLINE/OFFLINE) | AggregationService (gộp bucket + retention)
 HistoryService (truy vấn) | AgentRegistry + AgentManagementService
 DatabaseManager (SQLite, 1 writer) -> AgentRepository / MetricRepository / AlertRepository
 Thông báo: Notifier <- CompositeNotifier <- EmailNotifier -> EmailMessageBuilder
            -> MailTransport/SmtpMailTransport (NoOpNotifier khi chưa cấu hình email)
 Sự kiện  : MonitorEventBus -> MonitorEventListener
                 |
                 v
 LỚP 5 — HIỂN THỊ (Swing)                               [monitor-app/ui]
 MainFrame (5 tab + thanh trạng thái) + UiEventListener (EDT)
   |-- DashboardPanel + AgentTableModel      (bảng agent, 4 thẻ, nút quản lý)
   |-- ServerDetailPanel + TimeSeriesChartPanel (3 biểu đồ realtime)
   |-- HistoryPanel                          (biểu đồ lịch sử metric_raw/metric_5m)
   |-- AlertPanel (+ AlertTableModel)        (bảng cảnh báo)
   |-- SettingsPanel                         (trạng thái email + runtime)
   |-- AgentDialog, ConfigureAgentDialog     (hộp thoại modal)
   +-- UiConstants (màu/phông theo AgentStatus)
```

## 4. Hướng đọc chi tiết và quan hệ phụ thuộc giữa các module

### 4.1 Thứ tự đọc 6 file tài liệu

1. [01-monitoring-common.md](01-monitoring-common.md) — 27 class của module gốc: model packet, giao thức (codec/framing), lớp ký và kiểm tra HMAC, tiện ích. Đọc trước vì mọi module còn lại đều dùng các class này.
2. [02-agent-app.md](02-agent-app.md) — 14 class: vòng đời agent từ `AgentMain` → `AgentRuntime`, thu thập chỉ số qua OSHI, gửi UDP, server TCP điều khiển.
3. [03-monitor-app-phan-1.md](03-monitor-app-phan-1.md) — 24 class (phần 1/3): cấu hình, lõi `MonitorApplication`, model nội bộ, nhận UDP/TCP, kênh thông báo email.
4. [04-monitor-app-phan-2.md](04-monitor-app-phan-2.md) — 14 class (phần 2/3): repository SQLite và service nghiệp vụ (telemetry, heartbeat, cảnh báo, aggregation, quản lý agent).
5. [05-monitor-app-phan-3.md](05-monitor-app-phan-3.md) — 13 class (phần 3/3): toàn bộ giao diện Swing (5 tab, 2 hộp thoại, TableModel, hằng số UI).
6. [06-simulator-app.md](06-simulator-app.md) — 8 class: 4 mode mô phỏng (`valid`, `invalid-hmac`, `replay`, `load`) dùng cho kiểm thử bảo mật và tải.

### 4.2 Quan hệ phụ thuộc giữa các module (kiểm tra theo pom)

Cấu trúc pom (pom cha `server-health-monitoring` khai báo 4 module `monitoring-common`, `agent-app`, `monitor-app`, `simulator-app`):

```
                 server-health-monitoring (pom cha)
                             |
        +--------------------+--------------------+----------------+
        |                    |                    |                |
 monitoring-common        agent-app           monitor-app     simulator-app
  (dependency: gson)      |                    |                |
        ^                 +-- monitoring-common +-- monitoring-common
        |                 |   + oshi-core       |   + sqlite-jdbc  (chỉ phụ thuộc
        |                 |   + slf4j-jdk14     |   + jfreechart      monitoring-common)
        |                 |                     |   + jakarta.mail
        +-----------------+---------------------+   + slf4j-jdk14
                          |                         + agent-app  (scope: test)
              agent-app / monitor-app / simulator-app
              đều phụ thuộc monitoring-common là gốc
```

- `monitoring-common` là gốc: chỉ phụ thuộc `gson`; ba module ứng dụng đều khai báo dependency vào nó.
- `agent-app` → `monitoring-common` + `oshi-core` + `slf4j-jdk14`.
- `monitor-app` → `monitoring-common` + `sqlite-jdbc` + `jfreechart` + `jakarta.mail` + `slf4j-jdk14`.
- `simulator-app` → chỉ `monitoring-common` (không thư viện ngoài nào khác).
- **Ngoại lệ cần ghi rõ:** `monitor-app/pom.xml` cũng khai báo dependency vào `agent-app` nhưng với **`<scope>test</scope>`** — theo chú thích ngay trong pom, "Nothing in main/ depends on the agent"; dependency này chỉ dùng cho test tích hợp (`TcpIntegrationTest` dựng `TcpControlServer` thật để test phía client TCP). Như vậy về mặt mã chính (src/main), ba module ứng dụng chỉ phụ thuộc `monitoring-common`, đúng như giả định đặt ra.

## 5. Các entry point (lớp main)

Ba lớp `main` của dự án (bảng main class theo README):

| Class | Module | Cách chạy |
|---|---|---|
| `vn.edu.hutech.group10.monitoring.agent.AgentMain` | agent-app | `scripts\run-agent01.bat`, hoặc `java -jar agent-app\target\agent-app-1.0.0-all.jar --config="config\agent01.properties"` (tham số `--config` là tùy chọn, mặc định `config/agent01.properties`) |
| `vn.edu.hutech.group10.monitoring.monitor.MonitoringMain` | monitor-app | `scripts\run-monitor.bat` (bản demo: `scripts\run-monitor-demo.bat`), hoặc `java -Dmonitoring.home="<thư mục gốc dự án>" -jar monitor-app\target\monitor-app-1.0.0-all.jar --config="config\monitor.properties"` |
| `vn.edu.hutech.group10.monitoring.simulator.SimulatorMain` | simulator-app | `java -jar simulator-app\target\simulator-app-1.0.0-all.jar <mode> [--host=...] [--port=...] [...]` với mode `valid` / `invalid-hmac` / `replay` / `load`; qua script: `scripts\run-invalid-hmac-test.bat`, `scripts\run-replay-test.bat`, `scripts\run-load-test.bat` |

Ghi chú: ba jar `-all` (maven-shade-plugin) đều tự chứa dependency, máy chạy chỉ cần JRE 17+; build bằng `scripts\build.bat` hoặc `mvn clean package`. `AgentMain` không có GUI (agent phải "nhỏ và rẻ"), `MonitoringMain` mở cửa sổ Swing sau khi lõi đã khởi động xong, `SimulatorMain` là công cụ dòng lệnh chỉ dùng cho kiểm thử.
