# 07. Hình ảnh & đồ thị trực quan

Bộ công cụ hình ảnh trực quan của dự án: **1 dashboard đồ thị tổng hợp xem offline**
(HTML tự chứa, không cần server/internet), **3 sơ đồ tương tác Archify** (kiến trúc,
sequence, dataflow) và **3 sơ đồ tĩnh SVG** để chèn vào báo cáo.

Tất cả dữ liệu đồ thị được sinh tự động từ mã nguồn bằng pipeline
[Understand Anything](https://github.com/Egonex-AI/Understand-Anything)
(phân tích ngữ nghĩa từng file → nút/edge → phân lớp kiến trúc → tour học tập),
nội dung **tiếng Anh** (để tránh lỗi font), đối chiếu với code và `config\`.

---

## 1. Dashboard đồ thị tổng hợp (HTML offline)

**Cách dùng:** bấm đúp vào [01-dashboard.html](01-dashboard.html) → mở trực tiếp trong
trình duyệt (Chrome/Edge/Firefox), **hoạt động hoàn toàn offline** (`file://`),
không cần cài gì thêm, không cần internet.

Một dashboard duy nhất gộp toàn dự án: **649 nút** (120 file Java · 118 class ·
342 hàm · 15 script · 34 tài liệu · 12 cấu hình · 5 bảng), **1.244 quan hệ**,
**22 lớp kiến trúc**, **tour 50 bước** (mã nguồn → script → tài liệu → cấu hình).

**Dashboard có:**
- **Graph view** — sơ đồ nút/cạnh, kéo–thả, thu/phóng, nhấp nút để xem chi tiết
  (tóm tắt tiếng Anh, tag, quan hệ); có thanh tìm kiếm theo tên/tóm tắt/tag.
- **Tab Domain** — sơ đồ luồng nghiệp vụ (6 domains · 18 flows · 76 steps:
  protocol & security, telemetry collection, storage, email alerting,
  Swing UI, testing & simulation).
- **Tour** — lộ trình học 50 bước có hướng dẫn, bắt đầu từ các entry point.
- **Code viewer** — nhấp nút file để xem **nguồn file ngay trong trang**
  (182 file nguồn đã được nhúng kèm, xem được cả khi offline).
- **Layers** — 22 lớp kiến trúc, ẩn–hiện theo lớp.

**Ghi chú kỹ thuật:**
- Toàn bộ dashboard dùng **tiếng Anh** (nội dung lẫn giao diện) để tránh lỗi font.
- Nếu trình duyệt chặn Web Worker trên `file://`, đồ thị tự chuyển sang bố cục
  lưới dự phòng (vẫn xem được đầy đủ dữ liệu).
- Dữ liệu gốc: `.ua\graph-full-en.json` (gộp từ 4 graph tiếng Việt
  `.ua\graph-nguon-code.json`, `.ua\graph-script.json`, `.ua\graph-docs.json`,
  `.ua\graph-cau-hinh.json` rồi dịch sang tiếng Anh) + `.ua\domain-graph-en.json`.

## 2. Sơ đồ tĩnh (SVG) — chèn vào báo cáo Word

Xem [so-do/README.md](so-do/README.md): 3 sơ đồ 1400px, nền trắng, nhãn
tiếng Việt, mở bằng mọi trình duyệt, chèn thẳng vào Word
(Chèn → Hình ảnh → Từ tệp, chọn `.svg`).

| Sơ đồ | Nội dung |
|---|---|
| [01-kien-truc-tong-the.svg](so-do/01-kien-truc-tong-the.svg) | Kiến trúc tổng thể: monitor + 4 agent + simulator, UDP 6000 / TCP 5000, SQLite, email, `monitoring-common` |
| [02-luu-du-lieu-va-canh-bao.svg](so-do/02-luu-du-lieu-va-canh-bao.svg) | Luồng dữ liệu & cảnh báo: HMAC + chống replay (cửa sổ 64) → metric_raw → tổng hợp 5 phút → ngưỡng → alerts → email |
| [03-cac-lop-monitor-app.svg](so-do/03-cac-lop-monitor-app.svg) | Các lớp của `monitor-app`: Swing UI → Service → Network → Storage → Config + dải `monitoring-common` |

## 3. Sơ đồ tương tác Archify (HTML offline)

Ba sơ đồ được dựng bằng skill **Archify** từ chính dữ liệu
Understand Anything (7 lớp kiến trúc + 14 bước tour của graph mã nguồn,
6 lĩnh vực/18 luồng domain, 4 bảng của graph cấu hình): viết candidate JSON
→ chạy `finalize` qua đủ 4 gates (validate, deliver, check, browser-check).
Mở trực tiếp bằng trình duyệt, offline hoàn toàn.

| File HTML | Loại | Nội dung |
|---|---|---|
| [02-kien-truc-he-thong-archify.html](02-kien-truc-he-thong-archify.html) | architecture | Kiến trúc tổng thể: Agent ×4 → UDP 6000 (HMAC) → SQLite → Swing/Email; kênh TCP 5000; simulator; `monitoring-common`; 3 vùng máy + 3 thẻ ghi chú |
| [03-vong-doi-goi-telemetry-archify.html](03-vong-doi-goi-telemetry-archify.html) | sequence | 11 message qua 7 lifeline: gửi gói → xác thực → lưu `metric_raw` → tổng hợp → `alerts` → email → hiển thị lịch sử |
| [04-dong-du-lieu-metrics-archify.html](04-dong-du-lieu-metrics-archify.html) | dataflow | Dòng dữ liệu qua 5 chặng: nguồn → tiếp nhận → lưu thô → tổng hợp → tiêu thụ (`agents`, `metric_raw`, `metric_5m`, `alerts`) |

Mỗi trang có chuyển theme sáng/tối, nút xuất ảnh PNG/SVG và chế độ trình chiếu.

**Ghi chú kỹ thuật:**
- Nội dung sơ đồ là tiếng Việt; khung Viewer của Archify hiển thị tiếng Anh
  (không đặt `locale: vi` vì renderer hiện tại crash với locale này —
  đã kiểm chứng, schema nhận nhưng render rớt).
- Không gắn repository evidence (`meta.repository`, `--repo-root`) vì dự án
  không phải git repo — sơ đồ thuộc diện "diagram without repository evidence"
  theo đúng delivery contract của Archify; nguồn sự thật là 4 graph trong
  `.ua/` + tài liệu đã đối chiếu code ở `docs/02`.
- Bản gốc để tái sinh: `.archify/architecture-server-health-20261002-135132/`,
  `.archify/sequence-telemetry-20261002-135132/`,
  `.archify/dataflow-metrics-20261002-135132/` (mỗi thư mục giữ
  `candidate.json` + biên nhận finalize `*.finalize-summary.json`).

## 4. Ghi chú sinh dữ liệu

- Pipeline Understand Anything chạy 4 lần theo loại file (mã nguồn / script /
  tài liệu / cấu hình), sau đó gộp thành một graph duy nhất
  (649 nút, 0 trùng ID), dịch toàn bộ sang tiếng Anh và đánh lại tour 1–50.
- Repo không phải git → hash commit ghi `no-git`; dữ liệu phân tích one-shot,
  không dùng incremental update.
- File `config\email-secrets.properties` bị loại khỏi phạm vi phân tích
  (chỉ giữ bản `.example`). HTML có nhúng nguyên văn nguồn các file trong
  phạm vi graph — gồm cả giá trị demo/placeholder đã công khai trong repo
  (ví dụ `GROUP10_DEMO_SECRET_0x`, `PASTE_GMAIL_APP_PASSWORD_HERE`) —
  nên không dùng các file này để lưu secret thật.
