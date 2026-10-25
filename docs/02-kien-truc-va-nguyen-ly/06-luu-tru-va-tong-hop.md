# 06 - Lưu trữ và tổng hợp dữ liệu

Hệ thống giám sát server từ xa — Group 10 — HUTECH, Lập trình mạng máy tính

Tài liệu mô tả lớp lưu trữ theo **code**, tham chiếu chính:
`monitor-app/src/main/resources/db/schema.sql`,
`monitor-app/.../repository/{DatabaseManager,MetricRepository,AlertRepository,AgentRepository}.java`,
`monitor-app/.../service/{AggregationService,HistoryService}.java`,
`config/monitor.properties`, `config/monitor-demo.properties`.

---

## 1. SQLite: vì sao chọn

| | |
|---|---|
| File production | `data\monitoring.db` |
| File demo | `data\monitoring-demo.db` |
| Schema | `monitor-app/src/main/resources/db/schema.sql` |
| Driver | `org.xerial:sqlite-jdbc` 3.46 |

**Lý do chọn SQLite** (theo `11-co-so-du-lieu-goc.md` và README mục 3, 15):

1. **Một file duy nhất** — không có server database nào phải cài, khởi động,
   quản lý hay backup riêng.
2. **Tự tạo lúc khởi động lần đầu** — `DatabaseManager.initialize()` tạo thư mục
   cha nếu thiếu, mở file, áp pragma rồi chạy schema. Không có gì phải chuẩn bị
   bằng tay.
3. **Không cần quản trị** — phù hợp một ứng dụng desktop Swing thuần Java, không
   web server, không Spring.
4. Schema được **chạy lại mỗi lần khởi động** và mọi câu lệnh đều idempotent
   (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`), nên database
   cũ được **nâng cấp tại chỗ** thay vì bị tạo lại.

**Chế độ demo dùng file riêng** — `monitor-demo.properties` đặt
`database.path=data/monitoring-demo.db`, để buổi trình diễn **không làm xáo trộn**
dữ liệu đã thu thập.

---

## 2. Bốn bảng

### 2.1 `agents` — mọi agent mà client biết

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | INTEGER | primary key, autoincrement |
| `agent_id` | TEXT | **UNIQUE** — định danh ký packet |
| `name` | TEXT | tên hiển thị |
| `host` | TEXT | địa chỉ kênh điều khiển TCP của agent |
| `tcp_port` | INTEGER | port điều khiển |
| `shared_secret` | TEXT | khóa ký các gói UDP (không bao giờ hiển thị/log/gửi đi) |
| `cpu_threshold` | REAL | %, mặc định `85.0` |
| `memory_threshold` | REAL | %, mặc định `85.0` |
| `network_in_threshold` | REAL | Mbps, mặc định `50.0` |
| `network_out_threshold` | REAL | Mbps, mặc định `50.0` |
| `status` | TEXT | `UNKNOWN`, `ONLINE`, `WARNING`, `OFFLINE` |
| `last_seen` | INTEGER | epoch ms của gói được chấp nhận cuối |
| `created_at`, `updated_at` | INTEGER | epoch ms |

`status` đã lưu là **lịch sử, không phải bằng chứng**: lúc khởi động mọi agent
được reset về `UNKNOWN` cho đến khi có packet đến, để client khởi động lại sau
một tuần không khẳng định agent đang online chỉ vì nó online khi client đóng
lần trước.

### 2.2 `metric_raw` — một dòng cho mỗi gói telemetry được chấp nhận

| Cột | Kiểu |
|---|---|
| `id` | INTEGER primary key |
| `agent_id` | TEXT → `agents(agent_id)` **ON DELETE CASCADE** |
| `timestamp` | INTEGER, epoch ms |
| `cpu_usage` | REAL, phần trăm |
| `memory_usage` | REAL, phần trăm |
| `connection_count` | INTEGER |
| `uptime_seconds` | INTEGER |
| `network_in_bps` | REAL, byte/giây |
| `network_out_bps` | REAL, byte/giây |

Ở tốc độ 1 mẫu/giây, **4 agent tạo khoảng 345 000 dòng mỗi ngày**
(1 × 60 × 60 × 24 × 4 = 345 600) — chính là lý do bảng này **không được giữ mãi**.

### 2.3 `metric_5m` — một dòng cho mỗi agent cho mỗi bucket (khối thời gian)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | INTEGER primary key | |
| `agent_id` | TEXT | → `agents(agent_id)` ON DELETE CASCADE |
| `bucket_start` | INTEGER | epoch ms của **giây đầu tiên** trong bucket |
| `sample_count` | INTEGER | bao nhiêu mẫu thô được gộp vào |
| `avg_cpu`, `max_cpu` | REAL | trung bình **và** cực đại |
| `avg_memory`, `max_memory` | REAL | |
| `avg_connections` | REAL | *(không có `max_connections` trong schema)* |
| `avg_network_in_bps`, `max_network_in_bps` | REAL | |
| `avg_network_out_bps`, `max_network_out_bps` | REAL | |

**`UNIQUE (agent_id, bucket_start)`** — ràng buộc này làm cho một bucket **không
thể bị ghi hai lần**, kể cả khi job tổng hợp chạy lại trên cùng khoảng sau một
lần khởi động lại.

**Lưu giữ cả trung bình lẫn cực đại.** Chỉ giữ trung bình sẽ **che mất con số
đỉnh** (spike) đã gây ra sự cố — và đó thường là thứ thú vị duy nhất trong cả
bucket (`11-co-so-du-lieu-goc.md` mục 1.3; `AggregationService` javadoc).

### 2.4 `alerts` — mọi cảnh báo, đang mở hay đã đóng

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | INTEGER primary key | |
| `agent_id` | TEXT | → `agents(agent_id)` ON DELETE CASCADE |
| `alert_type` | TEXT | `CPU_HIGH`, `MEMORY_HIGH`, `NETWORK_IN_HIGH`, `NETWORK_OUT_HIGH`, `AGENT_OFFLINE`, `AGENT_RECOVERED`, `SECURITY_INVALID_HMAC`, `SECURITY_REPLAY_PACKET` |
| `severity` | TEXT | `INFO`, `WARNING`, `CRITICAL` |
| `message` | TEXT | mô tả người-đọc-được |
| `opened_at` | INTEGER | epoch ms |
| `resolved_at` | INTEGER | `NULL` khi vẫn còn mở |
| `status` | TEXT | `OPEN` hoặc `RESOLVED` |
| `current_value` | REAL | giá trị đã kích hoạt |
| `threshold` | REAL | ngưỡng đã so sánh |
| `unit` | TEXT | `%`, `Mbps`, `"ms"` hoặc rỗng |

Với `AGENT_OFFLINE`, `current_value` chứa **thời điểm last-seen** (timestamp) và
`threshold` chứa **timeout tính bằng ms** — email offline đọc đúng hai trường
này để nói "lần cuối nghe thấy agent là khi nào"
(`AlertService.raiseOffline`).

---

## 3. Index (chỉ mục)

```sql
CREATE INDEX idx_metric_raw_agent_time  ON metric_raw (agent_id, timestamp);
CREATE INDEX idx_metric_5m_agent_bucket ON metric_5m  (agent_id, bucket_start);
CREATE INDEX idx_alerts_agent_status    ON alerts     (agent_id, status);
CREATE INDEX idx_alerts_opened_at       ON alerts     (opened_at);
```

Mỗi index khớp đúng một truy vấn mà ứng dụng thực sự chạy:

| Index | Truy vấn |
|---|---|
| `metric_raw(agent_id, timestamp)` | Lịch sử luôn lọc theo agent rồi quét khoảng thời gian — đúng thứ tự cột dẫn |
| `metric_5m(agent_id, bucket_start)` | Tương tự cho bảng tổng hợp |
| `alerts(agent_id, status)` | **Đường nóng**: engine cảnh báo hỏi "có cảnh báo `OPEN` nào của loại này cho agent này không" trên **mọi mẫu** |
| `alerts(opened_at)` | Danh sách cảnh báo sắp xếp theo thời điểm mở |

Cả bốn đều khai báo `CREATE INDEX IF NOT EXISTS` — idempotent.

---

## 4. Quan hệ và khóa ngoại

```
   agents 1 ----- * metric_raw     ON DELETE CASCADE
   agents 1 ----- * metric_5m      ON DELETE CASCADE
   agents 1 ----- * alerts         ON DELETE CASCADE
```

Xóa một agent làm **theo luôn metrics và alerts của nó**, nên database không tích
lũy các dòng không nối lại được với đâu. **Khóa ngoại bị TẮC mặc định trong
SQLite** và phải bật **từng connection** — `DatabaseManager.applyPragmas` chạy
`PRAGMA foreign_keys = ON` mỗi khi mở connection.

---

## 5. Cài đặt connection: 1 kết nối + khóa

### 5.1 Pragma

Áp dụng trên **connection duy nhất** khi mở (`DatabaseManager.applyPragmas`):

| Pragma | Giá trị | Vì sao |
|---|---|---|
| `foreign_keys` | `ON` | các cascade phía trên chỉ chạy khi bật |
| `journal_mode` | `WAL` | truy vấn lịch sử có thể **đọc trong lúc** telemetry đang ghi (Write-Ahead Log — nhật ký ghi trước) |
| `busy_timeout` | `5000` | **chờ** thay vì thất bại khi file bị khóa tạm |
| `synchronous` | `NORMAL` | an toàn dưới WAL và nhanh hơn `FULL` nhiều |

Hằng: `BUSY_TIMEOUT_MS = 5000`.

### 5.2 Đồng thời (concurrency)

- **Một connection duy nhất** được chia sẻ, và **mọi đơn vị công việc** chạy
  trong `DatabaseManager.execute`, vốn giữ **một khóa `synchronized`**.
- Nhờ đó **số người ghi (writer) đúng bằng một theo cấu trúc** — đúng hình dạng
  SQLite thích nhất.
- Trên `busy_timeout` của driver còn có một **retry có giới hạn**: `LOCK_RETRIES = 3`
  với độ trễ `LOCK_RETRY_DELAY_MS = 150` ms, kích hoạt khi lỗi chứa
  `"database is locked"` hoặc `"busy"` (vẫn có thể xảy ra nếu **process khác**
  giữ file).
- Mọi `PreparedStatement` và `ResultSet` mở trong **try-with-resources**, nên
  không rò rỉ kể cả khi truy vấn thất bại.
- `inTransaction(description, work)`: commit khi thành công, **rollback khi có bất
  kỳ lỗi nào** kể cả `RuntimeException`.

```
   Thread udp-worker-0..3 ─┐
   Thread monitor-scheduler │──> synchronized(lock) { Connection shared }
   Thread email-sender     │         |
   SwingWorker pool        ─┘         v
                                execute() / inTransaction()
```

### 5.3 Tách schema khi đọc file `.sql`

`readSchemaStatements()` đọc `/db/schema.sql` từ trong jar, **bỏ dòng comment**
(trước `--`) và dòng trống, nối các dòng cho tới khi gặp `;`, rồi chạy từng câu.
Nhờ vậy schema có thể viết nhiều dòng cho dễ đọc.

---

## 6. Retention (thời gian giữ dữ liệu)

Chi tiết mỗi giây là quá nhiều chi tiết sau một giờ, nên mẫu thô được gộp vào
các bucket bề rộng cố định, rồi bucket cũ hết hạn.

| | Production | Demo profile |
|---|---|---|
| Giữ dữ liệu thô (raw retention) | **60 phút** | **60 giây** |
| Bề rộng bucket | **5 phút** | **10 giây** |
| Giữ dữ liệu tổng hợp (aggregate retention) | **30 ngày** | **300 giây** |
| Chu kỳ chạy job | 300 giây | 15 giây |
| File DB | `data/monitoring.db` | `data/monitoring-demo.db` |

Nguồn cấu hình:

```properties
# config/monitor.properties  (production)
raw.retention.minutes=60
aggregation.bucket.minutes=5
aggregate.retention.days=30
aggregation.interval.seconds=300
```

```properties
# config/monitor-demo.properties  (demo)
raw.retention.seconds=60
aggregation.bucket.seconds=10
aggregate.retention.seconds=300
aggregation.interval.seconds=15
```

Dạng `seconds` được ưu tiên hơn dạng `minutes`/`days` (comment trong file demo).
Cả hai profile nạp qua **cùng một loader** và về cùng các field millisecond của
`MonitorConfig` — **không có nhánh code riêng cho demo ở bất kỳ đâu**
(`AggregationService` javadoc; `11-co-so-du-lieu-goc.md` mục 5).

---

## 7. Aggregation / rollup (tổng hợp)

### 7.1 Một lượt chạy — `AggregationService.runOnce(nowMs)`

```java
// 1) Căn chỉnh xuống biên bucket, để bucket đang lấp giữ nguyên mẫu thô
long rawCutoff = TimeUtil.bucketStart(nowMs - config.getRawRetentionMs(), bucketMs);

// 2) Gộp + xóa mẫu thô — HAI BƯỚC NÀY TRONG CÙNG MỘT TRANSACTION
int buckets = metricRepository.aggregateAndPrune(rawCutoff, bucketMs);

// 3) Xóa các bucket đã hết hạn
long aggregateCutoff = nowMs - config.getAggregateRetentionMs();
int pruned = metricRepository.deleteAggregatesBefore(aggregateCutoff);
```

### 7.2 SQL gộp (`MetricRepository.aggregateAndPrune`)

```sql
INSERT OR IGNORE INTO metric_5m (agent_id, bucket_start, sample_count,
    avg_cpu, max_cpu, avg_memory, max_memory, avg_connections,
    avg_network_in_bps, max_network_in_bps,
    avg_network_out_bps, max_network_out_bps)
SELECT agent_id,
       (timestamp / ?) * ? AS bucket_start,   -- bucketMs, bucketMs -> làm tròn xuống
       COUNT(*),
       AVG(cpu_usage),      MAX(cpu_usage),
       AVG(memory_usage),   MAX(memory_usage),
       AVG(connection_count),
       AVG(network_in_bps), MAX(network_in_bps),
       AVG(network_out_bps),MAX(network_out_bps)
FROM metric_raw WHERE timestamp < ?
GROUP BY agent_id, bucket_start;

DELETE FROM metric_raw WHERE timestamp < ?;
```

Điểm cần chú ý:

- **Tính average VÀ peak** — `AVG(...)` và `MAX(...)` đi đôi với nhau cho CPU,
  RAM, mạng vào, mạng ra. `connection_count` chỉ có `AVG` (không có `MAX` trong
  schema).
- **Căn chỉnh ranh giới bucket** (canh ranh): `rawCutoff = bucketStart(now - rawRetention, bucketMs)`
  — `bucketStart` làm tròn **xuống** về biên bucket. Kết quả là một **bucket
  đang lấp dở giữ nguyên các dòng thô** cho tới lần chạy sau, nên **không bucket
  nào bị tóm tắt một nửa**.
- **`WHERE timestamp < cutoff`** — chỉ các mẫu **cũ hơn** cutoff bị gộp; cutoff
  đã nằm trên biên bucket nên mọi mẫu cũ hơn đều thuộc về các bucket đã đóng
  hoàn toàn.
- **Ghi summary + xóa raw trong MỘT transaction** (`database.inTransaction`):
  chi tiết **không bao giờ bị mất mà chưa có summary tồn tại**. Nếu bước ghi
  summary thất bại thì rollback, dữ liệu thô còn nguyên.

### 7.3 Idempotent (chạy lại không tạo hại)

`INSERT OR IGNORE` kết hợp với `UNIQUE (agent_id, bucket_start)` làm toàn bộ lượt
chạy **an toàn khi lặp lại**:

- Chạy job lần nữa trên cùng khoảng → các dòng bị IGNORE, không nhân đôi.
- Agent restart giữa chừng → bucket đã ghi vẫn nguyên, mẫu thô mới vẫn được gộp
  đúng chỗ.

`deleteAggregatesBefore` cũng idempotent (DELETE trên khoảng rỗng là no-op).

### 7.4 Lịch chạy

`AggregationService.start(executor)` đặt `scheduleAtFixedRate(..., interval, interval, MILLISECONDS)`
với `config.getAggregationIntervalMs()` (300 s production / 15 s demo), chạy trên
thread `monitor-scheduler-*` (`MonitorApplication`, pool 2 thread dùng chung với
heartbeat sweep).

Mọi tick đều bọc trong `safeRun()` bắt `RuntimeException` và log — vì
`ScheduledExecutorService` **hủy im lặng** một tác vụ thoát ra bằng exception,
thì việc tổng hợp sẽ dừng mà không có lỗi nào hiện ra.

Sau mỗi lượt, `eventBus.fireAggregationCompleted(buckets, pruned)` đẩy số liệu
lên GUI.

---

## 8. Bảng nào trả lời câu hỏi lịch sử

`HistoryService` quyết định — panel không biết (`HistoryService` javadoc):

| Khoảng thời gian yêu cầu | Nguồn |
|---|---|
| Bắt đầu **bên trong** cửa sổ raw retention (`fromMs >= now - rawRetentionMs`) | `metric_raw`, từng mẫu một |
| **Cũ hơn** cửa sổ đó | `metric_5m`, trung bình theo bucket |
| `metric_5m` rỗng (DB mới, chưa chạy job lần nào) | **fallback** sang `metric_raw` để panel không trắng |

```java
long rawHorizon = System.currentTimeMillis() - config.getRawRetentionMs();
if (fromMs >= rawHorizon) { return fromSamples(findRaw(...)); }
List<HistoryPoint> points = fromAggregates(findAggregates(...));
if (points.isEmpty()) { points = fromSamples(findRaw(...)); }   // fallback
```

Các range được cung cấp trong `HistoryService.Range`:

| Range | Độ dài |
|---|---|
| `LAST_HOUR` — "Last 1 Hour" | 1 giờ |
| `LAST_24_HOURS` — "Last 24 Hours" | 24 giờ |
| `LAST_7_DAYS` — "Last 7 Days" | 7 ngày |
| `LAST_30_DAYS` — "Last 30 Days" | 30 ngày |

`usesAggregates(range)` trả `true` khi `range.durationMs > rawRetentionMs`.
`HistoryPanel.MAX_PLOTTED_POINTS = 2000` — range dài được **down-sampled** (giảm
số điểm) trước khi vẽ, vì vẽ nhiều điểm hơn số pixel màn hình vừa tốn thời gian
vừa không hiện thêm gì.

Bảng `HistoryPoint` còn có cột `aggregated` (`boolean`) để UI biết điểm đến từ
bucket hay từ mẫu thô.

---

## 9. Chế độ demo

| Khía cạnh | Production | Demo |
|---|---|---|
| File cấu hình | `config/monitor.properties` | `config/monitor-demo.properties` |
| `profile.name` | `production` | `demo` (hiện trong tiêu đề cửa sổ) |
| DB | `data/monitoring.db` | `data/monitoring-demo.db` — **file riêng, có chủ đích** |
| Raw retention | 60 phút | 60 giây |
| Bucket | 5 phút | 10 giây |
| Aggregate retention | 30 ngày | 300 giây |
| Job interval | 300 s | 15 s |
| Chạy | `scripts\run-monitor.bat` | `scripts\run-monitor-demo.bat` |

Hai profile **khác nhau chỉ ở các con số trên**. Không tồn tại nhánh
demo-specific trong code: `AggregationService` đọc từ `MonitorConfig`, và unit
test cũng drive cùng một method với timestamp bịa ra để kiểm tra bucket 5 phút
và bucket 10 giây qua **cùng một đường code**.

---

## 10. Nhìn vào bên trong database

File là một SQLite database bình thường; bất kỳ công cụ SQLite nào cũng mở được.
Ví dụ:

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

**Đóng monitoring client trước**, hoặc mở file ở chế độ read-only: client giữ
connection trong lúc đang chạy (một connection duy nhất, khóa `synchronized`).

---

## Tài liệu liên quan

- [09-kien-truc-goc.md](09-kien-truc-goc.md) — phân lớp, luồng dữ liệu, mô hình thread
- [11-co-so-du-lieu-goc.md](11-co-so-du-lieu-goc.md) — bảng, index, retention, chế độ demo
- [01-cau-truc-du-an.md](01-cau-truc-du-an.md)
- [02-mo-hinh-he-thong.md](02-mo-hinh-he-thong.md)
- [03-cach-thuc-hoat-dong.md](03-cach-thuc-hoat-dong.md)
- [08-mo-hinh-da-luong.md](08-mo-hinh-da-luong.md)
