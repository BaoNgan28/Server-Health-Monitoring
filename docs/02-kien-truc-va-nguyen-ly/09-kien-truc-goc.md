# Kiến trúc

Nhóm 10 — Hệ thống Giám sát Sức khỏe Máy chủ (Server Health Monitoring System)

---

## 1. Hình dáng của hệ thống

```
                     MACHINE B ... MACHINE E
                   +-------------------------+
                   |          AGENT          |
                   |                         |
                   |  OSHI                   |
                   |    |                    |
                   |    v                    |
                   |  SystemMetricsCollector |
                   |    |                    |
                   |    v                    |
                   |  TelemetryScheduler     |   TcpControlServer  <---+
                   |    |                    |         ^              |
                   |    v                    |         |              |
                   |  TelemetryPacket        |         |              |
                   |    |                    |         |              |
                   |    v                    |         |              |
                   |  PacketSigner (HMAC)    |         |              |
                   |    |                    |         |              |
                   |    v                    |         |              |
                   |  UdpTelemetrySender     |         |              |
                   +---------|---------------+---------|--------------+
                             |                         |
               UDP 6000      |                         |   TCP 5000
               telemetry     |                         |   CONFIG_UPDATE
               heartbeat     |                         |   CONFIG_ACK
                             v                         |
    MACHINE A  +------------------------------------------------------+
               |             MONITORING CLIENT                        |
               |                                                      |
               |  UdpTelemetryReceiver                                |
               |    |                                                 |
               |    v                                                 |
               |  TelemetryService                                    |
               |    |                                                 |
               |    v                                                 |
               |  PacketValidator  --- rejected ---> security log     |
               |    |                                 + alert panel   |
               |    | accepted                                        |
               |    +---------------+-----------------+-------------+ |
               |    |               |                 |             | |
               |    v               v                 v             v |
               | AgentRegistry  MetricRepository  AlertService   GUI   |
               |                  (SQLite)           |                |
               |                                     v                |
               |                                EmailNotifier ---> SMTP
               |                                                      |
               |  HeartbeatMonitor      AggregationService            |
               |  AgentTcpConnectionManager --------------------------+
               +------------------------------------------------------+
```

---

## 2. Phân lớp

```
   UI          MainFrame, DashboardPanel, ServerDetailPanel,
                HistoryPanel, AlertPanel, SettingsPanel
                  |
                  v  (only ever calls services)
    Service     TelemetryService, AgentRegistry, HeartbeatMonitor,
                AlertService, AggregationService, HistoryService,
                AgentManagementService
                  |
                  v
    Network     UdpTelemetryReceiver, AgentTcpConnection(Manager)
    Repository  DatabaseManager, AgentRepository, MetricRepository,
                AlertRepository
    Notify      Notifier, EmailNotifier, NoOpNotifier, CompositeNotifier
```

Quy tắc này được thực thi bằng chính những gì mỗi class nhận được trong constructor: **giao diện người dùng không bao giờ tự mở socket, không bao giờ viết SQL và không bao giờ nói chuyện trực tiếp với SMTP.** Mỗi panel chỉ được trao các service; chỉ service mới nắm giữ repository, socket và notifier. `MonitorApplication` là nơi duy nhất thực hiện việc đấu nối (wiring), nhờ đó chiều phụ thuộc hiện rõ trong một file.

Đó không phải là trang trí. Chính điều này giúp GUI có thể kiểm thử gián tiếp — toàn bộ luồng nhận đều có integration test mà không cần mở cửa sổ — và giúp một truy vấn chậm hay một TCP peer bị treo không làm đóng băng giao diện.

---

## 3. Luồng nhận (receive path), từng bước

```
  DatagramSocket.receive()            udp-receiver thread, does nothing else
        |
        v
  copy bytes, pick worker by sender   same sender -> same worker, order kept
        |
        v
  JSON -> TelemetryPacket             udp-worker-N thread
        |
        v
  PacketValidator
        |  1. shape and ranges       0..100 %, no negatives, finite doubles
        |  2. protocol version
        |  3. known agent            unknown -> dropped, no signature computed
        |  4. HmacSHA256             over the canonical string, constant-time
        |  5. timestamp freshness    within +/- 30 s of this machine's clock
        |  6. replay window          per (agent, session), 64-packet window
        |
        +-- any failure ------> counted, logged, recorded as a security event
        |                       and nothing else happens
        v  accepted
  HeartbeatMonitor.markAlive          ONLINE, and AGENT_RECOVERED if it was down
        |
        v
  AgentRegistry.applySample           live view, then event -> GUI
        |
        v
  MetricRepository.insertRaw          one row in metric_raw
        |
        v
  AlertService.evaluate               thresholds, open/resolve, e-mail
```

Thứ tự này rất quan trọng. Trạng thái sống (liveness) được cập nhật trước khi xét ngưỡng (threshold), nhờ đó một máy vừa quay trở lại sẽ báo cáo khôi phục trước khi báo rằng CPU của nó đang cao. Và không có bước nào phía sau validator được chạy đối với packet bị từ chối, đó chính là lý do một datagram giả mạo khai báo CPU 100% không thể hiển thị lên dashboard, không thể lọt vào `metric_raw`, và không thể kích hoạt alert.

---

## 4. Luồng điều khiển (control path)

```
  DashboardPanel "Configure"
        |
        v  SwingWorker, never the event dispatch thread
  AgentManagementService.pushConfiguration
        |
        v
  AgentTcpConnectionManager.ensureConnected     reconnects if the socket died
        |
        v
  AgentTcpConnection.sendConfigUpdate
        |
        v  4-byte length + UTF-8 JSON
  === TCP ===
        |
        v
  TcpControlServer -> TcpClientSessionHandler   one pool thread per session
        |
        v
  ConfigUpdateService.apply                     validates everything, then
        |                                       applies everything, or nothing
        v
  CONFIG_ACK  SUCCESS | ERROR + reason
        |
  === TCP ===
        |
        v
  monitoring client stores the new threshold    only when the ack said SUCCESS
        |
        v
  dialog shows the agent's own answer
```

Cơ sở dữ liệu cục bộ chỉ được cập nhật **sau** acknowledgement, không bao giờ trước đó. Nếu agent từ chối một giá trị, màn hình vẫn hiển thị đúng giá trị mà agent đang thực sự sử dụng.

---

## 5. Các luồng (Threads)

| Luồng (Thread) | Sở hữu bởi | Chức năng |
|---|---|---|
| `udp-receiver` | monitoring client | chỉ gọi `receive()` và sao chép dữ liệu |
| `udp-worker-0..3` | monitoring client | validate, lưu trữ, cảnh báo; một worker cho mỗi nhóm sender |
| `monitor-scheduler-1..2` | monitoring client | quét heartbeat, aggregation |
| `email-sender` | monitoring client | một luồng đứng sau bounded queue |
| Swing EDT | monitoring client | chỉ vẽ giao diện và xử lý nhập liệu |
| `SwingWorker` pool | monitoring client | kết nối, đẩy cấu hình, lịch sử, e-mail kiểm thử |
| `agent-scheduler-1..2` | agent | tick telemetry, tick heartbeat |
| `agent-tcp-accept` | agent | vòng lặp `accept()` |
| `agent-tcp-session-1..8` | agent | mỗi session điều khiển một luồng |

Mọi pool đều có kích thước cố định và mọi queue đều bị giới hạn. Không có chỗ nào trong dự án tạo một luồng cho mỗi packet hay mỗi lần thử kết nối, nhờ đó bài load test với hai mươi identity không biến thành một vụ bùng nổ luồng.

Hai quy tắc được tuân thủ xuyên suốt:

* **Không bao giờ chạm vào Swing từ worker thread.** Các service event đến trên worker thread và được chuyển tiếp bằng `SwingUtilities.invokeLater`.
* **Không bao giờ block event dispatch thread.** Việc kết nối, đẩy cấu hình, tải lịch sử và gửi mail đều chạy trong `SwingWorker`.

Quy tắc thứ ba áp dụng bên trong các scheduler: một tác vụ định kỳ nếu ném exception sẽ bị `ScheduledExecutorService` hủy một cách lặng lẽ, khiến agent ngừng báo cáo mà không có lỗi hiển thị. Vì vậy mọi tick đều catch và log toàn bộ.

---

## 6. Cô lập lỗi

Hệ thống vẫn tiếp tục chạy khi bất kỳ sự cố nào dưới đây xảy ra:

| Sự cố | Hành xử |
|---|---|
| một agent bị dừng | agent đó chuyển OFFLINE, một alert; các agent khác không bị ảnh hưởng |
| một datagram sai định dạng | được đếm, log, loại bỏ; vòng lặp tiếp tục |
| một packet giả mạo hoặc replay | bị từ chối trước khi bất cứ thứ gì được cập nhật |
| một TCP peer biến mất | luồng của session đó kết thúc; vòng lặp accept vẫn sống |
| database bị khóa tạm thời | công việc được thử lại, sau đó được báo cáo |
| SMTP lỗi hoặc mất mạng | được log kèm lý do từ server; alert vẫn tới GUI và database |
| một metric không lấy được từ OS | metric đó báo cáo giá trị an toàn và warning được log tối đa một lần mỗi phút |
| một GUI listener ném exception | được log và bỏ qua; quá trình xử lý telemetry không bị ảnh hưởng |

---

## 7. Nơi lưu trữ secret

* Shared secret của agent nằm trong file properties của nó và trong bảng `agents`. Nó không bao giờ được in ra lúc khởi động, không bao giờ bị log, không bao giờ được gửi qua mạng, và không bao giờ hiển thị trong bảng agent. Dialog chỉnh sửa nó dùng `JPasswordField` và xóa sạch mảng ký tự sau khi đọc.
* Gmail App Password được đọc từ `config/email-secrets.properties` (hoặc từ biến môi trường) lúc runtime, chỉ do `EmailConfig` nắm giữ, và bị che khỏi mọi thông điệp lỗi SMTP trước khi thông điệp đó tới log hay dialog.
* Không giá trị nào xuất hiện trong `toString()` ở bất kỳ đâu trong dự án.
