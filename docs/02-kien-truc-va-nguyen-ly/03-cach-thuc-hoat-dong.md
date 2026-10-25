# Cách thức hoạt động

Tài liệu mô tả chi tiết từng luồng xử lý của hệ thống, kèm sơ đồ chuỗi (sequence
diagram) dạng text. Mọi mốc thời gian, cổng, ngưỡng đều lấy từ code và từ
`config\*.properties`; chỗ nào code và tài liệu nguồn khác nhau sẽ được ghi rõ.

Thuật ngữ dùng trong tài liệu:

- **telemetry** (dữ liệu đo lường): gói UDP định kỳ mang 6 chỉ số hệ thống.
- **heartbeat** (nhịp tim): gói UDP nhỏ "agent còn sống", không kèm chỉ số.
- **HMAC** (mã xác thực dựa trên hàm băm): chữ ký HmacSHA256 dùng bí mật chung.
- **framing** (đóng khung): quy ước 4 byte độ dài + JSON trên luồng byte TCP.
- **replay** (chạy lại): kẻ tấn công gửi lại một gói thật đã bắt được trước đó.
- **hysteresis** (dải trễ): vùng an toàn dưới ngưỡng phải tới mới đóng cảnh báo.

---

## Bảng mốc thời gian & ngưỡng (tham chiếu nhanh)

| Thông số | Giá trị | Nguồn |
|---|---|---|
| Chu kỳ gửi telemetry | `metric.interval.ms = 1000` | `config\agent01.properties`, mặc định `ProtocolConstants.DEFAULT_METRIC_INTERVAL_MS` |
| Chu kỳ gửi heartbeat | `heartbeat.interval.ms = 5000` | tương tự `DEFAULT_HEARTBEAT_INTERVAL_MS` |
| Timeout coi là OFFLINE | `heartbeat.timeout.ms = 15000` | `config\monitor.properties`, `DEFAULT_HEARTBEAT_TIMEOUT_MS` |
| Chu kỳ quét liveness | `heartbeat.check.interval.ms = 2000` | `config\monitor.properties` |
| Dung sai timestamp packet | `packet.timestamp.tolerance.ms = 30000` | `config\monitor.properties` |
| Cổng UDP monitor | 6000 | `monitor.udp.port` |
| Cổng TCP agent | 5000 (demo: 5001–5004) | `agent.tcp.port` |
| Timeout TCP monitor | `tcp.timeout.ms = 5000` | `config\monitor.properties` |
| Ngưỡng cảnh báo (mặc định agent) | CPU 85 %, RAM 85 %, NET in/out 50 Mbps | `alert.*.threshold` trong `config\agent0N.properties` |
| Range `metricIntervalMs` khi cấu hình từ xa | 200 … 3 600 000 ms | `ConfigUpdateService` + `ProtocolConstants` |
| Range `heartbeatIntervalMs` khi cấu hình từ xa | 1 000 … 3 600 000 ms | tương tự |
| Cửa sổ chống replay | 64 sequence, nhớ 4 session/agent | `ReplayProtector.WINDOW_SIZE`, `MAX_SESSIONS_PER_AGENT` |
| Giới hạn gói UDP | 4096 byte | `MAX_UDP_PACKET_SIZE` |
| Giới hạn frame TCP | 1 MiB (1 048 576 byte) | `MAX_TCP_FRAME_SIZE` |
| Retention production | raw 60 phút, bucket 5 phút, aggregate 30 ngày, job 300 s | `config\monitor.properties` |
| Retention demo | raw 60 s, bucket 10 s, aggregate 300 s, job 15 s | `config\monitor-demo.properties` |

---

## (a) Luồng khởi động Agent

```
Người dùng        AgentMain (main thread)             AgentRuntime
    |                    |                                  |
    | java -jar agent-app-1.0.0-all.jar                     |
    |   --config="config\agent01.properties"                |
    |-----------> |                                        |
    |        [1] resolveConfigPath(args)                    |
    |             ("--config=...", "--config x", hoặc file  |
    |              .properties trần; thiếu -> mặc định     |
    |              config/agent01.properties)               |
    |        [2] LoggingSetup.configure("agent-agent01")    |
    |             -> console + logs\agent-agent01-%g.log    |
    |                (xoay 5 file x 2 MiB)                  |
    |        [3] AgentConfigLoader.load(path)               |
    |             - thiếu file / sai cú pháp -> in lỗi,    |
    |               System.exit(2)                          |
    |             - chỉ agent.id và security.sharedSecret   |
    |               là bắt buộc; còn lại có mặc định        |
    |        [4] printBanner(...)  (KHÔNG in sharedSecret)  |
    |        [5] new AgentRuntime(config, "1.0.0")          |
    |             - sessionId = UUID.randomUUID()           |
    |             - sequence = AtomicLong(0)                |
    |        [6] cài shutdown hook "agent-shutdown"         |
    |        [7] runtime.start()  ------------------------> |
    |                                                        | [7.1] new OshiSystemMetricsCollector()
    |                                                        | [7.2] new UdpTelemetrySender(
    |                                                        |         monitor.host, monitor.udp.port)
    |                                                        | [7.3] new ConfigUpdateService(config,
    |                                                        |         changed -> reschedule 2 scheduler)
    |                                                        | [7.4] TcpControlServer.start()
    |                                                        |         - new ServerSocket(tcp.port)
    |                                                        |         - start luồng agent-tcp-accept
    |                                                        | [7.5] newScheduledThreadPool(2,
    |                                                        |         "agent-scheduler-1..2", daemon)
    |                                                        | [7.6] TelemetryScheduler.start()
    |                                                        |         -> tick NGAY (delay 0)
    |                                                        | [7.7] HeartbeatScheduler.start()
    |                                                        |         -> tick SAU heartbeat.interval.ms
    |        [8] in "Agent is running..."                    |
    |        [9] shutdownLatch.await()  <- main thread đỗ    |
```

Điểm đáng chú ý:

- **Thứ tự khởi động** được `AgentRuntime.start()` cố định: collector →
  UDP sender → TCP server → scheduler → hai timer. Nếu bước nào lỗi, toàn bộ
  phần đã mở được `stop()` thu hồi (rollback) rồi mới ném exception ra ngoài —
  `AgentMain` in lỗi và `System.exit(3)`.
- **Dừng** chạy theo thứ tự ngược lại và **idempotent** (gọi nhiều lần không
  sao): hủy 2 timer → `scheduler.shutdown()` (chờ 2 s rồi `shutdownNow`) →
  đóng TCP server → đóng UDP sender → đóng collector.
- Khi bấm Ctrl+C (hoặc JVM tắt), shutdown hook chạy `runtime.stop()` rồi
  `countDown()` để main thread thoát.
- Agent **không có giao diện**: chạy trên máy được giám sát nên phải nhỏ và nhẹ.

---

## (b) Luồng thu thập & gửi telemetry (1000 ms) và heartbeat (5000 ms)

Cả hai timer dùng chung **một** `ScheduledExecutorService` 2 luồng
(`agent-scheduler-1`, `agent-scheduler-2`), chung **một** bộ đếm `sequence`
và chung `sessionId`.

### (b.1) Telemetry — mỗi 1000 ms

```
agent-scheduler-1 (scheduleAtFixedRate, delay 0, period 1000 ms)
    |
    |  TelemetryScheduler.tick()          [bọc try/catch RuntimeException]
    |
    +--> [1] collector.collect()   (OshiSystemMetricsCollector)
    |        - CPU: SystemCpuLoadBetweenTicks so với mốc lần trước (*100)
    |        - RAM: (total - available) / total * 100
    |        - kết nối: TCPv4.established + TCPv6.established
    |        - uptime: operatingSystem.getSystemUptime()
    |        - mạng vào/ra: tổng rate của từng interface
    |          (NetworkRateTracker, refresh danh sách interface mỗi 60 000 ms)
    |        - mọi lỗi -> giá trị an toàn (0) + log cảnh báo tối đa 1 lần/phút
    |
    +--> [2] new TelemetryPacket()
    |        protocolVersion="1.0", type="TELEMETRY", agentId, hostname,
    |        sessionId, timestamp=metrics.getTimestamp(),
    |        sequence=sequence.incrementAndGet()   <-- dùng chung với heartbeat
    |        + 6 chỉ số
    |
    +--> [3] PacketSigner.sign(packet, sharedSecret)
    |        - PacketCanonicalizer.canonicalize(): các trường nối bằng '|',
    |          double format Locale.ROOT đúng 4 chữ số thập phân,
    |          -0.0 -> 0.0000, null -> chuỗi rỗng, KHÔNG gồm trường hmac
    |        - HmacSHA256(secret, canonical) -> hex thường (64 ký tự)
    |
    +--> [4] UdpTelemetrySender.send(packet)
             - Gson -> byte[]; > 4096 byte -> từ chối + đếm failed
             - InetAddress.getByName (cache; lỗi gửi sẽ xóa cache)
             - DatagramSocket.send(...) tới monitor.host:6000
             - lỗi IOException -> đếm failed, xóa cache địa chỉ, log; KHÔNG
               ném ra ngoài
```

Nếu `tick()` ném RuntimeException, nó bị bắt và log — **chủ ý**: một bài được
lên lịch mà thoát ra ngoài exception sẽ bị `ScheduledExecutorService` hủy ngầm,
khiến agent ngừng báo cáo mà không có lỗi nào hiện ra (ghi trong Javadoc của
`TelemetryScheduler` và `09-kien-truc-goc.md` mục 5).

### (b.2) Heartbeat — mỗi 5000 ms

```
agent-scheduler-2 (scheduleAtFixedRate, delay = 5000 ms, period 5000 ms)
    |
    |  HeartbeatScheduler.tick()          [bọc try/catch RuntimeException]
    +--> new HeartbeatPacket(): protocolVersion, type="HEARTBEAT", agentId,
    |     hostname, sessionId, timestamp=System.currentTimeMillis(),
    |     sequence=sequence.incrementAndGet()   <-- cùng chuỗi với telemetry
    +--> PacketSigner.sign(...)          (chuỗi ký: 7 trường, không chỉ số)
    +--> UdpTelemetrySender.send(...)
```

Khác biệt hai timer:

| | Telemetry | Heartbeat |
|---|---|---|
| Delay lần đầu | **0 ms** (gửi ngay khi khởi động) | **= chu kỳ** (5000 ms) |
| Nội dung | 6 chỉ số | chỉ thân phận |
| Mục đích | dữ liệu cho dashboard/DB/cảnh báo | giữ "sống" riêng khi thu thập chỉ số gặp trục trặc |

Vì cả hai dùng chung một bộ sequence và monitor giữ một cửa sổ chống replay
duy nhất theo `(agentId, sessionId)`, thứ tự tuyệt đối giữa hai loại gói không
quan trọng — chỉ cần mỗi sequence xuất hiện đúng một lần.

---

## (c) Luồng Monitor: nhận UDP → xác thực HMAC → kiểm tra replay → lưu DB → cập nhật UI → sinh cảnh báo

```
udp-receiver           udp-worker-N (1 trong 4)         TelemetryService
    |                         |                               |
[1] socket.receive()          |                               |
[2] sao chép bytes ra mảng   |                               |
    mới + lấy "ip:port"       |                               |
[3] workerFor(source)  -----> |                               |
    = workers[floorMod(         |                               |
      source.hashCode(),4)]     |                               |
    -> vòng lặp tiếp            | [4] handleDatagram(payload) -> |
                                |      codec.peekType(json)     |
                                |        null -> reject          |
                                |      TELEMETRY / HEARTBEAT     |
                                |      -> validate(...) -------> |
                                |                               | [5] PacketValidator
                                |                               |   (a) shape: sessionId
                                |                               |       có, sequence >= 0,
                                |                               |       double hữu hạn,
                                |                               |       % trong 0..100,
                                |                               |       count/uptime/rate >= 0
                                |                               |   (b) version == "1.0"
                                |                               |   (c) agent đã đăng ký?
                                |                               |       không -> UNKNOWN_AGENT,
                                |                               |       bỏ qua luôn việc
                                |                               |       tính chữ ký
                                |                               |   (d) HmacSHA256 trên chuỗi
                                |                               |       chuẩn, so sánh bằng
                                |                               |       MessageDigest.isEqual
                                |                               |       (constant-time)
                                |                               |   (e) |now - timestamp| <= 30000 ms
                                |                               |   (f) ReplayProtector.accept(
                                |                               |        agentId, sessionId, seq)
```

### (c.1) Nếu **bị từ chối** (một lỗi là dừng hẳn)

```
TelemetryService.reject()
   +--> rejectedCount++ (LongAdder)
   +--> registry.countRejected(agentId)
   +--> nếu lý do là INVALID_HMAC  -> AlertType.SECURITY_INVALID_HMAC
        nếu lý do là REPLAY        -> AlertType.SECURITY_REPLAY_PACKET
        -> AlertService.recordSecurityEvent():
             - cooldown 60 000 ms cho mỗi (agent, loại)  [SECURITY_EVENT_COOLDOWN_MS]
             - lưu với state = RESOLVED (đã đóng sẵn)
             - KHÔNG gửi email (chống dồn email khi bị tấn công dồn dập)
             - bắn eventBus -> bảng Alerts trên GUI
   +--> nếu không phải 2 loại trên -> chỉ log WARNING
   +--> eventBus.firePacketRejected(agentId, detail)
```

**Không có bất kỳ bước nào bên dưới validator chạy**: không markAlive, không
`metric_raw`, không alert ngưỡng, không biểu đồ. Gói giả mạo tuyên bố CPU 100 %
không thể xuất hiện trên dashboard.

### (c.2) Nếu **được chấp nhận** — gói TELEMETRY (thứ tự quan trọng)

```
[6]  heartbeatMonitor.markAlive(agentId, packet.timestamp, now)
       - registry.markSeen(max(packetTimestamp, now))
       - nếu trước đó là OFFLINE  -> setStatus(ONLINE)
            + alertService.raiseRecovered():
                 - đóng alert AGENT_OFFLINE đang mở (nếu có)
                 - insert AGENT_RECOVERED với state = RESOLVED ngay
                 - eventBus.fireAlert + notifier.sendAlert (email)
       -> "hồi phục" được báo TRƯỚC khi báo chỉ số cao

[7]  registry.applySample(sample)
       - state.applySample (cập nhật chỉ số mới nhất, lastSeen, countAccepted)
       - eventBus.fireAgentUpdated(snapshot)
            -> MainFrame.UiEventListener -> SwingUtilities.invokeLater
            -> DashboardPanel.applyUpdate + ServerDetailPanel.applyUpdate

[8]  metricRepository.insertRaw(sample)          --> 1 dòng trong metric_raw
       - lỗi database chỉ -> log WARNING; live view và cảnh báo KHÔNG bị mất

[9]  alertService.evaluate(record, sample)       (4 ngưỡng, xem dưới đây)
```

### (c.3) Đánh giá ngưỡng (`AlertService.evaluate`)

| AlertType | So sánh với | Dải trễ (hysteresis) |
|---|---|---|
| `CPU_HIGH` | `cpuUsage` vs `cpuThreshold` | 2 điểm phần trăm |
| `MEMORY_HIGH` | `memoryUsage` vs `memoryThreshold` | 2 điểm phần trăm |
| `NETWORK_IN_HIGH` | MBps (chuyển từ byte/s) vs `networkInThresholdMbps` | 5 % của ngưỡng |
| `NETWORK_OUT_HIGH` | MBps vs `networkOutThresholdMbps` | 5 % của ngưỡng |

Máy trạng thái theo khóa `agentId|LOAICANHBAO` (map `ConcurrentHashMap`):

```
value > ngưỡng  && chưa có OPEN   -> mở 1 cảnh báo:
                                        repository.insert (bảng alerts)
                                        openAlerts.put
                                        log WARNING
                                        eventBus.fireAlert  -> GUI
                                        notifier.sendAlert  -> email
value > ngưỡng  && đã có OPEN     -> KHÔNG làm gì
                                      (đây là thứ ngăn 1 cảnh báo + 1 email/giây)
value <= ngưỡng - dải trễ          -> đóng cảnh báo (repository.resolve, eventBus)
value vượt ngưỡng lần nữa sau đó  -> mở 1 sự kiện MỚI hoàn toàn
```

Gói **HEARTBEAT** được chấp nhận thì chỉ đi đến bước `markAlive` (bước 6) —
không lưu `metric_raw`, không đánh giá ngưỡng.

---

## (d) Luồng heartbeat / OFFLINE (15 s) & hồi phục

```
monitor-scheduler-1
    |  scheduleAtFixedRate(period = heartbeat.check.interval.ms = 2000)
    v
HeartbeatMonitor.safeCheck()          [try/catch trọn vẹn]
    |
    |  check(now):
    |    với mỗi AgentRuntimeState:
    |      - status không phải ONLINE/WARNING  -> bỏ qua
    |        (agent còn UNKNOWN = chưa từng nghe tới,
    |         restart monitor sẽ KHÔNG gửi email "mất máy" hàng loạt)
    |      - lastSeen <= 0                     -> bỏ qua
    |      - now - lastSeen > 15 000 ms        ->
    |            registry.setStatus(OFFLINE)   (ghi vào bảng agents)
    |            alertService.raiseOffline():
    |              - nếu (agent, AGENT_OFFLINE) đã OPEN -> bỏ qua
    |                => đúng 1 sự kiện dù quét 30 lần/phút (mỗi 2000 ms)
    |              - severity = CRITICAL,
    |                currentValue = lastSeen, threshold = timeoutMs
    |              - insert alerts + eventBus + notifier.sendAlert
```

```
Hồi phục (xảy ra trên luồng udp-worker):
    packet hợp lệ đầu tiên sau khi OFFLINE
      -> HeartbeatMonitor.markAlive()
      -> setStatus(ONLINE)
      -> raiseRecovered(): đóng AGENT_OFFLINE, ghi AGENT_RECOVERED (đã RESOLVED)
      -> GUI + email
```

Với heartbeat 5000 ms và timeout 15 000 ms, một agent khỏe mạnh có thể mất
**2 heartbeat liên tiếp** mà chưa bị khai OFFLINE (giải thích trong
`10-giao-thuc-goc.md` mục 9).

Agent **chưa bao giờ** gửi packet nào ở trạng thái `UNKNOWN` và không bao giờ
bị coi là "mất máy" — theo `HeartbeatMonitor.check()` và `AgentRegistry.load()`
(mỗi lần khởi động, status trong DB đều bị reset về `UNKNOWN`, vì status đã lưu
là lịch sử chứ không phải bằng chứng).

---

## (e) Luồng cấu hình từ xa (UI → TCP CONFIG_UPDATE → agent validate → CONFIG_ACK → ghi DB)

```
DashboardPanel (EDT)            SwingWorker (pool)         AgentManagementService
    |  bấm "Configure"               |                            |
    |  ConfigureAgentDialog.show()   |                            |
    |  nếu người dùng OK            |                            |
    |  configureButton.setEnabled(false)                          |
    |  new SwingWorker().execute() ->|                            |
    |                                | doInBackground():           |
    |                                |  pushConfiguration(record,  |
    |                                |    update) ---------------->|
    |                                |                            | [1] ensureConnected(agent)
    |                                |                            |     có kết nối sống -> dùng lại
    |                                |                            |     không -> connect() lại
    |                                |                            |     (AgentTcpConnection.connect:
    |                                |                            |      timeout = tcp.timeout.ms = 5000)
    |                                |                            | [2] registry.setTcpConnected(true)
    |                                |                            | [3] connection.sendConfigUpdate(update)
    |                                |                            |     - type = "CONFIG_UPDATE"
    |                                |                            |     - requestId = UUID nếu chưa có
    |                                |                            |     - writeFrame: 4 byte độ dài + JSON
    |
    | ============================ TCP (cổng 5000) ============================
    |
    |                                                         TcpClientSessionHandler (pool thread)
    |                                                            | [4] MessageFraming.readFrame(in)
    |                                                            | [5] handle(): codec.peekType
    |                                                            |        CONFIG_UPDATE ->
    |                                                            |     ConfigUpdateService.apply()
    |                                                            |       (synchronized)
    |                                                            |       [5a] validate TOÀN BỘ trường:
    |                                                            |            metricIntervalMs 200..3600000
    |                                                            |            heartbeatIntervalMs 1000..3600000
    |                                                            |            cpu/memory: >0 và <=100
    |                                                            |            network*: hữu hạn, >=0
    |                                                            |            null = bỏ qua trường đó
    |                                                            |       [5b] nếu có lỗi ->
    |                                                            |            CONFIG_ACK status=ERROR
    |                                                            |            + fillEffective() (giữ nguyên
    |                                                            |            giá trị hiện tại), KHÔNG áp gì
    |                                                            |       [5c] nếu hợp lệ -> áp DỒNG THỨI
    |                                                            |            các trường != null
    |                                                            |            + listener.onConfigChanged:
    |                                                            |              telemetryScheduler.reschedule()
    |                                                            |              heartbeatScheduler.reschedule()
    |                                                            |            -> CONFIG_ACK status=SUCCESS,
    |                                                            |               message "Applied ...",
    |                                                            |               fillEffective()
    |                                                            | [6] MessageFraming.writeFrame(out, ack)
    | ============================ TCP ============================
    |
    |                                |  exchange(): readFrame  <-- [7]
    |                                |  kiểm tra type == CONFIG_ACK
    |                                |                            | [8] CHỈ NẾU ack.isSuccess():
    |                                |                            |     applyLocally(agent, update)
    |                                |                            |     repository.update(agent)  <-- GHI DB Ở ĐÂY
    |                                |                            |     registry.updateRecord(agent)
    |                                | done() -> EDT hiện JOptionPane
    |  hiện CONFIG_ACK của chính agent (status, message, các giá trị effective*)
    |  configureButton.setEnabled(true) trở lại
```

Nguyên tắc: **cơ sở dữ liệu cục bộ chỉ được ghi sau khi có ACK thành công**, nên
màn hình không bao giờ hiển thị một ngưỡng mà agent đã từ chối
(`AgentManagementService.pushConfiguration`, `09-kien-truc-goc.md` mục 4).
Nếu `CONFIG_ACK` trả `ERROR`, người dùng thấy đúng câu trả lời của agent kèm
lý do (vd. `"metricIntervalMs must be between 200 and 3600000 ms, got 5"`).

---

## (f) Luồng gửi email cảnh báo

```
AlertService.store() / raiseRecovered()
    |  notifier.sendAlert(event)
    v
EmailNotifier.sendAlert
    |  EmailMessageBuilder.forAlert(config, event)   -> subject + body
    |  executor.execute(deliver)
    |     ThreadPoolExecutor(1 thread "email-sender", daemon,
    |                        ArrayBlockingQueue(50))
    |     nếu hàng đợi đầy -> RejectedExecutionException
    |        -> droppedCount++, log WARNING, DỪNG ở đây
    v
[luồng email-sender] deliver()
    |  SmtpMailTransport.send(message)
    |     smtp.gmail.com:587, STARTTLS, auth
    |     thành công -> sentCount++, log INFO
    |     thất bại   -> failedCount++, lastError = lý do TĨNH MẬT KHẨU
    |                    (safeReason thay chuỗi mật khẩu bằng "***"),
    |                    log WARNING
```

Nguồn thông tin đăng nhập (`EmailConfigLoader`), theo thứ tự ưu tiên:

1. Biến môi trường `SMTP_USERNAME`, `SMTP_APP_PASSWORD`, `ALERT_RECIPIENT_EMAIL`.
2. File `config\email-secrets.properties` (mặc định `smtp.host=smtp.gmail.com`,
   `smtp.port=587`, `smtp.starttls=true`, `smtp.auth=true`).

Trạng thái chưa cấu hình:

- Nếu thiếu thông tin, còn là placeholder (`PASTE_GMAIL_APP_PASSWORD_HERE`,
  `PASTE_RECIPIENT_EMAIL_HERE`, chuỗi bắt đầu bằng `PASTE_`/`YOUR_`) hoặc sai
  định dạng email → `EmailConfig.disabled(...)` kèm lý do.
- `MonitorApplication.buildNotifier()` khi đó dựng
  `CompositeNotifier(NoOpNotifier(lý do))`, log một dòng
  `"Email notifications disabled: credentials not configured"` — **không phải
  lỗi khởi động**, không thử đăng nhập SMTP nào; tab Settings hiện
  `NOT CONFIGURED`; GUI và DB vẫn hoạt động bình thường.
- Operator điền file rồi bấm nút reload trong Settings
  (`reloadEmailConfiguration()`) là bật email được, không cần build lại.
- Email test (`sendTestMessage()`) chạy **đồng bộ** trên `SwingWorker` của
  SettingsPanel để hiện kết quả vào dialog.

Email **không** được gửi cho các sự kiện bảo mật (`recordSecurityEvent` không
gọi notifier) — mục đích là chặn luồng gói giả mạo biến thành luồng email.

---

## (g) Luồng tổng hợp & dọn dữ liệu (aggregation / retention)

```
monitor-scheduler-2
    |  scheduleAtFixedRate(period = aggregation.interval.seconds)
    |     production: 300 s   |   demo: 15 s
    v
AggregationService.safeRun()          [try/catch trọn vẹn]
    |
    |  runOnce(nowMs):
    |    [1] bucketMs = aggregation.bucket   (5 phút / 10 giây)
    |    [2] rawCutoff = bucketStart(now - rawRetention, bucket)
    |        CĂN XUỐNG biên bucket -> bucket chưa đầy được GIỮ nguyên
    |    [3] metricRepository.aggregateAndPrune(rawCutoff, bucket)
    |          trong MỘT transaction:
    |          a) INSERT OR IGNORE INTO metric_5m
    |               SELECT agent_id, bucket_start, sample_count,
    |                      avg/max từng chỉ số
    |               FROM metric_raw WHERE timestamp < rawCutoff
    |               GROUP BY agent_id, bucket_start
    |          b) DELETE FROM metric_raw WHERE timestamp < rawCutoff
    |        UNIQUE(agent_id, bucket_start) -> chạy lại bao nhiêu lần
    |        cũng không ghi trùng
    |    [4] metricRepository.deleteAggregatesBefore(now - aggregateRetention)
    |          DELETE FROM metric_5m WHERE bucket_start < cutoff
    |    [5] eventBus.fireAggregationCompleted(buckets, pruned)
    |          -> GUI (Panel cập nhật nếu đang hiển thị)
```

So sánh hai profile (khác **chỉ** ở con số, cùng một code — không có nhánh demo
nào trong `AggregationService`):

| | Production (`monitor.properties`) | Demo (`monitor-demo.properties`) |
|---|---|---|
| Giữ dữ liệu thô | `raw.retention.minutes = 60` | `raw.retention.seconds = 60` |
| Rộng bucket | `aggregation.bucket.minutes = 5` | `aggregation.bucket.seconds = 10` |
| Giữ bucket tổng hợp | `aggregate.retention.days = 30` | `aggregate.retention.seconds = 300` |
| Job chạy mỗi | `aggregation.interval.seconds = 300` | `aggregation.interval.seconds = 15` |
| File database | `data/monitoring.db` | `data/monitoring-demo.db` |

Lưu ý thứ tự đọc key trong `MonitorConfigLoader`: key **giây** được ưu tiên hơn
key **phút/ngày** khi cả hai cùng xuất hiện — đây là cơ chế để profile demo
"thế tục" giá trị của production.

Mỗi bucket lưu **cả trung bình lẫn cực đại** của từng chỉ số: trung bình một
mình sẽ che mất đúng cái đỉnh đã gây ra sự cố.

`HistoryService` chọn nguồn dữ liệu cho tab History: khoảng thời gian nằm trong
cửa sổ dữ liệu thô thì đọc `metric_raw`, cũ hơn thì đọc `metric_5m` (rỗng thì
quay lại `metric_raw` làm phương án dự phòng).

---

## Tài liệu liên quan

- [09-kien-truc-goc.md](09-kien-truc-goc.md) — phân tầng, luồng dữ liệu, mô hình luồng.
- [10-giao-thuc-goc.md](10-giao-thuc-goc.md) — giao thức wire, framing, chuỗi ký, ví dụ.
- [11-co-so-du-lieu-goc.md](11-co-so-du-lieu-goc.md) — bảng, index, retention.
- [04-giao-thuc-tcp-udp.md](04-giao-thuc-tcp-udp.md) — giao thức TCP/UDP, cấu trúc từng message.
- [05-bao-mat-hmac-va-chong-phat-lai.md](05-bao-mat-hmac-va-chong-phat-lai.md) — xác thực HMAC-SHA256 và chống phát lại.
- [06-luu-tru-va-tong-hop.md](06-luu-tru-va-tong-hop.md) — lưu trữ SQLite, tổng hợp và retention.
- [07-canh-bao-va-thong-bao.md](07-canh-bao-va-thong-bao.md) — hệ thống cảnh báo và gửi email.
