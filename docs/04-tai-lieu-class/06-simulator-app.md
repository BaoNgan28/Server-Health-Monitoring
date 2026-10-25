# Tài liệu lớp — simulator-app

Module `simulator-app` là công cụ tạo lưu lượng kiểm thử (test traffic generator) dành cho hệ thống giám sát: nó giả lập các agent thật sự gửi telemetry/heartbeat đã ký HMAC qua UDP, đồng thời tạo các gói tấn công kiểm thử bảo mật — chữ ký sai (invalid HMAC), gói bị phát lại (replay) — và chạy đồng thời nhiều danh tính agent để đo tải (load). Mục tiêu là kiểm chứng phía Monitor: gói hợp lệ luôn được nhận, gói gian lận bị từ chối, và giao diện/luồng nhận dữ liệu vẫn ổn định khi bị "tràn" gói.

- Artifact Maven: `vn.edu.hutech.group10:simulator-app:1.0.0` (pom cha: `server-health-monitoring`).
- Package gốc: `vn.edu.hutech.group10.monitoring.simulator`.
- Phụ thuộc (theo `simulator-app\pom.xml`): chỉ `monitoring-common` (không có OSHI, không có thư viện ngoài nào khác).
- Build ra jar gộp `simulator-app\target\simulator-app-1.0.0-all.jar` (maven-shade-plugin), main class `vn.edu.hutech.group10.monitoring.simulator.SimulatorMain`.
- Đây là module **test**: không có file test JUnit riêng và không được module nào khác tham chiếu bằng Java API — nó chỉ được gọi từ dòng lệnh và từ các script `scripts\run-invalid-hmac-test.bat`, `scripts\run-replay-test.bat`, `scripts\run-load-test.bat`.

---

## 1. Bảng tổng quan class

| # | Class | Package | Vai trò | File nguồn |
|---|-------|---------|---------|------------|
| 1 | `SimulatorMain` | `simulator` | Điểm vào console: đọc mode (`valid` / `invalid-hmac` / `replay` / `load`), parse tùy chọn, phân luồng tới class chế độ tương ứng, in usage | `simulator-app\src\main\java\vn\edu\hutech\group10\monitoring\simulator\SimulatorMain.java` |
| 2 | `SimulatorOptions` | `simulator` | Tùy chọn dòng lệnh dùng chung cho cả 4 mode; tự có giá trị mặc định để chạy được ngay | `...\simulator\SimulatorOptions.java` |
| 3 | `PacketFactory` | `simulator` | Dựng gói Telemetry/Heartbeat "có vẻ thật", có hoặc chưa ký HMAC | `...\simulator\PacketFactory.java` |
| 4 | `SimulatorUdpSender` | `simulator` | Người gửi UDP tối giản: JSON hóa và gửi datagram, đếm số gói đã gửi | `...\simulator\SimulatorUdpSender.java` |
| 5 | `ValidAgentSimulator` | `simulator` | Mode `valid`: gửi telemetry + heartbeat ký đúng secret (trường hợp đối chứng) | `...\simulator\ValidAgentSimulator.java` |
| 6 | `InvalidHmacSender` | `simulator` | Mode `invalid-hmac`: 3 kiểu gói gian lận chữ ký phải bị Monitor từ chối | `...\simulator\InvalidHmacSender.java` |
| 7 | `ReplayPacketSender` | `simulator` | Mode `replay`: gửi một gói hợp lệ rồi gửi lại y hệt (phát lại) | `...\simulator\ReplayPacketSender.java` |
| 8 | `LoadSimulator` | `simulator` | Mode `load`: nhiều danh tính agent cùng lúc để thử tải phía Monitor | `...\simulator\LoadSimulator.java` |

Không có test JUnit nào trong `simulator-app\src\test` (thư mục `src\test` tồn tại nhưng rỗng, không có file nào). Kết quả kỳ vọng của từng mode được mô tả ngay trong thông báo của chính class đó (in ra console khi chạy) và được ghi nhận trong `docs\03-huong-dan-su-dung\09-bao-cao-kiem-thu.md`.

---

## 2. Vòng đời (luồng điều phối)

```
java -jar simulator-app-1.0.0-all.jar <mode> [--host=...] [--port=...] ...
        |
        v
[1] LoggingSetup.configure("simulator")        --> file log "simulator-%g.log" trong logs/
        |
        v
[2] args rỗng  --> printUsage() + exit(1)
        |
        v
[3] mode = args[0].toLowerCase()  ;  SimulatorOptions.parse(args)
        | parse lỗi (vd --duration=abc) --> in lỗi + printUsage() + exit(2)
        v
[4] In banner "GROUP 10 - SERVER HEALTH MONITORING - SIMULATOR" + mode
        |
        +----------------+----------------+----------------+----------------+
        |                |                |                |                |
   mode=valid      mode=invalid-hmac  mode=replay      mode=load      khác
        |                |                |                |          printUsage()
        v                v                v                v           exit(2)
 ValidAgentSimulator  InvalidHmacSender  ReplayPacketSender  LoadSimulator
        \                |                |                /
         \               |                |               /
          +---- mọi mode đều: new SimulatorUdpSender(host, port)
                              PacketFactory...(...)  --> sender.send(...)  --> Monitor UDP
        |
        v
[5] finish  --> in thống kê / "Expected on the monitoring client"
        |
   InterruptedException --> in "Interrupted." (không exit code khác)
   Exception khác       --> in "Simulator failed: ..." + exit(3)
```

Bốn mode chạy tuần tự (mỗi lần chạy một mode), không chạy song song trong cùng tiến trình.

---

## 3. Chi tiết từng class

### 3.1 SimulatorMain

- **Định nghĩa:** `simulator-app\src\main\java\vn\edu\hutech\group10\monitoring\simulator\SimulatorMain.java`
- **Vai trò:** điểm vào console, phân công mode, xử lý lỗi và mã thoát.
- **Thành phần chính:**
  - Constructor private (class utility).
  - `main(String[])` — thứ tự: `LoggingSetup.configure("simulator")` → bắt args rỗng → `SimulatorOptions.parse(args)` → in banner → `switch (mode)`:
    | mode (đã lowercase + trim) | Class được tạo và `run()` |
    |----------------------------|---------------------------|
    | `valid` | `new ValidAgentSimulator(options).run()` |
    | `invalid-hmac` | `new InvalidHmacSender(options).run()` |
    | `replay` | `new ReplayPacketSender(options).run()` |
    | `load` | `new LoadSimulator(options).run()` |
    | khác | in "Unknown mode: X" + usage + `exit(2)` |
  - `printUsage()` — liệt kê 4 mode và 9 tùy chọn: `--host=127.0.0.1`, `--port=6000`, `--agent=AGENT-01`, `--secret=...`, `--hostname=SIMULATOR`, `--agents=20`, `--agent-prefix=LOAD-AGENT-`, `--duration=60`, `--interval=1000`.
- **Được gọi ở đâu:**
  - `simulator-app\pom.xml` — `<main.class>` cho jar.
  - Các script: `scripts\run-invalid-hmac-test.bat` (mode `invalid-hmac --host=127.0.0.1 --port=6000 --agent=AGENT-01`), `scripts\run-replay-test.bat`, `scripts\run-load-test.bat` (mode `load --agents=20 --duration=60`).
  - README.md / 04-huong-dan-netbeans.md bảng main class của module.
- **Quan hệ:** dùng `SimulatorOptions` và 4 class mode; `LoggingSetup` từ `monitoring-common`.
- **Ghi chú — mã thoát:** `1` = không có đối số; `2` = parse tùy chọn thất bại hoặc mode lạ; `3` = lỗi runtime khi chạy mode; `InterruptedException` thì đặt lại cờ ngắt và in "Interrupted." (không gọi `System.exit`).

### 3.2 SimulatorOptions

- **Định nghĩa:** `simulator-app\src\main\java\vn\edu\hutech\group10\monitoring\simulator\SimulatorOptions.java`
- **Vai trò:** tập tùy chọn dòng lệnh dùng chung cho mọi mode, có giá trị mặc định nên chạy không flag vẫn hoạt động.
- **Thành phần chính — trường + mặc định (lấy từ code):**

  | Trường | Mặc định | Flag tương ứng (có alias) |
  |--------|----------|---------------------------|
  | `monitorHost` | `127.0.0.1` | `--host`, `--monitor-host` |
  | `monitorPort` | `6000` (`ProtocolConstants.DEFAULT_MONITOR_UDP_PORT`) | `--port`, `--monitor-port` |
  | `agentId` | `AGENT-01` | `--agent`, `--agent-id` |
  | `hostname` | `SIMULATOR` | `--hostname` |
  | `secret` | `GROUP10_DEMO_SECRET_01` | `--secret` |
  | `agents` | `20` | `--agents` (mode load) |
  | `durationSeconds` | `60` | `--duration` |
  | `intervalMs` | `1000` (`ProtocolConstants.DEFAULT_METRIC_INTERVAL_MS`) | `--interval` |
  | `agentPrefix` | `LOAD-AGENT-` | `--agent-prefix` (mode load) |

  - `static parse(String[])` — chỉ nhận dạng `--name=value` (phải có dấu `=`; không có thì bỏ qua, kể cả từ mode ở `args[0]`); tên flag được `trim().toLowerCase()`; giá trị được `strip()` (bỏ nháy kép bọc ngoài); flag lạ in "Ignoring unknown option --x" và **không** lỗi.
  - `parseInt(name, value)` — lỗi số → `IllegalArgumentException("--<name> must be a whole number, got ...")`.
  - `describe()` — chuỗi tóm tắt `monitor=host:port, agent=..., agents=..., duration=...s, interval=...ms`; **cố ý không chứa secret** (Javadoc: "The shared secret is deliberately not included").
  - Getter cho cả 9 trường.
- **Được gọi ở đâu:** `SimulatorMain.parse()`; được truyền vào ctor của cả 4 class mode (`ValidAgentSimulator`, `InvalidHmacSender`, `ReplayPacketSender`, `LoadSimulator`).
- **Quan hệ:** không kế thừa; dùng `ProtocolConstants` (monitoring-common).
- **Ghi chú:** secret mặc định `GROUP10_DEMO_SECRET_01` khớp giá trị trong `config\agent01.properties` — nên mode `valid` chạy "nguyên trạng" là đã ký đúng cho `AGENT-01`.

### 3.3 PacketFactory

- **Định nghĩa:** `simulator-app\src\main\java\vn\edu\hutech\group10\monitoring\simulator\PacketFactory.java`
- **Vai trò:** dựng các gói mà simulator gửi ra (class utility, `final`, constructor private).
- **Thành phần chính:**
  - `static Random RANDOM` — dữ liệu chỉ số giả ngẫu nhiên.
  - `static final String SESSION_ID = "SIM-" + UUID.randomUUID()` — **một session id cho cả tiến trình simulator**; Javadoc: vì khác session của agent thật nên simulator có "cửa sổ chống replay" riêng và không làm nhiễu agent mà nó đang giả danh.
  - `sessionId()` — trả SESSION_ID.
  - `telemetry(agentId, hostname, sequence)` — gói TelemetryPacket **chưa ký**, các giá trị "đủ thật":

    | Trường | Giá trị sinh ra |
    |--------|------------------|
    | `protocolVersion` | `"1.0"` |
    | `type` | `"TELEMETRY"` |
    | `sessionId` | `SESSION_ID` |
    | `timestamp` | `System.currentTimeMillis()` |
    | `sequence` | tham số truyền vào |
    | `cpuUsage` | 10.0 + random*40 → khoảng 10..50 (%), làm tròn 2 chữ số thập phân |
    | `memoryUsage` | 30.0 + random*40 → khoảng 30..70 (%) |
    | `connectionCount` | `20 + random(80)` → 20..99 |
    | `uptimeSeconds` | `3600 + sequence` |
    | `networkInBytesPerSecond` | 0..500 000 |
    | `networkOutBytesPerSecond` | 0..250 000 |

  - `signedTelemetry(agentId, hostname, sequence, secret)` — `telemetry(...)` rồi `PacketSigner.sign(packet, secret)`.
  - `signedHeartbeat(agentId, hostname, sequence, secret)` — HeartbeatPacket đầy đủ trường nền tảng (`protocolVersion`, `type = "HEARTBEAT"`, `agentId`, `hostname`, `sessionId`, `timestamp`, `sequence`) rồi ký.
  - `round(double)` — `Math.round(value*100)/100` (làm tròn 2 chữ số).
- **Được gọi ở đâu:** cả 4 class mode (`ValidAgentSimulator`, `InvalidHmacSender`, `ReplayPacketSender`, `LoadSimulator`).
- **Quan hệ:** dùng model `TelemetryPacket`/`HeartbeatPacket`, `MessageType`, `ProtocolConstants`, `PacketSigner` (cùng `monitoring-common` — ký đúng cách mà `PacketValidator` của Monitor kiểm tra).
- **Ghi chú:** giá trị CPU/RAM giả chỉ để bảng dashboard có số "hợp lý" — không phải số đo thật; không có giới hạn nào được kiểm tra trong factory.

### 3.4 SimulatorUdpSender

- **Định nghĩa:** `simulator-app\src\main\java\vn\edu\hutech\group10\monitoring\simulator\SimulatorUdpSender.java`
- **Vai trò:** người gửi UDP tối giản dùng cho mọi mode.
- **Thành phần chính:**
  - Trường: `codec = new JsonMessageCodec()`, `socket = new DatagramSocket()`, `address` (resolve ngay trong ctor qua `InetAddress.getByName(host)`), `port`, `long sentCount`.
  - Ctor ném `SocketException`, `UnknownHostException`.
  - `send(Object)` — `codec.toBytes(message)` rồi `socket.send(new DatagramPacket(...))`, tăng `sentCount`; ném `IOException` (không tự nuốt như `UdpTelemetrySender` của agent — mỗi mode tự xử lý).
  - `getSentCount()`, `getCodec()`, `close()` (`implements AutoCloseable`, đóng socket).
- **Được gọi ở đâu:** 4 class mode, luôn dùng trong khối `try (SimulatorUdpSender sender = new SimulatorUdpSender(...))` nên socket được đóng khi xong hoặc khi có lỗi.
- **Quan hệ:** `implements AutoCloseable`; phụ thuộc `JsonMessageCodec`.
- **Ghi chú:** khác với sender của agent, sender này **không** giới hạn kích thước gói và **không** đếm gói lỗi. `sentCount++` là thao tác không nguyên tử (không `AtomicLong`); trong mode `load` một `SimulatorUdpSender` được dùng chung bởi tối đa 4 thread gửi (`poolSize = Math.min(4, agents)`), nên con số `getSentCount()` chỉ mang tính xấp xỉ (đây là quan sát từ code; `LoadSimulator` lại không dùng `getSentCount()` để in thống kê mà dùng bộ đếm riêng `AtomicLong sent`, nên thống kê cuối vẫn chính xác).

### 3.5 ValidAgentSimulator

- **Định nghĩa:** `simulator-app\src\main\java\vn\edu\hutech\group10\monitoring\simulator\ValidAgentSimulator.java`
- **Vai trò:** chế độ đối chứng — gửi đúng như agent thật, Monitor **phải luôn chấp nhận** các gói này, bất kể kết quả của 3 mode bảo mật kia.
- **Thành phần chính:**
  - Ctor nhận `SimulatorOptions`.
  - `run()` (`throws IOException, InterruptedException`):
    1. in `"Valid agent simulator: " + options.describe()`;
    2. mở `SimulatorUdpSender`;
    3. `sequence = 0` (bắt đầu từ 1, Javadoc: session riêng nên không phá cửa sổ replay của agent thật);
    4. `deadline = now + duration*1000`;
    5. `heartbeatEvery = max(1, 5000 / max(1, intervalMs))` — với interval 1000 ms mặc định → cứ 5 gói telemetry gửi 1 heartbeat (tức ~5 giây/lần, trùng nhịp `heartbeat.interval.ms` mặc định của agent);
    6. vòng lặp tới deadline: gửi `signedTelemetry(++sequence, secret)`, cứ `heartbeatEvery` tick gửi thêm `signedHeartbeat(++sequence)`, in `sent seq=... cpu=...% mem=...%`, `Thread.sleep(intervalMs)`;
    7. in tổng số gói đã gửi (`sender.getSentCount()`).
- **Được gọi ở đâu:** `SimulatorMain` case `"valid"`.
- **Quan hệ:** dùng `PacketFactory`, `SimulatorUdpSender`, `SimulatorOptions`, model `TelemetryPacket`.
- **Ghi chú:** heartbeat dùng chung dãy `sequence` với telemetry (một bộ đếm cho cả hai loại, đúng như agent thật làm).

### 3.6 InvalidHmacSender

- **Định nghĩa:** `simulator-app\src\main\java\vn\edu\hutech\group10\monitoring\simulator\InvalidHmacSender.java`
- **Vai trò:** mạo danh agent đã đăng ký với chữ ký giả; Monitor phải từ chối cả 3 biến thể.
- **Thành phần chính:**
  - Hằng: `WRONG_SECRET = "ATTACKER_GUESSED_SECRET"`, `JUNK_SIGNATURE = "0000...deadbeef"` (56 ký tự `0` nối tiếp rồi tới `deadbeef`, tổng 64 ký tự = 32 byte dạng hex — đúng độ dài của HMAC-SHA256 nên validator không thể phân biệt bằng mắt thường, nhưng giá trị thì sai).
  - `run()` — gửi lần lượt 3 gói (giãn cách 500 ms), mỗi gói là `spike(sequence)` = telemetry hợp lệ nhưng `cpuUsage = 100.0`, `memoryUsage = 100.0`:
    1. `1/3`: gắn `hmac = JUNK_SIGNATURE` (chữ ký rác).
    2. `2/3`: `PacketSigner.sign(packet, WRONG_SECRET)` (ký bằng khóa sai).
    3. `3/3`: `PacketFactory.signedTelemetry(..., options.getSecret())` ký **đúng** rồi mới `setCpuUsage(100.0)` — sửa nội dung sau khi đã ký; đây chính là thứ chữ ký sinh ra để phát hiện.
  - In ra "Expected on the monitoring client": dashboard vẫn hiện CPU thật; không có dòng mới trong bảng `metric_raw`; không có cảnh báo `CPU_HIGH`; có `SECURITY_INVALID_HMAC` trong log và bảng cảnh báo.
  - `spike(long sequence)` — helper riêng tư dựng gói CPU/RAM = 100%.
- **Được gọi ở đâu:** `SimulatorMain` case `"invalid-hmac"`; script `scripts\run-invalid-hmac-test.bat`.
- **Quan hệ:** dùng `PacketFactory`, `PacketSigner`, `SimulatorUdpSender`.
- **Ghi chú:** vì session id riêng (`SIM-...`), các gói này không làm xáo trộn (không ảnh hưởng) cửa sổ replay của agent thật; Monitor ánh xạ `SECURITY_INVALID_HMAC` từ kết quả kiểm tra trong `monitor-app\...\service\TelemetryService.java`.

### 3.7 ReplayPacketSender

- **Định nghĩa:** `simulator-app\src\main\java\vn\edu\hutech\group10\monitoring\simulator\ReplayPacketSender.java`
- **Vai trò:** gửi một gói hợp lệ rồi gửi lại **đúng từng byte** — cuộc tấn công mà chỉ riêng chữ ký không chặn được, chỉ số sequence (số thứ tự) mới phân biệt được.
- **Thành phần chính:**
  - `run()` — 4 bước, giãn cách 1000 ms:
    1. Gửi `original` (sequence = 1, signed) — **phải ACCEPT**.
    2. Gửi lại `original` — **phải REJECTED** (replay).
    3. Gửi `original` lần ba — **phải REJECTED**.
    4. Gửi `fresh` = gói ký mới với `sequence = original.getSequence() + 1` (= 2) — **phải ACCEPT**.
  - In kỳ vọng: gói 1 và 4 được nhận, gói 2 và 3 bị chặn với `SECURITY_REPLAY_PACKET` trong log.
- **Được gọi ở đâu:** `SimulatorMain` case `"replay"`; script `scripts\run-replay-test.bat`.
- **Quan hệ:** dùng `PacketFactory`, `SimulatorUdpSender`.
- **Ghi chú:** gửi cùng **một đối tượng** `original` nhiều lần (không dựng lại), nên nội dung JSON giống hệt nhau — đúng đặc trưng replay. Việc chấp nhận/từ chối do `ReplayProtector` + `PacketValidator` phía `monitoring-common`/Monitor thực hiện, không nằm trong module này.

### 3.8 LoadSimulator

- **Định nghĩa:** `simulator-app\src\main\java\vn\edu\hutech\group10\monitoring\simulator\LoadSimulator.java`
- **Vai trò:** chạy nhiều danh tính agent cùng lúc để chứng minh Monitor theo kịp và không sập dưới "trận mưa" gói.
- **Thành phần chính:**
  - `run()` — các giá trị được siết về hợp lệ: `agents = max(1, options.getAgents())`, `duration = max(1, options.getDurationSeconds())`.
  - In dòng đầu: số danh tính, thời gian, interval, đích `host:port`.
  - Bộ đếm `AtomicLong sent`, `AtomicLong failed`, `AtomicInteger threadCounter`.
  - Thread factory đặt tên `load-sender-<n>`, daemon.
  - Pool: `poolSize = Math.min(4, agents)` — Javadoc: nhiều danh tính dùng chung vài thread vì công việc mỗi tick chỉ là một lần gửi; "một công cụ tạo tải tự sập trước thì chẳng chứng minh được gì về bên nhận".
  - Với mỗi danh tính `i = 1..agents`:
    - `agentId = String.format("%s%02d", agentPrefix, i)` → `LOAD-AGENT-01`, `LOAD-AGENT-02`, ...;
    - `AtomicLong sequence` riêng cho mỗi danh tính;
    - `stagger = (intervalMs * i) / agents` — lệch giờ khởi động để mọi identity không bắn cùng một mili giây;
    - `scheduleAtFixedRate(gửi signedTelemetry(agentId, agentId, seq, secret), stagger, intervalMs, MILLISECONDS)` — **hostname = agentId**;
    - lỗi gửi → tăng `failed`.
  - Vòng báo cáo: mỗi 5 giây in `"<sent> packet(s) sent, <failed> failed"`.
  - Dừng: `executor.shutdown()` → chờ 5 giây → `shutdownNow()`; in bảng tổng kết: `identities`, `duration`, `packets sent`, `packets failed`, `average rate = total/duration` (gói/giây).
  - `finally` gọi `executor.shutdownNow()` để không rò thread.
  - In kỳ vọng: cửa sổ Monitor vẫn phản hồi, bộ đếm gói nhận khớp, số thread không tăng.
- **Được gọi ở đâu:** `SimulatorMain` case `"load"`; script `scripts\run-load-test.bat` (bản release: `load --host=127.0.0.1 --port=6000 --agents=20 --duration=60`).
- **Quan hệ:** dùng `PacketFactory`, `SimulatorUdpSender`, `SimulatorOptions`; phụ thuộc `java.util.concurrent`.
- **Ghi chú (quan trọng, từ Javadoc):** mặc định các identity `LOAD-AGENT-0x` **chưa đăng ký** trên Monitor nên mọi gói bị từ chối với `UNKNOWN_AGENT` — đó là **kết quả mong muốn**: chế độ này kiểm tra Monitor "theo kịp và còn sống dưới cơn lũ", mà từ chối người gửi chưa đăng ký chính là hành vi đúng. Muốn thấy chúng được chấp nhận thì phải đăng ký identity trước và truyền secret bằng `--secret`.

---

## 4. Sơ đồ quan hệ

```
                        (module: simulator-app)

   dòng lệnh / scripts\run-*.bat
              |
              v
       +--------------+      parse       +--------------------+
       | SimulatorMain| ---------------> | SimulatorOptions   |
       +------+-------+                  | (host,port,agent,  |
              | dispatch theo mode       |  secret,agents,...)|
              |                          +---------+----------+
              |                                    | (truyền cho 4 mode)
   +----------+-----------+------------+-----------+
   |          |           |           |               |
   v          v           v           v               v
 valid   invalid-hmac   replay      load        (mỗi mode giữ
   |          |           |           |          1 SimulatorOptions)
   v          v           v           v
+---------------------+   |           |
| ValidAgentSimulator |   |           |
+----------+----------+   |           |
           |              |           |
           |    +---------+-----------+---------+
           |    |         |                     |
           |    v         v                     v
           |  +--------------------+   +-----------------+
           +->| PacketFactory      |   | LoadSimulator   |
              | telemetry()        |   | (pool <= 4 tb.) |
              | signedTelemetry()  |   +--------+--------+
              | signedHeartbeat()  |            |
              | sessionId()        |            |
              +---------+----------+            |
                        |                       |
              +---------v----------+            |
              | PacketSigner.sign  |<-----------+  (monitoring-common)
              | (HmacSHA256)       |
              +---------+----------+
                        |
              +---------v----------+     JSON bytes      +--------------------+
              | SimulatorUdpSender | ------------------> | Monitor (UDP 6000) |
              | (DatagramSocket)   |                     | UdpTelemetryReceiver|
              +--------------------+                     | -> PacketValidator  |
                                                         +--------------------+

  Phần dùng chung (monitoring-common): JsonMessageCodec, MessageType,
  ProtocolConstants, PacketSigner/PacketCanonicalizer/HmacUtil,
  TelemetryPacket, HeartbeatPacket, LoggingSetup.
  Monitor (monitor-app) kiểm tra và sinh cảnh báo:
  SECURITY_INVALID_HMAC / SECURITY_REPLAY_PACKET / UNKNOWN_AGENT
  (xem monitor-app\...\service\TelemetryService.java và AgentRegistry.java).
```

---

## 5. Lệnh mẫu và kết quả kỳ vọng

| Lệnh | Nguồn | Kết quả kỳ vọng phía Monitor |
|-------|--------|------------------------------|
| `java -jar simulator-app-1.0.0-all.jar valid --agent=AGENT-01 --duration=60` | usage của `SimulatorMain` | Mọi gói được chấp nhận; dashboard hiện chỉ số giả (CPU 10..50%, RAM 30..70%) |
| `java -jar simulator-app-1.0.0-all.jar invalid-hmac --host=127.0.0.1 --port=6000 --agent=AGENT-01` | `scripts\run-invalid-hmac-test.bat` | 3 gói bị từ chối: không dòng mới trong `metric_raw`, không cảnh báo `CPU_HIGH`, log + bảng cảnh báo có `SECURITY_INVALID_HMAC` |
| `java -jar simulator-app-1.0.0-all.jar replay --host=127.0.0.1 --port=6000 --agent=AGENT-01 --secret=GROUP10_DEMO_SECRET_01` | `scripts\run-replay-test.bat` | Gói 1 và 4 được nhận; gói 2 và 3 bị chặn, log `SECURITY_REPLAY_PACKET` |
| `java -jar simulator-app-1.0.0-all.jar load --host=127.0.0.1 --port=6000 --agents=20 --duration=60` | `scripts\run-load-test.bat` | Monitor vẫn phản hồi; các gói `LOAD-AGENT-0x` bị từ chối `UNKNOWN_AGENT` (chưa đăng ký); số thread Monitor không tăng |

Lưu ý chung: `--secret` mặc định là `GROUP10_DEMO_SECRET_01` — phải khớp secret đã đăng ký cho `agentId` tương ứng trên Monitor thì mode `valid` mới được chấp nhận; nếu đổi `--agent` thì cũng phải đổi `--secret` cho khớp.

---

## 6. Điểm chưa xác định

- Không tìm thấy test JUnit nào của `simulator-app` (`src\test\java\...` tồn tại nhưng rỗng, không có file `.java`) — chưa xác định dự định bổ sung test hay đây là module chỉ chạy tay.
- `SimulatorOptions.parse` bỏ qua lặng lẽ các flag không có dấu `=` (kể cả `--host 1.2.3.4` dạng có khoảng cách) — chưa xác định đây là chủ ý hay hạn chế; code chỉ ghi "ignoring" với flag lạ có `=`.
- Số liệu trong mode `load` về phía Monitor (số gói nhận/từ chối chi tiết) không nằm trong module này; để đối chiếu cần xem log của `monitor-app` (chưa xác định giá trị kỳ vọng cụ thể ngoài `UNKNOWN_AGENT`).
- `SimulatorUdpSender.sentCount` không nguyên tử khi nhiều thread dùng chung (mode `load`) — hành vi "có thể mất đếm" là suy luận từ code, chưa được đo thực nghiệm.
