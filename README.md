# HỆ THỐNG GIÁM SÁT SERVER TỪ XA (Remote Server Health Monitoring System)

> **Đề tài:** Hệ thống Giám sát Server (Server Health Monitoring) từ xa
> **Môn học:** Lập trình mạng máy tính (CMP180) — Trường Đại học HUTECH
> **Nhóm 10 — Lớp 23DTHA5 — Ca thi CA12 — Học kỳ 26-27 HK1A**

Hệ thống giám sát sức khỏe máy chủ hoạt động hoàn toàn trên nền ứng dụng mạng
desktop viết bằng Java: nhiều **agent** chạy trên các máy được giám sát, thu thập
chỉ số hệ thống và gửi về một **trung tâm giám sát** (monitoring client) đặt ở
máy quan sát. Trung tâm hiển thị theo thời gian thực, lưu lịch sử vào SQLite và
cảnh báo qua giao diện, cơ sở dữ liệu lẫn email khi ngưỡng bị vượt.

---

## MỤC LỤC

1. [Mục đích và lý do chọn đề tài](#1-mục-đích-và-lý-do-chọn-đề-tài)
2. [Mục tiêu đề tài](#2-mục-tiêu-đề-tài)
3. [Nội dung đồ án](#3-nội-dung-đồ-án)
4. [Thông tin thành viên nhóm](#4-thông-tin-thành-viên-nhóm)
5. [Phân công nhiệm vụ](#5-phân-công-nhiệm-vụ)
6. [Kiến trúc hệ thống](#6-kiến-trúc-hệ-thống)
7. [Công nghệ sử dụng](#7-công-nghệ-sử-dụng)
8. [Cấu trúc thư mục dự án](#8-cấu-trúc-thư-mục-dự-án)
9. [Yêu cầu hệ thống](#9-yêu-cầu-hệ-thống)
10. [Build dự án](#10-build-dự-án)
11. [Chạy thử trên một máy](#11-chạy-thử-trên-một-máy)
12. [Triển khai nhiều máy](#12-triển-khai-nhiều-máy)
13. [Kiểm thử](#13-kiểm-thử)
14. [Các điểm kỹ thuật nổi bật](#14-các-điểm-kỹ-thuật-nổi-bật)
15. [Chỉ mục tài liệu](#15-chỉ-mục-tài-liệu)

---

## 1. Mục đích và lý do chọn đề tài

### 1.1 Bối cảnh thực tế

Trong thực tế, một hệ thống thông tin thường gồm nhiều máy chủ đặt ở nhiều vị trí
khác nhau — phòng lab, trung tâm dữ liệu, văn phòng chi nhánh. Khi một máy gặp sự
cố (CPU treo ở 100%, hết RAM, ngập lưu lượng mạng, máy chết), người quản trị chỉ
biết khi có người báo lỗi hoặc khi dịch vụ đã ngừng — tức là **phát hiện trễ**.

Giám sát từ xa (remote monitoring) giải quyết bài toán đó: mỗi máy được giám sát
chạy một chương trình nhỏ (**agent** — tác nhân đo đạc) thu thập chỉ số hệ thống
và gửi định kỳ về một **trung tâm giám sát**. Trung tâm hiển thị trạng thái theo
thời gian thực, lưu lịch sử và cảnh báo khi ngưỡng bị vượt — người quản trị theo
dõi **tất cả các máy từ một nơi duy nhất**, không phải đăng nhập từng máy.

### 1.2 Vì sao chọn đề tài này cho môn Lập trình mạng máy tính

1. **Đúng trọng tâm môn học**: đề tài vận dụng trực tiếp hai giao thức vận chuyển
   cốt lõi — **UDP** và **TCP** — kèm cổng, socket, đóng khung thông điệp
   (framing) và đồng bộ nhiều luồng (multithreading).
2. **Bài toán có tính thực dụng**: giám sát server là nhu cầu có thật của mọi
   hệ thống IT, nên kịch bản sử dụng dễ hình dung và dễ trình bày trong ca thi.
3. **Đủ độ khó để thể hiện tư duy thiết kế**: chọn UDP cho dữ liệu đo lường và TCP
   cho kênh điều khiển, cộng yêu cầu bảo mật (chữ ký số, chống gửi lại) là các
   quyết định kỹ thuật phải giải thích và bảo vệ được.

---

## 2. Mục tiêu đề tài

### 2.1 Mục tiêu tổng quát

Xây dựng một **hệ thống giám sát server từ xa** hoạt động hoàn toàn trên nền ứng
dụng mạng desktop viết bằng Java: nhiều agent chạy trên các máy được giám sát gửi
chỉ số về một ứng dụng trung tâm, nơi người dùng theo dõi thời gian trực tiếp, lưu
dữ liệu lịch sử và nhận cảnh báo khi có sự cố.

### 2.2 Mục tiêu cụ thể

1. Xây dựng agent thu thập **6 chỉ số** của máy (CPU %, bộ nhớ %, số kết nối mạng,
   uptime, lưu lượng mạng vào và ra theo byte/giây).
2. Truyền dữ liệu đo lường (telemetry) qua **UDP** và kênh điều khiển qua
   **TCP**, giải thích và chứng minh được lý do chọn từng giao thức.
3. **Bảo mật luồng UDP** bằng chữ ký HMAC-SHA256 và cơ chế chống gửi lại
   (replay protection) dựa trên số thứ tự (sequence) + mốc thời gian (timestamp).
4. Xây dựng giao diện Swing giám sát **nhiều agent cùng lúc** theo thời gian thực,
   có biểu đồ lịch sử.
5. **Lưu trữ và tổng hợp dữ liệu**: giữ chi tiết ở chế độ thô có thời hạn, gộp
   thành bucket tổng hợp để giữ dài hạn.
6. **Cảnh báo đa kênh**: trên giao diện, trong cơ sở dữ liệu và qua email
   (SMTP Gmail).
7. Hỗ trợ **cấu hình từ xa** (đổi chu kỳ đo, ngưỡng cảnh báo) từ trung tâm xuống
   agent qua kênh TCP có xác nhận.
8. Có bộ **kiểm thử tự động** (JUnit 5) và các kịch bản kiểm thử bảo mật, tải
   bằng công cụ mô phỏng (simulator).

---

## 3. Nội dung đồ án

### 3.1 Hai thành phần chính

| Thành phần | Chương trình | Chạy ở đâu | Vai trò |
|---|---|---|---|
| **Agent** | `agent-app` (console) | Máy được giám sát | Thu thập 6 chỉ số, ký HMAC, gửi UDP về trung tâm; mở TCP server nhận lệnh cấu hình |
| **Monitor** | `monitor-app` (Swing GUI) | Máy quan sát | Nhận UDP từ nhiều agent, xác thực, hiển thị realtime, lưu SQLite, sinh cảnh báo, gửi lệnh cấu hình qua TCP |

Hai thành phần cùng dùng module `monitoring-common` (model, giao thức, bảo mật)
nên **cách ký bên gửi giống hệt cách kiểm tra bên nhận** — hai bên không thể lệch
chuẩn giao thức. Ngoài ra còn có `simulator-app` — công cụ mô phỏng lưu lượng
kiểm thử.

### 3.2 Sáu chỉ số giám sát

| # | Chỉ số | Ý nghĩa | Khi vượt ngưỡng |
|---|---|---|---|
| 1 | `cpuUsage` | Mức sử dụng CPU (%) | Cảnh báo `CPU_HIGH` |
| 2 | `memoryUsage` | Mức sử dụng bộ nhớ RAM (%) | Cảnh báo `MEMORY_HIGH` |
| 3 | `connectionCount` | Số kết nối mạng đang mở | — (hiển thị, lưu) |
| 4 | `uptimeSeconds` | Thời gian máy đã hoạt động (giây) | — (hiển thị, lưu) |
| 5 | `networkInBytesPerSecond` | Lưu lượng mạng **vào** (byte/giây) | Cảnh báo `NETWORK_IN_HIGH` |
| 6 | `networkOutBytesPerSecond` | Lưu lượng mạng **ra** (byte/giây) | Cảnh báo `NETWORK_OUT_HIGH` |

Chỉ số được thu qua thư viện **OSHI** (đọc số liệu hệ thống từ hệ điều hành).

### 3.3 Hai kênh truyền, hai giao thức

| Kênh | Giao thức | Cổng mặc định | Hướng | Nội dung |
|---|---|---|---|---|
| Telemetry + heartbeat | **UDP** | Monitor lắng nghe **6000** | Agent → Monitor | Gói `TELEMETRY` (1 giây/lần), `HEARTBEAT` (5 giây/lần) |
| Điều khiển (control) | **TCP** | Agent lắng nghe **5000** | Monitor → Agent | `PING`/`PONG`, `AGENT_INFO_*`, `CONFIG_UPDATE`/`CONFIG_ACK`, `ERROR` |

* **UDP cho telemetry**: gói nhỏ, đến đều đặn, mất gói này thì gói sau 1 giây thay
  thế — không cần truyền lại hay giữ trạng thái kết nối.
* **TCP cho điều khiển**: lệnh cấu hình không được mất âm thầm; phản hồi
  `CONFIG_ACK` là bằng chứng lệnh đã đến và được chấp nhận. Khung thông điệp kiểu
  **4 byte độ dài (big-endian) + JSON UTF-8**.

### 3.4 Bảo mật luồng dữ liệu

* Chữ ký **HMAC-SHA256** cho từng gói UDP, ký trên chuỗi canonical (13 trường,
  chuẩn hóa bằng `Locale.ROOT`, 4 chữ số thập phân) — hai máy khác locale vẫn cho
  ra cùng byte ký.
* So sánh chữ ký theo **thời gian hằng định** (`MessageDigest.isEqual`).
* Chống gửi lại: cửa sổ bitmap **64 bit** theo `(agentId, sessionId)` + kiểm tra
  timestamp **±30 giây**.
* Secret riêng cho từng agent, không bao giờ in/log/gửi đi.
* Gói thất bại ở bất kỳ bước nào **không đi xa hơn**: không cập nhật GUI, không ghi
  database, không sinh cảnh báo — chỉ ghi thành sự kiện bảo mật.

### 3.5 Lưu trữ và tổng hợp dữ liệu

SQLite tại `data\monitoring.db`, tự tạo kèm schema ngay lần chạy đầu tiên.

| Bảng | Nội dung |
|---|---|
| `agents` | agent đã đăng ký, endpoint, secret, ngưỡng, trạng thái |
| `metric_raw` | mỗi mẫu đo một dòng (chi tiết) |
| `metric_5m` | tổng hợp theo bucket 5 phút |
| `alerts` | mọi cảnh báo, đang mở hoặc đã xử lý |

| | Chế độ sản xuất | Chế độ demo |
|---|---|---|
| Giữ dữ liệu thô | 60 phút | 60 giây |
| Độ rộng bucket | 5 phút | 10 giây |
| Giữ tổng hợp | 30 ngày | 300 giây |

Mỗi bucket giữ số mẫu và trung bình **và cực đại** của từng chỉ số — cực đại quan
trọng vì trung bình đơn thuần sẽ che mất ngọn nguồn của sự cố. Việc gộp và xóa dữ
liệu thô diễn ra trong **một transaction**, nên chi tiết không bao giờ mất mà chưa
có bản tổng hợp.

### 3.6 Cảnh báo đa kênh

8 loại: `CPU_HIGH`, `MEMORY_HIGH`, `NETWORK_IN_HIGH`, `NETWORK_OUT_HIGH`,
`AGENT_OFFLINE`, `AGENT_RECOVERED`, `SECURITY_INVALID_HMAC`,
`SECURITY_REPLAY_PACKET`.

* Mỗi cặp (agent, loại) chỉ có tối đa **một** sự kiện đang mở → không cảnh báo
  nhấp nháy, không email mỗi giây.
* Dải **hysteresis** (2 điểm % với CPU/RAM, 5% ngưỡng với mạng) khi giải ngưỡng.
* Không nhận được gói hợp lệ quá **15 giây** → agent `OFFLINE` + một cảnh báo
  `AGENT_OFFLINE`; gói hợp lệ kế tiếp → `AGENT_RECOVERED`.
* **Ba kênh thông báo**: giao diện GUI, bảng `alerts` trong SQLite, và **email**
  qua SMTP Gmail.

> **Lưu ý:** kênh email đọc Gmail App Password lúc runtime từ biến môi trường hoặc
> `config\email-secrets.properties`. Hiện mật khẩu **chưa được điền** (còn placeholder)
> nên email đang ở trạng thái **chưa bật** — tab Settings hiển thị `NOT
> CONFIGURED`. Khi chưa cấu hình, GUI và database vẫn hoạt động bình thường; bật
> email chỉ cần sửa một file và khởi động lại, không phải sửa mã nguồn.
> Hướng dẫn chi tiết: [`docs/03-huong-dan-su-dung/06-cai-dat-email.md`](docs/03-huong-dan-su-dung/06-cai-dat-email.md).

### 3.7 Cấu hình từ xa

`CONFIG_UPDATE` mang `metricIntervalMs`, `heartbeatIntervalMs`, `cpuThreshold`,
`memoryThreshold`, `networkInThresholdMbps`, `networkOutThresholdMbps`. Mỗi trường
đều tùy chọn (`null` = giữ nguyên). Agent kiểm tra **tất cả** các trường trước khi
áp dụng **bất kỳ** trường nào, rồi trả `CONFIG_ACK` (`SUCCESS` hoặc `ERROR` kèm lý
do). Monitor chỉ ghi ngưỡng mới vào database **sau khi có xác nhận** — màn hình
không bao giờ hiển thị cài đặt mà agent đã từ chối.

### 3.8 Công cụ mô phỏng và phạm vi

`simulator-app` có 4 mode: `valid` (mô phỏng agent thật), `invalid-hmac` (kiểm
thử chống giả mạo chữ ký), `replay` (kiểm thử chống gửi lại), `load` (kiểm thử tải,
mặc định 20 định danh agent × 60 giây). Nhờ vậy nhóm không cần nhiều máy vật lý
vẫn thử nghiệm được hệ thống đa agent.

**Trong phạm vi:** socket UDP/TCP, HMAC + chống gửi lại, giao diện Swing, SQLite
kèm aggregation/retention, cảnh báo GUI/DB/email, cấu hình từ xa, simulator, kiểm
thử JUnit 5.

**Ngoài phạm vi:** web dashboard/REST API/SNMP, phân quyền người dùng, Docker,
thay thế Zabbix/Prometheus/Nagios, cảnh báo qua Telegram, giám sát log/APM/dự đoán
sự cố bằng máy học.

---

## 4. Thông tin thành viên nhóm

| STT | Họ và tên | MSSV | Email | Github |
|---|---|---|---|---|
| 1 | Nguyễn Phương Nam | 2380613311 | nguyenphuongnam16112005@gmail.com | nguyenphuongnam16112005 |
| 2 | Bùi Tấn Quốc Đạt | 2182310415 | dattan.191103@gmail.com | dattan191103-lab |
| 3 | Nguyễn Thanh Bảo Ngân | 2380601424 | baongan05289@gmail.com | BaoNgan28 |
| 4 | Lê Huỳnh Ngọc | 2380601465 | quoclam2278@gmail.com | Lehuynh-Ngoc |
| 5 | Trương Thị Yến Nhi | 2380601581 | yennhitruong704@gmail.com | yennhitruong704-cloud |

**Thông tin lớp:** Lớp 23DTHA5 — Ca thi CA12 — Học kỳ 26-27 HK1A.
Chi tiết: [`docs/Member.md`](docs/Member.md).

---

## 5. Phân công nhiệm vụ

| STT | Thành viên | Phân công công việc | Sản phẩm phụ trách |
|---|---|---|---|
| 1 | Nguyễn Phương Nam (2380613311) | Cấu hình Monitor & quản lý Agent | Cấu hình hệ thống (`config\*.properties`), đăng ký/quản lý agent trên GUI, kênh TCP điều khiển |
| 2 | Bùi Tấn Quốc Đạt (2182310415) | Lưu trữ SQLite, tổng hợp & cảnh báo | `MetricRepository`, `AlertRepository`, `AggregationService`, retention/aggregation |
| 3 | Nguyễn Thanh Bảo Ngân (2380601424) | Khởi tạo repository, xây dựng nền tảng Agent | Cấu trúc dự án Maven, vòng đời `AgentMain`, scheduler gửi telemetry/heartbeat |
| 4 | Lê Huỳnh Ngọc (2380601465) | Giao thức dùng chung, ký HMAC, Monitor TCP/UDP | `monitoring-common` (model, giao thức, security), `PacketSigner`/`PacketValidator`, UDP receiver & TCP client phía Monitor |
| 5 | Trương Thị Yến Nhi (2380601581) | Trực quan hóa, thành phần giao diện | Giao diện Swing (`DashboardPanel`, `ServerDetailPanel`, biểu đồ JFreeChart), `AlertPanel` |

> * **Cột "Phân công công việc"**: lấy nguyên từ
>   [`Muc_Luc_ServerHealthMonitoring.md`](Muc_Luc_ServerHealthMonitoring.md) mục
>   1.2, đã sửa lại tên thành viên cho đúng với [`docs/Member.md`](docs/Member.md)
>   (file mục lục ghi sai tên — ví dụ "Nguyễn Thành Bảo Ngô" → đúng là *Nguyễn
>   Thanh Bảo Ngân*), khớp theo MSSV.
> * **Cột "Sản phẩm phụ trách"** là *suy ra* từ phân công trên đối chiếu với code
>   hiện có — **không có văn bản chính thức** phân công tới từng class. Cần confirm
>   lại với nhóm trước khi đưa vào báo cáo.

---

## 6. Kiến trúc hệ thống

```
  MÁY ĐƯỢC GIÁM SÁT (có thể có nhiều máy)                MÁY QUAN SÁT
+----------------------+   +----------------------+    +---------------------------+
| MÁY B - AGENT 01     |   | MÁY C - AGENT 02     |    |  MONITOR (monitor-app)    |
|  OSHI -> 6 chỉ số    |   |  OSHI -> 6 chỉ số    |    |  Java Swing GUI           |
+----------+-----------+   +----------+-----------+    |                           |
           |                          |                |  UDP 6000: nhận telemetry |
           |   UDP 6000: TELEMETRY + HEARTBEAT         |  + heartbeat (ký HMAC)    |
           |   (ký HmacSHA256, 1 giây/lần)             |                           |
           +------------------------->-----------------+  TCP 5000: PING/PONG,      |
                                          ^            |  AGENT_INFO, CONFIG_UPDATE|
                                          |            |  / CONFIG_ACK             |
                                          +------------+  SQLite: metric_raw,       |
                               TCP điều khiển (monitor  |  metric_5m, alerts        |
                               chủ động kết nối agent)  |  Email: SMTP Gmail        |
                                                        +---------------------------+
```

Các tài liệu chuyên sâu:

* Kiến trúc, luồng dữ liệu, mô hình đa luồng:
  [`docs/02-kien-truc-va-nguyen-ly/`](docs/02-kien-truc-va-nguyen-ly/)
* Giao thức TCP/UDP, đóng khung, chuỗi ký:
  [`docs/02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md`](docs/02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md)
* Bảo mật HMAC và chống phát lại:
  [`docs/02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md`](docs/02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md)
* Sơ đồ trực quan (SVG, HTML offline):
  [`docs/07-hinh-anh-truc-quan/`](docs/07-hinh-anh-truc-quan/README.md)

---

## 7. Công nghệ sử dụng

| Hạng mục | Công nghệ | Phiên bản |
|---|---|---|
| Ngôn ngữ | Java (biên dịch `--release 17`) | 17 |
| Build | Maven (multi-module, 4 module) | — |
| Giao diện đồ họa | Java Swing | kèm JDK 17 |
| Biểu đồ | JFreeChart | 1.5.5 |
| Thu thập chỉ số hệ thống | OSHI (`oshi-core`) | 6.6.5 |
| JSON | Gson | 2.11.0 |
| Cơ sở dữ liệu | SQLite (`org.xerial:sqlite-jdbc`) | 3.46.1.3 |
| Email (SMTP) | Jakarta Mail (`com.sun.mail:jakarta.mail`) | 2.0.1 |
| Logging | SLF4J (`slf4j-jdk14`) | 2.0.13 |
| Kiểm thử | JUnit Jupiter (JUnit 5) | 5.10.2 |
| Đồng xử lý | `ExecutorService`, `ScheduledExecutorService`, concurrent collections | — |

**Không dùng** Spring, **không** dùng web server, **không** dùng server database
ngoài — đây là ứng dụng mạng desktop thuần Java, triển khai đơn giản (jar tự chứa,
chạy bằng JRE là đủ).

---

## 8. Cấu trúc thư mục dự án

```
ServerHealthMonitoring\
  pom.xml                     POM cha, quản lý phiên bản dependency
  README.md                   tài liệu này
  Muc_Luc_ServerHealthMonitoring.md   mục lục báo cáo
  .gitignore

  config\                     cấu hình ngoài jar, sửa không cần build lại
    monitor.properties          monitor, chế độ sản xuất
    monitor-demo.properties     monitor, chế độ demo (thời gian nén lại)
    email-secrets.properties    thông tin SMTP (git-ignored)
    email-secrets.properties.example
    agent01..04.properties      từng agent một file

  data\                       database SQLite, tự tạo khi chạy lần đầu
  logs\                       file log xoay vòng
  docs\                       bộ tài liệu tiếng Việt (xem mục 15)
  scripts\                    script build, test, chạy, firewall
  release\                    bản đóng gói phát hành

  monitoring-common\          model, giao thức, bảo mật, tiện ích dùng chung
  agent-app\                  agent (console)
  monitor-app\                trung tâm giám sát (Swing)
  simulator-app\              công cụ tạo lưu lượng kiểm thử
```

---

## 9. Yêu cầu hệ thống

* Windows 10 hoặc 11 (dự án được phát triển và thử trên Windows 11).
* JDK 17 trở lên (build thử với JDK 22, bytecode nhắm 17).
* Maven 3.8+, hoặc bản kèm theo Apache NetBeans — xem
  [`scripts/find-maven.bat`](scripts/find-maven.bat).
* Khi triển khai nhiều máy: máy chạy agent chỉ cần JRE/JDK 17+, file jar agent và
  file properties — **không** cần cài NetBeans.

---

## 10. Build dự án

```bat
scripts\build.bat
```

hoặc từ thư mục gốc dự án:

```bat
mvn clean package
```

Kết quả:

```
monitor-app\target\monitor-app-1.0.0-all.jar
agent-app\target\agent-app-1.0.0-all.jar
simulator-app\target\simulator-app-1.0.0-all.jar
```

Các jar `-all` là **tự chứa** (đủ dependency bên trong), máy chạy agent chỉ cần JRE.

### Mở dự án bằng NetBeans

`File → Open Project` → chọn thư mục gốc dự án, chờ Maven tải dependency. Các lớp
main:

| Module | Lớp main |
|---|---|
| monitor-app | `vn.edu.hutech.group10.monitoring.monitor.MonitoringMain` |
| agent-app | `vn.edu.hutech.group10.monitoring.agent.AgentMain` |
| simulator-app | `vn.edu.hutech.group10.monitoring.simulator.SimulatorMain` |

Chi tiết từng bước: [`docs/03-huong-dan-su-dung/04-huong-dan-netbeans.md`](docs/03-huong-dan-su-dung/04-huong-dan-netbeans.md).

---

## 11. Chạy thử trên một máy

### 11.1 Chạy trung tâm giám sát

```bat
scripts\run-monitor.bat
```

hoặc:

```bat
java -Dmonitoring.home="<thư mục gốc dự án>" ^
     -jar monitor-app\target\monitor-app-1.0.0-all.jar ^
     --config="config\monitor.properties"
```

Chế độ demo (nén thời gian giữ dữ liệu để thấy aggregation sau vài phút thay vì
một giờ):

```bat
scripts\run-monitor-demo.bat
```

File `data\monitoring.db` tự tạo khi chạy lần đầu — không cần chuẩn bị gì.

### 11.2 Chạy một agent

```bat
scripts\run-agent01.bat
```

hoặc:

```bat
java -jar agent-app\target\agent-app-1.0.0-all.jar --config="config\agent01.properties"
```

Agent in định danh, endpoint và chu kỳ khi khởi động; **không bao giờ** in, log hay
gửi đi secret dùng chung.

### 11.3 Demo 4 agent trên một máy

```bat
scripts\run-local-demo.bat
```

Mở 5 cửa sổ: 1 monitor + 4 agent. Vì chung một máy nên mỗi agent cần cổng TCP riêng:

| Agent | Cổng TCP | Secret dùng chung |
|---|---|---|
| AGENT-01 | 5001 | `GROUP10_DEMO_SECRET_01` |
| AGENT-02 | 5002 | `GROUP10_DEMO_SECRET_02` |
| AGENT-03 | 5003 | `GROUP10_DEMO_SECRET_03` |
| AGENT-04 | 5004 | `GROUP10_DEMO_SECRET_04` |

Thêm một lần trên tab **Dashboard** của monitor (`Add Agent`), host `127.0.0.1`,
cổng và secret như trên. Danh sách được lưu vào SQLite và vẫn còn ở lần chạy sau.

> **Hai lưu ý quan trọng khi chạy demo:**
>
> 1. Bảng trên lấy theo `scripts\run-local-demo.bat`, nhưng **`config\agent02.properties`
>    hiện đang bị chỉnh cho chế độ đa máy**: `monitor.host=100.74.100.79`,
>    `agent.tcp.port=5000`, secret `61f6efa2…` (khác bản demo). Trước khi chạy
>    demo 4 agent trên một máy, phải sửa lại file này về `127.0.0.1` / `5002` /
>    `GROUP10_DEMO_SECRET_02`, nếu không AGENT-02 sẽ gửi sai nơi.
> 2. Cổng 5001–5004 chỉ dùng khi 4 agent chung một máy. Ở chế độ đa máy, mọi agent
>    đều dùng cổng **5000**.

---

## 12. Triển khai nhiều máy

Copy sang mỗi máy agent:

* `agent-app-1.0.0-all.jar`
* file `agentNN.properties` của máy đó, đặt `monitor.host` = IP máy monitor và
  `agent.tcp.port` = 5000

Chạy `scripts\firewall-agent.ps1` bằng quyền Administrator trên mỗi máy agent và
`scripts\firewall-monitor.ps1` trên máy monitor. Hướng dẫn từng bước:
[`docs/03-huong-dan-su-dung/05-trien-khai-nhieu-may.md`](docs/03-huong-dan-su-dung/05-trien-khai-nhieu-may.md).

---

## 13. Kiểm thử

```bat
scripts\test.bat
```

**172 kiểm thử tự động**, chạy lại bằng `mvn -B test` ngày 25/10/2026: `BUILD
SUCCESS`, 0 lỗi — `monitoring-common` 50 + `agent-app` 26 + `monitor-app` 96
(`simulator-app` không có test), gồm cả test tích hợp mở socket UDP/TCP thật trên
loopback.

> Bản README cũ và một số tài liệu ghi **165 test** — con số đó đã cũ; kết quả
> nêu trên là số thật chạy được. Đối chiếu chi tiết:
> [`docs/03-huong-dan-su-dung/09-bao-cao-kiem-thu.md`](docs/03-huong-dan-su-dung/09-bao-cao-kiem-thu.md).

Kịch bản bảo mật và tải:

```bat
scripts\run-invalid-hmac-test.bat   &rem chữ ký sai → phải bị từ chối
scripts\run-replay-test.bat         &rem gửi lại → phải bị từ chối
scripts\run-load-test.bat           &rem 20 định danh × 60 giây
```

Danh sách test và cách chạy: [`docs/03-huong-dan-su-dung/08-kiem-thu.md`](docs/03-huong-dan-su-dung/08-kiem-thu.md).

---

## 14. Các điểm kỹ thuật nổi bật

### 14.1 Đóng khung TCP

Khung = **4 byte độ dài big-endian** + đúng từng đó byte JSON UTF-8. TCP là dòng
byte không có ranh giới thông điệp nên một lần đọc có thể trả về nửa thông điệp
hoặc hai thông điệp; reader dùng `readFully` đợi đủ độ dài khai báo. Không dùng đọc
theo dòng vì payload JSON không được đảm bảo không chứa xuống dòng.

### 14.2 Nhận gói UDP

Client lắng nghe UDP 6000. Một luồng duy nhất chỉ làm việc `receive()` rồi chuyển
bytes cho các worker. Mỗi worker là một luồng riêng có hàng đợi riêng, và một gói
luôn đi về worker được chọn theo **địa chỉ người gửi** — gói từ cùng một agent được
xử lý đúng theo thứ tự đến thay vì đua nhau. Hàng đợi có giới hạn; khi đầy, công
việc chạy trên luồng nhận, làm **throttle** lưu lượng thay vì để bộ nhớ phình ra.

### 14.3 Chuỗi ký canonical

Chữ ký không tính trên JSON gốc — hai bộ encode có thể sắp thứ tự trường hoặc định
dạng số khác nhau nhưng cùng mô tả một mẫu đo. Gói được rút về chuỗi chuẩn:

```
protocolVersion|type|agentId|hostname|timestamp|sequence|cpuUsage|memoryUsage|
connectionCount|uptimeSeconds|networkInBytesPerSecond|networkOutBytesPerSecond
```

số thực hiển thị bằng `Locale.ROOT` với đúng 4 chữ số thập phân (máy dùng dấu phẩy
thập phân vẫn cho ra cùng byte). Trường `hmac` không bao giờ nằm trong chuỗi mà nó
ký.

### 14.4 Chống gửi lại

Mỗi agent đánh số gói của mình. Client giữ, theo từng agent, dãy sequence cao nhất
đã chấp nhận cộng một bitmap 64 bit của 64 gói trước đó — **cùng hình dạng với
IPsec**. Gói được chấp nhận khi mới hơn mọi thứ đã thấy, hoặc nằm trong cửa sổ và
bit vẫn trống; bị từ chối khi lặp lại sequence đã ghi hoặc cũ hơn cửa sổ.

Yêu cầu mỗi sequence đơn giản phải lớn hơn cái trước sẽ loại những gói thật đến
muộn — mà UDP không hứa không làm vậy. Timestamp cũng phải nằm trong **±30 giây**
so với đồng hồ client (có thể cấu hình).

### 14.5 Đa luồng

* **Agent**: 1 luồng gửi telemetry (1 s), 1 luồng heartbeat (5 s), TCP accept +
  pool 8 luồng phiên.
* **Monitor**: 1 luồng `receive()` UDP + 4 worker có hàng đợi; SQLite dùng **một**
  kết nối chia sẻ với một lock duy nhất → đúng **một writer**; GUI luôn cập nhật qua
  `SwingUtilities.invokeLater` trên EDT.

### 14.6 Chống email flood

Mỗi cặp (agent, loại) tối đa một sự kiện `OPEN`; dải hysteresis 2 điểm % (CPU/RAM)
và 5% ngưỡng (mạng); sự kiện bảo mật được ghi ở trạng thái đã xử lý, tối đa 1
phút/agent/loại và **không gửi email** — một đợt gói giả mạo không được biến thành
một đợt email.

---

## 15. Chỉ mục tài liệu

Bộ tài liệu tiếng Việt đầy đủ nằm trong [`docs/`](docs/README.md):

| Tài liệu | Nội dung |
|---|---|
| [`docs/README.md`](docs/README.md) | Chỉ mục tổng hợp toàn bộ tài liệu |
| [`docs/Member.md`](docs/Member.md) | Thông tin thành viên nhóm |
| [`docs/01-gioi-thieu/01-tong-quan-de-tai.md`](docs/01-gioi-thieu/01-tong-quan-de-tai.md) | Tổng quan đề tài: bối cảnh, mục tiêu, kiến trúc, công nghệ, phạm vi |
| [`docs/01-gioi-thieu/02-chi-tiet-de-tai.md`](docs/01-gioi-thieu/02-chi-tiet-de-tai.md) | Bài toán, yêu cầu chức năng/phi chức năng, kiểm chứng 5 yêu cầu bổ sung |
| [`docs/02-kien-truc-va-nguyen-ly/`](docs/02-kien-truc-va-nguyen-ly/) | Kiến trúc, giao thức, bảo mật, lưu trữ, cảnh báo, đa luồng (11 tài liệu) |
| [`docs/03-huong-dan-su-dung/`](docs/03-huong-dan-su-dung/) | Cài đặt, build, chạy, cấu hình, NetBeans, đa máy, email, demo, kiểm thử (11 tài liệu) |
| [`docs/04-tai-lieu-class/`](docs/04-tai-lieu-class/) | Chi tiết từng class cả dự án theo module (bảng tổng hợp ghi 100 class) |
| [`docs/05-tu-khoa/01-tu-khoa.md`](docs/05-tu-khoa/01-tu-khoa.md) | Bảng thuật ngữ A–Z theo 8 nhóm (docs ghi 106 mục) |
| [`docs/06-huong-dan-script/01-cac-file-bat.md`](docs/06-huong-dan-script/01-cac-file-bat.md) | 15 script `.bat`/`.ps1`: mục đích, cách chạy, kết quả mong đợi |
| [`docs/07-hinh-anh-truc-quan/`](docs/07-hinh-anh-truc-quan/README.md) | Dashboard HTML offline + 3 sơ đồ SVG chèn Word |
| [`docs/08-git/`](docs/08-git/) | Quy trình git: nhánh, commit, đóng góp |
| [`Muc_Luc_ServerHealthMonitoring.md`](Muc_Luc_ServerHealthMonitoring.md) | Mục lục báo cáo đồ án (5 chương) |

### Ghi chú khi báo cáo

1. Kênh email đã xây dựng nhưng **chưa bật** (chưa điền App Password) — cần ghi rõ
   khi trình bày.
2. Số test thực tế là **172** (đã chạy `mvn -B test`, 0 lỗi) — tài liệu cũ ghi 165;
   các bản báo cáo/vạch slide cần cập nhật lại con số này.
3. Cổng TCP 5001–5004 chỉ dùng cho demo 4 agent cùng máy; chế độ đa máy dùng 5000.
4. `config\agent02.properties` đang trỏ IP đa máy (`100.74.100.79`, port 5000) —
   phải sửa về bản demo trước khi chạy `run-local-demo.bat`.
5. Hệ thống cảnh báo bằng **email**, không dùng Telegram.
