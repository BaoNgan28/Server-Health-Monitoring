# Bộ tài liệu dự án — Hệ thống Giám sát Server từ xa (Server Health Monitoring)

Tài liệu lý thuyết, hướng dẫn và tham chiếu của dự án, được sắp xếp thành 8 nhóm
phù hợp với cấu trúc code (4 module Maven), bộ trực quan hóa và quy trình git. Đây là **chỉ mục tổng hợp** — chọn
đúng thư mục theo mục đích đọc.

- Ngôn ngữ: **tiếng Việt cho toàn bộ file Markdown** trong `docs\`
  (kể cả các tài liệu gốc tiếng Anh của dự án đã được dịch và giữ nguyên cấu trúc).
  Chỉ các sản phẩm do công cụ sinh mới giữ tiếng Anh để tránh lỗi font:
  dashboard Understand Anything (`07-hinh-anh-truc-quan/01-dashboard.html`)
  và khung Viewer của 3 sơ đồ Archify (nội dung sơ đồ vẫn là tiếng Việt).
- Quy tắc: mọi nội dung trong bộ tài liệu này được kiểm chứng từ mã nguồn,
  file cấu hình và script của dự án; điểm chưa kiểm chứng được đều được ghi rõ
  là "chưa xác định".
- Lịch sử thay đổi của **tài liệu** ghi tại [00-lich-su/01-tai-lieu.md](00-lich-su/01-tai-lieu.md)
  (đối trọng với [00-lich-su/02-ma-nguon.md](00-lich-su/02-ma-nguon.md) — lịch sử **mã nguồn**).
  Dòng `Trạng thái: Docs khớp mã nguồn …` ở đầu file cho biết docs đang mô tả bản nào.

---

## 1. Cấu trúc bộ tài liệu

```
docs\
├── README.md                      ← chỉ mục này
├── 00-lich-su\                    Lịch sử theo version: tài liệu & mã nguồn
├── 01-gioi-thieu\                 Giới thiệu tổng quan → chi tiết đề tài
├── 02-kien-truc-va-nguyen-ly\     Cấu trúc, mô hình, nguyên lý, cách hoạt động
├── 03-huong-dan-su-dung\          Hướng dẫn cài đặt, chạy, cấu hình, kiểm thử
├── 04-tai-lieu-class\             Các class đã tạo: công dụng, vai trò, nơi gọi
├── 05-tu-khoa\                    Danh sách từ khóa và giải thích
├── 06-huong-dan-script\           Hướng dẫn nhanh các file .bat / .ps1
├── 07-hinh-anh-truc-quan\         Dashboard HTML offline + sơ đồ SVG
└── 08-git\                        Quy trình git: nhánh, commit, đóng góp
```

---

## 2. Danh mục tài liệu

### 2.0. `00-lich-su\` — Lịch sử theo version

| Tài liệu | Nội dung |
|---|---|
| [00-lich-su/01-tai-lieu.md](00-lich-su/01-tai-lieu.md) | Lịch sử **tài liệu**: mỗi version tạo/sửa/giữ nguyên file nào, đối chiếu code lúc nào, trạng thái "Docs khớp mã nguồn", checklist cập nhật khi ra v2 |
| [00-lich-su/02-ma-nguon.md](00-lich-su/02-ma-nguon.md) | Lịch sử **mã nguồn** (semver): chức năng từng bản 1.0.0, template chờ 2.0.0 |

### 2.1. `01-gioi-thieu\` — Giới thiệu đề tài

| Tài liệu | Nội dung |
|---|---|
| [01-tong-quan-de-tai.md](01-gioi-thieu/01-tong-quan-de-tai.md) | Thông tin đề tài & thành viên nhóm, bối cảnh, mục tiêu, tổng quan hệ thống, công nghệ, phạm vi |
| [02-chi-tiet-de-tai.md](01-gioi-thieu/02-chi-tiet-de-tai.md) | Bài toán, yêu cầu chức năng và phi chức năng, kiểm chứng 5 yêu cầu bổ sung của giảng viên, kịch bản sử dụng |

### 2.2. `02-kien-truc-va-nguyen-ly\` — Cấu trúc, mô hình, nguyên lý, hoạt động

| Tài liệu | Nội dung |
|---|---|
| [01-cau-truc-du-an.md](02-kien-truc-va-nguyen-ly/01-cau-truc-du-an.md) | Cấu trúc thư mục, 4 module Maven, cấu trúc package, phiên bản thư viện |
| [02-mo-hinh-he-thong.md](02-kien-truc-va-nguyen-ly/02-mo-hinh-he-thong.md) | Mô hình client–server, sơ đồ tổng, hai kênh UDP 6000 / TCP 5000 và lý do chọn |
| [03-cach-thuc-hoat-dong.md](02-kien-truc-va-nguyen-ly/03-cach-thuc-hoat-dong.md) | Cách thức hoạt động từng luồng: khởi động, đo & gửi, nhận–xác thực–lưu, heartbeat, cấu hình từ xa, email, tổng hợp dữ liệu |
| [04-giao-thuc-tcp-udp.md](02-kien-truc-va-nguyen-ly/04-giao-thuc-tcp-udp.md) | Khung frame TCP, các loại message, cấu trúc packet UDP, ví dụ JSON |
| [05-bao-mat-hmac-va-chong-phat-lai.md](02-kien-truc-va-nguyen-ly/05-bao-mat-hmac-va-chong-phat-lai.md) | HMAC-SHA256, chuỗi canonical, so sánh constant-time, cửa sổ sequence, timestamp skew, kịch bản kiểm thử |
| [06-luu-tru-va-tong-hop.md](02-kien-truc-va-nguyen-ly/06-luu-tru-va-tong-hop.md) | SQLite, 4 bảng, index, WAL, retention, aggregation/rollup, chế độ demo |
| [07-canh-bao-va-thong-bao.md](02-kien-truc-va-nguyen-ly/07-canh-bao-va-thong-bao.md) | 8 loại cảnh báo, hysteresis, offline/recovered, luồng gửi email SMTP |
| [08-mo-hinh-da-luong.md](02-kien-truc-va-nguyen-ly/08-mo-hinh-da-luong.md) | Các luồng phía Agent và Monitor, ExecutorService, EDT, cơ chế đồng bộ, xử lý khi queue đầy |
| [09-kien-truc-goc.md](02-kien-truc-va-nguyen-ly/09-kien-truc-goc.md) | Phân tầng, luồng dữ liệu, mô hình luồng |
| [10-giao-thuc-goc.md](02-kien-truc-va-nguyen-ly/10-giao-thuc-goc.md) | Wire format, framing, chuỗi ký, ví dụ message |
| [11-co-so-du-lieu-goc.md](02-kien-truc-va-nguyen-ly/11-co-so-du-lieu-goc.md) | Bảng, index, retention, chế độ demo |

### 2.3. `03-huong-dan-su-dung\` — Hướng dẫn sử dụng

| Tài liệu | Nội dung |
|---|---|
| [01-cai-dat-va-build.md](03-huong-dan-su-dung/01-cai-dat-va-build.md) | Yêu cầu hệ thống, cài đặt, build, cấu trúc jar, mở dự án trên NetBeans |
| [02-huong-dan-su-dung.md](03-huong-dan-su-dung/02-huong-dan-su-dung.md) | Chạy Monitor/Agent/demo 4 agent, 5 tab giao diện, cấu hình từ xa, các bước thao tác |
| [03-cau-hinh-he-thong.md](03-huong-dan-su-dung/03-cau-hinh-he-thong.md) | Giải thích từng file `.properties`, từng khóa cấu hình, thư mục `data\`, `logs\`, firewall |
| [04-huong-dan-netbeans.md](03-huong-dan-su-dung/04-huong-dan-netbeans.md) | Các bước thao tác chi tiết trên NetBeans |
| [05-trien-khai-nhieu-may.md](03-huong-dan-su-dung/05-trien-khai-nhieu-may.md) | Triển khai 5 máy (1 monitor + 4 agent) |
| [06-cai-dat-email.md](03-huong-dan-su-dung/06-cai-dat-email.md) | Bật cảnh báo email qua Gmail SMTP từng bước |
| [07-kich-ban-demo.md](03-huong-dan-su-dung/07-kich-ban-demo.md) | Kịch bản trình chiếu 12–15 phút |
| [08-kiem-thu.md](03-huong-dan-su-dung/08-kiem-thu.md) | Toàn bộ test tự động và cách chạy |
| [09-bao-cao-kiem-thu.md](03-huong-dan-su-dung/09-bao-cao-kiem-thu.md) | Kết quả kiểm thử thực tế, phần chưa thực thi |
| [10-xu-ly-su-co.md](03-huong-dan-su-dung/10-xu-ly-su-co.md) | Triệu chứng thường gặp và cách sửa |
| [11-chay-thu-cong-khong-dung-file-bat.md](03-huong-dan-su-dung/11-chay-thu-cong-khong-dung-file-bat.md) | Gõ lệnh trực tiếp thay vì double-click `.bat`: thứ tự build → client → agent, bảng tra nhanh lệnh tương đương từng script, lỗi thường gặp |

### 2.4. `04-tai-lieu-class\` — Các class đã tạo và vai trò

| Tài liệu | Nội dung |
|---|---|
| [00-tong-quan-class.md](04-tai-lieu-class/00-tong-quan-class.md) | Bảng tổng hợp đủ 100 class cả dự án, sơ đồ phân tầng, hướng đọc, các lớp `main` |
| [01-monitoring-common.md](04-tai-lieu-class/01-monitoring-common.md) | 27 class module dùng chung: model message, giao thức, bảo mật, tiện ích |
| [02-agent-app.md](04-tai-lieu-class/02-agent-app.md) | 14 class phía Agent: vòng đời, thu thập chỉ số, gửi UDP, TCP server, cấu hình |
| [03-monitor-app-phan-1.md](04-tai-lieu-class/03-monitor-app-phan-1.md) | 24 class Monitor phần 1: cấu hình, lõi, model, mạng, thông báo email |
| [04-monitor-app-phan-2.md](04-tai-lieu-class/04-monitor-app-phan-2.md) | 14 class Monitor phần 2: repository (SQLite) và service |
| [05-monitor-app-phan-3.md](04-tai-lieu-class/05-monitor-app-phan-3.md) | 13 class Monitor phần 3: giao diện Swing |
| [06-simulator-app.md](04-tai-lieu-class/06-simulator-app.md) | 8 class trình mô phỏng lưu lượng & kiểm thử bảo mật |

Mỗi file gồm: bảng tổng quan package → chi tiết từng class (vai trò, thành phần
chính, **được gọi ở đâu và để làm gì**, quan hệ, ghi chú) → sơ đồ quan hệ.

### 2.5. `05-tu-khoa\` — Từ khóa

| Tài liệu | Nội dung |
|---|---|
| [01-tu-khoa.md](05-tu-khoa/01-tu-khoa.md) | 106 thuật ngữ A–Z theo 8 nhóm (giao thức & mạng, bảo mật, giám sát & cảnh báo, lưu trữ, cấu hình & build, kiến trúc & đa luồng, UI & thông báo, khái niệm dự án), mỗi mục có cách đọc, nghĩa và liên kết tài liệu chi tiết |

### 2.6. `06-huong-dan-script\` — Hướng dẫn nhanh script

| Tài liệu | Nội dung |
|---|---|
| [01-cac-file-bat.md](06-huong-dan-script/01-cac-file-bat.md) | 15 script `.bat`/`.ps1`: mục đích, nội dung, cách chạy, kết quả mong đợi, thứ tự chạy khuyến nghị |

### 2.7. `07-hinh-anh-truc-quan\` — Hình ảnh & đồ thị trực quan

| Tài liệu | Nội dung |
|---|---|
| [README.md](07-hinh-anh-truc-quan/README.md) | Chỉ mục 4 dashboard HTML offline + 3 sơ đồ SVG, cách mở, ghi chú kỹ thuật |
| [01-dashboard.html](07-hinh-anh-truc-quan/01-dashboard.html) | Dashboard đồ thị tổng hợp (tiếng Anh, offline): 649 nút, 1.244 quan hệ, 22 lớp, tour 50 bước |
| [so-do/README.md](07-hinh-anh-truc-quan/so-do/README.md) | 3 sơ đồ SVG nhúng sẵn để chèn vào báo cáo Word |
| [02-kien-truc-he-thong-archify.html](07-hinh-anh-truc-quan/02-kien-truc-he-thong-archify.html) | Sơ đồ kiến trúc tổng thể tương tác (Archify, offline) |
| [03-vong-doi-goi-telemetry-archify.html](07-hinh-anh-truc-quan/03-vong-doi-goi-telemetry-archify.html) | Sơ đồ sequence vòng đời gói telemetry (Archify, offline) |
| [04-dong-du-lieu-metrics-archify.html](07-hinh-anh-truc-quan/04-dong-du-lieu-metrics-archify.html) | Sơ đồ dataflow pipeline metrics (Archify, offline) |

### 2.8. `08-git\` — Quy trình git

| Tài liệu | Nội dung |
|---|---|
| [01-quy-trinh-dong-gop.md](08-git/01-quy-trinh-dong-gop.md) | Quy trình đóng góp: nhánh, task workflow, Conventional Commits, danh sách không commit |
| [02-quy-trinh-git.md](08-git/02-quy-trinh-git.md) | Sơ đồ nhánh main/develop/release, lệnh git hằng ngày, squash merge, tag bản release |

---

## 3. Gợi ý thứ tự đọc

**Để hiểu đề tài (người mới):**
`01-gioi-thieu/01` → `01-gioi-thieu/02` → `02-kien-truc-va-nguyen-ly/02` →
`02-kien-truc-va-nguyen-ly/03`

**Để hiểu code (developer):**
`04-tai-lieu-class/00` → tài liệu module tương ứng →
`02-kien-truc-va-nguyen-ly/04` đến `08` → `05-tu-khoa/01-tu-khoa.md`

**Để chạy dự án (vận hành):**
`03-huong-dan-su-dung/01` → `02` → `03` → `06-huong-dan-script/01-cac-file-bat.md`
→ muốn gõ lệnh tay thay vì chạy `.bat` thì đọc
`03-huong-dan-su-dung/11-chay-thu-cong-khong-dung-file-bat.md`

**Để ôn thi / thuyết trình:**
`02-kien-truc-va-nguyen-ly/01..08` (nhất là 02, 03, 04, 05) → `05-tu-khoa` →
`03-huong-dan-su-dung/07-kich-ban-demo.md`

**Để học bằng hình ảnh (trực quan):**
mở `07-hinh-anh-truc-quan/01-dashboard.html` → đi theo Tour trong dashboard →
xem `so-do/01-kien-truc-tong-the.svg` và `so-do/02-luu-du-lieu-va-canh-bao.svg`

---

## 4. Ghi chú

- Mọi file Markdown trong bộ tài liệu này đã được thống nhất tiếng Việt;
  nội dung được kiểm chứng từ mã nguồn, file cấu hình và script của dự án.
- Nếu phát hiện nội dung nào chưa khớp với code, ưu tiên kiểm chứng tại:
  `README.md` (gốc dự án), mã nguồn trong 4 module và các file `config\*.properties`.
