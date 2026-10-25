# Giới thiệu tổng quan đề tài

Đề tài: **Hệ thống Giám sát Server (Server Health Monitoring) từ xa**
Môn học: Lập trình mạng máy tính (CMP180) — Trường Đại học HUTECH.

Tài liệu nêu bối cảnh, mục tiêu, tổng quan kiến trúc, công nghệ và phạm vi của
đề tài. Mọi số liệu kỹ thuật được lấy từ `README.md`, `pom.xml` và bộ tài liệu
`docs\02-kien-truc-va-nguyen-ly\` của dự án.

---

## 1. Thông tin đề tài và nhóm

| Hạng mục | Nội dung |
|---|---|
| Tên đề tài | Hệ thống Giám sát Server (Server Health Monitoring) từ xa |
| Môn học | Lập trình mạng máy tính (CMP180) |
| Nhóm | Nhóm 10 |
| Lớp | 23DTHA5 |
| Ca thi | CA12 |
| Học kỳ | 26-27 HK1A |

### Thành viên nhóm

| STT | Họ và tên | MSSV |
|-----|-----------|------|
| 1 | Nguyễn Phương Nam | 2380613311 |
| 2 | Bùi Tấn Quốc Đạt | 2182310415 |
| 3 | Nguyễn Thanh Bảo Ngân | 2380601424 |
| 4 | Lê Huỳnh Ngọc | 2380601465 |
| 5 | Trương Thị Yến Nhi | 2380601581 |

---

## 2. Bối cảnh và lý do chọn đề tài

Trong thực tế, một hệ thống thông tin thường gồm nhiều máy chủ (server) đặt ở
nhiều vị trí khác nhau — trong phòng lab, trong trung tâm dữ liệu, hoặc ở văn
phòng chi nhánh. Khi một máy gặp sự cố (CPU treo ở 100%, hết RAM, ngập lưu
lượng mạng, máy chết), người quản trị chỉ biết khi có người báo lỗi hoặc khi
dịch vụ đã ngừng — tức là **phát hiện trễ**.

Giám sát từ xa (remote monitoring) giải quyết bài toán đó: mỗi máy được giám
sát chạy một chương trình nhỏ (**agent** — tác nhân đo đạc) thu thập chỉ số hệ
thống và gửi định kỳ về một **trung tâm giám sát** (monitoring client) đặt ở
máy quan sát. Trung tâm hiển thị trạng thái theo thời gian thực, lưu lịch sử
và cảnh báo khi ngưỡng bị vượt.

Lý do nhóm chọn đề tài này cho môn Lập trình mạng máy tính:

1. **Đúng trọng tâm môn học**: đề tài buộc phải vận dụng trực tiếp hai giao
   thức vận chuyển (transport) cốt lõi của lớp ứng dụng — **UDP** và **TCP** —
   kèm các khái niệm cổng, socket, đóng khung thông điệp (framing), đồng bộ
   nhiều luồng (multithreading).
2. **Bài toán có tính thực dụng**: giám sát server là nhu cầu có thật của bất
   kỳ hệ thống IT nào, nên sản phẩm có kịch bản sử dụng dễ hình dung và dễ
   trình bày trong ca thi.
3. **Đủ độ khó để thể hiện tư duy thiết kế**: chọn UDP cho dữ liệu đo lường và
   TCP cho kênh điều khiển, cộng thêm yêu cầu bảo mật (chữ ký số, chống gửi
   lại) là các quyết định kỹ thuật phải giải thích và bảo vệ được.

---

## 3. Mục tiêu đề tài

### 3.1 Mục tiêu tổng quát

Xây dựng một **hệ thống giám sát server từ xa** hoạt động hoàn toàn trên nền
ứng dụng mạng desktop viết bằng Java: nhiều agent chạy trên các máy được giám
sát gửi chỉ số về một ứng dụng trung tâm, nơi người dùng theo dõi thời gian
trực, lưu dữ liệu lịch sử và nhận cảnh báo khi có sự cố.

### 3.2 Mục tiêu cụ thể

1. Xây dựng agent thu thập **6 chỉ số** của máy (CPU %, bộ nhớ %, số kết nối
   mạng, uptime — thời gian hoạt động, lưu lượng mạng vào và ra theo byte/giây).
2. Truyền dữ liệu đo lường (telemetry) qua **UDP** và kênh điều khiển qua
   **TCP**, giải thích và chứng minh được lý do chọn từng giao thức.
3. **Bảo mật luồng UDP** bằng chữ ký HMAC-SHA256 và cơ chế chống gửi lại
   (replay protection) dựa trên số thứ tự (sequence) + mốc thời gian (timestamp).
4. Xây dựng giao diện Swing giám sát **nhiều agent cùng lúc** theo thời gian
   thực, có biểu đồ lịch sử.
5. **Lưu trữ và tổng hợp dữ liệu**: giữ chi tiết ở chế độ thô có thời hạn, gộp
   thành bucket tổng hợp để giữ dài hạn.
6. **Cảnh báo đa kênh**: trên giao diện, trong cơ sở dữ liệu và qua email
   (SMTP Gmail).
7. Hỗ trợ **cấu hình từ xa** (đổi chu kỳ đo, ngưỡng cảnh báo) từ trung tâm xuống
   agent qua kênh TCP có xác nhận.
8. Có bộ **kiểm thử tự động** (JUnit 5) và các kịch bản kiểm thử bảo mật, tải
   bằng công cụ mô phỏng (simulator).

---

## 4. Tổng quan hệ thống

### 4.1 Hai thành phần chính

| Thành phần | Chương trình | Chạy ở đâu | Vai trò |
|---|---|---|---|
| **Agent** | `agent-app` (console) | Máy được giám sát | Thu thập 6 chỉ số, ký HMAC, gửi UDP về trung tâm; mở TCP server nhận lệnh cấu hình |
| **Monitor** | `monitor-app` (Swing GUI) | Máy quan sát | Nhận UDP từ nhiều agent, xác thực, hiển thị realtime, lưu SQLite, sinh cảnh báo, gửi lệnh cấu hình qua TCP |

Hai thành phần phụ thuộc module dùng chung `monitoring-common` (model, giao
thức, bảo mật) nên **cách ký bên gửi giống hệt cách kiểm tra bên nhận** — hai
không thể lệch chuẩn giao thức.

Ngoài ra còn có `simulator-app` — công cụ mô phỏng lưu lượng kiểm thử (xem
mục 6) và `monitoring-common` — thư viện dùng chung.

### 4.2 Sơ đồ tổng quan

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

### 4.3 Hai kênh truyền, hai giao thức

| Kênh | Giao thức | Cổng mặc định | Hướng | Nội dung |
|---|---|---|---|---|
| Dữ liệu đo lường (telemetry) + nhịp tim (heartbeat) | **UDP** | Monitor lắng nghe **6000** | Agent → Monitor | Gói `TELEMETRY` (1 giây/lần), `HEARTBEAT` (5 giây/lần) |
| Điều khiển (control) | **TCP** | Agent lắng nghe **5000** | Monitor → Agent | `PING`/`PONG`, `AGENT_INFO_*`, `CONFIG_UPDATE`/`CONFIG_ACK`, `ERROR` |

- **UDP cho telemetry**: gói nhỏ, đến đều đặn, mất gói này thì gói sau 1 giây
  thay thế — không cần truyền lại hay giữ trạng thái kết nối.
- **TCP cho điều khiển**: lệnh cấu hình không được mất âm thầm; phản hồi
  `CONFIG_ACK` là bằng chứng lệnh đã đến và được chấp nhận. Khung thông điệp
  kiểu **4 byte độ dài (big-endian) + JSON UTF-8**.

### 4.4 Sáu chỉ số giám sát

Mỗi agent định kỳ đo và báo cáo 6 chỉ số:

| # | Chỉ số | Ý nghĩa | Khi vượt ngưỡng |
|---|---|---|---|
| 1 | `cpuUsage` | Mức sử dụng CPU (%) | Cảnh báo `CPU_HIGH` |
| 2 | `memoryUsage` | Mức sử dụng bộ nhớ RAM (%) | Cảnh báo `MEMORY_HIGH` |
| 3 | `connectionCount` | Số kết nối mạng đang mở | — (hiển thị, lưu) |
| 4 | `uptimeSeconds` | Thời gian máy đã hoạt động (giây) | — (hiển thị, lưu) |
| 5 | `networkInBytesPerSecond` | Lưu lượng mạng **vào** (byte/giây) | Cảnh báo `NETWORK_IN_HIGH` |
| 6 | `networkOutBytesPerSecond` | Lưu lượng mạng **ra** (byte/giây) | Cảnh báo `NETWORK_OUT_HIGH` |

Chỉ số được thu qua thư viện **OSHI** (đọc số liệu hệ thống từ hệ điều hành).

### 4.5 Cảnh báo và lưu trữ

- **Cảnh báo**: 8 loại — `CPU_HIGH`, `MEMORY_HIGH`, `NETWORK_IN_HIGH`,
  `NETWORK_OUT_HIGH`, `AGENT_OFFLINE`, `AGENT_RECOVERED` và 2 sự kiện bảo mật
  (`SECURITY_INVALID_HMAC`, `SECURITY_REPLAY_PACKET`). Mỗi cặp (agent, loại)
  chỉ có tối đa một sự kiện đang mở; có dải hysteresis (vùng trễ) tránh cảnh
  báo nhấp nháy.
- **Ba kênh thông báo**: hiển thị trên giao diện (GUI), ghi vào bảng `alerts`
  của SQLite, và gửi **email** qua SMTP Gmail (chưa bật — xem mục 7).
- **Lưu trữ**: SQLite — bảng `agents`, `metric_raw` (chi tiết), `metric_5m`
  (tổng hợp 5 phút), `alerts`. Database tự tạo ở `data\monitoring.db` ngay lần
  chạy đầu tiên.

---

## 5. Công nghệ sử dụng

Số liệu phiên bản lấy trực tiếp từ `pom.xml` của dự án:

| Hạng mục | Công nghệ | Phiên bản |
|---|---|---|
| Ngôn ngữ | Java (biên dịch `--release 17`) | 17 |
| Build | Maven (multi-module, 4 module) | — |
| Giao diện đồ họa | Java Swing (kèm JDK) | kèm JDK 17 |
| Biểu đồ | JFreeChart | 1.5.5 |
| Thu thập chỉ số hệ thống | OSHI (`oshi-core`) | 6.6.5 |
| JSON | Gson | 2.11.0 |
| Cơ sở dữ liệu | SQLite (`org.xerial:sqlite-jdbc`) | 3.46.1.3 |
| Email (SMTP) | Jakarta Mail (`com.sun.mail:jakarta.mail`) | 2.0.1 |
| Logging | SLF4J (`slf4j-jdk14`) | 2.0.13 |
| Kiểm thử | JUnit Jupiter (JUnit 5) | 5.10.2 |

Đặc điểm: **không** dùng Spring, **không** dùng web server, **không** dùng
server database ngoài — đây là ứng dụng mạng desktop thuần Java, triển khai đơn
giản (jar tự chứa, chạy bằng JRE là đủ).

---

## 6. Phạm vi đề tài

### 6.1 Trong phạm vi

- Truyền dữ liệu bằng **socket UDP và TCP** (phần cốt lõi của môn học).
- Chữ ký **HMAC-SHA256** và chống gửi lại (sequence + timestamp) cho luồng UDP.
- Giao diện **Swing** giám sát realtime nhiều agent, biểu đồ JFreeChart.
- Lưu trữ **SQLite** kèm tổng hợp (aggregation) và chính sách giữ dữ liệu
  (retention).
- Cảnh báo qua **GUI, database và email SMTP** (Gmail).
- Cấu hình từ xa agent qua kênh TCP có phản hồi xác nhận (`CONFIG_ACK`).
- Công cụ mô phỏng **simulator-app** và **bộ kiểm thử JUnit 5**
  (README ghi nhận 165 test tự động, gồm test tích hợp chạy socket thật trên
  loopback).

### 6.2 Ngoài phạm vi

- Web dashboard / REST API / giao thức SNMP — hệ thống là ứng dụng desktop.
- Phân quyền người dùng, đa người dùng, xác thực đăng nhập.
- Docker, hệ thống quản lý container.
- Các công cụ giám sát chuyên nghiệp thay thế (Zabbix, Prometheus, Nagios...).
- Cảnh báo qua Telegram hoặc kênh nhắn tin khác — **hệ thống hiện tại dùng
  email**, không dùng Telegram.
- Giám sát tập tin log, giám sát ứng dụng (APM), dự đoán sự cố bằng máy học.

---

## 7. Lưu ý về trạng thái hiện tại

- Kênh email dùng **Gmail App Password** đọc lúc runtime từ biến môi trường
  hoặc `config\email-secrets.properties`. Theo `README.md` mục 18, **mật khẩu
  chưa được điền** (còn placeholder) nên email đang ở trạng thái chưa bật; khi
  chưa cấu hình, các kênh còn lại (GUI, database) vẫn hoạt động bình thường.
- Thư mục `docs\05-tu-khoa\` chứa bảng thuật ngữ (106 mục) và
  `docs\06-huong-dan-script\` chứa hướng dẫn các file script `.bat`/`.ps1`.

---

## 8. Sơ đồ bộ tài liệu dự án

```
docs\
+-- 01-gioi-thieu\                  Tài liệu giới thiệu (thư mục hiện tại)
|     01-tong-quan-de-tai.md          tổng quan đề tài (file này)
|     02-chi-tiet-de-tai.md           chi tiết đề tài, yêu cầu & kiểm chứng
+-- 02-kien-truc-va-nguyen-ly\       kiến trúc, giao thức, bảo mật, lưu trữ
+-- 03-huong-dan-su-dung\            hướng dẫn cài đặt, chạy, kiểm thử, demo
+-- 04-tai-lieu-class\               tài liệu chi tiết từng module (theo lớp)
+-- 05-tu-khoa\                      bảng thuật ngữ (01-tu-khoa.md)
+-- 06-huong-dan-script\             hướng dẫn các script (01-cac-file-bat.md)
```

Các tài liệu chính (liên kết tương đối từ `docs/01-gioi-thieu/`):

| Tài liệu | Nội dung |
|---|---|
| [Cấu trúc dự án](../02-kien-truc-va-nguyen-ly/01-cau-truc-du-an.md) | thư mục, module Maven, công nghệ |
| [Mô hình hệ thống](../02-kien-truc-va-nguyen-ly/02-mo-hinh-he-thong.md) | client–server, hai kênh UDP/TCP |
| [Bảo mật HMAC và chống phát lại](../02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md) | chữ ký, chuỗi canonical, cửa sổ sequence |
| [Lưu trữ và tổng hợp](../02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md) | SQLite, retention, aggregation |
| [Cảnh báo và thông báo](../02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md) | 8 loại cảnh báo, GUI/DB/email |
| [Hướng dẫn sử dụng](../03-huong-dan-su-dung/) | build, chạy, demo, kiểm thử |

---

## Tài liệu liên quan

- [02-chi-tiet-de-tai.md](02-chi-tiet-de-tai.md) — chi tiết đề tài và kiểm
  chứng 5 yêu cầu bổ sung.
- `README.md` — tài liệu tổng quan gốc của dự án (tiếng Anh).
