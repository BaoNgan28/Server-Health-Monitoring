# Hướng dẫn sử dụng

Group 10 — Hệ thống giám sát server từ xa (Server Health Monitoring System)

Tài liệu tổng quan tiếng Việt, đi theo đúng thứ tự thao tác: chạy Monitoring
Client, chạy Agent, demo 4 agent trên một máy, 5 tab giao diện, cấu hình từ xa,
rồi liên kết tới các tài liệu chi tiết (kịch bản trình chiếu, đa máy, kiểm thử,
sự cố, email). Lệnh, port, secret trong tài liệu này đều lấy từ
`scripts\*.bat`, `config\*.properties` và code giao diện thật.

Yêu cầu môi trường và cách build: xem [`01-cai-dat-va-build.md`](01-cai-dat-va-build.md).
Giải thích từng khóa cấu hình: xem [`03-cau-hinh-he-thong.md`](03-cau-hinh-he-thong.md).

Mục lục:

- [a. Chạy Monitoring Client](#a-chạy-monitoring-client)
- [b. Chạy 1 Agent](#b-chạy-1-agent)
- [c. Demo local 4 agent](#c-demo-local-4-agent)
- [d. 5 tab của giao diện](#d-5-tab-của-giao-diện)
- [e. Cấu hình từ xa](#e-cấu-hình-từ-xa-configure-agent)
- [f. Kịch bản demo trình chiếu](#f-kịch-bản-demo-trình-chiếu)
- [g. Triển khai đa máy](#g-triển-khai-đa-máy)
- [h. Chạy kiểm thử và kịch bản bảo mật/tải](#h-chạy-kiểm-thử-và-kịch-bản-bảo-mật-tải)
- [i. Xử lý sự cố](#i-xử-lý-sự-cố)
- [j. Bật email](#j-bật-email)

---

## a. Chạy Monitoring Client

### a.1 Chạy bình thường (hồ sơ production)

```bat
scripts\run-monitor.bat
```

Script kiểm tra jar có tồn tại chưa (chưa build thì báo
`[ERROR] monitor-app\target\monitor-app-1.0.0-all.jar not found` và nhắc chạy
`scripts\build.bat`), rồi chạy:

```bat
java -Dmonitoring.home="<thư mục gốc dự án>" ^
     -jar monitor-app\target\monitor-app-1.0.0-all.jar ^
     --config="config\monitor.properties"
```

* `-Dmonitoring.home` ghim thư mục `config\`, `data\`, `logs\` về thư mục gốc dự
  án, nên client chạy đúng dù được khởi động từ đâu.
* Lần đầu chạy, ứng dụng **tự tạo** `data\monitoring.db` kèm schema — không
  chuẩn bị gì bằng tay.
* Cửa sổ console in banner: UDP port, đường dẫn database, thư mục log.

### a.2 Chạy hồ sơ demo (dữ liệu tổng hợp theo phút-giây)

```bat
scripts\run-monitor-demo.bat
```

Khác bản production duy nhất: dùng `config\monitor-demo.properties` → database
riêng `data\monitoring-demo.db`, retention (thời gian giữ dữ liệu) được nén lại
để thấy cảnh tổng hợp (aggregation) chỉ sau vài phút thay vì một tiếng. Code là
một, chỉ số khác nhau.

### a.3 Lệnh gọi tay (tương đương script)

```bat
java -Dmonitoring.home="C:\duong-dan\ServerHealthMonitoring" ^
     -jar monitor-app\target\monitor-app-1.0.0-all.jar ^
     --config="config\monitor.properties"
```

Nếu port UDP 6000 đã bị chiếm (đã có client khác chạy) sẽ báo
`Address already in use: bind` — xem [`10-xu-ly-su-co.md`](10-xu-ly-su-co.md).

---

## b. Chạy 1 Agent

```bat
scripts\run-agent01.bat
```

Tương đương (script thêm `-Dmonitoring.home="%CD%"`):

```bat
java -Dmonitoring.home="<thư mục gốc dự án>" ^
     -jar agent-app\target\agent-app-1.0.0-all.jar ^
     --config="config\agent01.properties"
```

Các script khác: `run-agent02.bat`, `run-agent03.bat`, `run-agent04.bat` (dùng
`config\agent02.properties` … `agent04.properties`).

Khi khởi động, agent in banner: `agent.id`, `agent.name`, hostname, địa chỉ
monitoring client, port TCP của chính nó, các khoảng thời gian gửi dữ liệu.
**Banner không in shared secret** (khóa bí mật dùng ký gói tin) — agent không bao
giờ in, ghi log hay gửi secret đi.

Muốn chạy agent bằng lệnh (ở máy đã có jar, ví dụ máy riêng):

```bat
java -jar agent-app-1.0.0-all.jar --config="config\agent01.properties"
```

Lưu ý: nếu chạy trực tiếp bằng `java -jar` mà lỗi `Agent configuration file not
found`, nghĩa là đường dẫn `--config` được dò theo thư mục hiện hành — hãy chạy
từ thư mục gốc, dùng script, hoặc truyền đường dẫn tuyệt đối
(xem [`10-xu-ly-su-co.md`](10-xu-ly-su-co.md)).

---

## c. Demo local 4 agent

### c.1 Khởi động cả cụm (1 client + 4 agent, 5 cửa sổ)

```bat
scripts\run-local-demo.bat
```

Script tự làm:

1. Kiểm tra 2 jar monitor và agent đã tồn tại (chưa build thì nhắc chạy
   `scripts\build.bat`).
2. Mở cửa sổ **Monitoring Client**, chờ **6 giây** cho client kịp gắn UDP 6000.
3. Mở lần lượt **AGENT-01 → AGENT-02 → AGENT-03 → AGENT-04**, cách nhau 2 giây.
4. In bảng hướng dẫn Add Agent (xem c.3) rồi `pause`.

Đóng từng cửa sổ = dừng tiến trình tương ứng.

### c.2 Bảng port và shared secret (4 agent chung 1 máy)

Bốn agent cùng một máy nên mỗi agent cần một port TCP khác nhau:

| Agent | TCP port | Shared secret | File cấu hình |
|---|---|---|---|
| AGENT-01 | 5001 | `GROUP10_DEMO_SECRET_01` | `config\agent01.properties` |
| AGENT-02 | 5002 | `GROUP10_DEMO_SECRET_02` | `config\agent02.properties` |
| AGENT-03 | 5003 | `GROUP10_DEMO_SECRET_03` | `config\agent03.properties` |
| AGENT-04 | 5004 | `GROUP10_DEMO_SECRET_04` | `config\agent04.properties` |

Các agent đều hướng về `monitor.host=127.0.0.1`, `monitor.udp.port=6000`.

### c.3 Thêm agent vào Dashboard (Add Agent)

Trong cửa sổ Monitoring Client, tab **Dashboard** → nút **Add Agent** → điền
hộp thoại (đúng theo trường của `AgentDialog`):

| Trường | Giá trị cho demo local |
|---|---|
| Agent ID | `AGENT-01` (phải trùng `agent.id` trong file properties của agent) |
| Name | tên hiển thị, ví dụ `LOCAL-AGENT-01` |
| Host / IP | `127.0.0.1` (giá trị mặc định của hộp thoại) |
| TCP Port | `5001` (mặc định của hộp thoại là `5000` — phải đổi khi chung máy) |
| Shared Secret | `GROUP10_DEMO_SECRET_01` (bắt buộc khi thêm; ô dùng ô nhập dạng ẩn ký tự) |
| CPU Threshold (%) | `85.0` |
| Memory Threshold (%) | `85.0` |
| Network IN Threshold (Mbps) | `50.0` |
| Network OUT Threshold (Mbps) | `50.0` |

Nhấn **Save**. Lặp lại cho AGENT-02/03/04 với port 5002/5003/5004 và secret
tương ứng.

* Danh sách agent được lưu trong SQLite (`data\monitoring.db`) nên **chỉ cần
  làm một lần** — lần chạy sau vẫn còn.
* Khi **Edit Agent**: `Agent ID` không sửa được; ô Shared Secret để trống nghĩa
  là **giữ nguyên secret đã lưu**; secret không bao giờ hiện lên bảng.
* Trạng thái sẽ chuyển `UNKNOWN` → `ONLINE` trong khoảng 10 giây, tóm tắt đọc
  `TOTAL 4 / ONLINE 4` (các thẻ TOTAL SERVERS / ONLINE / WARNING / OFFLINE).

Mô tả đầy đủ các nút và cột: mục d.1 bên dưới.

---

## d. 5 tab của giao diện

Tiêu đề cửa sổ: `Group 10 - Server Health Monitoring System  [profile: <tên>]`
(với client chạy `monitor.properties` là `production`, chạy
`monitor-demo.properties` là `demo`). Kích thước cửa sổ 1200×780, tối thiểu
1000×640.

### d.1 Tab `Dashboard`

* **4 thẻ tổng hợp** ở trên: `TOTAL SERVERS`, `ONLINE`, `WARNING`, `OFFLINE`.
* **Bảng theo thời gian thực, 9 cột** (đúng thứ tự trong code
  `AgentTableModel`):

  | Cột | Nội dung |
  |---|---|
  | Name | tên agent |
  | IP | hiển thị dạng `host:tcpPort` |
  | CPU | % (một chữ số thập phân), `-` khi chưa có số liệu |
  | RAM | % bộ nhớ |
  | Connections | số kết nối mạng |
  | Network IN | tốc độ vào (byte/giây) |
  | Network OUT | tốc độ ra (byte/giây) |
  | Status | `UNKNOWN` / `ONLINE` / `WARNING` / `OFFLINE`, tô màu theo trạng thái |
  | Last Seen | thời điểm nhận gói tin gần nhất |

* **6 nút**: `Add Agent`, `Edit Agent`, `Delete Agent`, `Connect`,
  `Disconnect`, `Configure`.
  * `Connect` — mở kết nối TCP điều khiển tới agent và hiện hộp thoại thông tin
    agent (xem e.1).
  * `Disconnect` — ngắt kết nối TCP đó.
  * `Configure` — mở hộp thoại cấu hình từ xa (xem mục e).
  * `Delete Agent` — có bước xác nhận, xóa kèm số liệu và cảnh báo của agent đó.
* Chọn một dòng sẽ tự chọn cùng agent ở tab **Server Detail**.
* Bảng được làm mới gom lại ~2 lần mỗi giây (mỗi 500 ms), số liệu nhận ~1 lần/
  giây mỗi agent.

### d.2 Tab `Server Detail`

* Hộp chọn **Agent**.
* **11 ô thông tin**: `Name`, `Address`, `Status`, `Last Seen`, `CPU`, `Memory`,
  `Connections`, `Uptime` (thời gian hệ thống chạy), `Network IN`,
  `Network OUT`, `Packets` (gói tin).
* **3 biểu đồ chạy trực tiếp**: `CPU Usage` (%), `Memory Usage` (%),
  `Network Throughput` (bytes/s, gồm 2 chuỗi Network IN và Network OUT).
* Mỗi biểu đồ chỉ giữ số điểm theo `chart.window.samples` (mặc định **120**,
  cho phép 60–300) để ứng dụng chạy lâu không phình bộ nhớ.

### d.3 Tab `History`

* Điều khiển: chọn **Agent**, chọn **Range** (khoảng thời gian) gồm
  `Last 1 Hour`, `Last 24 Hours`, `Last 7 Days`, `Last 30 Days`, nút **Load**,
  và nhãn kết quả có dạng `N point(s) from raw samples (metric_raw)` hoặc
  `N point(s) from aggregated buckets (metric_5m)` (tức là dữ liệu lấy từ bảng
  chi tiết mỗi giây hay từ bucket đã tổng hợp); khi chưa có dữ liệu là
  `No data in this range yet.`.
* **2 biểu đồ**: `CPU and Memory` (%) và `Network Throughput` (bytes/s).
* Dữ liệu dài được lấy mẫu đều về tối đa **2000 điểm** trước khi vẽ.
* Truy vấn chạy trong nền (`SwingWorker`) nên cửa sổ không bị treo.

### d.4 Tab `Alerts`

* Điều khiển: nút **Refresh**, checkbox **Open alerts only**, nhãn tóm tắt
  “`N alert(s) shown, M currently open`”.
* **Bảng 6 cột**: `Time`, `Agent`, `Alert Type`, `Severity`, `State`,
  `Message`.
* Dòng đang `OPEN` được **in đậm** và tô màu theo mức nghiêm trọng
  (CRITICAL/WARNING); `State` hiện `RESOLVED <giờ>` cho cảnh báo đã đóng.
* Không lọc: hiển thị tối đa **500 dòng** gần nhất.
* Loại cảnh báo (`Alert Type`): `CPU_HIGH`, `MEMORY_HIGH`, `NETWORK_IN_HIGH`,
  `NETWORK_OUT_HIGH`, `AGENT_OFFLINE`, `AGENT_RECOVERED`, `SECURITY_INVALID_HMAC`,
  `SECURITY_REPLAY_PACKET`.
* Nguyên tắc “một sự cố — một cảnh báo”: giá trị vượt ngưỡng chỉ mở **một** dòng
  `OPEN`, giữ nguyên khi vẫn vượt, đóng khi giá trị tụt xuống dưới ngưỡng trừ
  dải chống dao động (hysteresis: 2 điểm phần trăm cho CPU/bộ nhớ, 5% của ngưỡng
  cho lưu lượng mạng). Agent im lặng quá 15 giây → `OFFLINE` + một cảnh báo
  `AGENT_OFFLINE`; gói tin hợp lệ quay lại → `AGENT_RECOVERED`.

### d.5 Tab `Settings`

* Thẻ **Notifications** (thông báo email):
  * `Email Notification Status`: `CONFIGURED` hoặc `NOT CONFIGURED`
  * `Sender`, `Recipient`, `SMTP`, `Secrets File` (đường dẫn
    `config\email-secrets.properties`), `Reason` (lý do chưa cấu hình)
  * 2 nút: **Send Test Email** (gửi mail thử thật) và
    **Reload Email Configuration** (đọc lại file secret mà không cần khởi động
    lại).
* Thẻ **Runtime** (cấu hình đang chạy): `Profile`, `UDP Listen Port`,
  `Heartbeat Timeout`, `Timestamp Tolerance`, `Raw Retention`,
  `Aggregation Bucket`, `Aggregate Retention`, `Database`, `Log Directory`.
* Không nơi nào hiển thị App Password — panel không đọc mật khẩu, chỉ hỏi
  notifier gửi thư.

### d.6 Thanh trạng thái (dưới cùng cửa sổ)

Cập nhật mỗi 500 ms, gồm 3 nhãn:

```text
UDP 6000 | agents: 4 | email: NOT CONFIGURED
packets: 120 received, 120 accepted, 0 rejected
last alert: CPU_HIGH on AGENT-01 at 15:04:11
```

* `packets: N received / accepted / rejected` là cách nhanh nhất phân biệt “gói
  không tới” (received không tăng) với “gói bị từ chối” (received tăng, accepted
  không tăng — xem [`10-xu-ly-su-co.md`](10-xu-ly-su-co.md)).

---

## e. Cấu hình từ xa (Configure Agent)

Toàn bộ thao tác nằm ở tab **Dashboard**, chọn agent (khuyến nghị bấm **Connect**
trước), rồi bấm **Configure**.

### e.1 `Connect` — đọc thông tin agent qua TCP

Hộp thoại hiện thông tin lấy từ agent ngay lúc đó qua kênh TCP:
`Agent ID`, `Name`, `Hostname`, `OS` (tên + phiên bản), `CPU` (model + số logical
cores), `Memory` (tổng RAM), `Intervals` (metric interval, heartbeat interval).

### e.2 `Configure` — hộp thoại Configure Agent

Hộp thoại `Configure Agent - <agentId>` có **6 trường**, trường trống nghĩa là
“giữ nguyên giá trị hiện tại của agent”:

| Trường | Đơn vị | Khoảng agent chấp nhận (CONFIG_UPDATE) |
|---|---|---|
| Metric Interval | ms | 200 … 3 600 000 |
| Heartbeat Interval | ms | 1 000 … 3 600 000 |
| CPU Threshold | % | > 0 và ≤ 100 |
| Memory Threshold | % | > 0 và ≤ 100 |
| Network IN Threshold | Mbps | ≥ 0 (số hữu hạn) |
| Network OUT Threshold | Mbps | ≥ 0 (số hữu hạn) |

Nút **Send CONFIG_UPDATE** gửi đi; nút **Cancel** hủy. Hộp thoại tự kiểm tra đầu
vào kiểu số trước khi gửi (ví dụ `CPU Threshold must be a number, for example
85.0`).

### e.3 `CONFIG_ACK` — bằng chứng cấu hình đã tới và được chấp nhận

Sau khi gửi, hiện hộp thoại `CONFIG_ACK from <agentId>` với:

```text
Status : SUCCESS | ERROR
Message: Applied metricIntervalMs=2000   (hoặc lý do từ chối)
Effective on the agent:
  metric interval    : 2000 ms
  heartbeat interval : 5000 ms
  CPU threshold      : 5.0 %
  memory threshold   : 85.0 %
```

Nguyên tắc quan trọng (README mục 11):

* Agent **kiểm tra toàn bộ trường trước khi áp dụng bất kỳ trường nào** — một
  giá trị sai làm cả thông báo bị từ chối, agent giữ nguyên trạng thái và trả
  `ERROR` kèm lý do, ví dụ:

  ```text
  metricIntervalMs must be between 200 and 3600000 ms, got 5
  ```

* Monitoring client **chỉ ghi ngưỡng mới vào database của mình sau khi nhận
  ACK `SUCCESS`** — màn hình không bao giờ hiển thị cấu hình mà agent đã từ chối.
* Trường để trống trong CONFIG_UPDATE = không đổi (agent không đụng tới).

Ví dụ minh họa (thứ tự các bước có trong [`07-kich-ban-demo.md`](07-kich-ban-demo.md) mục 8–9):
đặt Metric Interval `2000` → ACK `SUCCESS`, log agent in
`Applied metricIntervalMs=2000`, dashboard giảm tần suất cập nhật; thử `5` →
`ERROR` và không có gì thay đổi; đặt lại `1000`.

---

## f. Kịch bản demo trình chiếu

Kịch bản 12–15 phút, từng bước có sẵn lệnh và câu nói gợi ý (kiến trúc → chạy
client → 4 agent → dashboard → CPU/bộ nhớ → lưu lượng mạng → biểu đồ → cấu hình
TCP → hạ ngưỡng CPU → cảnh báo → email → offline/recover → gói tin giả mạo →
replay → history → tổng hợp dữ liệu):

* [`07-kich-ban-demo.md`](07-kich-ban-demo.md)

Danh sách kiểm trước khi trình bày nằm ngay đầu file đó (build xong, agent đã
đăng ký, ngưỡng CPU của AGENT-01 về `85.0`, email cấu hình nếu muốn show, …).

---

## g. Triển khai đa máy

Một monitoring client (máy A) giám sát 4 agent trên 4 máy riêng, mỗi máy một IP,
**tất cả agent đều dùng TCP 5000** (4 port 5001–5004 chỉ cần khi chung một máy).
Bao gồm: nội dung cần copy sang từng máy, file properties mẫu với
`monitor.host` trỏ tới IP client, lệnh chạy firewall từng bên, thứ tự thao tác
và cách kiểm tra mạng:

* [`05-trien-khai-nhieu-may.md`](05-trien-khai-nhieu-may.md)

Firewall (chỉ cần khi máy khác kết nối tới, chạy bằng PowerShell **Run as
Administrator**): xem mục 3.4 của
[`03-cau-hinh-he-thong.md`](03-cau-hinh-he-thong.md).

---

## h. Chạy kiểm thử và kịch bản bảo mật/tải

### h.1 Bộ test tự động

```bat
scripts\test.bat
```

(tương đương `mvn clean test`; thành công thì in `ALL TESTS PASSED`). Chi tiết
từng lớp test, các kịch bản kiểm thử thủ công và cách chạy:

* [`08-kiem-thu.md`](08-kiem-thu.md) — mọi test và cách chạy
* [`09-bao-cao-kiem-thu.md`](09-bao-cao-kiem-thu.md) — cái gì đã thực sự chạy, cái gì chưa

Kết quả chi tiết từng test: `<module>\target\surefire-reports\`.

### h.2 Ba kịch bản bảo mật / tải (dùng `simulator-app`)

```bat
scripts\run-invalid-hmac-test.bat
scripts\run-replay-test.bat
scripts\run-load-test.bat
```

| Script | Lệnh bên trong | Kỳ vọng ở monitoring client |
|---|---|---|
| `run-invalid-hmac-test.bat` | `simulator-app … invalid-hmac --host=127.0.0.1 --port=6000 --agent=AGENT-01` — 3 gói: chữ ký rác, ký bằng sai key, gói thật bị sửa CPU sau khi ký | Dashboard vẫn hiện CPU thật (không phải 100%); không thêm dòng nào vào `metric_raw`; không sinh cảnh báo `CPU_HIGH`; hiện `SECURITY_INVALID_HMAC` trong `logs\monitor-0.log` và tab Alerts |
| `run-replay-test.bat` | `simulator-app … replay --host=127.0.0.1 --port=6000 --agent=AGENT-01 --secret=GROUP10_DEMO_SECRET_01` — 1 gói hợp lệ, gửi lại đúng gói đó 2 lần, rồi 1 gói mới | Gói đầu chấp nhận; các bản sao bị từ chối `SECURITY_REPLAY_PACKET`; gói mới sau đó vẫn được chấp nhận |
| `run-load-test.bat` | `simulator-app … load --host=127.0.0.1 --port=6000 --agents=20 --duration=60` — 20 identity trong 60 giây, chưa đăng ký nên tất cả bị từ chối `UNKNOWN_AGENT` | Cửa sổ vẫn mượt, bộ đếm `received` khớp simulator báo, số thread không tăng (kiểm tra bằng Task Manager hoặc `jcmd <pid> Thread.print`) |

Cả 3 script đều yêu cầu jar đã build (`simulator-app\target\
simulator-app-1.0.0-all.jar`) và một monitoring client đang chạy.

Kịch bản kiểm thử thủ công từng bước (demo 4 agent, cấu hình TCP, cảnh báo CPU,
offline/recover, aggregation, email thật, đa máy): mục 3 của
[`08-kiem-thu.md`](08-kiem-thu.md).

---

## i. Xử lý sự cố

Xem [`10-xu-ly-su-co.md`](10-xu-ly-su-co.md) — phân theo nhóm: build, khởi
động, agent không hiện, cảnh báo, TCP control, database, email, giao diện, thông
báo vô hại, cách tăng log chi tiết.

Điểm xuất phát luôn là file log:

| Máy | Log |
|---|---|
| Monitoring client | `logs\monitor-0.log` |
| Agent | `logs\agent-agent01-0.log`, … (tương ứng agent) |

Bắt nhanh 3 trường hợp hay gặp:

| Triệu chứng | Việc làm đầu tiên |
|---|---|
| `Address already in use` UDP 6000 | Đóng client đang chạy, hoặc đổi `monitor.udp.port` (và mọi file agent cho khớp) |
| Agent vĩnh viễn `UNKNOWN` | Xem `packets: … received` ở thanh trạng thái: không tăng → sai `monitor.host`/firewall; tăng nhưng `accepted` không tăng → xem lý do trong `logs\monitor-0.log` |
| Connect `Connection refused` | Chạy `Test-NetConnection <ip-agent> -Port 5000`; thường do agent chưa chạy hoặc thiếu rule firewall |

---

## j. Bật email

Email hiện **tắt** vì `config\email-secrets.properties` vẫn còn placeholder —
đây là trạng thái bình thường: ứng dụng chạy hết bình thường, cảnh báo vẫn hiện
trên cửa sổ và database, không có lần đăng nhập SMTP nào được thực hiện.

Để bật: điền Gmail **App Password** (16 ký tự, không dùng mật khẩu tài khoản,
gõ không có khoảng trắng) vào `smtp.appPassword` và `smtp.recipient`, rồi restart
client hoặc bấm **Settings → Reload Email Configuration**, kiểm tra trạng thái
`CONFIGURED` và **Send Test Email**.

Chi tiết từng bước, bảng lỗi, danh sách subject thư, cách dùng biến môi trường
thay file: [`06-cai-dat-email.md`](06-cai-dat-email.md).

---

## Liên kết nhanh

| Tài liệu | Nội dung |
|---|---|
| [`01-cai-dat-va-build.md`](01-cai-dat-va-build.md) | yêu cầu hệ thống, build, NetBeans, cấu trúc jar |
| [`03-cau-hinh-he-thong.md`](03-cau-hinh-he-thong.md) | từng file và từng khóa cấu hình |
| [`04-huong-dan-netbeans.md`](04-huong-dan-netbeans.md) | mở/chạy dự án trên NetBeans |
| [`05-trien-khai-nhieu-may.md`](05-trien-khai-nhieu-may.md) | 5 máy, firewall, thứ tự thao tác |
| [`07-kich-ban-demo.md`](07-kich-ban-demo.md) | kịch bản 12–15 phút |
| [`08-kiem-thu.md`](08-kiem-thu.md) / [`09-bao-cao-kiem-thu.md`](09-bao-cao-kiem-thu.md) | test tự động / kết quả thực tế |
| [`10-xu-ly-su-co.md`](10-xu-ly-su-co.md) | triệu chứng → cách sửa |
| [`06-cai-dat-email.md`](06-cai-dat-email.md) | bật thông báo email |
| [`../02-kien-truc-va-nguyen-ly/09-kien-truc-goc.md`](../02-kien-truc-va-nguyen-ly/09-kien-truc-goc.md) | kiến trúc, luồng dữ liệu, mô hình luồng |
| [`../02-kien-truc-va-nguyen-ly/10-giao-thuc-goc.md`](../02-kien-truc-va-nguyen-ly/10-giao-thuc-goc.md) | định dạng gói TCP/UDP, chuỗi ký |
| [`../06-huong-dan-script/01-cac-file-bat.md`](../06-huong-dan-script/01-cac-file-bat.md) | mô tả từng file script |
