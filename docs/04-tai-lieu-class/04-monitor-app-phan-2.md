# Tài liệu lớp — monitor-app (Phần 2: Lưu trữ repository & Dịch vụ service)

Phần này mô tả hai nhóm lớp nằm ở giữa kiến trúc của ứng dụng giám sát (monitor = Swing desktop app nhận telemetry — dữ liệu đo lường từ xa — qua UDP, lưu SQLite, phát cảnh báo và tổng hợp theo "bucket", tức khung thời gian cố định): nhóm `repository` chịu trách nhiệm đọc/ghi SQLite và nhóm `service` chứa toàn bộ nghiệp vụ (lưu trữ và phát sự kiện, kiểm tra sống còn, đánh giá ngưỡng, tổng hợp, truy vấn lịch sử, quản lý agent). Tài liệu monitor-app được chia làm ba phần: **phần 1** — `config` (cấu hình), `core` (lõi), `model` (mô hình), `network` (mạng), `notification` (thông báo); **phần 2** (file này) — `repository` và `service`; **phần 3** — `ui` (giao diện).

## 1. Bảng tổng quan class (phần 2)

| # | Class | Package | Vai trò | File nguồn |
|---|-------|---------|---------|------------|
| 1 | `DatabaseManager` | `monitor.repository` | Sở hữu kết nối SQLite duy nhất, áp pragma (cờ cấu hình của SQLite) và `db/schema.sql`, cung cấp `execute`/`inTransaction` kèm khoá và retry | `monitor-app/src/main/java/vn/edu/hutech/group10/monitoring/monitor/repository/DatabaseManager.java` |
| 2 | `DataAccessException` | `monitor.repository` | Exception runtime bọc `SQLException`, cách ly JDBC khỏi service/GUI | `.../repository/DataAccessException.java` |
| 3 | `AgentRepository` | `monitor.repository` | Đọc/ghi bảng `agents` | `.../repository/AgentRepository.java` |
| 4 | `AlertRepository` | `monitor.repository` | Đọc/ghi bảng `alerts` | `.../repository/AlertRepository.java` |
| 5 | `MetricRepository` | `monitor.repository` | Đọc/ghi bảng `metric_raw` và `metric_5m`, gộp + dọn dữ liệu cũ | `.../repository/MetricRepository.java` |
| 6 | `MonitorEventBus` | `monitor.service` | Hàng đợi phát sự kiện từ service sang listener | `.../service/MonitorEventBus.java` |
| 7 | `MonitorEventListener` | `monitor.service` | Interface 5 callback mặc định, nơi GUI lắng nghe sự kiện | `.../service/MonitorEventListener.java` |
| 8 | `AgentRegistry` | `monitor.service` | Bản đồ in-memory các agent, đồng bộ với bảng `agents`; kiêm `SecretProvider` (cung cấp bí mật chung để kiểm tra chữ ký gói) | `.../service/AgentRegistry.java` |
| 9 | `TelemetryService` | `monitor.service` | Đường đi duy nhất của mọi Datagram UDP: giải mã → kiểm tra → đăng ký → lưu DB → đánh giá cảnh báo → báo GUI | `.../service/TelemetryService.java` |
| 10 | `HeartbeatMonitor` | `monitor.service` | Quét định kỳ để chuyển ONLINE → OFFLINE và xử lý hồi phục qua `markAlive` | `.../service/HeartbeatMonitor.java` |
| 11 | `AlertService` | `monitor.service` | Máy trạng thái cảnh báo: mở/đóng theo ngưỡng, hysteresis (vùng chống dao động), cảnh báo offline/được cứu, sự kiện bảo mật | `.../service/AlertService.java` |
| 12 | `AggregationService` | `monitor.service` | Job định kỳ gộp mẫu cũ thành bucket và xoá dữ liệu quá hạn lưu trữ (retention) | `.../service/AggregationService.java` |
| 13 | `HistoryService` | `monitor.service` | Trả lời truy vấn lịch sử, tự chọn bảng `metric_raw` hay `metric_5m` | `.../service/HistoryService.java` |
| 14 | `AgentManagementService` | `monitor.service` | Toàn bộ thao tác màn hình quản lý agent (thêm/sửa/xoá/kênh điều khiển TCP/push cấu hình) tách khỏi GUI | `.../service/AgentManagementService.java` |

Nguồn phụ trợ đọc kèm: `monitor-app/src/main/resources/db/schema.sql` (4 bảng + 4 chỉ mục), `config/monitor.properties` (profile `production`), và các test trong `monitor-app/src/test/java/.../repository/` và `.../service/`.

## 2. Chi tiết từng class (nhóm theo package)

### 2.1 Nhóm repository

#### DatabaseManager

- **Định nghĩa**: `public class DatabaseManager implements AutoCloseable` — lớp sở hữu kết nối SQLite duy nhất và cấu trúc bảng (schema). Có interface hàm (functional interface) lồng bên trong: `SqlWork<T>` với `T apply(Connection) throws SQLException`.
- **Vai trò**: mở file DB, tạo thư mục cha nếu thiếu, áp pragma rồi chạy `db/schema.sql` ở **mỗi lần khởi động**; toàn bộ việc đọc/ghi đi qua một khoá (`synchronized` trên field `lock`) nên "số lượng writer đúng bằng 1 theo cấu trúc" — kiểu SQLite thích nhất; WAL (write-ahead logging, ghi trước vào log) cho phép đọc song song với ghi.
- **Thành phần chính**:
  - Hằng: `SCHEMA_RESOURCE = "/db/schema.sql"`, `BUSY_TIMEOUT_MS = 5000`, `LOCK_RETRIES = 3`, `LOCK_RETRY_DELAY_MS = 150L`.
  - `initialize()`: nếu `connection != null` thì bỏ qua; `AppPaths.ensureDirectory(parent)`; `DriverManager.getConnection("jdbc:sqlite:" + absPath)`; `setAutoCommit(true)`; `applyPragmas`; `applySchema`; lỗi → `DataAccessException("Could not open the database at ...")`.
  - `applyPragmas`: `PRAGMA foreign_keys = ON`, `PRAGMA journal_mode = WAL`, `PRAGMA busy_timeout = 5000`, `PRAGMA synchronous = NORMAL`.
  - `applySchema()` + `readSchemaStatements()`: đọc resource theo dòng, **bỏ dòng trống và dòng bắt đầu bằng `--`**, ghép đến khi gặp `;` rồi thực thi từng câu; thiếu resource → `DataAccessException("Schema resource /db/schema.sql is missing from the jar")`.
  - `execute(String description, SqlWork<T> work)`: giữ khoá; thử tối đa 3 lần; chỉ retry khi lỗi chứa `"database is locked"` hoặc `"busy"` (kiểm tra không phân biệt hoa thường); giữa các lần `Thread.sleep(150)`; hết lượt → `DataAccessException("Database operation failed: <description>")`.
  - `inTransaction(String description, SqlWork<T> work)`: tắt autoCommit, `commit()` khi thành công, `rollback()` khi `SQLException | RuntimeException` (lỗi rollback riêng được log `Level.WARNING`), luôn khôi phục autoCommit trong `finally`.
  - `requireConnection()` ném `DataAccessException("Database has not been initialised")`; `getDatabasePath()`, `isOpen()`, `close()` (idempotent — gọi nhiều lần vô hại).
- **Được gọi ở đâu**:
  - `core/MonitorApplication.start()` dòng 91–92: `new DatabaseManager(config.getDatabasePath())` + `initialize()`; `close()` dòng 217–219 khi tắt ứng dụng.
  - Tất cả 3 repository nhận nó qua constructor.
  - Test: `repository/DatabaseManagerTest` (10 test: `createsDatabaseAndDirectories`, `appliesTheWholeSchema`, `schemaApplicationIsIdempotent`, `enablesPragmas`, `foreignKeysCascade`, `transactionRollsBackOnFailure`, `refusesWorkBeforeInitialise`, `concurrentWritesAreSerialised`, `closeIsIdempotent`, `insertAndFind`) và mọi test dựng DB tạm trong `@TempDir`.
- **Ghi chú**: `MonitorApplication.start()` bắt `IOException | RuntimeException` và gọi `close()` để rollback, nên DB hỏng không để lại socket/file treo.

#### DataAccessException

- **Định nghĩa**: `public class DataAccessException extends RuntimeException`, `serialVersionUID = 1L`, hai constructor `(String message, Throwable cause)` và `(String message)`.
- **Vai trò**: "bọc" `SQLException` để các tầng phía trên repository — service, scheduler, GUI — **không bao giờ phải import JDBC**. Vì là runtime exception nên các lời gọi thuần (không cần try/catch) vẫn chạy được.
- **Thành phần chính**: chỉ 2 constructor, không có method riêng.
- **Được gọi ở đâu**: chỉ được **ném** từ `DatabaseManager` (5 chỗ: không mở được DB, thiếu resource schema, không đọc được schema, thao tác thất bại sau retry, chưa `initialize`); `MonitorApplication.start()` bắt `RuntimeException` để rollback.
- **Ghi chú**: repository khác (`AgentRepository`, `AlertRepository`, `MetricRepository`) không tự ném exception này — chúng để `DatabaseManager.execute`/`inTransaction` làm việc đó.

#### AgentRepository

- **Định nghĩa**: `public class AgentRepository` — đọc/ghi bảng `agents`.
- **Vai trò**: lớp duy nhất biết câu SQL bảng `agents`; mọi thao tác CRUD agent (từ `AgentManagementService`) và mọi cập nhật trạng thái sống (từ `AgentRegistry`) đều đi qua nó.
- **Thành phần chính**:
  - Hằng `SELECT_COLUMNS` = 14 cột: `id, agent_id, name, host, tcp_port, shared_secret, cpu_threshold, memory_threshold, network_in_threshold, network_out_threshold, status, last_seen, created_at, updated_at`.
  - `findAll()` → `SELECT ... FROM agents ORDER BY agent_id` (Javadoc nói "ordered by id" nhưng câu SQL thật sắp theo `agent_id`).
  - `findByAgentId(String)` → `... WHERE agent_id = ?`, trả `Optional<AgentRecord>`.
  - `insert(AgentRecord)` → `INSERT INTO agents (13 cột) VALUES (?, ..., ?)` với `Statement.RETURN_GENERATED_KEYS`, điền `record.setId(...)`; `created_at = updated_at = System.currentTimeMillis()`.
  - `update(AgentRecord)` → `UPDATE agents SET name=?, host=?, tcp_port=?, shared_secret=?, cpu_threshold=?, memory_threshold=?, network_in_threshold=?, network_out_threshold=?, updated_at=? WHERE agent_id=?` — **không** sửa `status`/`last_seen`/`created_at`.
  - `updateStatus(String agentId, AgentStatus status, long lastSeen)` → `UPDATE agents SET status=?, last_seen=?, updated_at=? WHERE agent_id=?` (Javadoc: "called by the heartbeat monitor").
  - `delete(String agentId)` → `DELETE FROM agents WHERE agent_id = ?`, trả `boolean` (`executeUpdate() > 0`); metric và alert theo về nhờ `ON DELETE CASCADE` trong schema.
  - `exists(String)` = `findByAgentId(...).isPresent()`.
  - Ràng buộc an toàn chuỗi trạng thái: `statusName(null) → "UNKNOWN"`, `parseStatus` trả `UNKNOWN` khi giá trị không khớp enum (`AgentStatus.valueOf` ném `IllegalArgumentException` thì bắt).
- **Được gọi ở đâu**:
  - `core/MonitorApplication` dòng 94 (tạo) — không expose getter nên chỉ các service được truyền nó.
  - `service/AgentRegistry`: `load()` gọi `findAll()`; `setStatus()` và `persistLastSeen()` gọi `updateStatus(...)`.
  - `service/AgentManagementService`: `exists`, `insert`, `update`, `delete`.
  - Test: `HeartbeatMonitorTest.agentRepository.insert/findByAgentId`, `UdpIntegrationTest`, `DatabaseManagerTest.insertAndFind`.
- **Ghi chú**: thời gian luôn lấy `System.currentTimeMillis()` bên trong repository, không nhận `now` từ ngoài.

#### AlertRepository

- **Định nghĩa**: `public class AlertRepository` — đọc/ghi bảng `alerts`.
- **Vai trò**: lưu sự kiện cảnh báo (AlertEvent) và trả về danh sách cho GUI/`AlertService`.
- **Thành phần chính**:
  - Hằng `COLUMNS` = 11 cột: `id, agent_id, alert_type, severity, message, opened_at, resolved_at, status, current_value, threshold, unit`.
  - `insert(AlertEvent)` → `INSERT INTO alerts (10 cột) VALUES (?, ...)` với `RETURN_GENERATED_KEYS`; `resolved_at <= 0` thì `setNull(Types.INTEGER)`, ngược lại `setLong`; điền `event.setId(...)`.
  - `resolve(long alertId, long resolvedAt)` → `UPDATE alerts SET status = 'RESOLVED', resolved_at = ? WHERE id = ?` (chữ ký enum lấy từ `AlertState.RESOLVED.name()`).
  - `findOpen(agentId, alertType)` → `... WHERE agent_id = ? AND alert_type = ? AND status = 'OPEN' ORDER BY opened_at DESC LIMIT 1`.
  - `findAllOpen()` → `... WHERE status = 'OPEN' ORDER BY opened_at DESC`.
  - `findRecent(int limit)` → `... ORDER BY opened_at DESC LIMIT ?` với `limit = Math.max(1, limit)`.
  - `findByAgent(String agentId, int limit)` → `... WHERE agent_id = ? ORDER BY opened_at DESC LIMIT ?`.
  - `countOpen()` → `SELECT COUNT(*) FROM alerts WHERE status = 'OPEN'`; `count()` → `SELECT COUNT(*) FROM alerts`.
  - `map(ResultSet)` dùng `AlertType.valueOf` / `AlertSeverity.valueOf` / `AlertState.valueOf` **không có fallback** — chuỗi lạ trong DB sẽ ném `IllegalArgumentException` (khác `AgentRepository`).
- **Được gọi ở đâu**:
  - `service/AlertService`: `insert(...)` (mở alert, hồi phục, sự kiện bảo mật), `resolve(...)`, `findAllOpen()` trong `load()`.
  - `ui/AlertPanel.reload()` dòng 129–130 qua `application.getAlertRepository()`: `findAllOpen()` nếu chọn "open only", ngược lại `findRecent(MAX_ROWS)` với `MAX_ROWS = 500`.
  - `core/MonitorApplication` dòng 96 tạo, dòng 255 `getAlertRepository()`.
  - Test: `AlertServiceTest`, `HeartbeatMonitorTest`, `UdpIntegrationTest` dùng `count()` và `findByAgent(...)` để kiểm chứng.
- **Ghi chú**: `findOpen(...)` và `countOpen()` **không tìm thấy nơi gọi** trong mã chính hay test (chỉ có định nghĩa); `count()` và `findByAgent(...)` chỉ được test dùng; `insert`/`resolve`/`findAllOpen`/`findRecent` là các method đang chạy thật trong ứng dụng.

#### MetricRepository

- **Định nghĩa**: `public class MetricRepository` — đọc/ghi bảng `metric_raw` (mẫu thô, mỗi packet 1 dòng) và `metric_5m` (đã tổng hợp theo bucket).
- **Vai trò**: tầng duy nhất biết SQL metric; vừa là nơi `TelemetryService` ghi mẫu mới, vừa là nguồn của `HistoryService`, vừa là công cụ của `AggregationService`.
- **Thành phần chính**:
  - `RAW_COLUMNS` (8): `agent_id, timestamp, cpu_usage, memory_usage, connection_count, uptime_seconds, network_in_bps, network_out_bps`.
  - `AGG_COLUMNS` (12): `agent_id, bucket_start, sample_count, avg_cpu, max_cpu, avg_memory, max_memory, avg_connections, avg_network_in_bps, max_network_in_bps, avg_network_out_bps, max_network_out_bps`.
  - `insertRaw(MetricSample)` → `INSERT INTO metric_raw (...) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`.
  - `insertRawBatch(List<MetricSample>)` → dùng `database.inTransaction` + `addBatch()`/`executeBatch()`; rỗng/null thì thoát ngay (Javadoc: "used by the tests and by imports").
  - `findRaw(agentId, fromMs, toMs)` → `... WHERE agent_id = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp`.
  - `findAggregates(agentId, fromMs, toMs)` → `... FROM metric_5m WHERE agent_id = ? AND bucket_start BETWEEN ? AND ? ORDER BY bucket_start`.
  - `aggregateAndPrune(long cutoffMs, long bucketMs)` — **cốt lõi**, trong **một transaction duy nhất**:
    1. `bucketMs <= 0` → `IllegalArgumentException("bucketMs must be positive")`;
    2. `INSERT OR IGNORE INTO metric_5m (AGG_COLUMNS) SELECT agent_id, (timestamp / ?) * ? AS bucket_start, COUNT(*), AVG(cpu_usage), MAX(cpu_usage), AVG(memory_usage), MAX(memory_usage), AVG(connection_count), AVG(network_in_bps), MAX(network_in_bps), AVG(network_out_bps), MAX(network_out_bps) FROM metric_raw WHERE timestamp < ? GROUP BY agent_id, bucket_start` (tham số thứ 1 và 2 đều là `bucketMs`, thứ 3 là `cutoffMs`);
    3. `DELETE FROM metric_raw WHERE timestamp < ?` (cùng `cutoffMs`);
    4. trả về **số bucket đã ghi**. `INSERT OR IGNORE` + `UNIQUE (agent_id, bucket_start)` trong schema làm cho một bucket không thể bị ghi hai lần kể cả job chạy lại trên cùng cửa sổ.
  - `deleteAggregatesBefore(long cutoffMs)` → `DELETE FROM metric_5m WHERE bucket_start < ?`, trả số dòng đã xoá.
  - `countRaw()`, `countRaw(agentId)`, `countAggregates()`, `latestRawTimestamp(agentId)` → `SELECT MAX(timestamp) ... WHERE agent_id = ?`, trả `0` khi chưa có mẫu nào.
- **Được gọi ở đâu**:
  - `service/TelemetryService.handleTelemetry` → `insertRaw(sample)` (bọc `try/catch RuntimeException`).
  - `service/AggregationService.runOnce` → `aggregateAndPrune(...)` và `deleteAggregatesBefore(...)`.
  - `service/HistoryService.load` → `findRaw(...)` / `findAggregates(...)`.
  - `core/MonitorApplication` dòng 95 tạo.
  - Test: `MetricRepositoryTest` (9 test: `storesAndReadsBackASample`, `filtersByAgentAndRange`, `aggregatesCorrectly`, `separatesBucketsAndAgents`, `prunesOnlyWhatWasAggregated`, `aggregationIsIdempotent`, `prunesExpiredAggregates`, `aggregationWithNoDataDoesNothing`, `reportsLatestTimestamp`), `AggregationServiceTest`, `DatabaseManagerTest`, `UdpIntegrationTest`.
- **Ghi chú**: `countRaw(String)` và `latestRawTimestamp` hiện **chỉ được test sử dụng**.

**Bảng và chỉ mục thực tế từ `db/schema.sql`** (mọi câu `CREATE` đều `IF NOT EXISTS`, chạy lại mỗi lần mở app):

| Bảng | Cột chính | Ràng buộc |
|------|-----------|-----------|
| `agents` | `id` (PK AUTOINCREMENT), `agent_id` (UNIQUE), `name`, `host`, `tcp_port`, `shared_secret`, `cpu_threshold` (DEFAULT 85.0), `memory_threshold` (DEFAULT 85.0), `network_in_threshold` (DEFAULT 50.0), `network_out_threshold` (DEFAULT 50.0), `status` (DEFAULT 'UNKNOWN'), `last_seen` (DEFAULT 0), `created_at`, `updated_at` | `agent_id TEXT NOT NULL UNIQUE` |
| `metric_raw` | `id`, `agent_id`, `timestamp`, `cpu_usage`, `memory_usage`, `connection_count`, `uptime_seconds`, `network_in_bps`, `network_out_bps` | FK `agent_id → agents(agent_id) ON DELETE CASCADE` |
| `metric_5m` | `id`, `agent_id`, `bucket_start`, `sample_count`, `avg_cpu`, `max_cpu`, `avg_memory`, `max_memory`, `avg_connections`, `avg_network_in_bps`, `max_network_in_bps`, `avg_network_out_bps`, `max_network_out_bps` | `UNIQUE (agent_id, bucket_start)`; FK cascade |
| `alerts` | `id`, `agent_id`, `alert_type`, `severity`, `message`, `opened_at`, `resolved_at`, `status`, `current_value` (DEFAULT 0), `threshold` (DEFAULT 0), `unit` | FK cascade |

Chỉ mục: `idx_metric_raw_agent_time (agent_id, timestamp)`, `idx_metric_5m_agent_bucket (agent_id, bucket_start)`, `idx_alerts_agent_status (agent_id, status)` (đường nóng của máy cảnh báo), `idx_alerts_opened_at (opened_at)`.

### 2.2 Nhóm service

#### MonitorEventBus

- **Định nghĩa**: `public class MonitorEventBus` — hàng đợi phát sự kiện (event bus) của tầng service.
- **Vai trò**: tách service khỏi GUI: service chỉ "bắn" sự kiện, không biết ai nghe. Danh sách listener dùng `CopyOnWriteArrayList` vì listener đăng ký một lần lúc khởi động rồi chỉ bị đọc ở mỗi packet.
- **Thành phần chính**:
  - `addListener(MonitorEventListener)` (bỏ qua `null`), `removeListener(...)`.
  - `fireAgentUpdated(AgentSnapshot)`, `fireAgentListChanged()`, `fireAlert(AlertEvent)`, `firePacketRejected(String agentId, String reason)`, `fireAggregationCompleted(int bucketsWritten, int aggregatesPruned)`.
  - `dispatch(Consumer<MonitorEventListener>)`: duyệt listener, bắt `RuntimeException` từng cái và ghi `Level.WARNING` — **một lỗi trong GUI không được làm hỏng xử lý telemetry**.
  - `getListenerCount()`.
- **Được gọi ở đâu**:
  - `core/MonitorApplication` khai báo `private final MonitorEventBus eventBus = new MonitorEventBus()` (dòng 57), truyền vào `AgentRegistry`, `AlertService`, `TelemetryService`, `AggregationService`; getter `getEventBus()` dòng 235.
  - `ui/MainFrame` dòng 83: `application.getEventBus().addListener(new UiEventListener())`.
  - Người bắn sự kiện: `AgentRegistry` (updated / list changed), `AlertService` (`fireAlert`), `TelemetryService` (`firePacketRejected`), `AggregationService` (`fireAggregationCompleted`).
- **Ghi chú**: `firePacketRejected` và `fireAggregationCompleted` hiện **chưa có listener nào override** callback tương ứng (MainFrame chỉ cài 3 callback đầu).

#### MonitorEventListener

- **Định nghĩa**: `public interface MonitorEventListener` — cách tầng service báo cho GUI biết có sự việc xảy ra.
- **Vai trò**: hợp đồng 5 callback, **tất cả đều có `default`** nên listener chỉ cần cài method mình quan tâm.
- **Thành phần chính** (đúng thứ tự trong code):
  - `onAgentUpdated(AgentSnapshot snapshot)` — mẫu metric mới hoặc đổi trạng thái của 1 agent.
  - `onAgentListChanged()` — thêm/sửa/xoá agent, nên nạp lại toàn bộ danh sách.
  - `onAlert(AlertEvent event)` — cảnh báo vừa mở hoặc vừa đóng.
  - `onPacketRejected(String agentId, String reason)` — gói bị từ chối; `reason` đã an toàn để hiển thị.
  - `onAggregationCompleted(int bucketsWritten, int aggregatesPruned)` — một lượt tổng hợp kết thúc.
- **Được gọi ở đâu**: chỉ duy nhất `ui/MainFrame.UiEventListener` (dòng 148) cài interface này và **chỉ override 3 method đầu**; mỗi callback đều tự `SwingUtilities.invokeLater` vì chúng đến từ **worker thread** (thread nhận UDP, timer heartbeat, timer aggregation), không phải Event Dispatch Thread (EDT — luồng vẽ của Swing).

#### AgentRegistry

- **Định nghĩa**: `public class AgentRegistry implements SecretProvider` — bản đồ in-memory (`ConcurrentHashMap<String, AgentRuntimeState>`) mọi agent đã đăng ký, luôn "giữ nhịp" với bảng `agents`.
- **Vai trò**: vừa là nguồn trạng thái sống cho GUI, vừa là `SecretProvider` mà bộ kiểm tra gói (`PacketValidator`) tra cứu — **agent không có trong registry thì không có bí mật chung, gói bị bỏ với lý do UNKNOWN_AGENT trước khi tính chữ ký**.
- **Thành phần chính**:
  - `load()`: gọi `repository.findAll()`, **ép `record.setStatus(AgentStatus.UNKNOWN)`** rồi bọc trong `AgentRuntimeState`; log "Loaded N agent(s) from the database".
  - `secretFor(agentId)` (override): trả `sharedSecret` hoặc `null`.
  - Truy vấn: `contains`, `find`, `snapshot(agentId)`, `snapshots()` (sắp theo `AgentSnapshot::getAgentId`), `states()` (bộ bất biến), `size()`.
  - `register(AgentRecord)` → `agents.put` + `eventBus.fireAgentListChanged()`, trả `AgentRuntimeState`.
  - `updateRecord(AgentRecord)`: nếu chưa có thì `register`, nếu có thì `state.setRecord(record)` (**giữ metric đang sống**) + `fireAgentListChanged`.
  - `unregister(agentId)` → `remove` + `fireAgentListChanged` (chỉ khi thật sự xoá được).
  - `applySample(MetricSample)`: `state.applySample(sample)` (chép số metric), `setLastSeen(sample.getTimestamp())`, `countAccepted()`, `fireAgentUpdated(snapshot)`.
  - `markSeen(agentId, timestamp)`: chỉ cập nhật nếu `timestamp > lastSeen`.
  - `countRejected(agentId)`.
  - `setStatus(agentId, status)`: bỏ qua nếu agent không có hoặc trạng thái không đổi; set trong memory (cả `state` lẫn `record`) + `repository.updateStatus(...)` + `fireAgentUpdated`.
  - `persistLastSeen()`: ghi `updateStatus` cho **mọi** agent — dùng lúc tắt ứng dụng.
  - `setTcpConnected(agentId, boolean)` + `fireAgentUpdated`; `countByStatus(AgentStatus)` cho bảng tổng kết dashboard.
- **Được gọi ở đâu**:
  - `core/MonitorApplication`: tạo dòng 101 + `load()` dòng 102; truyền làm tham số cho `PacketValidator` (dòng 110 — chính là `SecretProvider`), `TelemetryService`, `HeartbeatMonitor`, `AgentManagementService`; `persistLastSeen()` trong `close()` (dòng 208); getter `getRegistry()` dòng 239.
  - `ui/DashboardPanel`: `snapshots()` (194), `size()` (220), `countByStatus` ONLINE/WARNING/OFFLINE (222–226).
  - `ui/ServerDetailPanel`: `snapshots()` (137) để dựng combo, `snapshot(agentId)` (181).
  - `ui/HistoryPanel`: `snapshots()` (98); `ui/MainFrame`: `size()` (118) ở thanh trạng thái.
  - Test: gián tiếp qua `AlertServiceTest`, `HeartbeatMonitorTest`, `UdpIntegrationTest`.
- **Ghi chú**: Javadoc của `applySample` nói "promotes it to ONLINE", nhưng **code không đổi trạng thái** — việc chuyển sang `ONLINE` do `HeartbeatMonitor.markAlive` đảm nhiệm (xem mục `HeartbeatMonitor.markAlive`). Javadoc của `AgentRepository.findAll` nói "ordered by id" trong khi câu SQL thật là `ORDER BY agent_id`.

#### TelemetryService

- **Định nghĩa**: `public class TelemetryService` — đường đi duy nhất mọi Datagram UDP (gói dữ liệu mạng không cần thiết lập kết nối) đi qua: `bytes -> JSON -> validate -> registry -> database -> alert engine -> GUI`.
- **Vai trò**: không có gì phía sau bộ kiểm tra chạy được với gói bị từ chối — gói giả mạo khai 100% CPU không chạm tới bảng, biểu đồ, `metric_raw` hay máy cảnh báo; nó chỉ được đếm, ghi log và bỏ.
- **Thành phần chính**:
  - Phụ thuộc: `JsonMessageCodec`, `PacketValidator`, `AgentRegistry`, `MetricRepository`, `AlertService`, `HeartbeatMonitor`, `MonitorEventBus`.
  - Hai bộ đếm `LongAdder` `acceptedCount`/`rejectedCount` (vì nhiều thread UDP cùng tăng; counter thường sẽ mất cập nhật).
  - `handleDatagram(byte[] payload, int offset, int length, String sourceDescription)` → `boolean`:
    1. đổi sang `String` UTF-8 (thất bại → `reject`);
    2. `codec.peekType(json)`; `null` → "unrecognised datagram";
    3. `switch`: `TELEMETRY` → `handleTelemetry(codec.fromJson(...))`, `HEARTBEAT` → `handleHeartbeat(...)`, loại khác → "message type ... is not accepted over UDP";
    4. `ProtocolException` → "malformed ..."; `RuntimeException` → log `WARNING` + `rejectedCount++` (một packet hỏng không được dừng vòng lặp nhận).
  - `handleTelemetry(packet, now)`:
    - `validator.validate(packet, now)`; không đạt → `reject(...)` và trả `false`;
    - `acceptedCount++`, `MetricSample.from(packet)`;
    - **`heartbeatMonitor.markAlive(...)` trước** — "Liveness first, so a recovery alert is raised before any threshold alert" (cảnh báo hồi phục luôn ra trước cảnh báo ngưỡng);
    - `registry.applySample(sample)`;
    - `metricRepository.insertRaw(sample)` trong `try/catch RuntimeException` — "A database hiccup must not cost the live view or the alerting";
    - lấy `AgentRuntimeState` rồi `alertService.evaluate(state.getRecord(), sample)`.
  - `handleHeartbeat(packet, now)`: validate → `acceptedCount++` → `markAlive`.
  - `reject(agentId, detail, reason, now)`: `rejectedCount++`; nếu có `agentId` thì `registry.countRejected(...)`; `securityTypeOf(reason)` chỉ đổi `INVALID_HMAC → AlertType.SECURITY_INVALID_HMAC` và `REPLAY → AlertType.SECURITY_REPLAY_PACKET` (các lý do khác không phải sự kiện bảo mật) → `alertService.recordSecurityEvent(...)`; ngược lại `LOG.warning("Packet rejected: ...")`; luôn `eventBus.firePacketRejected(agentId, detail)`.
  - `getAcceptedCount()`, `getRejectedCount()`.
- **Được gọi ở đâu**:
  - `network/UdpTelemetryReceiver` (constructor dòng 61, gọi `telemetryService.handleDatagram(payload, 0, payload.length, source)` dòng 129) trên **4 worker thread** (mỗi worker 1 thread + hàng đợi `ArrayBlockingQueue` sức chứa 2000, `CallerRunsPolicy`; packet luôn về cùng 1 worker theo địa chỉ nguồn để giữ thứ tự).
  - `core/MonitorApplication` dòng 113 tạo, dòng 263 getter.
  - `ui/MainFrame.updateStatusBar()` dòng 115/124–125 hiển thị "packets: N received, N accepted, N rejected".
  - Test: `network/UdpIntegrationTest` (10 test đầu-cuối gồm `rejectsForgedPacket`, `rejectsReplay`, `rejectsUnknownAgent`, `rejectsStaleTimestamp`, `survivesGarbage`, `raisesAlertFromRealTraffic`, `handlesABurst`).

#### HeartbeatMonitor

- **Định nghĩa**: `public class HeartbeatMonitor` — quyết định mỗi agent còn sống không và **sở hữu cả hai chuyển tiếp trạng thái** (OFFLINE và hồi phục).
- **Vai trò**: quét định kỳ, so sánh đồng hồ máy với `lastSeen` của từng agent; quá timeout thì `OFFLINE` và mở **đúng một** sự kiện `AGENT_OFFLINE` (kiểm tra "đã mở rồi" nằm ở `AlertService` mới là thứ chặn alert trùng lặp, vì quét chạy mỗi 2 giây).
- **Thành phần chính**:
  - Trạng thái: `volatile ScheduledFuture<?> task`; tham số `timeoutMs`, `checkIntervalMs`, `clock` (`LongSupplier` — **đồng hồ được tiêm vào** để test đẩy thời gian thay vì ngủ 15 giây).
  - Constructor 2 bản: bản chuẩn dùng `System::currentTimeMillis`, bản có `clock` cho test.
  - `start(ScheduledExecutorService)`: `scheduleAtFixedRate(this::safeCheck, checkIntervalMs, checkIntervalMs, MILLISECONDS)`; log "Heartbeat monitor started: timeout ... ms, checked every ... ms".
  - `safeCheck()`: bắt `RuntimeException` — "Never let this escape: the executor would cancel the job".
  - `markAlive(String agentId, long packetTimestamp, long nowMs)`:
    - `registry.markSeen(agentId, Math.max(packetTimestamp, nowMs))` — ghi theo **đồng hồ máy nhận** để agent có đồng hồ hơi chậm không bị coi là cũ ngay khi packet đến;
    - nếu đã `ONLINE` thì thoát;
    - `registry.setStatus(agentId, ONLINE)` (ghi cả DB và báo GUI);
    - nếu **trước đó là `OFFLINE`** → `LOG.info("Agent ... recovered")` + `alertService.raiseRecovered(state.getRecord(), nowMs)`.
  - `check(long nowMs)` → số agent vừa OFFLINE: chỉ xét trạng thái `ONLINE` hoặc `WARNING`; bỏ qua `lastSeen <= 0`; nếu `nowMs - lastSeen > timeoutMs` → `setStatus(OFFLINE)` + `alertService.raiseOffline(record, lastSeen, timeoutMs, nowMs)` + log `WARNING`.
  - `getTimeoutMs()`, `stop()` (`task.cancel(false)`).
- **Được gọi ở đâu**:
  - `core/MonitorApplication`: tạo dòng 107 với `config.getHeartbeatTimeoutMs()` và `config.getHeartbeatCheckIntervalMs()`; `heartbeatMonitor.start(scheduler)` dòng 133 (scheduler 2 thread daemon "monitor-scheduler-N"); `stop()` dòng 182.
  - `service/TelemetryService`: `markAlive` ở cả nhánh telemetry và heartbeat.
  - Test: `service/HeartbeatMonitorTest` (12 test, gồm `staleLastSeenFromTheDatabaseDoesNotRaiseAnOutage`, `neverSeenAgentIsNotReportedOffline`, `statusIsPersisted`).
- **Ghi chú / giá trị cấu hình**: profile `production` trong `config/monitor.properties`: `heartbeat.timeout.ms=15000`, `heartbeat.check.interval.ms=2000`. Agent ở trạng thái `UNKNOWN` **không bao giờ** bị khai báo chết — nếu không, restart ứng dụng sẽ gửi mail outage cho mọi agent chưa từng báo. Không có code nào trong `monitor-app` **gán** trạng thái `WARNING` (chỉ `DashboardPanel` đếm nó và `HeartbeatMonitor` coi nó là "đang sống").

#### AlertService

- **Định nghĩa**: `public class AlertService` — biến mẫu metric và thay đổi trạng thái sống thành sự kiện cảnh báo (AlertEvent).
- **Vai trò**: máy trạng thái; mỗi cặp `(agent, alert type)` hoặc đang đóng, hoặc có **đúng một** sự kiện `OPEN`:
  - giá trị vượt ngưỡng mà chưa có gì mở → mở sự kiện, ghi DB, đẩy GUI, đưa cho notifier (gửi mail);
  - vẫn trên ngưỡng → không làm gì (chính điều này chặn "một alert mỗi mẫu" và "một email mỗi giây");
  - giá trị tụt xuống dưới **ngưỡng trừ vùng hysteresis** → đóng sự kiện;
  - leo lại sau đó → mở sự kiện hoàn toàn mới.
- **Thành phần chính**:
  - Hằng: `PERCENTAGE_HYSTERESIS = 2.0d` (2 điểm phần trăm cho CPU/memory), `RATE_HYSTERESIS_FRACTION = 0.05d` (5% ngưỡng cho tốc độ mạng), `SECURITY_EVENT_COOLDOWN_MS = 60_000L`.
  - `Map<String, AlertEvent> openAlerts` (`ConcurrentHashMap`, key `agentId|ALERT_TYPE`), `Map<String, Long> lastSecurityEventAt`.
  - `load()`: xoá cache rồi nạp `repository.findAllOpen()` vào map; log "Loaded N open alert(s) from the database". Gọi 1 lần lúc khởi động.
  - `evaluate(AgentRecord agent, MetricSample sample)` — kiểm tra 4 ngưỡng, tất cả `AlertSeverity.WARNING`:
    | AlertType | Giá trị | Ngưỡng | Đơn vị | Hysteresis |
    |-----------|---------|--------|--------|------------|
    | `CPU_HIGH` | `sample.getCpuUsage()` | `agent.getCpuThreshold()` | `%` | 2.0 điểm |
    | `MEMORY_HIGH` | `sample.getMemoryUsage()` | `agent.getMemoryThreshold()` | `%` | 2.0 điểm |
    | `NETWORK_IN_HIGH` | `ByteRateFormatter.toMbps(network_in)` = `bytes * 8 / 1_000_000` | `agent.getNetworkInThresholdMbps()` | `Mbps` | 5% ngưỡng |
    | `NETWORK_OUT_HIGH` | `ByteRateFormatter.toMbps(network_out)` | `agent.getNetworkOutThresholdMbps()` | `Mbps` | 5% ngưỡng |
  - `evaluateThreshold(...)`: `threshold <= 0` → **bỏ qua** (ngưỡng 0 = tắt kiểm tra); `value > threshold` và chưa có alert mở → mở với message `"%s is %.1f %s, above the threshold of %.1f %s"`; ngược lại nếu `value <= threshold - max(0, hysteresis)` và đang có alert mở → đóng với message `"%s recovered to %.1f %s"`.
  - `raiseOffline(AgentRecord, long lastSeen, long timeoutMs, long now)`: nếu key `AGENT_OFFLINE` đã có mở thì **thoát ngay**; tạo sự kiện `AGENT_OFFLINE` mức `CRITICAL`, message `"No valid packet received since <TimeUtil.formatTimestamp(lastSeen)>"`; đặt `currentValue = lastSeen`, `threshold = timeoutMs`, `unit = "ms"` (vì thân email đọc hai giá trị này thành "last seen" và "timeout") rồi `store(...)`.
  - `raiseRecovered(AgentRecord, long now)`: nếu có `AGENT_OFFLINE` đang mở → đóng với lý do `"Agent is reachable again"`; tạo `AGENT_RECOVERED` mức `INFO`, message `"Agent is online again"`, **set thẳng `state = RESOLVED`, `resolvedAt = now`** (hồi phục là một "khoảnh khắc", không phải một "điều kiện") → `repository.insert` → `eventBus.fireAlert` → `notifier.sendAlert` (vẫn gửi mail).
  - `recordSecurityEvent(String agentId, AlertType type, String detail, long now)`: rate-limit 60 giây/cặp agent+type; `severity = WARNING`, `state = RESOLVED`, `openedAt = resolvedAt = now`, `unit = ""`; `repository.insert` trong `try/catch RuntimeException` (agent lạ không có hàng để gắn → chỉ log `"Security event for ... could not be stored"`); luôn `LOG.warning("SECURITY <type> from <agentId>: <detail>")` và `eventBus.fireAlert`; **không gọi notifier** — tràn packet giả mạo không được biến thành tràn mail.
  - `openAlert(...)` → `store(key, event)`: `repository.insert` → `openAlerts.put` → `LOG.warning("Alert OPEN ...")` → `eventBus.fireAlert` → `notifier.sendAlert`.
  - `resolveAlert(key, event, now, reason)`: `openAlerts.remove` → set `RESOLVED` + `resolvedAt` → `repository.resolve(event.getId(), now)` → `LOG.info("Alert RESOLVED ...")` → `eventBus.fireAlert` — **không** gửi notifier.
  - `newEvent(...)`: dùng constructor `AlertEvent(agentId, name, type, severity, message, now)`, `unit = ""`.
  - `hasOpenAlert(agentId, type)`, `getOpenAlertCount()`, `forgetAgent(agentId)` (xoá hết cache có prefix `agentId|` ở **cả hai** map).
- **Được gọi ở đâu**:
  - `service/TelemetryService`: `evaluate(...)` sau mỗi mẫu được chấp nhận; `recordSecurityEvent(...)` khi lỗi chữ ký/phát lại.
  - `service/HeartbeatMonitor`: `raiseOffline(...)` trong quét, `raiseRecovered(...)` trong `markAlive`.
  - `service/AgentManagementService.deleteAgent` → `forgetAgent(...)`.
  - `core/MonitorApplication`: `load()` dòng 105; **tạo lại toàn bộ** `AlertService` trong `reloadEmailConfiguration()` (dòng 169) để đổi notifier (bật/tắt email) mà không cần restart, rồi `load()` lại.
  - `ui/AlertPanel` dòng 139: `getOpenAlertCount()` cho dòng tóm tắt.
  - Test: `AlertServiceTest` (15 test: `opensCpuAlert`, `suppressesDuplicateAlerts`, `resolvesOnRecovery`, `hysteresisPreventsFlapping`, `reopensAfterRecovery`, `metricsAreIndependent`, `networkThresholdUsesMbps`, `offlineAlertIsRaisedOnce`, `recoveryResolvesOffline`, `offlineCanBeRaisedAgainAfterRecovery`, `securityEventsAreRecordedButNotEmailed`, `reloadsOpenAlertsOnStartup`, `zeroThresholdDisablesTheCheck`, `forgetAgentClearsCache`), `HeartbeatMonitorTest`, `UdpIntegrationTest`.
- **Ghi chú**: cache `openAlerts` được nạp lại từ DB lúc khởi động nên cảnh báo mở còn nguyên sau khi restart.

#### AggregationService

- **Định nghĩa**: `public class AggregationService` — job định kỳ nhằm chống DB phình vô hạn: gộp mẫu thô cũ vào bucket (khung thời gian cố định) rồi xoá bucket quá hạn.
- **Vai trò**: mẫu thô đến mỗi giây mỗi agent — quá chi tiết sau 1 giờ; sau cửa sổ giữ thô (raw retention) mỗi bucket được thay bằng **một dòng** mang `sample_count`, trung bình và **đỉnh (max)** của mọi chỉ số — vì chỉ có trung bình sẽ che mất cú spike (đỉnh) gây ra sự cố. Cutoff được **canh lề xuống biên bucket** nên bucket đang dở dang giữ nguyên mẫu thô đến lần chạy sau; ghi tóm tắt và xoá mẫu thô nằm trong **một transaction** nên chi tiết không bao giờ bị mất khi tóm tắt chưa tồn tại.
- **Thành phần chính**:
  - Trạng thái `volatile`: `task`, `lastRunAt`, `totalBucketsWritten`; phụ thuộc `MetricRepository`, `MonitorConfig`, `MonitorEventBus`, `LongSupplier clock` (2 constructor: bản chuẩn dùng `System::currentTimeMillis`).
  - `start(ScheduledExecutorService executor)`: `scheduleAtFixedRate(this::safeRun, interval, interval, MILLISECONDS)` với `interval = config.getAggregationIntervalMs()`; log đủ 4 số: interval, raw kept, bucket, aggregates kept.
  - `safeRun()`: bắt `RuntimeException` để executor không huỷ job.
  - `runOnce(long nowMs)` → số bucket:
    1. `bucketMs = config.getAggregationBucketMs()`;
    2. `rawCutoff = TimeUtil.bucketStart(nowMs - config.getRawRetentionMs(), bucketMs)` (canh lề = `Math.floorDiv(epochMillis, bucketMillis) * bucketMillis`, canh theo epoch);
    3. `buckets = metricRepository.aggregateAndPrune(rawCutoff, bucketMs)`;
    4. `aggregateCutoff = nowMs - config.getAggregateRetentionMs()`; `pruned = metricRepository.deleteAggregatesBefore(aggregateCutoff)`;
    5. cập nhật `lastRunAt`/`totalBucketsWritten`; nếu `buckets > 0 || pruned > 0` → log `"Aggregation wrote X bucket(s) and pruned Y expired bucket(s)"`;
    6. `eventBus.fireAggregationCompleted(buckets, pruned)`; trả `buckets`.
  - `getLastRunAt()`, `getTotalBucketsWritten()`, `stop()` (`cancel(false)`).
- **Được gọi ở đâu**:
  - `core/MonitorApplication`: tạo dòng 121, `aggregationService.start(scheduler)` dòng 134, `stop()` dòng 181. Getter `getAggregationService()` (dòng 259) **không thấy UI gọi**.
  - Test: `AggregationServiceTest` (8 test, `runOnce` với giờ giả, gồm `doesNotAggregateAPartialBucket`, `preservesAveragesAndPeaks`, `repeatedRunsAreIdempotent`, `emptyRunIsSafe`, `productionTimingUsesTheSameCode`).
- **Ghi chú / giá trị cấu hình** (`config/monitor.properties`, profile `production`): `raw.retention.minutes=60` (giữ chi tiết 1 giờ), `aggregation.bucket.minutes=5`, `aggregate.retention.days=30`, `aggregation.interval.seconds=300`. `MonitorConfigLoader` đổi hết sang mili giây (khóa `*.seconds` thắng khóa `*.minutes`/`*.days` khi có cả hai — đây là cách profile demo thu nhỏ cửa sổ mà **không đổi mã**).

#### HistoryService

- **Định nghĩa**: `public class HistoryService` — trả lời câu hỏi lịch sử từ **bảng đúng**.
- **Vai trò**: khoảng ngắn lấy từ `metric_raw` (mỗi mẫu 1 dòng); khoảng cũ hơn cửa sổ giữ thô không còn mẫu thô nên lấy từ `metric_5m`. Panel chỉ hỏi khoảng thời gian và luôn nhận về `List<HistoryPoint>` — **không viết SQL, không cần biết bảng nào được dùng**.
- **Thành phần chính**:
  - `enum Range` (các khoảng panel History cung cấp), mỗi phần tử có `label` tiếng Anh và `durationMs`:

    | Hằng | Label | durationMs |
    |------|-------|------------|
    | `LAST_HOUR` | `Last 1 Hour` | `3_600_000` (1 giờ) |
    | `LAST_24_HOURS` | `Last 24 Hours` | `86_400_000` (24 giờ) |
    | `LAST_7_DAYS` | `Last 7 Days` | `604_800_000` (7 ngày) |
    | `LAST_30_DAYS` | `Last 30 Days` | `2_592_000_000` (30 ngày) |

    có `getLabel()`, `getDurationMs()`, `toString()` trả label.
  - `load(String agentId, Range range)`: `now = System.currentTimeMillis()`, gọi `load(agentId, now - range.getDurationMs(), now)`.
  - `load(String agentId, long fromMs, long toMs)`:
    - `agentId` null/rỗng hoặc `toMs <= fromMs` → `List.of()`;
    - `rawHorizon = System.currentTimeMillis() - config.getRawRetentionMs()`;
    - `fromMs >= rawHorizon` → `metricRepository.findRaw(...)` (nguồn duy nhất có chi tiết từng giây);
    - ngược lại → `metricRepository.findAggregates(...)`; **nếu kết quả rỗng** (DB mới, chưa chạy aggregation lần nào) → **fallback** sang `findRaw(...)` để panel không trống; log `FINE` báo bảng nào đã trả lời.
  - `usesAggregates(Range)` = `range.getDurationMs() > config.getRawRetentionMs()` (chưa thấy nơi gọi).
  - `fromSamples(...)`: `new HistoryPoint(ts, cpu, mem, connections, in, out, aggregated = false)`; `fromAggregates(...)`: `new HistoryPoint(bucketStart, avgCpu, avgMemory, avgConnections, avgNetworkIn, avgNetworkOut, aggregated = true)` — cờ `aggregated` để panel biết điểm là mẫu thật hay là trung bình của bucket.
- **Được gọi ở đâu**:
  - `core/MonitorApplication` dòng 120 tạo, dòng 247 getter `getHistoryService()`.
  - `ui/HistoryPanel`: `JComboBox<HistoryService.Range>` (dòng 48–49, mọi giá trị `Range.values()`), đọc selection dòng 114, gọi `application.getHistoryService().load(agentId, range)` dòng 121 **bên trong `SwingWorker`** (không chặn EDT); combo agent được nạp từ `registry.snapshots()` (dòng 98).
- **Ghi chú**: Javadoc trong `HistoryPanel` xác nhận "Which table answers the question is decided by HistoryService".

#### AgentManagementService

- **Định nghĩa**: `public class AgentManagementService` — mọi thứ màn hình quản lý agent có thể làm, tách khỏi GUI. "Các dialog gọi các method này từ background worker và hiển thị kết quả; chúng không tự mở socket và không tự viết SQL."
- **Vai trò**: điều phối 4 phụ thuộc: `AgentRepository`, `AgentRegistry`, `AlertService`, `AgentTcpConnectionManager` (kênh điều khiển TCP — TCP là giao diện kết nối có thiết lập, dùng ở đây để push cấu hình).
- **Thành phần chính**:
  - `addAgent(AgentRecord)`: `validate(record)` → `repository.exists(...)` → ném `IllegalArgumentException("An agent with id X already exists")` → `repository.insert` → `registry.register(stored)` → log → trả về record đã lưu (có id).
  - `updateAgent(AgentRecord)`: `validate` → không tồn tại → `IllegalArgumentException("No agent with id X")` → `repository.update` → `registry.updateRecord`. **Không sửa được `agentId`.**
  - `deleteAgent(String agentId)`: `connectionManager.disconnect(agentId)` → `repository.delete` → nếu xoá được thì `registry.unregister` + `alertService.forgetAgent` → log → trả `boolean`.
  - `connect(AgentRecord) throws IOException`: `connectionManager.connect` → `connection.ping()`; nếu không trả lời → `disconnect` và ném `IOException("Agent X did not answer PING")` → `connection.requestInfo()` → `registry.setTcpConnected(true)` → trả `AgentInfoMessage`.
  - `disconnect(String agentId)`, `boolean isConnected(String agentId)`.
  - `pushConfiguration(AgentRecord, ConfigUpdateMessage) throws IOException`: `connectionManager.ensureConnected` + `setTcpConnected(true)` → `connection.sendConfigUpdate(update)` → **chỉ khi `ack.isSuccess()`** mới `applyLocally(...)` rồi `repository.update(agent)` + `registry.updateRecord(agent)`; trả `ConfigAckMessage`. "Ngưỡng cục bộ chỉ được ghi vào DB sau khi agent xác nhận, nên màn hình không bao giờ hiển thị cấu hình mà agent đã từ chối."
  - `applyLocally(...)`: chỉ copy các field khác `null` trong `ConfigUpdateMessage` (cpu, memory, network in, network out threshold).
  - `validate(AgentRecord)` — **static**, kiểm tra đúng thứ tự:
    - `record == null` → `"No agent details were supplied"`;
    - `ValidationUtil.requireText` cho `Agent ID`, `Name`, `Host`, `Shared Secret` (thiếu → ngoại lệ nêu tên trường);
    - `tcpPort` phải `1..65535`;
    - `requirePercentage`: CPU/Memory threshold phải hữu hạn (finite), `> 0` và `<= 100` → `"... must be greater than 0 and at most 100"`;
    - `requireRate`: Network IN/OUT threshold phải `>= 0` → `"... must be zero or a positive number of Mbps"`.
- **Được gọi ở đâu**:
  - `core/MonitorApplication` dòng 117 tạo, dòng 243 getter.
  - `ui/DashboardPanel`: `isConnected` (186), `addAgent` (250), `updateAgent` (268), `deleteAgent` (288), `connect` (306), `disconnect` (348), `pushConfiguration` (368) — tất cả chạy trong `SwingWorker`.
  - `ui/AgentDialog` dòng 188: `AgentManagementService.validate(record)` (method static, gọi ngay cả khi chưa có instance).
- **Ghi chú**: service này là nơi duy nhất ở tầng service biết `AgentTcpConnectionManager`.

## 3. Sơ đồ quan hệ & luồng dữ liệu trong phần 2

### 3.1 Sơ đồ kết cấu (dependency trong Phần 2 + điểm nối với Phần 1/3)

```
                         [ui/*  — Phần 3]
   MainFrame.UiEventListener     DashboardPanel / ServerDetailPanel / HistoryPanel / AlertPanel / AgentDialog
            ^  (addListener)                | gọi getter qua MonitorApplication
            |                               v
   +--------+--------------------------------+---------------------------------------------+
   |                 core/MonitorApplication (Phần 1: lõi) — new + start + close           |
   +--------+--------------------------------+---------------------------------------------+
            |                               |
            v                               v
   MonitorEventBus <------------------- các service bắn sự kiện
            ^
            | implements
   MonitorEventListener (interface 5 callback default)
            ^
            | implements
   [ui/MainFrame.UiEventListener]

   ====================== NHÓM service (Phần 2) ======================

   TelemetryService -----> HeartbeatMonitor.markAlive -----> AgentRegistry.setStatus
        |                        |                                |
        |                        v                                v
        |                    AlertService.raiseRecovered     AgentRepository.updateStatus
        |
        +--> AgentRegistry.applySample / countRejected   (không ghi bảng agents)
        +--> MetricRepository.insertRaw
        +--> AlertService.evaluate / recordSecurityEvent
        +--> MonitorEventBus.firePacketRejected

   HeartbeatMonitor (timer) ----check()----> AgentRegistry.setStatus
                                       `---> AlertService.raiseOffline

   AlertService ---> AlertRepository.insert/resolve/findAllOpen
        |     `----> Notifier.sendAlert (Phần 1: notification)
        `---------> MonitorEventBus.fireAlert

   AggregationService (timer) ---> MetricRepository.aggregateAndPrune
        |                             MetricRepository.deleteAggregatesBefore
        `--------------------------> MonitorEventBus.fireAggregationCompleted

   HistoryService ---------------> MetricRepository.findRaw / findAggregates

   AgentManagementService -------> AgentRepository.insert/update/delete/exists
        |      |                  AgentRegistry.register/updateRecord/unregister/setTcpConnected
        |      `-----------------> AlertService.forgetAgent
        `------------------------> AgentTcpConnectionManager (Phần 1: network)

   AgentRegistry (SecretProvider) <--- PacketValidator (Phần 1: common.security)

   ====================== NHÓM repository (Phần 2) ===================

   AgentRepository   \
   AlertRepository   +---> DatabaseManager.execute / inTransaction ---> SQLite (data/monitoring.db)
   MetricRepository  /         `-- DataAccessException (RuntimeException)
                                   `-- db/schema.sql (chạy mỗi lần khởi động)
```

### 3.2 Luồng 1 gói telemetry UDP đi qua Phần 2

```
 Agent (mạng)
    |
    v
 UdpTelemetryReceiver (network, Phần 1)          4 worker, 1 worker/sender, hàng đợi 2000
    |  telemetryService.handleDatagram(bytes, 0, len, "ip:port")
    v
 +--------------------------------------------------------------+
 | TelemetryService                                             |
 |  1. bytes -> String UTF-8                                    |
 |  2. codec.peekType  -> TELEMETRY | HEARTBEAT | loại khác     |
 |  3. validator.validate(packet, now)                          |
 |     - FAIL -> reject():                                     |
 |         rejectedCount++                                      |
 |         registry.countRejected(agentId)                      |
 |         nếu INVALID_HMAC / REPLAY ->                        |
 |             alertService.recordSecurityEvent(...)            |
 |                 (ghi DB, state=RESOLVED, rate-limit 60s,     |
 |                  fireAlert, KHÔNG gửi mail)                  |
 |         eventBus.firePacketRejected(...)                     |
 |         -> return false                                      |
 |     - OK  -> acceptedCount++                                 |
 +--------------------------------------------------------------+
    |
    v  (nhánh TELEMETRY)
 MetricSample.from(packet)
    |
    +--> heartbeatMonitor.markAlive(agentId, ts, now)
    |        registry.markSeen(max(ts, now))
    |        nếu trước đó OFFLINE -> setStatus(ONLINE)
    |                              -> alertService.raiseRecovered
    |                                   (đóng AGENT_OFFLINE,
    |                                    chèn AGENT_RECOVERED đã RESOLVED,
    |                                    fireAlert + notifier.sendAlert)
    +--> registry.applySample(sample)
    |        chép metric, lastSeen, countAccepted
    |        eventBus.fireAgentUpdated -> MainFrame.UiEventListener
    |             (invokeLater -> DashboardPanel / ServerDetailPanel)
    +--> metricRepository.insertRaw(sample)          [try/catch RuntimeException]
    |        -> bảng metric_raw
    +--> alertService.evaluate(record, sample)
             CPU_HIGH / MEMORY_HIGH / NETWORK_IN_HIGH / NETWORK_OUT_HIGH
             - value > ngưỡng & chưa có OPEN -> repository.insert
                                                + openAlerts.put
                                                + fireAlert + notifier.sendAlert
             - value <= ngưỡng - hysteresis  -> repository.resolve + openAlerts.remove
                                                + fireAlert
             - ngưỡng <= 0 -> bỏ qua (tắt kiểm tra)
```

### 3.3 Luồng 2 job định kỳ (2 thread scheduler daemon)

```
 scheduler "monitor-scheduler-*" (2 thread)
    |
    +--> HeartbeatMonitor.safeCheck() mỗi heartbeat.check.interval.ms = 2000 ms
    |        check(now): chỉ xét ONLINE/WARNING, lastSeen > 0
    |        now - lastSeen > 15000 ms -> registry.setStatus(OFFLINE)
    |                                   -> alertService.raiseOffline (CRITICAL,
    |                                      chỉ mở 1 lần, unit=ms,
    |                                      currentValue=lastSeen, threshold=timeout)
    |
    +--> AggregationService.safeRun() mỗi aggregation.interval.seconds = 300 s
             rawCutoff   = bucketStart(now - 60 phút, 5 phút)      [canh lề xuống]
             aggregateCutoff = now - 30 ngày
             MetricRepository.aggregateAndPrune(rawCutoff, 5 phút)
                transaction {
                   INSERT OR IGNORE INTO metric_5m ... SELECT ...
                       (timestamp/bucket)*bucket, COUNT, AVG, MAX ...
                       FROM metric_raw WHERE timestamp < rawCutoff
                       GROUP BY agent_id, bucket_start
                   DELETE FROM metric_raw WHERE timestamp < rawCutoff
                }
             MetricRepository.deleteAggregatesBefore(aggregateCutoff)
             eventBus.fireAggregationCompleted(buckets, pruned)
                    (hiện chưa có listener override)
```

### 3.4 Luồng truy vấn lịch sử (đọc)

```
 HistoryPanel (SwingWorker)
    |  range ∈ {1h, 24h, 7d, 30d}  ->  from = now - duration
    v
 HistoryService.load(agentId, from, to)
    |
    +-- from >= now - raw.retention (60 phút)?
    |        YES -> MetricRepository.findRaw            -> metric_raw
    |                 HistoryPoint(aggregated=false)
    |        NO  -> MetricRepository.findAggregates      -> metric_5m
    |                 HistoryPoint(aggregated=true)
    |                 nếu rỗng -> fallback findRaw      -> metric_raw
    v
 List<HistoryPoint>  -> TimeSeriesChartPanel (Phần 3)
```

### 3.5 Luồng thao tác quản lý agent (đọc/ghi)

```
 DashboardPanel / AgentDialog (SwingWorker, Phần 3)
    |  validate() chạy trước ở dialog
    v
 AgentManagementService
    +-- addAgent     : validate -> exists? -> AgentRepository.insert -> AgentRegistry.register
    +-- updateAgent  : validate -> exists? -> AgentRepository.update -> AgentRegistry.updateRecord
    +-- deleteAgent  : TcpManager.disconnect -> AgentRepository.delete
    |                                     -> AgentRegistry.unregister
    |                                     -> AlertService.forgetAgent
    +-- connect      : TcpManager.connect -> ping -> requestInfo -> registry.setTcpConnected(true)
    +-- pushConfig   : ensureConnected -> sendConfigUpdate
                          ack OK  -> applyLocally -> AgentRepository.update -> registry.updateRecord
                          ack FAIL -> không ghi DB
```
