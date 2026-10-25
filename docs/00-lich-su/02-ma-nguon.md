# Lịch sử mã nguồn (Source Changelog)

Tài liệu thay đổi gì ở từng bản → xem [`01-tai-lieu.md`](01-tai-lieu.md).
File này chỉ ghi **mã nguồn**.

Quy ước đánh số theo [Semantic Versioning](https://semver.org/lang/vi/):
`MAJOR.MINOR.PATCH` — phá API → MAJOR, thêm chức năng tương thích → MINOR,
sửa lỗi → PATCH.

---

## [1.0.0] — 2026-10-02

Bản phát hành đầu tiên. Snapshot: `release/ServerHealthMonitoring-v1.0.0/`.

### Cấu trúc
- Maven multi-module, Java 17, package `vn.edu.hutech.group10.monitoring`, 4 module:
  `monitoring-common`, `agent-app`, `monitor-app`, `simulator-app`.

### Chức năng
- **Agent**: thu thập CPU / RAM / ổ đĩa / mạng, gửi telemetry UDP cổng **6000** (chu kỳ 1 s),
  nhận cấu hình & điều khiển qua TCP cổng **5000**.
- **Monitor**: gom mẫu metric, tổng hợp theo bucket **5 phút**, lưu SQLite
  (`agents`, `metric_raw`, `metric_5m`, `alerts`).
- **Cảnh báo**: ngưỡng **85% CPU / 85% RAM / 50 Mbps** với hysteresis **2.0 điểm / 5%**,
  8 loại `AlertType`, gửi email qua hàng đợi `ArrayBlockingQueue(50)`.
- **Bảo mật**: ký HMAC-**SHA256**, chống replay với cửa sổ **64** packet.
- **Demo**: 4 agent `agent01`–`agent04`, simulator tạo tải.

### Tài liệu
- Bộ docs tiếng Việt 8 nhóm + dashboard knowledge graph + 3 sơ đồ Archify:
  [`01-tai-lieu.md`](01-tai-lieu.md).

---

<!-- ## [2.0.0] — (ngày ra mắt)
### Thêm
### Sửa
### Phá API
-->
