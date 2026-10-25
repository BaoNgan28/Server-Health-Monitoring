# Hướng dẫn nhanh các file script (`.bat` / `.ps1`)

Tài liệu mô tả từng file trong thư mục `scripts\` của dự án Server Health Monitoring
System (Group 10). Nội dung lấy trực tiếp từ code script, không mô tả thêm những gì
không có trong script.

* Dạng file:
  * `.bat` = batch file của Windows (chạy trong `cmd` hoặc double-click trong Explorer).
  * `.ps1` = PowerShell script (phải chạy bằng PowerShell, file firewall cần quyền Administrator).
* `%~dp0` trong script = đường dẫn thư mục chứa script (kèm dấu `\` cuối), nên mọi
  script đều tự quay về thư mục gốc dự án bằng `cd /d "%~dp0.."` — nghĩa là bạn có
  thể gọi script từ bất kỳ thư mục nào.

---

## Bảng tổng quan

| Script | Loại | Mục đích 1 dòng | Khi nào dùng |
|---|---|---|---|
| `build.bat` | build | Chạy `mvn clean package` cho toàn bộ 4 module, sinh 3 file jar thực thi `-all.jar` | Lần đầu và sau khi đổi mã nguồn |
| `test.bat` | test | Chạy toàn bộ unit/integration test bằng `mvn clean test` | Trước khi bàn giao, sau khi sửa mã |
| `find-maven.bat` | build (trợ giúp) | Tìm lệnh Maven (`mvn` trên PATH hoặc bản kèm NetBeans), ghi vào biến `MVN_CMD` | Không chạy trực tiếp; được `build.bat` / `test.bat` gọi |
| `run-monitor.bat` | run | Khởi động Monitoring Client với `config\monitor.properties` | Chạy môi trường production (giữ liệu 60 phút) |
| `run-monitor-demo.bat` | demo | Khởi động Monitoring Client với `config\monitor-demo.properties` | Demo/trình diễn (giữ liệu nén: 60 giây) |
| `run-agent01.bat` | run | Khởi động Agent 01 với `config\agent01.properties` | Chạy từng agent thủ công |
| `run-agent02.bat` | run | Khởi động Agent 02 với `config\agent02.properties` | Chạy từng agent thủ công |
| `run-agent03.bat` | run | Khởi động Agent 03 với `config\agent03.properties` | Chạy từng agent thủ công |
| `run-agent04.bat` | run | Khởi động Agent 04 với `config\agent04.properties` | Chạy từng agent thủ công |
| `run-local-demo.bat` | demo | Mở 5 cửa sổ: 1 monitoring client + 4 agent, tất cả trên một máy | Trình diễn toàn hệ thống trên 1 máy |
| `run-invalid-hmac-test.bat` | bảo mật | Gửi gói tin HMAC giả mạo qua `simulator-app` để client phải từ chối | Kiểm thử chống giả mạo chữ ký |
| `run-replay-test.bat` | bảo mật | Gửi 1 gói hợp lệ rồi gửi lại chính gói đó (phát hiện replay) | Kiểm thử chống gửi lại gói tin |
| `run-load-test.bat` | bảo mật | Đẩy lưu lượng 20 identity trong 60 giây, tất cả bị từ chối `UNKNOWN_AGENT` | Kiểm thử tải cho luồng nhận UDP |
| `firewall-agent.ps1` | firewall | Mở port TCP inbound (mặc định 5000) cho máy chạy agent | Triển khai nhiều máy, chạy 1 lần với Administrator |
| `firewall-monitor.ps1` | firewall | Mở port UDP inbound (mặc định 6000) cho máy chạy monitoring client | Triển khai nhiều máy, chạy 1 lần với Administrator |

---

## 1. Nhóm build / test

### `build.bat`

* **Mục đích:** Build toàn bộ dự án, tạo file jar thực thi (README mục 6).
* **Nội dung chính:**
  1. `cd /d "%~dp0.."` — chuyển về thư mục gốc dự án (thư mục cha của `scripts\`).
  2. `call "%~dp0find-maven.bat"` — tìm Maven; nếu lỗi (`ERRORLEVEL` khác 0) thì
     `pause` và `exit /b 1` (dừng, không build tiếp).
  3. In banner: `Project root: %CD%` và `Maven: %MVN_CMD%`.
  4. `call "%MVN_CMD%" clean package` — `clean` (xóa thư mục `target` cũ) rồi
     `package` (biên dịch + đóng gói jar). Không xóa file config hay database.
  5. Nếu thành công in `BUILD SUCCESS` kèm 3 đường dẫn jar; nếu thất bại in
     `BUILD FAILED with exit code ...` và hướng dẫn tìm dòng `[ERROR]` đầu tiên
     trong output Maven.
  6. Luôn `pause` (chờ gõ phím) rồi `exit /b %BUILD_RESULT%` để trả mã lỗi thật
     cho caller (script khác hoặc CI).
* **Cách chạy:**
  ```bat
  scripts\build.bat
  ```
  hoặc double-click `scripts\build.bat` trong Explorer. Điều kiện tiên quyết: JDK 17+
  và Maven (đã cài đặt và đưa lên PATH, hoặc có bản kèm NetBeans — xem
  `find-maven.bat`).
* **Kết quả mong đợi:** 3 file jar:
  ```
  monitor-app\target\monitor-app-1.0.0-all.jar
  agent-app\target\agent-app-1.0.0-all.jar
  simulator-app\target\simulator-app-1.0.0-all.jar
  ```
  Cửa sổ cmd in `BUILD SUCCESS` rồi chờ gõ phím (do có `pause`).
* **Ghi chú:** Script dừng lại chờ gõ phím ở mọi nhánh (thành công lẫn thất bại);
  các script `run-*` đều kiểm tra jar này và báo `Run scripts\build.bat first`
  nếu chưa build.

### `test.bat`

* **Mục đích:** Chạy toàn bộ suite test (unit + integration) của cả 4 module
  (README mục 19).
* **Nội dung chính:**
  1. `cd /d "%~dp0.."` về thư mục gốc dự án.
  2. `call "%~dp0find-maven.bat"`; lỗi thì `pause` + `exit /b 1`.
  3. `call "%MVN_CMD%" clean test` — chạy JUnit 5 qua Maven (không đóng gói jar).
  4. Thành công → in `ALL TESTS PASSED`; thất bại → in `TESTS FAILED with exit
     code ...` và chỉ chỗ xem chi tiết từng test:
     `<module>\target\surefire-reports` (thư mục báo cáo của Surefire — plugin
     test của Maven).
  5. `pause` rồi `exit /b %TEST_RESULT%`.
* **Cách chạy:**
  ```bat
  scripts\test.bat
  ```
  (double-click hoặc gọi trong `cmd`). Điều kiện: JDK 17+, Maven — **không bắt buộc
  đã chạy `build.bat`** vì lệnh là `clean test`, không cần jar sẵn.
* **Kết quả mong đợi:** Cửa sổ in `ALL TESTS PASSED` (README ghi 165 test), hoặc
  `TESTS FAILED` kèm đường dẫn `surefire-reports`.
* **Ghi chú:** Có `pause` ở cuối — cửa sổ không tự đóng. Chi tiết test: xem
  [`../03-huong-dan-su-dung/08-kiem-thu.md`](../03-huong-dan-su-dung/08-kiem-thu.md).

### `find-maven.bat`

* **Mục đích:** Định vị bộ khởi chạy Maven và ghi vào biến môi trường `MVN_CMD`.
* **Nội dung chính:**
  1. `set "MVN_CMD="` (đặt rỗng).
  2. `where mvn` — nếu `mvn` có trên PATH thì `MVN_CMD=mvn` và nhảy `:found`
     (`exit /b 0`).
  3. Nếu không, duyệt lần lượt 4 đường dẫn Maven kèm theo NetBeans (chỉ kiểm tra
     `if exist`, lấy cái đầu tiên tìm thấy):
     * `C:\Program Files\NetBeans-22\netbeans\java\maven\bin\mvn.cmd`
     * `C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd`
     * `C:\Program Files\NetBeans-20\netbeans\java\maven\bin\mvn.cmd`
     * `C:\Program Files\Apache NetBeans\netbeans\java\maven\bin\mvn.cmd`
  4. Không tìm thấy: in `[ERROR] Maven could not be found.` (khuyến nghị cài
     Apache Maven và đưa lên PATH, hoặc sửa `scripts\find-maven.bat` trỏ vào
     Maven kèm NetBeans) rồi `exit /b 1`.
* **Cách chạy:** Không chạy trực tiếp — comment trong file ghi rõ
  `Called by build.bat and test.bat; not meant to be run on its own.` (chỉ được
  gọi bởi `build.bat` và `test.bat`).
* **Kết quả mong đợi:** Biến `MVN_CMD` chứa `mvn` hoặc đường dẫn đầy đủ
  `mvn.cmd`; mã thoát 0 = tìm thấy, 1 = không tìm thấy.
* **Ghi chú:** Nếu máy bạn cài NetBeans ở phiên bản khác 20/21/22 hoặc thư mục
  khác, phải sửa file này (hoặc cài Maven đưa lên PATH).

---

## 2. Nhóm chạy Monitoring Client

Hai script này giống hệt nhau, khác duy nhất là file `--config` truyền vào.

### `run-monitor.bat`

* **Mục đích:** Khởi động Monitoring Client ở hồ sơ (profile) production
  (README mục 8).
* **Nội dung chính:**
  1. `cd /d "%~dp0.."` về thư mục gốc dự án.
  2. Kiểm tra jar `monitor-app\target\monitor-app-1.0.0-all.jar` có tồn tại
     không; nếu không: in `[ERROR] ... not found. Run scripts\build.bat first.`,
     `pause`, `exit /b 1`.
  3. Chạy:
     ```bat
     java -Dmonitoring.home="%CD%" -jar "%JAR%" --config="config\monitor.properties"
     ```
     * `-Dmonitoring.home="%CD%"` — ghim thư mục config/data/logs về thư mục gốc
       dự án, để client chạy đúng dù được khởi động từ đâu (comment trong script).
     * `--config="config\monitor.properties"` — hồ sơ production: UDP 6000,
       raw giữ 60 phút, bucket 5 phút, aggregate 30 ngày, DB
       `data/monitoring.db`.
  4. Nếu mã thoát khác 0: in `The monitoring client exited with code ...` và
     `pause`; sau đó `exit /b` mã đó.
* **Cách chạy:**
  ```bat
  scripts\run-monitor.bat
  ```
  Điều kiện tiên quyết: JDK 17+ (`java` trên PATH) và **đã chạy `build.bat`**.
* **Kết quả mong đợi:** Cửa sổ cmd mở ứng dụng Swing Monitoring Client; lần đầu
  tạo `data\monitoring.db`; log ghi vào thư mục `logs\`. Cửa sổ chỉ tự đóng khi
  ứng dụng thoát — nếu thoát bằng lỗi mới gặp `pause`.
* **Ghi chú:** Client nghe UDP port **6000** (theo `config\monitor.properties`).

### `run-monitor-demo.bat`

* **Mục đích:** Khởi động Monitoring Client ở hồ sơ demo (retention được nén để
  thấy aggregation trong vài phút).
* **Nội dung chính:** Giống `run-monitor.bat` từng dòng, chỉ khác dòng chạy:
  ```bat
  java -Dmonitoring.home="%CD%" -jar "%JAR%" --config="config\monitor-demo.properties"
  ```
  Theo `config\monitor-demo.properties`: `profile.name=demo`, UDP 6000,
  `raw.retention.seconds=60`, `aggregation.bucket.seconds=10`,
  `aggregate.retention.seconds=300`, `aggregation.interval.seconds=15`, và dùng
  database riêng `data/monitoring-demo.db` (comment trong file config: demo
  không được làm xáo trộn dữ liệu production).
* **Cách chạy:**
  ```bat
  scripts\run-monitor-demo.bat
  ```
  Điều kiện: JDK 17+, đã `build.bat`.
* **Kết quả mong đợi:** Cửa sổ Swing client dùng DB `data\monitoring-demo.db`
  (tự tạo nếu chưa có), log trong `logs\`.
* **Ghi chú:** Không chạy đồng thời `run-monitor.bat` và `run-monitor-demo.bat`
  — cả hai đều nghe UDP 6000 (điều này suy ra từ cùng cấu hình port, script
  không có cơ chế chống trùng port).

---

## 3. Nhóm chạy Agent (`run-agent01..04.bat`)

Bốn script có cấu trúc **giống hệt nhau**, khác nhau duy nhất: thông báo
`Starting AGENT-0N ...` và file config truyền vào.

### Cấu trúc chung (ví dụ `run-agent01.bat`)

* **Mục đích:** Khởi động một agent với file config tương ứng (README mục 9).
* **Nội dung chính:**
  1. `cd /d "%~dp0.."` về thư mục gốc dự án.
  2. Kiểm tra `agent-app\target\agent-app-1.0.0-all.jar`; nếu thiếu:
     `[ERROR] ... Run scripts\build.bat first.` + `pause` + `exit /b 1`.
  3. In `Starting AGENT-0N ...`.
  4. Chạy:
     ```bat
     java -Dmonitoring.home="%CD%" -jar "%JAR%" --config="config\agent0N.properties"
     ```
  5. Mã thoát khác 0 → in `AGENT-0N exited with code ...` + `pause`; `exit /b`.
* **Cách chạy (mỗi script một cửa sổ `cmd`):**
  ```bat
  scripts\run-agent01.bat
  scripts\run-agent02.bat
  scripts\run-agent03.bat
  scripts\run-agent04.bat
  ```
  Điều kiện: JDK 17+, đã `build.bat`. Chạy thử local thì monitoring client
  phải đang chạy (agent gửi UDP tới 127.0.0.1:6000).
* **Kết quả mong đợi:** Cửa sổ console agent in identity, endpoint và interval
  khi khởi động (theo README: agent không bao giờ in/log/gửi đi shared secret).
  Agent gửi telemetry mỗi 1000 ms, heartbeat mỗi 5000 ms.

### Các file config tương ứng (điểm khác biệt giữa 4 script)

| Script | Config | `agent.id` | `agent.tcp.port` (TCP control) | `security.sharedSecret` |
|---|---|---|---|---|
| `run-agent01.bat` | `config\agent01.properties` | AGENT-01 | 5001 | `GROUP10_DEMO_SECRET_01` |
| `run-agent02.bat` | `config\agent02.properties` | AGENT-02 | 5002 | `GROUP10_DEMO_SECRET_02` |
| `run-agent03.bat` | `config\agent03.properties` | AGENT-03 | 5003 | `GROUP10_DEMO_SECRET_03` |
| `run-agent04.bat` | `config\agent04.properties` | AGENT-04 | 5004 | `GROUP10_DEMO_SECRET_04` |

Cả 4 config đều dùng chung: `monitor.host=127.0.0.1`, `monitor.udp.port=6000`,
`metric.interval.ms=1000`, `heartbeat.interval.ms=5000`, ngưỡng cảnh báo CPU/mem
85.0, network 50.0 Mbps. Port TCP 5001–5004 là bắt buộc khi 4 agent chạy chung
một máy; trên 4 máy riêng thì tất cả đều dùng 5000 (theo comment trong
`run-local-demo.bat` và README mục 10/20).

* **Ghi chú:** Script chỉ in `Starting AGENT-0N` chứ không in `Project root`
  (khác với 2 script monitor). Muốn đổi port/secret thì sửa file `.properties`,
  không cần build lại (config đọc ngoài jar).

---

## 4. Nhóm demo

### `run-local-demo.bat`

* **Mục đích:** Khởi động toàn bộ demo cục bộ: 1 monitoring client + 4 agent,
  mỗi tiến trình trong cửa sổ riêng, tất cả trên máy này (README mục 10).
* **Nội dung chính:**
  1. `cd /d "%~dp0.."`.
  2. Kiểm tra **cả hai** jar `monitor-app...all.jar` và `agent-app...all.jar`;
     thiếu cái nào cũng báo `The jars are missing. Run scripts\build.bat first.`
     + `pause` + `exit /b 1`.
  3. Mở monitoring client trước, trong cửa sổ mới có tiêu đề:
     ```bat
     start "Monitoring Client" cmd /c ""%~dp0run-monitor.bat""
     ```
  4. `timeout /t 6 /nobreak >nul` — chờ 6 giây để client kịp bind UDP 6000
     trước khi agent gửi (comment trong script).
  5. Lần lượt mở 4 agent, cách nhau 2 giây (`timeout /t 2 /nobreak`):
     ```bat
     start "AGENT-01" cmd /c ""%~dp0run-agent01.bat""
     ...tương tự AGENT-02, AGENT-03, AGENT-04
     ```
  6. In hướng dẫn thêm agent vào tab **Dashboard** của client (nếu chúng chưa
     được đăng ký), kèm bảng host/port/secret:
     ```
     AGENT-01  127.0.0.1  5001  secret GROUP10_DEMO_SECRET_01
     AGENT-02  127.0.0.1  5002  secret GROUP10_DEMO_SECRET_02
     AGENT-03  127.0.0.1  5003  secret GROUP10_DEMO_SECRET_03
     AGENT-04  127.0.0.1  5004  secret GROUP10_DEMO_SECRET_04
     ```
     và dặn: `Close each window to stop that process.` (đóng từng cửa sổ để
     dừng tiến trình đó).
  7. `pause` ở cuối (cửa sổ chính chờ gõ phím, 5 cửa sổ kia vẫn chạy).
* **Cách chạy:**
  ```bat
  scripts\run-local-demo.bat
  ```
  Điều kiện: JDK 17+, đã `build.bat`.
* **Kết quả mong đợi:** Mở **5 cửa sổ cmd** (1 tiêu đề `Monitoring Client`,
  4 tiêu đề `AGENT-01`..`AGENT-04`). Agent chưa tự đăng ký — phải thêm tay trên
  Dashboard tab theo bảng in ra.
* **Ghi chú:**
  * Timeout: 6 giây sau client, 2 giây giữa các agent — đừng tắt các cửa sổ khi
    script còn đang chạy dở.
  * Comment trong script trỏ tới `docs\05-trien-khai-nhieu-may.md` cho cấu hình 4
    máy; trong repo hiện tại file nằm ở
    `docs\03-huong-dan-su-dung\05-trien-khai-nhieu-may.md`.

---

## 5. Nhóm kiểm thử bảo mật / lưu lượng (dùng `simulator-app`)

Cả 3 script đều: về thư mục gốc → kiểm tra
`simulator-app\target\simulator-app-1.0.0-all.jar` (thiếu thì báo build trước) →
chạy `java -Dmonitoring.home="%CD%" -jar ...` với một subcommand → `pause`.
Điều kiện chung: monitoring client **đang chạy** (nghe UDP 6000), agent AGENT-01
đã được đăng ký (trừ load test cố ý dùng identity chưa đăng ký).

### `run-invalid-hmac-test.bat`

* **Mục đích:** Gửi các gói tin bị làm giả chữ ký (HMAC sai) mà monitoring client phải
  từ chối.
* **Nội dung chính:**
  ```bat
  java -Dmonitoring.home="%CD%" -jar "%JAR%" invalid-hmac --host=127.0.0.1 --port=6000 --agent=AGENT-01
  ```
  (subcommand `invalid-hmac`, gửi tới 127.0.0.1:6000, giả danh AGENT-01).
* **Cách chạy:**
  ```bat
  scripts\run-invalid-hmac-test.bat
  ```
* **Kết quả mong đợi** (ghi nguyên trong comment của script, kiểm tra ở phía
  monitoring client):
  * Dashboard vẫn hiện số CPU thật, không phải 100%;
  * không có dòng mới nào được ghi vào bảng `metric_raw` cho các gói này;
  * không có cảnh báo `CPU_HIGH` nào do chúng sinh ra;
  * `SECURITY_INVALID_HMAC` xuất hiện trong `logs\monitor-0.log` và trong tab
    Alerts.

### `run-replay-test.bat`

* **Mục đích:** Gửi một gói hợp lệ rồi gửi lại **đúng gói đó** — kiểm tra cơ
  chế chống gửi lại (replay).
* **Nội dung chính:**
  ```bat
  java -Dmonitoring.home="%CD%" -jar "%JAR%" replay --host=127.0.0.1 --port=6000 --agent=AGENT-01 --secret=GROUP10_DEMO_SECRET_01
  ```
  (subcommand `replay`; **có truyền `--secret`** — chữ ký đúng nên gói đầu mới
  được chấp nhận).
* **Cách chạy:**
  ```bat
  scripts\run-replay-test.bat
  ```
* **Kết quả mong đợi** (comment trong script): bản sao đầu tiên được chấp nhận;
  mọi bản sao sau bị từ chối với `SECURITY_REPLAY_PACKET`; một gói mới hơn
  thực sự vẫn được chấp nhận sau đó.

### `run-load-test.bat`

* **Mục đích:** Thử tải: điều khiển 20 identity agent trong 60 giây.
* **Nội dung chính:**
  ```bat
  java -Dmonitoring.home="%CD%" -jar "%JAR%" load --host=127.0.0.1 --port=6000 --agents=20 --duration=60
  ```
  (subcommand `load`, 20 agent ảo, 60 giây).
* **Cách chạy:**
  ```bat
  scripts\run-load-test.bat
  ```
* **Kết quả mong đợi** (comment trong script): 20 identity **không được đăng ký**
  nên mọi gói đều bị từ chối `UNKNOWN_AGENT` — đó là chủ đích. Mục tiêu kiểm
  tra: bộ nhận giữ được nhịp, vẫn phản hồi và **không phình thread hay bộ nhớ**
  trong khi từ chối một trận mưa gói tin.
* **Ghi chú:** Cả 3 script đều `pause` cuối cùng — cần gõ phím để đóng cửa sổ.

---

## 6. Nhóm firewall (PowerShell)

Hai script có cấu trúc như nhau (kiểm tra quyền Administrator, hỗ trợ `-Remove`,
idempotent — chạy lại không tạo rule trùng), khác protocol/port/mục đích. Cả hai
**không bao giờ được build tự chạy** — comment ghi rõ đổi firewall là quyết định
của người vận hành, không phải tác dụng phụ của việc biên dịch.

### `firewall-agent.ps1`

* **Mục đích:** Mở rule firewall inbound mà agent cần cho kênh điều khiển TCP.
  Chạy **trên mỗi máy chứa agent**, một lần, trong PowerShell **Run as
  Administrator**.
* **Nội dung chính:**
  1. Tham số (param): `-Port` mặc định **5000**,
     `-RuleName` mặc định `Group10 Server Health Monitoring - TCP control`,
     switch `-Remove`.
  2. `Test-Administrator` — nếu không phải Administrator: warning
     `...needs administrator rights.` + hướng dẫn chạy lại bằng "Run as
     Administrator" rồi `exit 1`.
  3. Nếu có `-Remove`: tìm rule theo DisplayName; có thì `Remove-NetFirewallRule`,
     không có thì báo `nothing to remove`; `exit 0`.
  4. Rule đã tồn tại → in `already exists; leaving it as it is.` kèm bảng
     `Protocol, LocalPort` rồi `exit 0` (không tạo trùng).
  5. Rule chưa có → `New-NetFirewallRule` với: `Direction Inbound`, `Action Allow`,
     `Protocol TCP`, `LocalPort $Port`, `Profile Domain,Private`, Description
     `Inbound TCP control channel from the monitoring client (PING, AGENT_INFO,
     CONFIG_UPDATE).`
  6. In `Allowed inbound TCP $Port for the agent.` và gợi ý kiểm chứng từ máy
     client: `Test-NetConnection <this-machine-ip> -Port $Port`.
* **Cách chạy:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\firewall-agent.ps1
  powershell -ExecutionPolicy Bypass -File .\firewall-agent.ps1 -Port 5001
  powershell -ExecutionPolicy Bypass -File .\firewall-agent.ps1 -Remove
  ```
* **Kết quả mong đợi:** Rule firewall inbound TCP (mặc định 5000) xuất hiện
  trong Windows Firewall; chạy `-Remove` để gỡ.
* **Ghi chú:** Chỉ cần mở TCP control — telemetry của agent đi ra ngoài bằng
  UDP và Windows cho phép outbound theo mặc định (nguyên văn comment).

### `firewall-monitor.ps1`

* **Mục đích:** Mở rule firewall inbound cho máy hosting Monitoring Client.
  Chạy trên máy client, một lần, PowerShell **Run as Administrator**.
* **Nội dung chính:** Cấu trúc y hệt `firewall-agent.ps1`, khác ở:
  * `-Port` mặc định **6000**;
  * `-RuleName` mặc định `Group10 Server Health Monitoring - UDP telemetry`;
  * `Protocol UDP`, Description `Inbound UDP telemetry and heartbeats from
    monitored agents.`;
  * Sau khi tạo có thêm lưu ý nguyên văn: rule chỉ phủ profile **Domain và
    Private**; nếu máy ở mạng Windows xếp vào **Public** thì phải đổi mạng đó
    sang Private, hoặc tự thêm `-Profile Any` — “knowing what that exposes”.
* **Cách chạy:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\firewall-monitor.ps1
  powershell -ExecutionPolicy Bypass -File .\firewall-monitor.ps1 -Port 6000
  powershell -ExecutionPolicy Bypass -File .\firewall-monitor.ps1 -Remove
  ```
* **Kết quả mong đợi:** Rule inbound UDP 6000 cho máy client.
* **Ghi chú:** Chạy cả hai script **chỉ khi triển khai nhiều máy** (README mục
  20); demo trên một máy không cần mở firewall.

---

## Thứ tự chạy khuyến nghị cho lần đầu

```bat
:: 1. Build (bắt buộc trước mọi script run-*)
scripts\build.bat

:: 2. Test (tùy chọn nhưng nên chạy — không cần jar)
scripts\test.bat

:: 3a. Chạy client hồ sơ demo (nén retention, DB riêng) HOẶC 3b:
scripts\run-monitor-demo.bat
:: 3b. Chạy client hồ sơ production:
:: scripts\run-monitor.bat

:: 4. Chạy agent (mỗi script một cửa sổ)...
scripts\run-agent01.bat
scripts\run-agent02.bat
scripts\run-agent03.bat
scripts\run-agent04.bat

:: ...hoặc để script mở sẵn 5 cửa sổ (khuyên dùng cho demo trên 1 máy):
:: scripts\run-local-demo.bat

:: 5. Thêm 4 agent trên tab Dashboard: 127.0.0.1 + port 5001..5004 + secret như bảng

:: 6. Kiểm thử bảo mật/lưu lượng (client phải đang chạy):
scripts\run-invalid-hmac-test.bat
scripts\run-replay-test.bat
scripts\run-load-test.bat

:: 7. Chỉ khi triển khai nhiều máy, chạy PowerShell as Administrator:
:: scripts\firewall-monitor.ps1   (máy client, UDP 6000)
:: scripts\firewall-agent.ps1     (mỗi máy agent, TCP 5000)
```

Lưu ý thứ tự: **build trước, monitor trước, agent sau** (agent gửi UDP ngay khi
chạy nên client cần kịp nghe port 6000 — script `run-local-demo.bat` đã tự chờ
6 giây).

---

## Lưu ý chung

1. **Giả định thư mục làm việc:** mọi script `.bat` đều tự
   `cd /d "%~dp0.."` về thư mục gốc dự án (chứa `pom.xml`, `config\`, `data\`,
   `logs\`), nên bạn có thể gọi script từ bất kỳ đâu. Riêng 2 file `.ps1` thì
   **không** đổi thư mục — nên chạy từ `scripts\` (hoặc ghi đường dẫn đầy đủ),
   và tham số trong ví dụ là `.\firewall-*.ps1`.
2. **Cách sửa đường dẫn:** đường dẫn jar/config trong script là **tương đối** so
   với thư mục gốc (`monitor-app\target\...`, `config\monitor.properties`) — đổi
   vị trí thư mục dự án thì không cần sửa gì. Chỉ cần sửa khi:
   * đổi version jar (đang hard-code `1.0.0`);
   * đổi phiên bản NetBeans → sửa danh sách trong `scripts\find-maven.bat`;
   * đổi port/secret/threshold → sửa `config\*.properties` (không cần build lại).
3. **`pause`:** `build.bat`, `test.bat`, `run-local-demo.bat` và 3 script kiểm
   thử luôn `pause` ở cuối; `run-monitor*.bat` và `run-agent0*.bat` chỉ `pause`
   khi thoát bằng mã lỗi khác 0.
4. **Biến JVM:** `-Dmonitoring.home` được truyền cho mọi tiến trình Java
   để ghim `config\`, `data\`, `logs\` về thư mục gốc dự án.
5. **Tài liệu liên quan (tương đối từ `docs\06-huong-dan-script\`):**
   * Hướng dẫn sử dụng chi tiết:
     [`../03-huong-dan-su-dung/02-huong-dan-su-dung.md`](../03-huong-dan-su-dung/02-huong-dan-su-dung.md)
   * Danh mục test & cách chạy test:
     [`../03-huong-dan-su-dung/08-kiem-thu.md`](../03-huong-dan-su-dung/08-kiem-thu.md)
   * Tham chiếu README mục 6 (build), 8 (chạy client), 9 (chạy agent),
     10 (demo 4 agent), 19 (testing).
