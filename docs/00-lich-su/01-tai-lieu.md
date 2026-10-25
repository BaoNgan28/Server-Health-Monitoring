# Lịch sử tài liệu (Docs Changelog)

Trạng thái: **Docs khớp mã nguồn v1.0.0** (đối chiếu lần cuối: 02/10/2026)

- File này ghi lại *tài liệu* thay đổi gì, khi nào và vì sao — đối trọng với
  [`02-ma-nguon.md`](02-ma-nguon.md) ở cùng thư mục (ghi *mã nguồn*).
- Quy tắc mỗi lần cập nhật: ghi **đã sửa gì (file) — nguyên nhân từ code —
  phần nào xác nhận giữ nguyên**.

---

## v1.0.0 — 02/10/2026 (tạo mới)

**Khớp mã nguồn:** `pom.xml` version `1.0.0`, snapshot `release/ServerHealthMonitoring-v1.0.0/`.

### Tạo mới
- Bộ tài liệu 8 nhóm (`01-gioi-thieu` … `08-git`), tổng 40 file Markdown,
  301 link nội bộ (QA: 0 hỏng).
- Bộ trực quan hóa `07-hinh-anh-truc-quan/`:
  - `01-dashboard.html` — knowledge graph Understand Anything (offline, tiếng Anh để tránh lỗi font).
  - `02`–`04` … `-archify.html` — 3 sơ đồ Archify: kiến trúc, vòng đời gọi telemetry, dòng dữ liệu metric.
  - `so-do/` — 3 SVG tĩnh tiếng Việt để chèn Word.
- Đối chiếu toàn bộ số liệu với code (phần "Xác minh qua code" trong README):
  cổng UDP 6000 / TCP 5000, HMAC-SHA256, replay window 64,
  ngưỡng 85% CPU / 85% RAM / 50 Mbps + hysteresis, bucket 5 phút,
  4 bảng SQLite (`agents`, `metric_raw`, `metric_5m`, `alerts`),
  8 `AlertType`, 4 agent (`agent01`–`agent04`), email queue `ArrayBlockingQueue(50)`.

### Sửa / sắp xếp
- Dịch toàn bộ 12 file Markdown gốc tiếng Anh sang tiếng Việt (giữ cấu trúc,
  code block/lệnh/số liệu giữ nguyên). Còn lại tiếng Anh chỉ trong
  `01-dashboard.html` và khung Viewer của 3 sơ đồ Archify.
- Đánh số thứ tự toàn bộ file theo nhóm (01-, 02-, …), thay 265 tham chiếu
  trong 26 file + 1 dòng `rem` trong `scripts/run-local-demo.bat`.
- Tách nhóm `08-git/` riêng (khác với hướng dẫn sử dụng `03-`).
- Xóa 4 dashboard phiên bản tách theo loại file (đã gộp thành 1).

### Giữ nguyên
- `01-gioi-thieu`, `05-tu-khoa/tu-khoa.md` — nội dung không phụ thuộc version.

---

## Khi ra v2.0.0 — checklist

1. Bump `Docs khớp mã nguồn:` đầu file này.
2. Rà bảng mapping trong README mục 2 (code đổi phần nào → sửa file docs đó).
3. Grep số liệu/version (`1.0.0`, cổng, ngưỡng) đối chiếu lại code.
4. Regenerate dashboard + archify (snapshot cũ giữ trong `release/`).
5. Chạy link-checker: 301 link, 0 hỏng.
6. Ghi entry mới vào file này + [`02-ma-nguon.md`](02-ma-nguon.md).
