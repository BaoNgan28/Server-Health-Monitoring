# Cơ sở dữ liệu

Nhóm 10 — Hệ thống Giám sát Sức khỏe Máy chủ (Server Health Monitoring System)

SQLite, một file duy nhất, được tạo cùng schema ngay lần đầu monitoring client khởi động. Không cần chuẩn bị gì bằng tay và không cần database server.

| | |
|---|---|
| File production | `data\monitoring.db` |
| File của profile demo | `data\monitoring-demo.db` |
| Schema | `monitor-app/src/main/resources/db/schema.sql` |

Profile demo cố ý dùng file riêng, nhờ đó việc demo hệ thống không thể làm ảnh hưởng dữ liệu đã thu thập.

---

## 1. Các bảng

### 1.1 `agents`

Mọi agent mà client biết tới.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | INTEGER | khóa chính, tự tăng |
| `agent_id` | TEXT | **UNIQUE**; định danh dùng để ký packet |
| `name` | TEXT | tên hiển thị |
| `host` | TEXT | địa chỉ của kênh điều khiển TCP của agent |
| `tcp_port` | INTEGER | cổng điều khiển |
| `shared_secret` | TEXT | khóa dùng để ký các packet UDP của nó |
| `cpu_threshold` | REAL | phần trăm, mặc định 85 |
| `memory_threshold` | REAL | phần trăm, mặc định 85 |
| `network_in_threshold` | REAL | Mbps, mặc định 50 |
| `network_out_threshold` | REAL | Mbps, mặc định 50 |
| `status` | TEXT | `UNKNOWN`, `ONLINE`, `WARNING`, `OFFLINE` |
| `last_seen` | INTEGER | epoch ms của packet được chấp nhận gần nhất |
| `created_at`, `updated_at` | INTEGER | epoch ms |

`shared_secret` không bao giờ hiển thị trong bảng agent, không bao giờ bị log và không bao giờ gửi đi đâu. Dialog chỉnh sửa nó dùng trường mật khẩu.

`status` đã lưu chỉ là lịch sử, không phải bằng chứng: lúc khởi động mọi agent đều được đặt lại về `UNKNOWN` cho đến khi packet tới, nhờ đó client khởi động lại sau một tuần không tuyên bố agent đang online chỉ vì nó từng online lúc client đóng lần trước.

### 1.2 `metric_raw`

Một dòng cho mỗi packet telemetry được chấp nhận.

| Cột | Kiểu |
|---|---|
| `id` | INTEGER primary key |
| `agent_id` | TEXT → `agents(agent_id)` ON DELETE CASCADE |
| `timestamp` | INTEGER, epoch ms |
| `cpu_usage` | REAL, percent |
| `memory_usage` | REAL, percent |
| `connection_count` | INTEGER |
| `uptime_seconds` | INTEGER |
| `network_in_bps` | REAL, bytes per second |
| `network_out_bps` | REAL, bytes per second |

Với một mẫu mỗi giây, bốn agent tạo ra khoảng 345 000 dòng mỗi ngày, đó chính là lý do bảng này không được giữ mãi mãi.

### 1.3 `metric_5m`

Một dòng cho mỗi agent trong mỗi bucket aggregation.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | INTEGER | khóa chính |
| `agent_id` | TEXT | → `agents(agent_id)` ON DELETE CASCADE |
| `bucket_start` | INTEGER | epoch ms của thời điểm đầu tiên trong bucket |
| `sample_count` | INTEGER | số mẫu thô đã được gộp vào |
| `avg_cpu`, `max_cpu` | REAL | |
| `avg_memory`, `max_memory` | REAL | |
| `avg_connections` | REAL | |
| `avg_network_in_bps`, `max_network_in_bps` | REAL | |
| `avg_network_out_bps`, `max_network_out_bps` | REAL | |

`UNIQUE (agent_id, bucket_start)` — chính điều này khiến một bucket không thể bị ghi hai lần, ngay cả khi job aggregation chạy lại trên cùng cửa sổ sau khởi động lại.

Cả giá trị trung bình **và** giá trị đỉnh đều được giữ. Chỉ giữ trung bình sẽ che mất spike gây ra sự cố, mà đó thường là thứ duy nhất đáng chú ý trong cả bucket.

### 1.4 `alerts`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | INTEGER | khóa chính |
| `agent_id` | TEXT | → `agents(agent_id)` ON DELETE CASCADE |
| `alert_type` | TEXT | `CPU_HIGH`, `MEMORY_HIGH`, `NETWORK_IN_HIGH`, `NETWORK_OUT_HIGH`, `AGENT_OFFLINE`, `AGENT_RECOVERED`, `SECURITY_INVALID_HMAC`, `SECURITY_REPLAY_PACKET` |
| `severity` | TEXT | `INFO`, `WARNING`, `CRITICAL` |
| `message` | TEXT | con người đọc được |
| `opened_at` | INTEGER | epoch ms |
| `resolved_at` | INTEGER | null khi vẫn đang mở |
| `status` | TEXT | `OPEN` hoặc `RESOLVED` |
| `current_value` | REAL | giá trị đã kích hoạt nó |
| `threshold` | REAL | giá trị dùng để so sánh |
| `unit` | TEXT | `%`, `Mbps`, hoặc rỗng |

Đối với `AGENT_OFFLINE`, `current_value` giữ timestamp last-seen và `threshold` giữ timeout tính bằng milliseconds; e-mail offline đọc chính các giá trị này để nói agent được nghe thấy lần cuối khi nào.

---

## 2. Các index

```sql
CREATE INDEX idx_metric_raw_agent_time  ON metric_raw (agent_id, timestamp);
CREATE INDEX idx_metric_5m_agent_bucket ON metric_5m  (agent_id, bucket_start);
CREATE INDEX idx_alerts_agent_status    ON alerts     (agent_id, status);
CREATE INDEX idx_alerts_opened_at       ON alerts     (opened_at);
```

Mỗi index tương ứng với một truy vấn mà ứng dụng thực sự thực hiện. Lịch sử luôn lọc theo agent rồi quét một khoảng thời gian, đúng với thứ tự cột đầu của hai index đầu. Engine cảnh báo hỏi “có alert đang mở thuộc loại này cho agent này không” trên từng mẫu, vì vậy `(agent_id, status)` nằm trên hot path.

---

## 3. Quan hệ

```
  agents 1 ----- * metric_raw     ON DELETE CASCADE
  agents 1 ----- * metric_5m      ON DELETE CASCADE
  agents 1 ----- * alerts         ON DELETE CASCADE
```

Xóa một agent sẽ xóa luôn metric và alert của nó, nhờ đó database không tích tụ các dòng không còn join về đâu được. Foreign key trong SQLite mặc định tắt và được bật cho mỗi kết nối.

---

## 4. Thiết lập kết nối

Được áp dụng trên kết nối duy nhất khi nó được mở:

| Pragma | Giá trị | Lý do |
|---|---|---|
| `foreign_keys` | `ON` | các cascade ở trên chỉ hoạt động khi bật cái này |
| `journal_mode` | `WAL` | truy vấn lịch sử có thể đọc trong khi telemetry đang ghi |
| `busy_timeout` | `5000` | chờ thay vì lỗi nếu file bị khóa tạm thời |
| `synchronous` | `NORMAL` | an toàn dưới WAL, và nhanh hơn nhiều so với `FULL` |

**Đồng thời (Concurrency).** Một kết nối được dùng chung và mọi đơn vị công việc chạy bên trong `DatabaseManager.execute`, nơi giữ một lock duy nhất. Nhờ cấu trúc này số writer luôn đúng bằng một, đúng mô hình SQLite thích nhất. Ngoài busy timeout của driver còn có một lần thử lại có giới hạn cho lỗi `database is locked`, lỗi này vẫn có thể xảy ra nếu tiến trình khác đang mở file.

Mọi statement và result set đều được mở trong khối try-with-resources, nhờ đó không rò rỉ gì ngay cả khi truy vấn lỗi. `inTransaction` commit khi thành công và rollback khi có bất kỳ lỗi nào, kể cả runtime exception.

---

## 5. Lưu giữ và aggregation (Retention and aggregation)

Chi tiết từng giây vượt xa nhu cầu của bất kỳ ai ngoài một giờ gần nhất, vì vậy các mẫu thô được gộp vào các bucket có độ rộng cố định và các bucket cuối cùng sẽ hết hạn.

| | Production | Profile demo |
|---|---|---|
| Lưu giữ raw | 60 phút | 60 giây |
| Độ rộng bucket | 5 phút | 10 giây |
| Lưu giữ aggregate | 30 ngày | 300 giây |
| Khoảng chạy job | 300 giây | 15 giây |

Một lượt chạy thực hiện đúng các bước sau:

1. tính `cutoff = now - rawRetention`, **căn xuống theo biên bucket**;
2. `INSERT OR IGNORE INTO metric_5m ... SELECT ... FROM metric_raw WHERE
   timestamp < cutoff GROUP BY agent_id, bucket_start`;
3. `DELETE FROM metric_raw WHERE timestamp < cutoff`;
4. `DELETE FROM metric_5m WHERE bucket_start < now - aggregateRetention`.

Bước 2 và 3 nằm trong một transaction, nhờ đó chi tiết không bao giờ bị xóa khi bản tóm tắt chưa tồn tại. Căn cutoff xuống dưới nghĩa là bucket đang ghi dở vẫn giữ các dòng raw đến lượt chạy sau, nhờ đó không bucket nào bị tóm tắt dang dở. `INSERT OR IGNORE` cộng với unique index khiến toàn bộ lượt chạy an toàn khi lặp lại.

Hai profile chỉ khác nhau ở các con số trên. Không có nhánh riêng cho demo ở bất kỳ đâu trong code — `AggregationService` đọc chúng từ `MonitorConfig`, và các unit test dùng cùng một method với timestamp giả lập để kiểm tra bucket 5 phút và bucket 10 giây qua cùng một đường đi.

---

## 6. Bảng nào trả lời câu hỏi lịch sử

`HistoryService` quyết định, và panel không cần biết:

| Khoảng yêu cầu | Nguồn |
|---|---|
| bắt đầu trong cửa sổ lưu giữ raw | `metric_raw`, từng mẫu |
| cũ hơn thế | `metric_5m`, trung bình bucket |

Nếu bảng aggregate rỗng — database mới, trước khi job đầu tiên chạy — bảng raw được dùng làm fallback để panel không trống. Các khoảng dài được down-sample trước khi vẽ, vì vẽ nhiều điểm hơn số pixel của màn hình vừa tốn thời gian vừa chẳng hiển thị thêm gì.

---

## 7. Nhìn vào bên trong database

File này là database SQLite bình thường; bất kỳ công cụ SQLite nào cũng mở được. Ví dụ:

```sql
SELECT agent_id, status, datetime(last_seen/1000, 'unixepoch', 'localtime')
FROM agents;

SELECT COUNT(*) FROM metric_raw;

SELECT agent_id, datetime(bucket_start/1000,'unixepoch','localtime'),
       sample_count, ROUND(avg_cpu,1), ROUND(max_cpu,1)
FROM metric_5m ORDER BY bucket_start DESC LIMIT 20;

SELECT alert_type, agent_id, severity, status,
       datetime(opened_at/1000,'unixepoch','localtime'), message
FROM alerts ORDER BY opened_at DESC LIMIT 20;
```

Hãy đóng monitoring client trước, hoặc mở file ở chế độ chỉ đọc: client giữ kết nối trong khi chạy.
