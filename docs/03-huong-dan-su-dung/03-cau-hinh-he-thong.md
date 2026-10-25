# Cấu hình hệ thống

Group 10 — Hệ thống giám sát server từ xa (Server Health Monitoring System)

Giải thích từng file cấu hình và từng khóa quan trọng. Mọi giá trị trong bảng
đều lấy từ file `config\*.properties` thật và từ code loader tương ứng
(`MonitorConfigLoader`, `AgentConfigLoader`, `EmailConfigLoader`,
`ProtocolConstants`); cột “giá trị mặc định” là giá trị loader dùng khi **thiếu**
khóa đó.

Cách chạy/build: [`01-cai-dat-va-build.md`](01-cai-dat-va-build.md) ·
Thao tác sử dụng: [`02-huong-dan-su-dung.md`](02-huong-dan-su-dung.md)

Mục lục:

- [0. Nguyên tắc chung](#0-nguyên-tắc-chung)
- [1. `config\monitor.properties`](#1-configmonitorproperties)
- [2. `config\monitor-demo.properties`](#2-configmonitor-demoproperties)
- [3. `config\agent01.properties` … `agent04.properties`](#3-configagent01properties--agent04properties)
- [4. `config\email-secrets.properties` và `.example`](#4-configemail-secretsproperties-và-example)
- [5. Thư mục `data\`](#5-thư-mục-data)
- [6. Thư mục `logs\`](#6-thư-mục-logs)
- [7. Script firewall](#7-script-firewall)

---

## 0. Nguyên tắc chung

* File cấu hình nằm **ngoài jar**, trong `config\`, nên sửa là có hiệu lực khi
  chạy lại — không cần sửa source, không cần build lại.
* Đường dẫn tương đối (như `data/monitoring.db`) được giải quyết theo
  **thư mục gốc dự án**, nhờ hệ thống thuộc tính JVM `-Dmonitoring.home=<gốc>`;
  các script `run-*.bat` đã tự đặt thuộc tính này.
* File nào cũng nhận qua tham số `--config=<đường dẫn>`; ví dụ:

  ```bat
  java -Dmonitoring.home="<gốc>" -jar monitor-app\target\monitor-app-1.0.0-all.jar --config="config\monitor.properties"
  ```

* Sửa `monitor.udp.port` ở client thì **phải sửa `monitor.udp.port` ở mọi file
  agent** cho khớp.

---

## 1. `config\monitor.properties`

Monitoring Client — hồ sơ **production**. Các khóa và giá trị thực tế:

| Khóa | Giá trị trong file (mặc định của loader) | Ý nghĩa |
|---|---|---|
| `profile.name` | `production` (`production`) | Tên hồ sơ, hiện trên tiêu đề cửa sổ và tab Settings |
| `monitor.udp.port` | `6000` (`6000`, hợp lệ 1–65535) | Cổng UDP client lắng nghe telemetry (dữ liệu đo) và heartbeat (gói tin giữ liên lạc) từ agent |
| `tcp.timeout.ms` | `5000` (`5000`, 500–60000) | Thời gian chờ kết nối/đọc của kênh TCP điều khiển |
| `heartbeat.timeout.ms` | `15000` (`15000`, 1000–3600000) | Không nhận được gì trong chừng này → agent bị coi là `OFFLINE` |
| `heartbeat.check.interval.ms` | `2000` (`2000`, 200–3600000) | Chu kỳ chạy vòng quét trạng thái sống (liveness sweep) |
| `packet.timestamp.tolerance.ms` | `30000` (`30000`, ≥1000) | Dung sai chênh lệch timestamp (thời điểm gắn trong gói) giữa 2 máy; lệch quá mức này → gói bị từ chối |
| `database.path` | `data/monitoring.db` (`data/monitoring.db`) | Đường dẫn SQLite; tương đối thì tính từ thư mục gốc dự án |
| `raw.retention.minutes` | `60` (60 phút) | Giữ dữ liệu chi tiết mỗi giây trong 60 phút, sau đó gộp vào bucket |
| `aggregation.bucket.minutes` | `5` (5 phút) | Kích thước một bucket (gói tổng hợp 5 phút) |
| `aggregate.retention.days` | `30` (30 ngày) | Thời gian giữ các bucket đã tổng hợp |
| `aggregation.interval.seconds` | `300` (5–3600) | Chu kỳ chạy job tổng hợp (aggregation) |
| `chart.window.samples` | `120` (120, 60–300) | Số điểm dữ liệu mỗi chuỗi biểu đồ thời gian thực giữ lại |

Khóa lưu trữ còn nhận dạng **giây**: `raw.retention.seconds`,
`aggregation.bucket.seconds`, `aggregate.retention.seconds` — dạng giây **thắng**
dạng phút/ngày khi có cả hai (đây là cơ chế để hồ sơ demo ghi đè production).

Tham chiếu: `monitor-app\...\config\MonitorConfigLoader.java`,
`monitoring-common\...\protocol\ProtocolConstants.java`.

---

## 2. `config\monitor-demo.properties`

Monitoring Client — hồ sơ **demo**: y hệt production ngoại trừ các số về giữ
dữ liệu, được nén lại để hiện tượng tổng hợp dữ liệu xuất hiện sau vài phút.
Dùng file này bằng `scripts\run-monitor-demo.bat`.

| Khóa | Giá trị | Ý nghĩa / khác gì production |
|---|---|---|
| `profile.name` | `demo` | Tên hồ sơ hiển thị trên cửa sổ |
| `database.path` | `data/monitoring-demo.db` | Database **riêng** — demo không đụng dữ liệu production |
| `raw.retention.seconds` | `60` | Giữ chi tiết mỗi giây 60 **giây** (production: 60 phút) |
| `aggregation.bucket.seconds` | `10` | Bucket rộng 10 giây (production: 5 phút) |
| `aggregate.retention.seconds` | `300` | Giữ bucket 300 giây (production: 30 ngày) |
| `aggregation.interval.seconds` | `15` | Job tổng hợp chạy mỗi 15 giây (production: 300 giây) |

Các khóa còn lại (`monitor.udp.port=6000`, `tcp.timeout.ms=5000`,
`heartbeat.timeout.ms=15000`, `heartbeat.check.interval.ms=2000`,
`packet.timestamp.tolerance.ms=30000`, `chart.window.samples=120`) giống hệt
production. **Code là một, chỉ số khác** — không có nhánh “demo” nào trong mã.

---

## 3. `config\agent01.properties` … `agent04.properties`

Bốn file giống nhau, chỉ khác `agent.id`, `agent.name`, `agent.tcp.port` và
`security.sharedSecret`.

| Khóa | agent01 | agent02 | agent03 | agent04 | Ý nghĩa / giá trị mặc định khi thiếu |
|---|---|---|---|---|---|
| `agent.id` | `AGENT-01` | `AGENT-02` | `AGENT-03` | `AGENT-04` | **Bắt buộc.** Định danh agent gửi trong mọi gói tin; client tra secret theo id này. Phải trùng Agent ID đã đăng ký ở Dashboard |
| `agent.name` | `LOCAL-AGENT-01` | `LOCAL-AGENT-02` | `LOCAL-AGENT-03` | `LOCAL-AGENT-04` | Tên hiển thị (mặc định = `agent.id`) |
| `monitor.host` | `127.0.0.1` | `127.0.0.1` | `127.0.0.1` | `127.0.0.1` | Địa chỉ **của monitoring client** (mặc định `127.0.0.1`); đa máy phải đổi thành IP máy client |
| `monitor.udp.port` | `6000` | `6000` | `6000` | `6000` | Cổng UDP phía client (mặc định `6000`) — phải khớp `monitor.udp.port` của client |
| `agent.tcp.port` | `5001` | `5002` | `5003` | `5004` | Cổng TCP mà agent **lắng nghe** kênh điều khiển từ client (mặc định `5000`, 1–65535). Cùng một máy phải khác nhau; mỗi máy riêng thì tất cả dùng `5000` |
| `metric.interval.ms` | `1000` | `1000` | `1000` | `1000` | Chu kỳ gửi telemetry (mặc định 1000 ms; hợp lệ 200–3 600 000 ms) |
| `heartbeat.interval.ms` | `5000` | `5000` | `5000` | `5000` | Chu kỳ gửi heartbeat (mặc định 5000 ms; hợp lệ 1 000–3 600 000 ms) |
| `security.sharedSecret` | `GROUP10_DEMO_SECRET_01` | `GROUP10_DEMO_SECRET_02` | `GROUP10_DEMO_SECRET_03` | `GROUP10_DEMO_SECRET_04` | **Bắt buộc.** Khóa bí mật chung để ký mọi gói UDP bằng HmacSHA256; phải khớp **chính xác từng ký tự** với secret đã đăng ký cho `agent.id` đó ở client. Mỗi agent một secret riêng → một secret lọt không giả mạo được agent khác |
| `alert.cpu.threshold` | `85.0` | `85.0` | `85.0` | `85.0` | Ngưỡng cảnh báo CPU % (mặc định 85.0; hợp lệ 0–100; `0` = **tắt** kiểm tra cảnh báo này) |
| `alert.memory.threshold` | `85.0` | `85.0` | `85.0` | `85.0` | Ngưỡng cảnh báo bộ nhớ % (mặc định 85.0; 0–100) |
| `alert.network.in.threshold.mbps` | `50.0` | `50.0` | `50.0` | `50.0` | Ngưỡng Network IN tính bằng Mbps (mặc định 50.0; ≥ 0) |
| `alert.network.out.threshold.mbps` | `50.0` | `50.0` | `50.0` | `50.0` | Ngưỡng Network OUT tính bằng Mbps (mặc định 50.0; ≥ 0) |

Lưu ý về 4 khóa `alert.*`:

* Đây là **bản sao cục bộ** ở agent. Việc **nâng cảnh báo** do monitoring client
  làm (ngưỡng nằm trong database của client); các giá trị này là thứ agent báo
  lại trong `CONFIG_ACK` và là thứ `CONFIG_UPDATE` thay đổi.
* Qua `CONFIG_UPDATE` (hộp thoại Configure), CPU/bộ nhớ phải **> 0 và ≤ 100**
  (khác file cấu hình, nơi `0` hợp lệ và nghĩa là tắt kiểm tra).

Tham chiếu: `agent-app\...\config\AgentConfigLoader.java`,
`agent-app\...\service\ConfigUpdateService.java`.

---

## 4. `config\email-secrets.properties` và `.example`

Credential gửi email cảnh báo qua Gmail SMTP. File `.example` là **mẫu** (an toàn
để trong git); file thật chỉ được tạo bằng cách copy mẫu rồi điền:

```bat
copy config\email-secrets.properties.example config\email-secrets.properties
```

| Khóa | Giá trị trong file mẫu / giá trị mặc định | Ý nghĩa |
|---|---|---|
| `smtp.username` | mẫu: `PASTE_SENDER_GMAIL_ADDRESS_HERE` (file thật trên đĩa đang ghi `bocon2663@gmail.com`) | Địa chỉ Gmail gửi thư. Mặc định loader: không có → tắt email |
| `smtp.appPassword` | mẫu: `PASTE_GMAIL_APP_PASSWORD_HERE` | **Gmail App Password** 16 ký tự (không phải mật khẩu tài khoản), gõ **không** kèm khoảng trắng Google hiển thị. Cần bật 2-Step Verification trước |
| `smtp.recipient` | mẫu: `PASTE_RECIPIENT_EMAIL_HERE` | Địa chỉ người nhận cảnh báo (có thể trùng người gửi) |
| `smtp.host` | `smtp.gmail.com` (mặc định `smtp.gmail.com`) | Máy chủ SMTP |
| `smtp.port` | `587` (mặc định 587) | Cổng SMTP |
| `smtp.starttls` | `true` (mặc định `true`) | Bật STARTTLS (bắt buộc mã hóa kênh) |
| `smtp.auth` | `true` (mặc định `true`) | Bật xác thực SMTP |

Quy tắc đọc (thứ tự ưu tiên — `EmailConfigLoader`):

1. Biến môi trường `SMTP_USERNAME`, `SMTP_APP_PASSWORD`,
   `ALERT_RECIPIENT_EMAIL` (đứng trước file);
2. `config\email-secrets.properties`.

Trong khi `smtp.appPassword` hoặc `smtp.recipient` còn là placeholder:

* email bị **tắt** — không có lần đăng nhập SMTP nào;
* tab Settings hiện `NOT CONFIGURED` kèm `Reason:` chỉ đúng khóa còn thiếu;
* mọi thứ khác chạy bình thường, cảnh báo vẫn hiện trên GUI và database.

**Không commit secret thật:** `.gitignore` có dòng
`config/email-secrets.properties` (chỉ giữ lại file `.example`). Mật khẩu không
được in vào log, không hiện trên GUI, và bị che `***` trước mọi thông báo lỗi.
App Password có thể thu hồi bất kỳ lúc nào từ trang Google Account.

Chi tiết cách bật và lỗi thường gặp: [`06-cai-dat-email.md`](06-cai-dat-email.md).

---

## 5. Thư mục `data\`

Database SQLite của monitoring client — được **tự tạo** lần đầu chạy, không chuẩn
bị gì bằng tay.

| Đường dẫn | Xuất hiện khi nào | Nội dung |
|---|---|---|
| `data\monitoring.db` | hồ sơ production (`run-monitor.bat`) | Các bảng `agents`, `metric_raw`, `metric_5m`, `alerts` |
| `data\monitoring-demo.db` | hồ sơ demo (`run-monitor-demo.bat`) | Database riêng của demo |
| `data\monitoring.db-shm`, `-wal` | kèm database (WAL journal) | file phụ trợ của SQLite; xóa cùng database khi reset |
| `data\.gitkeep` | có sẵn trong git | giữ thư mục khi chưa có database |

* Các file `data/*.db`, `*.db-shm`, `*.db-wal` nằm trong `.gitignore` — dữ liệu
  không vào kho nguồn.
* Reset về mặc định: **đóng client**, xóa `data\monitoring.db` (cùng `-shm`,
  `-wal`) → lần chạy sau tạo lại từ đầu, nhưng **danh sách agent đăng ký cũng mất**,
  phải Add Agent lại.
* `database is locked` thường do đang mở database bằng trình xem SQLite khác →
  đóng nó đi.

Chi tiết bảng, index, retention: [`../02-kien-truc-va-nguyen-ly/11-co-so-du-lieu-goc.md`](../02-kien-truc-va-nguyen-ly/11-co-so-du-lieu-goc.md).

---

## 6. Thư mục `logs\`

| Đường dẫn | Ứng dụng |
|---|---|
| `logs\monitor-0.log` | monitoring client |
| `logs\agent-agent01-0.log` … `logs\agent-agent04-0.log` | từng agent |
| `logs\simulator-0.log`, `logs\e2e-monitor-0.log` | simulator / lần chạy e2e |
| `logs\.gitkeep` | giữ thư mục trong git |

* Log **xoay ở 2 MiB, tối đa 5 file mỗi ứng dụng**, được nối tiếp qua các lần
  khởi động lại → log của hôm qua thường vẫn còn.
* File `logs/*.log`, `logs/*.log.*` nằm trong `.gitignore`.
* Secret dùng ký gói tin và App Password **không bao giờ** xuất hiện trong log,
  ở bất kỳ mức log nào.
* Tăng chi tiết log tạm thời: thêm vào dòng lệnh `java`
  `-Djava.util.logging.ConsoleHandler.level=FINE`.

---

## 7. Script firewall

Windows chặn kết nối **đến (inbound)** theo mặc định. Hai script mở port cần
thiết cho hệ thống. Cả hai:

* **chỉ chạy khi có nhu cầu kết nối từ máy khác tới** (kịch bản đa máy — xem
  [`05-trien-khai-nhieu-may.md`](05-trien-khai-nhieu-may.md)); với demo trên cùng một
  máy qua `127.0.0.1`, tài liệu dự án không nhắc tới việc chạy firewall;
* **phải chạy trong PowerShell mở bằng “Run as Administrator”** — script tự kiểm
  tra quyền và thoát với cảnh báo nếu chưa đủ quyền;
* **không bao giờ được build tự chạy** — việc sửa firewall là quyết định của
  người vận hành;
* chạy một lần là đủ (nếu rule đã có, script báo “already exists” và giữ nguyên).

### 7.1 `scripts\firewall-monitor.ps1` — cho máy chạy monitoring client

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\firewall-monitor.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\firewall-monitor.ps1 -Port 6000
powershell -ExecutionPolicy Bypass -File .\scripts\firewall-monitor.ps1 -Remove
```

| Thông số | Giá trị mặc định | Ý nghĩa |
|---|---|---|
| `-Port` | `6000` | Cổng UDP được phép **đến** (telemetry + heartbeat từ agent) |
| `-RuleName` | `Group10 Server Health Monitoring - UDP telemetry` | Tên rule firewall |
| `-Remove` | (không có) | Xóa rule này |

Chỉ cần mở UDP vì client gửi lệnh TCP **đi ra** (outbound Windows cho phép mặc
định).

### 7.2 `scripts\firewall-agent.ps1` — cho mỗi máy chạy agent

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\firewall-agent.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\firewall-agent.ps1 -Port 5001
powershell -ExecutionPolicy Bypass -File .\scripts\firewall-agent.ps1 -Remove
```

| Thông số | Giá trị mặc định | Ý nghĩa |
|---|---|---|
| `-Port` | `5000` | Cổng TCP agent lắng nghe kênh điều khiển (PING / AGENT_INFO / CONFIG_UPDATE); truyền `-Port 5001`…`5004` nếu agent chạy chung máy |
| `-RuleName` | `Group10 Server Health Monitoring - TCP control` | Tên rule firewall |
| `-Remove` | (không có) | Xóa rule này |

Agent gửi UDP **đi ra** nên không cần rule UDP cho máy agent.

### 7.3 Lưu ý profile của rule

Cả hai script tạo rule cho profile **Domain, Private**. Nếu Windows xếp mạng
hiện hành vào **Public** thì rule không có tác dụng: đổi mạng sang Private
(**Settings → Network & Internet**) hoặc tự thêm `-Profile Any` (nhận biết rõ
hiểu rõ hậu quả — lưu lượng sẽ tới được máy ở mọi profile). Kiểm tra từ máy client:

```powershell
Test-NetConnection <ip-máy-agent> -Port 5000
```

`TcpTestSucceeded : True` nghĩa là agent đang lắng nghe và firewall cho qua.

---

## Bảng tra nhanh các giá trị “nóng”

| Giá trị | Ý nghĩa | Nơi khai báo |
|---|---|---|
| `6000` | Cổng UDP client nhận telemetry/heartbeat | `monitor.udp.port` (client và mọi agent) |
| `5000` | Cổng TCP agent mặc định (mỗi máy một agent) | `agent.tcp.port`, `-Port` của `firewall-agent.ps1` |
| `5001`–`5004` | 4 cổng TCP khi 4 agent chung một máy | `agent01..04.properties` |
| `1000` ms | Chu kỳ gửi số liệu (metric interval) | `metric.interval.ms` |
| `5000` ms | Chu kỳ heartbeat | `heartbeat.interval.ms` |
| `15000` ms | Không nghe thấy gì → `OFFLINE` | `heartbeat.timeout.ms` |
| `30000` ms | Dung sai đồng hồ giữa 2 máy | `packet.timestamp.tolerance.ms` |
| `85.0` / `50.0` | Ngưỡng CPU–RAM (%) và mạng (Mbps) | `alert.*.threshold*` |
| `GROUP10_DEMO_SECRET_01..04` | Secret ký HMAC của 4 agent demo | `agent01..04.properties` + Add Agent ở Dashboard |

---

Tài liệu liên quan: [`02-huong-dan-su-dung.md`](02-huong-dan-su-dung.md) (dùng
hệ thống), [`05-trien-khai-nhieu-may.md`](05-trien-khai-nhieu-may.md) (đa máy),
[`06-cai-dat-email.md`](06-cai-dat-email.md) (email),
[`../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md`](../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md)
(bí mật HMAC), [`../06-huong-dan-script/01-cac-file-bat.md`](../06-huong-dan-script/01-cac-file-bat.md)
(mô tả script).
