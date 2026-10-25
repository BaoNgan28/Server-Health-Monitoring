# Mô hình đa luồng

Tài liệu mô tả các luồng (thread) của cả hai tiến trình, cơ chế đồng bộ được
dùng trong code, các quy tắc an toàn luồng và cách hệ thống xử lý khi hàng đợi
đầy. Đối chiếu với `09-kien-truc-goc.md` mục 5 (bảng luồng trong tiếng Anh).

Thuật ngữ:

- **scheduler** (bộ lên lịch): `ScheduledExecutorService` — chạy một tác vụ
  lặp lại theo chu kỳ.
- **pool** (tàn dư luồng): nhóm luồng cố định phục vụ tác vụ đến nơi.
- **queue** (hàng đợi): nơi chứa tác vụ chờ xử lý, ở đây luôn **có giới hạn**
  (bounded) để bộ nhớ không phình vô hạn.
- **EDT** (Event Dispatch Thread — luồng xử lý sự kiện giao diện): luồng duy
  nhất của Swing được phép đụng vào component.
- **back pressure** (áp lực ngược): khi nguồn vào nhanh hơn xử lý, hệ thống tự
  làm chậm nguồn vào thay vì tích lũy vô hạn.

Nguyên tắc tổng quát (theo `09-kien-truc-goc.md`): **mọi pool đều có kích thước cố
định, mọi hàng đợi đều có giới hạn**. Không có chỗ nào tạo một luồng cho mỗi
gói tin hoặc cho mỗi lần kết nối — nhờ đó bài kiểm thử tải 20 định danh không
gây ra "bão luồng".

---

## 1. Luồng phía Agent

| Luồng | Cách tạo | Việc làm |
|---|---|---|
| `main` | JVM (`AgentMain.main`) | Nạp cấu hình, khởi động runtime, rồi **đỗ** trên `CountDownLatch.await()`; không làm gì cả. |
| `agent-shutdown` | `Runtime.addShutdownHook` | Gọi `runtime.stop()` khi Ctrl+C/JVM tắt, rồi `countDown()` để main thoát. |
| `agent-scheduler-1` | `Executors.newScheduledThreadPool(2, ...)`, daemon | Chạy tick **telemetry** (`scheduleAtFixedRate`, period = `metric.interval.ms`, delay đầu 0). |
| `agent-scheduler-2` | cùng pool trên | Chạy tick **heartbeat** (period = `heartbeat.interval.ms`, delay đầu = chu kỳ). |
| `agent-tcp-accept` | `new Thread(acceptLoop)`, daemon | Chỉ `ServerSocket.accept()`; gửi mỗi kết nối vào pool phiên. Lỗi khi accept chỉ bị log, vòng lặp tiếp tục. |
| `agent-tcp-session-1..8` | `Executors.newFixedThreadPool(8)`, daemon | Mỗi kết nối TCP điều khiển chiếm **một** luồng trọn đời kết nối; `SoTimeout = 120 000 ms` nên client im lặng quá lâu sẽ bị đóng. |

`AgentRuntime` khai báo `SCHEDULER_THREADS = 2`;
`TcpControlServer` khai báo `MAX_SESSIONS = 8`. Tất cả luồng đều là
**daemon** — JVM có thể thoát mà không cần chờ chúng.

Đường dẫn đồng bộ chính phía agent:

```
agent-scheduler-1 ──┐
                    ├──> UdpTelemetrySender (DatagramSocket dùng chung,
agent-scheduler-2 ──┘      bộ đếm LongAdder, IP cache volatile)

agent-tcp-session-N ──> ConfigUpdateService.apply()  [synchronized]
                            |-- ghi AgentConfig (các field volatile)
                            └-- listener -> TelemetryScheduler.reschedule()
                                          -> HeartbeatScheduler.reschedule()
                                             [cả hai synchronized]
```

Hai luồng scheduler **đọc** `metricIntervalMs`/`heartbeatIntervalMs` trong khi
một luồng TCP session **ghi** — vì các field này là `volatile` trong
`AgentConfig`, việc ghi được nhìn thấy ngay lập tức; `reschedule()` tự nó là
`synchronized` nên hai lời gọi đồng thời không đụng nhau.

---

## 2. Luồng phía Monitor

| Luồng | Cách tạo | Việc làm |
|---|---|---|
| `main` | JVM (`MonitoringMain.main`) | Khởi động toàn bộ application **trước** khi dựng cửa sổ; xong thì nhường việc vẽ cho EDT. |
| `udp-receiver` | `new Thread(receiveLoop)`, daemon | Chỉ `DatagramSocket.receive()` + sao chép bytes + chọn worker. Không validate, không SQL, không gì khác — để vòng nhận luôn rảnh (khi vòng này bận, buffer kernel đầy và gói bị rơi). |
| `udp-worker-0..3` | 4 × `ThreadPoolExecutor(1, 1, ArrayBlockingQueue(2000))`, daemon | Xử lý trọn gói: JSON → validate → markAlive → registry → DB → alert. |
| `monitor-scheduler-1..2` | `Executors.newScheduledThreadPool(2)`, daemon | Luồng 1: quét liveness mỗi `heartbeat.check.interval.ms` (2000 ms). Luồng 2: job aggregation mỗi `aggregation.interval.seconds`. |
| `email-sender` | `ThreadPoolExecutor(1, 1, ArrayBlockingQueue(50))`, daemon | Gửi SMTP, 1 luồng đứng sau hàng đợi có giới hạn. |
| Swing EDT | JVM/Swing | Chỉ vẽ và xử lý nhập liệu. Mọi callback từ service đều đi vào đây qua `SwingUtilities.invokeLater`. |
| `SwingWorker` pool | EDT khi `.execute()` | Kết nối TCP, đẩy cấu hình, nạp lịch sử (History), gửi email test, reload cấu hình email — mọi thao tác có thể chặn. |
| `monitor-shutdown` | `MainFrame.shutdown()` | Đóng socket + database rồi `System.exit(0)`, tách khỏi EDT để cửa sổ biến mất ngay. |
| `monitor-shutdown-hook` | `Runtime.addShutdownHook` | Dự phòng: nếu cửa sổ không bao giờ mở được, socket vẫn được đóng. |

Bộ nhớ hàng đợi / đếm của monitor:

| Đối tượng | Hàng đợi / giới hạn | Chính sách khi đầy |
|---|---|---|
| `UdpTelemetryReceiver` (mỗi worker) | `ArrayBlockingQueue(2000)` | **`CallerRunsPolicy`** — việc xử lý chạy ngay trên luồng `udp-receiver` (xem mục 5). |
| `EmailNotifier` | `ArrayBlockingQueue(50)` | Mặc định của `ThreadPoolExecutor` = **AbortPolicy** — ném `RejectedExecutionException`, bị bắt và đếm `droppedCount` (xem mục 5). |
| `TcpControlServer` | pool cố định 8 | Submit bị từ chối khi pool đóng → `acceptLoop` bắt `RuntimeException` và log, vòng lặp vẫn sống. |

---

## 3. Cơ chế đồng bộ được dùng trong code

### 3.1 `ExecutorService` / `ScheduledExecutorService`

| Nơi dùng | Kiểu pool | Kích thước |
|---|---|---|
| `AgentRuntime` | `newScheduledThreadPool` | 2 luồng (`agent-scheduler-1..2`) |
| `TcpControlServer` | `newFixedThreadPool` | 8 luồng phiên |
| `MonitorApplication` | `newScheduledThreadPool` | 2 luồng (`monitor-scheduler-1..2`) |
| `UdpTelemetryReceiver` | 4 × `ThreadPoolExecutor(1,1,...)` | mỗi worker đúng 1 luồng |
| `EmailNotifier` | `ThreadPoolExecutor(1,1,...)` | 1 luồng |

Mọi luồng trong pool đều đặt tên riêng (`ThreadFactory` + `AtomicInteger`) và
`setDaemon(true)` để việc dừng không bị kẹt.

### 3.2 Khóa `synchronized`

| Lớp / phương thức | Vì sao cần |
|---|---|
| `AgentRuntime.start()` / `stop()` | Không cho hai lần khởi động/dừng đụng nhau; `stop()` idempotent. |
| `ConfigUpdateService.apply()` | Một CONFIG_UPDATE tại một thời điểm; validate + áp dụng + trả ACK là một khối nguyên tử. |
| `AgentTcpConnection` (mọi method `synchronized`) | Mỗi kết nối là một cặp yêu cầu–đáp ứng tuần tự; hai thao tác từ GUI không được xen câu chuyện vào giữa (`exchange()` nằm trong lock). |
| `TelemetryScheduler` / `HeartbeatScheduler` (`start/reschedule/cancel`) | Hủy timer cũ và gắn timer mới không được chồng lên nhau. |
| `DatabaseManager.initialize/execute/inTransaction/close` | **Một `Object lock` duy nhất** cho toàn bộ truy vấn → theo định dạng có **đúng một writer** (điều SQLite thích nhất). |
| `MonitorApplication.start/close/reloadEmailConfiguration` | Vòng đời application đơn luồng hóa. |
| `MessageFraming.writeFrame` — `synchronized(out)` | Hai thread cùng ghi một stream TCP sẽ trộn byte vào nhau. |
| `LoggingSetup.configure` | Cấu hình logger đúng một lần mỗi tiến trình. |

### 3.3 `volatile`

- `AgentConfig`: `metricIntervalMs`, `heartbeatIntervalMs`, 4 ngưỡng — scheduler
  đọc trong khi TCP session ghi.
- `running` (AgentRuntime, TcpControlServer, UdpTelemetryReceiver) — cờ dừng
  được luồng khác thấy ngay.
- `ScheduledFuture<?> task`, `currentIntervalMs`, `lastSample` ở các scheduler.
- `UdpTelemetrySender.resolvedAddress` — cache IP, xóa khi gửi lỗi.
- `EmailNotifier.lastError`, `AggregationService.lastRunAt/totalBucketsWritten`.

### 3.4 Biến nguyên tử (`java.util.concurrent.atomic`)

- `AtomicLong sequence` — số thứ tự packet của agent, tăng từ cả hai luồng
  scheduler; `incrementAndGet()` bảo đảm mỗi giá trị đúng một lần.
- `AtomicInteger` — bộ đếm đặt tên luồng (`agent-scheduler-N`,
  `agent-tcp-session-N`, `udp-worker-N`, `monitor-scheduler-N`).
- `LongAdder` — bộ đếm thống kê chạm nhiệt độ cao: `sentCount`/`failedCount`
  (UDP sender dùng chung bởi 2 luồng), `receivedCount`, `acceptedCount`,
  `rejectedCount`, bộ đếm email. Chú thích trong code ghi rõ: `long` thường sẽ
  mất cập nhật âm thầm khi nhiều luồng cùng tăng.

### 3.5 Collection đồng bộ / an toàn luồng

| Collection | Nơi dùng | Ý nghĩa |
|---|---|---|
| `ConcurrentHashMap` | `AgentRegistry.agents` | Receiver, heartbeat timer và GUI cùng đọc/ghi **không cần khóa toàn cục** (mỗi entry còn giữ field `volatile` riêng). |
| `ConcurrentHashMap` | `AlertService.openAlerts`, `lastSecurityEventAt` | Tra cứu "đã có alert mở chưa" trên **mọi** gói dữ liệu. |
| `ConcurrentHashMap.compute(...)` | `ReplayProtector.agents` | Quyết định nhận/từ chối sequence **và** cập nhật cửa sổ chạy nguyên tử theo từng agent, nên hai luồng worker xử lý cùng agent không thể xung đột. |
| `LinkedHashMap` access-ordered LRU (bọc trong `AgentSessions`) | `ReplayProtector` | Nhớ 4 session mới nhất mỗi agent; thao tác luôn nằm trong `compute` của ConcurrentHashMap. |
| `ConcurrentHashMap` | `AgentTcpConnectionManager.connections` | Kết nối TCP theo agent. |
| `CopyOnWriteArrayList` | `MonitorEventBus.listeners` | Listener đăng ký một lần lúc khởi động, sau đó chỉ đọc trên mọi gói → list ghi-chép (copy-on-write) là tối ưu. |

### 3.6 Khóa database

`DatabaseManager` dùng **một connection SQLite chia sẻ** cho toàn bộ tiến trình
và mọi đơn vị công việc đều chạy trong `execute()` dưới cùng một `synchronized`
lock:

- `PRAGMA journal_mode = WAL` → truy vấn lịch sử đọc được trong lúc ghi.
- `PRAGMA busy_timeout = 5000` (chờ driver) + retry **3 lần**, giãn 150 ms
  (`LOCK_RETRIES`, `LOCK_RETRY_DELAY_MS`) cho lỗi "database is locked" do tiến
  trình khác giữ file.
- `inTransaction(...)` — commit khi thành công, rollback khi lỗi (kể cả
  `RuntimeException`), dùng cho bước gộp + xóa trong aggregation.

---

## 4. Quy tắc an toàn luồng trong code

1. **Không bao giờ đụng Swing từ luồng worker.** Sự kiện service đến trên luồng
   UDP worker / scheduler, và `MainFrame.UiEventListener` chuyển tiếp mỗi callback
   bằng `SwingUtilities.invokeLater` (xem `MainFrame.java` dòng 152, 160, 169).
2. **Không bao giờ chặn EDT.** Kết nối TCP, đẩy cấu hình, nạp lịch sử, gửi email
   test đều chạy trong `SwingWorker` (`DashboardPanel`, `HistoryPanel`,
   `AlertPanel`, `SettingsPanel`). Kết quả trở về qua `done()` trên EDT.
3. **Tác vụ lên lịch phải bắt mọi RuntimeException.** Nếu một tick thoát ra ngoài
   exception, `ScheduledExecutorService` sẽ **hủy ngầm** tác vụ đó — agent sẽ
   ngừng báo cáo mà không có lỗi nào hiện ra. Vì thế mọi `tick()`, `safeCheck()`,
   `safeRun()` đều bọc try/catch và chỉ log.
4. **Lỗi của listener GUI chỉ bị log và bỏ qua.** `MonitorEventBus.dispatch()`
   bọc từng listener bằng try/catch — lỗi giao diện không thể làm hỏng đường đi
   telemetry.
5. **Không tạo luồng theo gói / theo kết nối.** Gói UDP vào worker theo hash địa
   chỉ nguồn (pool 4), kết nối TCP vào pool 8; vượt quá thì xử lý theo chính
   sách hàng đợi chứ không `new Thread`.
6. **Thứ tự xử lý có ý nghĩa nên được duy trì bằng cách chọn luồng, không bằng
   khóa**: một datagram luôn đến đúng worker được chọn theo `source.hashCode()`
   → mọi packet của cùng một agent được xử lý **theo thứ tự đến**, nhờ đó kiểm
   tra replay không vô tình từ chối packet tốt.
7. **Bí mật không bao giờ đi vào luồng log**: `AgentConfig.toString()` không chứa
   `sharedSecret`, banner không in nó, mật khẩu SMTP bị thay bằng `***` trước khi
   vào thông báo lỗi.
8. **Thao tác dừng luôn đặt ràng buộc thời gian**: mọi `shutdown()` đều
   `awaitTermination` (2 s với scheduler/pool worker, 5 s với email) rồi mới
   `shutdownNow()`; đóng socket (`DatagramSocket.close()`, `ServerSocket.close()`)
   là cách giải phóng bị chặn trong `receive()`/`accept()`.

---

## 5. Xử lý khi hàng đợi đầy

### 5.1 Hàng đợi UDP worker — `CallerRunsPolicy` (giữ luồng nhận chạy chậm lại)

```java
new ThreadPoolExecutor(1, 1, 0L, MILLISECONDS,
        new ArrayBlockingQueue<>(2000),          // QueueCapacity = 2000
        threadFactory,                            // "udp-worker-N"
        new ThreadPoolExecutor.CallerRunsPolicy())// <-- chính sách
```

Khi một worker có 2000 phần tử đang chờ, `execute()` **không vứt bỏ** và **không
ném exception**: chính luồng `udp-receiver` sẽ tự chạy việc xử lý gói đó. Hệ
quả:

- Việc xử lý chậm lại làm chậm luôn vòng `receive()` → gói đến chậm được
  chứa trong buffer kernel (được yêu cầu 1 MiB qua
  `setReceiveBufferSize(1024 * 1024)`) thay vì phình hàng đợi bộ nhớ.
- Đây chính là **back pressure** (áp lực ngược): làm chậm nguồn vào thay vì để
  bộ nhớ cạn kiệt — là hành vi bài kiểm thử tải 20 agent kiểm tra
  (Javadoc `UdpTelemetryReceiver`).
- Đổi lại, việc chạy trên luồng nhận có thể làm **đảo thứ tự tương đối** so với
  các phần tử còn trong hàng đợi; đó là lý do thứ hai (sau bản chất UDP không
  đảm bảo thứ tự) khiến kiểm tra replay dùng **cửa sổ 64 bit** thay vì đòi
  sequence tăng nghiêm ngặt liên tiếp.

### 5.2 Hàng đợi email — vứt có kiểm soát (AbortPolicy)

```java
new ThreadPoolExecutor(1, 1, 0L, MILLISECONDS,
        new ArrayBlockingQueue<>(50),            // QUEUE_CAPACITY = 50
        threadFactory,                            // "email-sender"
        /* mặc định = AbortPolicy */);
```

Khi 50 email chờ đã đầy (ví dụ mail server treo), lần `execute()` kế tiếp ném
`RejectedExecutionException`; `EmailNotifier.submit()` bắt nó và làm đúng ba
điều: `droppedCount++`, log WARNING `"Email notification dropped, the send
queue is full"`, **dừng lại**. Không có email nào khác bị xếp vào — bộ nhớ có
giới hạn, và việc mất email không làm gì vì cảnh báo vẫn hiện trên GUI và vẫn
được ghi vào bảng `alerts`.

### 5.3 Kết nối TCP đến nơi nhận quá tải

`TcpControlServer.acceptLoop()` bắt `RuntimeException` quanh
`sessionPool.submit(...)` (comment trong code nêu rõ "a rejected submission once
the pool is shutting down") và **tiếp tục vòng lặp** — một client xấu không thể
làm sập kênh điều khiển. Với pool đang mở, tối đa 8 phiên đồng thời; phiên thứ
9 sẽ chờ đến khi có luồng rảnh (giới hạn của `newFixedThreadPool`, BlockingQueue
unbounded cho job chờ — giới hạn thực tế nằm ở số luồng là 8).

### 5.4 Gói UDP quá lớn

Ở phía gửi, `UdpTelemetrySender.send()` từ chối payload > 4096 byte ngay trước
khi gửi (đếm `failedCount`, log WARNING) — tránh datagram bị tầng IP cắt cụt.
Ở phía nhận, buffer đọc cũng đúng 4096 byte
(`ProtocolConstants.MAX_UDP_PACKET_SIZE`).

---

## 6. Sơ đồ đồng bộ tổng hợp

```
                      AGENT                                  MONITOR
+-------------------------------------------+   +------------------------------------------+
| main thread (đỗ trên CountDownLatch)      |   | EDT  <--- invokeLater ---+               |
|                                           |   |  |                       |               |
| agent-scheduler-1 ─┐                      |   |  +-- SwingWorker --------+               |
| agent-scheduler-2 ─┼─ AtomicLong sequence |   |       (connect, push cfg,                |
|                    │  DatagramSocket chung|   |        history, test mail)               |
|                    v                      |   |                                          |
|             UdpTelemetrySender  === UDP ==> |   | udp-receiver ──copy──> udp-worker-0..3   |
|                                            |   |                           |              |
| agent-tcp-accept ──> pool 8 session        |   |   (queue 2000, CallerRunsPolicy)         |
|        ^                 |                |   |                           v              |
|        |          synchronized:            |   |                    TelemetryService       |
|        |          ConfigUpdateService,     |   |                     |  |  |              |
|        |          AgentTcpConnection,      |   |        ConcurrentHashMap registry/alert  |
|        |          MessageFraming(out)      |   |                     v  v  v              |
|        |                 |                |   |        DB (1 khóa Object) + EDT           |
|   TCP 5000 <====== TCP điều khiển ======> |   |        monitor-scheduler-1..2             |
+-------------------------------------------+   |        email-sender (queue 50, drop)      |
                                                +------------------------------------------+
```

Tổng số luồng tối đa (không tính EDT và các SwingWorker tạm thời):

| Tiến trình | Thành phần | Số luồng |
|---|---|---|
| Agent | main + shutdown hook + 2 scheduler + 1 accept + tối đa 8 session | tối đa **13** (cộng 1 luồng shutdown hook khi thoát) |
| Monitor | main + 1 receiver + 4 worker + 2 scheduler + 1 email + 1 shutdown | tối đa **10** (cộng EDT + SwingWorker tạm) |

---

## Tài liệu liên quan

- [09-kien-truc-goc.md](09-kien-truc-goc.md) — phân tầng, luồng dữ liệu, mô hình luồng.
- [10-giao-thuc-goc.md](10-giao-thuc-goc.md) — giao thức wire, framing, chuỗi ký, ví dụ.
- [11-co-so-du-lieu-goc.md](11-co-so-du-lieu-goc.md) — bảng, index, retention.
- [04-giao-thuc-tcp-udp.md](04-giao-thuc-tcp-udp.md) — giao thức TCP/UDP, cấu trúc từng message.
- [05-bao-mat-hmac-va-chong-phat-lai.md](05-bao-mat-hmac-va-chong-phat-lai.md) — xác thực HMAC-SHA256 và chống phát lại.
- [06-luu-tru-va-tong-hop.md](06-luu-tru-va-tong-hop.md) — lưu trữ SQLite, tổng hợp và retention.
- [07-canh-bao-va-thong-bao.md](07-canh-bao-va-thong-bao.md) — hệ thống cảnh báo và gửi email.
