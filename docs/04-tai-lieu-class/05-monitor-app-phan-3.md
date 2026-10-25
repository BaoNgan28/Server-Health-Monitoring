# Tài liệu lớp — monitor-app (Phần 3: Giao diện người dùng — Java Swing)

Tài liệu mô tả nhóm lớp giao diện `vn.edu.hutech.group10.monitoring.monitor.ui` của module `monitor-app`: 11 file nguồn, chứa 13 class (11 class công khai + 2 class lồng nhau). Tài liệu lớp của `monitor-app` được chia làm ba phần: **phần 1** — package `config`, `core`, `model`, `network`, `notification` (cấu hình, lõi, mô hình, mạng, thông báo); **phần 2** — package `repository`, `service` (lưu trữ và nghiệp vụ); **phần 3 (tài liệu này)** — package `ui`, toàn bộ giao diện Java Swing (Swing = bộ thư viện widget đồ họa desktop chuẩn của Java).

Trong tài liệu, các thuật ngữ tiếng Anh giữ nguyên nhưng được giải thích khi xuất hiện lần đầu: **EDT** (Event Dispatch Thread — luồng duy nhất của Swing chịu trách nhiệm vẽ giao diện và xử lý sự kiện chuột/bàn phím), **TableModel** (giao diện `AbstractTableModel` của Swing cung cấp dữ liệu hàng/cột cho `JTable`), **SwingWorker** (lớp chạy một việc nặng ở nền rồi trả kết quả về EDT), **JFreeChart** (thư viện vẽ biểu đồ), **renderer** (lớp tùy chỉnh cách một ô bảng được vẽ), **composition root** (nơi khởi tạo và ghép nối toàn bộ đối tượng — ở đây là `MonitorApplication`).

## 1. Bảng tổng quan class (phần 3)

| # | Class | Vai trò | File nguồn |
|---|-------|---------|------------|
| 1 | `MainFrame` | Cửa sổ chính `JFrame`: 5 tab, thanh trạng thái, đăng ký lắng nghe sự kiện | `monitor-app/src/main/java/vn/edu/hutech/group10/monitoring/monitor/ui/MainFrame.java` |
| 2 | `MainFrame.UiEventListener` (lồng trong `MainFrame`) | Chuyển sự kiện từ service sang EDT và rải về các panel | cùng file `MainFrame.java` |
| 3 | `DashboardPanel` | Tab Dashboard: 4 thẻ tổng quan, bảng agent trực tiếp, 6 nút quản lý | `.../monitor/ui/DashboardPanel.java` |
| 4 | `AgentTableModel` | `TableModel` của bảng Dashboard, mỗi hàng là một `AgentSnapshot` bất biến | `.../monitor/ui/AgentTableModel.java` |
| 5 | `ServerDetailPanel` | Tab Server Detail: chọn agent, 11 ô số liệu, 3 biểu đồ thời gian thực | `.../monitor/ui/ServerDetailPanel.java` |
| 6 | `TimeSeriesChartPanel` | Component biểu đồ thời gian (time series) bọc JFreeChart, có giới hạn số điểm | `.../monitor/ui/TimeSeriesChartPanel.java` |
| 7 | `HistoryPanel` | Tab History: biểu đồ lịch sử theo khoảng thời gian người dùng chọn | `.../monitor/ui/HistoryPanel.java` |
| 8 | `AlertPanel` | Tab Alerts: bảng lịch sử cảnh báo, lọc "chỉ cảnh báo đang mở" | `.../monitor/ui/AlertPanel.java` |
| 9 | `AlertPanel.AlertTableModel` (lồng trong `AlertPanel`) | `TableModel` nội bộ cho bảng cảnh báo | cùng file `AlertPanel.java` |
| 10 | `SettingsPanel` | Tab Settings: trạng thái cấu hình email + thông tin runtime (chỉ đọc) | `.../monitor/ui/SettingsPanel.java` |
| 11 | `AgentDialog` | Hộp thoại modal thêm/sửa agent (9 trường, validates qua service) | `.../monitor/ui/AgentDialog.java` |
| 12 | `ConfigureAgentDialog` | Hộp thoại modal thu thập cấu hình gửi `CONFIG_UPDATE` cho agent qua TCP | `.../monitor/ui/ConfigureAgentDialog.java` |
| 13 | `UiConstants` | Hằng số màu, phông chữ dùng chung + `colorOf(AgentStatus)` | `.../monitor/ui/UiConstants.java` |

Thư mục `monitor-app/src/test/java/...` **không có test nào** cho package `ui` (các test chỉ nằm ở `config`, `network`, `notification`, `repository`, `service`).

## 2. Cấu trúc giao diện

```
MonitoringMain.main(...)
  └─ MonitorApplication (composition root: repository + service + socket + notifier)
  └─ SwingUtilities.invokeLater → new MainFrame(application).setVisible(true)

MainFrame (JFrame, BorderLayout, 1200x780, tối thiểu 1000x640)
│
├── CENTER: JTabbedPane — 5 tab
│   │
│   ├── Tab "Dashboard" → DashboardPanel (BorderLayout, padding 10)
│   │   ├── NORTH  : hàng 4 thẻ (GridLayout 1x4):
│   │   │            [TOTAL SERVERS] [ONLINE] [WARNING] [OFFLINE]
│   │   ├── CENTER : JScrollPane → JTable (AgentTableModel, 9 cột, rowHeight 22)
│   │   │            cột: Name | IP | CPU | RAM | Connections |
│   │   │                 Network IN | Network OUT | Status | Last Seen
│   │   └── SOUTH  : [Add Agent] [Edit Agent] [Delete Agent]
│   │                [Connect] [Disconnect] [Configure]
│   │
│   ├── Tab "Server Detail" → ServerDetailPanel (BorderLayout)
│   │   ├── NORTH  : hàng chọn agent (JComboBox "Agent:")
│   │   │            + thẻ GridLayout(0,4) 11 ô:
│   │   │            Name, Address, Status, Last Seen, CPU, Memory,
│   │   │            Connections, Uptime, Network IN, Network OUT, Packets
│   │   └── CENTER : GridLayout(3,1) 3 × TimeSeriesChartPanel:
│   │                "CPU Usage" (%), "Memory Usage" (%),
│   │                "Network Throughput" (bytes/s, 2 series IN/OUT)
│   │
│   ├── Tab "History" → HistoryPanel (BorderLayout)
│   │   ├── NORTH  : [Agent: combo] [Range: combo] [Load]
│   │   │            + sourceLabel ("N point(s) from raw samples (metric_raw)" ...)
│   │   └── CENTER : GridLayout(2,1) 2 × TimeSeriesChartPanel:
│   │                "CPU and Memory" (%), "Network Throughput" (bytes/s)
│   │
│   ├── Tab "Alerts" → AlertPanel (BorderLayout)
│   │   ├── NORTH  : [Refresh] [x Open alerts only] summaryLabel
│   │   └── CENTER : JScrollPane → JTable (AlertTableModel, 6 cột)
│   │                cột: Time | Agent | Alert Type | Severity | State | Message
│   │
│   └── Tab "Settings" → SettingsPanel (BorderLayout)
│       ├── NORTH  : card "Notifications" (GridBagLayout, 6 dòng) +
│       │            [Send Test Email] [Reload Email Configuration]
│       └── CENTER : card "Runtime" (GridBagLayout, 9 dòng, font Monospaced)
│
└── SOUTH: thanh trạng thái (FlowLayout) — 3 JLabel
           statusLabel | packetsLabel | lastAlertLabel
           (cập nhật bởi Swing Timer mỗi 500 ms — REPAINT_INTERVAL_MS)

Hộp thoại (modal, chỉ mở từ DashboardPanel):
  AgentDialog          ← Add Agent / Edit Agent
  ConfigureAgentDialog ← Configure
```

Ghi chú về trạng thái các nút của Dashboard (`updateButtonState`): **Connect** bật khi có chọn hàng và chưa kết nối TCP; **Disconnect** bật khi hàng đã chọn đang kết nối (`AgentManagementService.isConnected`); **Configure** bật khi có chọn hàng.

## 3. Chi tiết từng class

#### UiConstants
- **Định nghĩa**: class `final` tiện ích, constructor private, chứa màu/phông dùng chung cho toàn bộ giao diện và hàm đổi màu theo trạng thái agent.
- **Thành phần chính**: màu trạng thái `ONLINE = 0x1B8A3A`, `WARNING = 0xC97A00`, `OFFLINE = 0xC62828`, `UNKNOWN = 0x666666`; màu nền `PANEL_BACKGROUND = 0xF5F6F8`, `CARD_BACKGROUND = Color.WHITE`; phông `TITLE_FONT` (SansSerif bold 16), `VALUE_FONT` (SansSerif bold 22), `LABEL_FONT` (SansSerif plain 12), `MONO_FONT` (Monospaced plain 12); phương thức `colorOf(AgentStatus)` tra màu theo `switch` (null → `UNKNOWN`, giá trị không khớp → nhánh `default` trả `UNKNOWN`).
- **Được gọi ở đâu**: `MainFrame.buildStatusBar` (phông cho 3 nhãn thanh trạng thái); `DashboardPanel` (nền, phông thẻ, renderer cột Status); `ServerDetailPanel` (nền, phông nhãn ô, màu trạng thái); `HistoryPanel` (nền); `AlertPanel` (nền, màu severity); `SettingsPanel` (nền, phông mono, màu CONFIGURED/NOT CONFIGURED, màu cảnh báo `Reason`).
- **Ghi chú**: `AgentStatus` là enum `UNKNOWN | ONLINE | WARNING | OFFLINE` (module `monitoring-common`); đây là nguồn màu duy nhất nên các bảng và màn hình chi tiết hiển thị màu trạng thái nhất quán.

#### MainFrame
- **Định nghĩa**: cửa sổ chính của ứng dụng monitor, kế thừa `JFrame`, tiêu đề `"Group 10 - Server Health Monitoring System  [profile: <tên profile>]"`.
- **Thành phần chính**: 5 panel thành viên (`DashboardPanel`, `ServerDetailPanel`, `HistoryPanel`, `AlertPanel`, `SettingsPanel`); `JTabbedPane` gắn 5 tab theo thứ tự Dashboard → Server Detail → History → Alerts → Settings; 3 nhãn thanh trạng thái (`statusLabel`, `packetsLabel`, `lastAlertLabel`); `Timer statusTimer` chu kỳ 500 ms (`REPAINT_INTERVAL_MS`) gọi `updateStatusBar()`; inner class `UiEventListener`.
- **Được gọi ở đâu**: `MonitoringMain.main` — sau khi `MonitorApplication.start()` thành công và đăng ký shutdown hook, gọi `SwingUtilities.invokeLater(() -> { applyLookAndFeel(); new MainFrame(application).setVisible(true); })` (`MonitoringMain.java:68-71`). Không class nào khác tạo `MainFrame`.
- **Ghi chú**:
  - Luồng: mọi callback từ service đến trên thread worker nên `UiEventListener` luôn bọc bằng `SwingUtilities.invokeLater` (invokeLater = hẹn gọi đoạn mã trên EDT); ngược lại mọi thao tác có thể chặn (kết nối TCP, đẩy cấu hình, tải lịch sử, gửi SMTP) đều chạy trong `SwingWorker` ở các panel, không chạy trên EDT (theo Javadoc của lớp).
  - `serverDetailPanel.reloadAgents()` và `historyPanel.reloadAgents()` được gọi ngay trong constructor để combo agent có dữ liệu ngay khi mở cửa sổ.
  - `updateStatusBar()` (mỗi 500 ms): `statusLabel` = cổng UDP (`config.getUdpPort()`), số agent (`registry.size()`), trạng thái email (`EmailConfig.isConfigured()`); `packetsLabel` = số gói đã nhận/accepted/rejected (`UdpTelemetryReceiver.getReceivedCount()`, `TelemetryService.getAcceptedCount()`, `TelemetryService.getRejectedCount()`).
  - `onAgentSelected(String)` chỉ gọi `serverDetailPanel.selectAgent(agentId)` — **không** tự chuyển tab sang Server Detail.
  - Đóng cửa sổ: `setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)` + `WindowAdapter.windowClosing` → `shutdown()`: dừng `statusTimer`, ẩn cửa sổ, rồi chạy `application.close()` và `System.exit(0)` trên thread tên `monitor-shutdown` để chặn giải phóng socket/DB không nằm trên EDT.
  - Kích thước: 1200x780, tối thiểu 1000x640, căn giữa màn hình.

#### UiEventListener (class lồng nhau trong MainFrame)
- **Định nghĩa**: inner class `private final` thực hiện giao diện `MonitorEventListener` (interface ở package `service` có 5 phương thức `default` rỗng: `onAgentUpdated`, `onAgentListChanged`, `onAlert`, `onPacketRejected`, `onAggregationCompleted`).
- **Thành phần / hành vi**: đăng ký trong constructor `MainFrame` bằng `application.getEventBus().addListener(new UiEventListener())`.
  - `onAgentUpdated(AgentSnapshot)` → `invokeLater`: `dashboardPanel.applyUpdate(snapshot)` + `serverDetailPanel.applyUpdate(snapshot)`.
  - `onAgentListChanged()` → `invokeLater`: `dashboardPanel.reload()` + `serverDetailPanel.reloadAgents()` + `historyPanel.reloadAgents()`.
  - `onAlert(AlertEvent)` → `invokeLater`: cập nhật `lastAlertLabel` dạng `last alert: <AlertType> on <agentId> at <giờ:phút>` (dùng `TimeUtil.formatTimeOnly`) + `alertPanel.reload()`.
  - Không hiện thực `onPacketRejected` và `onAggregationCompleted` (giữ `default` rỗng).
- **Được gọi ở đâu**: `MonitorEventBus` phát sự kiện; `AgentRegistry` bắn `fireAgentUpdated` (3 chỗ) và `fireAgentListChanged` (3 chỗ); `AlertService` bắn `fireAlert` (4 chỗ); `TelemetryService` bắn `firePacketRejected`; `AggregationService` bắn `fireAggregationCompleted`.
- **Ghi chú**: `MonitorEventBus` dùng `CopyOnWriteArrayList` và bọc mỗi listener trong `try/catch` — một lỗi trong listener chỉ bị log, không làm hỏng xử lý telemetry. Callback đến trên thread worker (nhận UDP, heartbeat, aggregation) nên việc nhảy sang EDT là bắt buộc.

#### DashboardPanel
- **Định nghĩa**: tab Dashboard — bảng tổng quan trạng thái, bảng agent trực tiếp và các nút quản lý agent. Kế thừa `JPanel`, layout `BorderLayout(8,8)`, padding 10, nền `UiConstants.PANEL_BACKGROUND`.
- **Thành phần chính**: `AgentTableModel tableModel` + `JTable table` (chọn 1 hàng, `rowHeight` 22, `TableRowSorter` để sắp xếp/lọc, renderer riêng cho cột index 7 = Status tô màu theo `UiConstants.colorOf`); 4 nhãn thẻ (`totalValue`, `onlineValue`, `warningValue`, `offlineValue`) với thẻ do `card(...)` tạo (viền `0xDDDDDD`, cố định 140x76); 6 nút `Add Agent`, `Edit Agent`, `Delete Agent`, `Connect`, `Disconnect`, `Configure`; `Consumer<String> agentSelectionListener`.
- **Được gọi ở đâu**: `MainFrame` tạo trong constructor: `new DashboardPanel(application, this::onAgentSelected)`; `UiEventListener.onAgentUpdated` gọi `applyUpdate(snapshot)`; `UiEventListener.onAgentListChanged` gọi `reload()`.
- **Ghi chú / hành vi chi tiết**:
  - `reload()`: đọc `application.getRegistry().snapshots()`, ghi đè toàn bộ hàng qua `tableModel.setRows`, cập nhật 4 thẻ bằng `registry.size()` và `registry.countByStatus(...)`; giữ nguyên hàng đang chọn (tìm lại bằng `tableModel.indexOfAgent` rồi `convertRowIndexToView`).
  - `applyUpdate(snapshot)`: `tableModel.updateRow` trả `false` (agent chưa có trong bảng) thì rơi về `reload()`; luôn `updateSummary()`.
  - Chọn hàng trên bảng → `ListSelectionListener` → `updateButtonState` + gọi `agentSelectionListener.accept(agentId)` (chính là `MainFrame.onAgentSelected` → `ServerDetailPanel.selectAgent`).
  - `onAdd` / `onEdit`: mở `AgentDialog.showAddDialog` / `showEditDialog`; nếu trả về record (không null) thì gọi `AgentManagementService.addAgent` / `updateAgent` rồi `reload()`; `RuntimeException` → `showError` (log `Level.WARNING` + `JOptionPane` lỗi).
  - `onDelete`: hỏi lại bằng `JOptionPane.showConfirmDialog` (nhắn metrics và alerts của agent cũng sẽ bị xóa) → `AgentManagementService.deleteAgent`.
  - `onConnect`: tắt nút, chạy `SwingWorker` gọi `AgentManagementService.connect(record)`; thành công hiện hộp thoại mô tả `AgentInfoMessage` (agent id, name, hostname, OS + version, CPU model + số core, tổng RAM, hai khoảng metric/heartbeat) qua `describe(...)`; thất bại hiện lỗi kèm `host:tcpPort`; `finally` bật lại nút.
  - `onDisconnect`: gọi `AgentManagementService.disconnect(agentId)` (đồng bộ, không qua worker trong code hiện tại) rồi cập nhật nút.
  - `onConfigure`: mở `ConfigureAgentDialog.show(...)`; nếu trả `ConfigUpdateMessage` thì chạy `SwingWorker` gọi `AgentManagementService.pushConfiguration(record, update)`; hiện `ConfigAckMessage` (status, message, metric interval, heartbeat interval, CPU threshold, memory threshold hiệu lực trên agent) rồi `reload()`.
  - `updateButtonState(selected)`: `connectButton` bật khi có chọn và `!isConnected`; `disconnectButton` bật khi đã kết nối; `configureButton` bật khi có chọn.
  - Mọi giá trị hiển thị lấy từ `AgentSnapshot` (ảnh chụp bất biến của agent) — GUI không đụng `AgentRuntimeState` trực tiếp.

#### AgentTableModel
- **Định nghĩa**: `TableModel` (lớp nền tảng của Swing cung cấp dữ liệu cho `JTable`) kế thừa `AbstractTableModel`, phía sau bảng Dashboard.
- **Thành phần chính**: mảng `COLUMNS` gồm 9 cột: `Name`, `IP`, `CPU`, `RAM`, `Connections`, `Network IN`, `Network OUT`, `Status`, `Last Seen`; danh sách hàng `List<AgentSnapshot> rows` (snapshot bất biến); `serialVersionUID = 1L`.
- **Được gọi ở đâu**: chỉ trong `DashboardPanel` (`new AgentTableModel()` truyền vào `new JTable(tableModel)`).
- **Ghi chú**:
  - `setRows(List)` xóa và thêm lại toàn bộ hàng rồi `fireTableDataChanged()` (báo JTable vẽ lại toàn bộ).
  - `updateRow(snapshot)` thay đúng một hàng theo `agentId` rồi `fireTableRowsUpdated(i, i)` — repaint một dòng thay vì dựng lại cả bảng nên selection và thứ tự sắp xếp không bị mất; trả `false` khi không tìm thấy (lúc này `DashboardPanel` gọi `reload()`).
  - `getValueAt`: cột 0 = `name`; cột 1 = `host:tcpPort`; cột 2/3 = CPU/RAM theo định dạng `%.1f %%`; cột 4 = số kết nối; cột 5/6 = `ByteRateFormatter.formatRate`; cột 7 = tên trạng thái (`AgentStatus.name()`, null → `UNKNOWN`); cột 8 = `TimeUtil.formatTimestamp(lastSeen)`; nếu `!hasMetrics()` thì các cột số trả `"-"`.
  - `isCellEditable` luôn `false`, `getColumnClass` luôn `String.class`.
  - Theo Javadoc: secret dùng chung không nằm trong bất kỳ cột nào và không bao giờ được vẽ trong bảng.

#### ServerDetailPanel
- **Định nghĩa**: tab Server Detail — chi tiết trực tiếp của một agent: định danh, 11 ô số liệu và 3 biểu đồ được nạp một điểm mỗi mẫu metric được chấp nhận.
- **Thành phần chính**: `JComboBox<String> agentSelector`; 11 nhãn giá trị (`nameValue`, `addressValue`, `statusValue`, `lastSeenValue`, `cpuValue`, `memoryValue`, `connectionsValue`, `uptimeValue`, `networkInValue`, `networkOutValue`, `packetsValue`) sắp trong thẻ `GridLayout(0,4)`; 3 `TimeSeriesChartPanel` — `cpuChart` ("CPU Usage", "%", fixedRange), `memoryChart` ("Memory Usage", "%", fixedRange), `networkChart` ("Network Throughput", "bytes/s", 2 series) với cửa sổ điểm lấy từ `config.getChartWindowSamples()`; cờ `updatingSelector` chống sự kiện tự sinh của combo làm tải lại đệ quy.
- **Được gọi ở đâu**: `MainFrame` tạo trong constructor; `MainFrame.onAgentSelected` gọi `selectAgent(agentId)`; `UiEventListener.onAgentUpdated` gọi `applyUpdate(snapshot)`; `UiEventListener.onAgentListChanged` và constructor `MainFrame` gọi `reloadAgents()`.
- **Ghi chú**:
  - `reloadAgents()`: dựng `DefaultComboBoxModel` từ `registry.snapshots()` (chỉ `agentId`), đặt cờ `updatingSelector = true` trong lúc thay model, giữ lựa chọn cũ nếu còn ngược lại chọn phần tử đầu, rồi gọi `onAgentSelected()`.
  - `selectAgent(agentId)`: bỏ qua nếu `null` hoặc đã đang chọn; duyệt combo tìm index rồi `setSelectedIndex` — nhờ cờ `updatingSelector` không được đặt ở đây, việc chọn từ bên ngoài vẫn kích hoạt `onAgentSelected` bình thường.
  - `onAgentSelected()`: gọi `clear()` cho cả 3 biểu đồ (điểm cũ thuộc agent trước), nếu có agent thì lấy `registry.snapshot(agentId)` → `showSnapshot`, không thì `clearFields()` (mọi ô về `"-"`, status màu `UNKNOWN`).
  - `applyUpdate(snapshot)`: bỏ qua nếu snapshot thuộc agent khác đang chọn; `showSnapshot`; nếu `hasMetrics()` thì thêm điểm vào 4 series (`CPU %`, `Memory %`, `Network IN`, `Network OUT`) với thời gian `getLastMetricTimestamp()`.
  - `showSnapshot`: đặt text các ô; trạng thái được tô màu qua `UiConstants.colorOf`; không có metric thì 6 ô số về `"-"`; `packetsValue` = `"<accepted> accepted / <rejected> rejected"`.
  - Biểu đồ giữ cửa sổ điểm hữu hạn (xem `TimeSeriesChartPanel`) nên chạy qua đêm không tích lũy bộ nhớ (theo Javadoc).

#### TimeSeriesChartPanel
- **Định nghĩa**: component `JPanel` bọc một biểu đồ time series (biểu đồ chuỗi theo thời gian) của JFreeChart (thư viện vẽ biểu đồ Java, phiên bản 1.5.5 theo `pom.xml` gốc), dựng một lần và chỉ nhận dữ liệu về sau.
- **Thành phần chính**: `TimeSeriesCollection dataset`; `Map<String, TimeSeries> series` (`LinkedHashMap`, mỗi series có `setMaximumItemCount(maximumItemCount)`); `JFreeChart chart` tạo trong constructor; hằng `SERIES_COLORS` 4 màu (`0x1F77B4`, `0xD62728`, `0x2CA02C`, `0xFF7F0E`) lặp theo index series.
- **Được gọi ở đâu**: `ServerDetailPanel` tạo 3 instance (CPU, Memory, Network); `HistoryPanel` tạo 2 instance (usage, network).
- **Ghi chú**:
  - Constructor: `ChartFactory.createTimeSeriesChart(title, "Time", valueAxisLabel, dataset, legend = (số series > 1), tooltips = true, urls = false)`; nền chart trắng, nền plot `0xFAFAFA`, gridline `0xDDDDDD`; trục thời gian (`DateAxis`) auto-range; trục giá trị (`NumberAxis`) cố định 0..100 khi `fixedRange = true`, ngược lại auto-range có tính 0; renderer `XYLineAndShapeRenderer(true, false)` (vẽ đường, không vẽ điểm) với stroke `1.8f`; `ChartPanel` 520x220, tắt zoom theo chuột/wheel (Javadoc: zoom một biểu đồ trực tiếp chỉ chống lại trục auto-range).
  - `addPoint(seriesName, timestamp, value)`: dùng `TimeSeries.addOrUpdate` với `Millisecond` thay vì `add` vì hai mẫu có thể cùng một mili-giây và `add` sẽ ném lỗi trùng lặp; tên series không tồn tại thì bỏ qua.
  - `setPoints(seriesName, timestamps, values)`: `setNotify(false)` → `clear()` → thêm tối đa `maximumItemCount` điểm **mới nhất** (bỏ phần đầu khi dữ liệu dài hơn cửa sổ) → `setNotify(true)`; dùng khi tải lịch sử.
  - `clear()`: xóa mọi series (khi đổi agent).
  - Getter `getChart()`, `getMaximumItemCount()`.
  - Javadoc: mọi phương thức phải gọi trên EDT; chart/plot/renderer không bao giờ được thay thế nên cập nhật mỗi giây rất rẻ.

#### HistoryPanel
- **Định nghĩa**: tab History — biểu đồ lịch sử của một agent trong khoảng thời gian người dùng chọn; panel chỉ hỏi "một khoảng thời gian", không biết bảng DB nào trả lời (việc chọn `metric_raw` hay `metric_5m` do `HistoryService` quyết định).
- **Thành phần chính**: `agentSelector` (combo agentId), `rangeSelector` (combo `HistoryService.Range`: `LAST_HOUR` "Last 1 Hour", `LAST_24_HOURS`, `LAST_7_DAYS`, `LAST_30_DAYS` — hiển thị qua `toString()` trả label), nút `refreshButton` ("Load"), `sourceLabel`; 2 biểu đồ `usageChart` ("CPU and Memory", "%", fixedRange, series `CPU %` + `Memory %`) và `networkChart` ("Network Throughput", "bytes/s", 2 series `Network IN` + `Network OUT`); hằng `MAX_PLOTTED_POINTS = 2000`.
- **Được gọi ở đâu**: `MainFrame` tạo trong constructor; `UiEventListener.onAgentListChanged` gọi `reloadAgents()`.
- **Ghi chú**:
  - `reloadAgents()`: dựng lại model combo từ `registry.snapshots()`, giữ lựa chọn cũ nếu còn, ngược lại chọn index 0 — không tự động tải dữ liệu.
  - `loadHistory()`: tắt nút, `sourceLabel = "Loading ..."`, chạy `SwingWorker<List<HistoryPoint>>` gọi `application.getHistoryService().load(agentId, range)`; `done()` → `plot(get())`; lỗi → log `Level.WARNING` + hộp thoại lỗi; `finally` bật lại nút.
  - `plot(points)`: rỗng → `clear()` cả 2 biểu đồ + nhãn `"No data in this range yet."`; ngược lại down-sample đều `step = max(1, points.size() / 2000)` (giữ 2000 điểm cách đều thay vì vẽ cả đợt), dựng 5 mảng `timestamps/cpu/memory/inbound/outbound` rồi `setPoints` cho 4 series; `sourceLabel` ghi nguồn dữ liệu: `"N point(s) from aggregated buckets (metric_5m)"` hoặc `"raw samples (metric_raw)"` theo `HistoryPoint.isAggregated()`.
  - Theo code, không có listener nào tự gọi `loadHistory()` khi mở tab — dữ liệu chỉ tải khi người dùng bấm **Load**.

#### AlertPanel
- **Định nghĩa**: tab Alerts — lịch sử cảnh báo: điều gì đã kích hoạt, khi nào, và còn đang mở hay đã được xử lý. Dòng đang mở hiển thị in đậm và màu theo mức độ (severity) để phân biệt với cảnh báo đã đóng.
- **Thành phần chính**: `AlertTableModel tableModel` (inner class) + `JTable table` (`rowHeight` 22, `TableRowSorter`); nút `Refresh`; `JCheckBox openOnly` ("Open alerts only"); `summaryLabel`; hằng `MAX_ROWS = 500`; một `DefaultTableCellRenderer` dùng chung cho **tất cả** 6 cột — mỗi ô gọi `tableModel.getRow(table.convertRowIndexToModel(row))` (chuyển index qua bộ sắp xếp) để lấy `AlertEvent`, nếu `state == OPEN` thì font in đậm + màu severity, không thì font thường + `Color.DARK_GRAY`; cột 5 (Message) `preferredWidth = 320`.
- **Được gọi ở đâu**: `MainFrame` tạo trong constructor; `UiEventListener.onAlert` gọi `reload()` mỗi khi có sự kiện cảnh báo mới; chính panel gọi `reload()` khi bấm Refresh hoặc đổi checkbox.
- **Ghi chú**:
  - `severityColor(event)`: `CRITICAL` → `UiConstants.OFFLINE` (đỏ), `WARNING` → `UiConstants.WARNING` (cam), mặc định (`INFO`) → `UiConstants.ONLINE` (xanh) — `AlertSeverity` là enum `INFO | WARNING | CRITICAL`.
  - `reload()`: đọc lựa chọn checkbox, chạy `SwingWorker` gọi `application.getAlertRepository().findAllOpen()` (khi chọn lọc) hoặc `findRecent(500)`; `done()` → `tableModel.setRows(events)` + `summaryLabel = "N alert(s) shown, M currently open"` với `M = AlertService.getOpenAlertCount()`; lỗi → log + hộp thoại.
  - Không hiện thực `onAgentListChanged` — danh sách chỉ tự làm mới khi có alert mới hoặc bấm Refresh.

#### AlertTableModel (class lồng nhau trong AlertPanel)
- **Định nghĩa**: `private static final class` kế thừa `AbstractTableModel`, mô hình bảng nội bộ của `AlertPanel`.
- **Thành phần chính**: `COLUMNS = {"Time", "Agent", "Alert Type", "Severity", "State", "Message"}`; `List<AlertEvent> rows`.
- **Được gọi ở đâu**: chỉ trong `AlertPanel` (`new AlertTableModel()` → `new JTable(tableModel)`), đọc thêm trong renderer qua `getRow(...)`.
- **Ghi chú**: `setRows` → `fireTableDataChanged()`; `getRow(index)` trả `null` khi index ngoài phạm vi; `getValueAt`: cột 0 = `TimeUtil.formatTimestamp(openedAt)`, 1 = `agentId`, 2 = `AlertType.name()` (enum gồm `CPU_HIGH`, `MEMORY_HIGH`, `NETWORK_IN_HIGH`, `NETWORK_OUT_HIGH`, `AGENT_OFFLINE`, `AGENT_RECOVERED`, `SECURITY_INVALID_HMAC`, `SECURITY_REPLAY_PACKET`), 3 = `Severity.name()`, 4 = `"OPEN"` hoặc `"RESOLVED " + TimeUtil.formatTimeOnly(resolvedAt)`, 5 = `message`; `isCellEditable` luôn `false`.

#### SettingsPanel
- **Định nghĩa**: tab Settings — hiển thị trạng thái kênh thông báo (email) và các thông số runtime của ứng dụng (chỉ đọc), kèm nút gửi mail thử và nút nạp lại cấu hình email.
- **Thành phần chính**: card **Notifications** (`GridBagLayout`, viền titled "Notifications") với 6 dòng: `Email Notification Status` (`emailStatusValue`), `Sender`, `Recipient`, `SMTP` (`serverValue`), `Secrets File` (`secretsFileValue`), `Reason` (`reasonValue`); 2 nút `testButton` ("Send Test Email") và `reloadButton` ("Reload Email Configuration"); card **Runtime** (`GridBagLayout`, viền titled "Runtime") 9 dòng hiển thị bằng `MONO_FONT`: Profile, UDP Listen Port, Heartbeat Timeout, Timestamp Tolerance, Raw Retention (giây), Aggregation Bucket (giây), Aggregate Retention (giây), Database (đường dẫn), Log Directory.
- **Được gọi ở đâu**: `MainFrame` tạo trong constructor (trong constructor của panel có gọi `refresh()` một lần); mọi tương tác đều do người dùng trên tab này.
- **Ghi chú**:
  - `refresh()`: đọc `application.getEmailConfig()`; trạng thái `CONFIGURED` tô xanh / `NOT CONFIGURED` tô đỏ; `Sender` = `config.getUsername()`, `Recipient` = `config.getRecipient()`, `SMTP` = `config.describeServer()`, `Secrets File` = `AppPaths.configFile(EmailConfigLoader.DEFAULT_FILE_NAME)` (tên file hằng = `email-secrets.properties`), `Reason` = `config.getDisabledReason()` khi chưa cấu hình (màu cam) hoặc `"-"`.
  - `onSendTestEmail()`: nếu chưa cấu hình → hộp thoại hướng dẫn cụ thể (sửa file secrets, đặt `smtp.appPassword` và `smtp.recipient`, rồi bấm "Reload Email Configuration"); nếu đã cấu hình → tắt nút, chạy `SwingWorker` gọi `application.getNotifier().sendTestMessage()`, hiện `NotificationResult.getMessage()` với icon theo `isSuccess()`; `finally` bật lại nút.
  - `onReload()`: `SwingWorker` gọi `application.reloadEmailConfiguration()` (tải lại file secrets và dựng lại notifier, không cần khởi động lại app), xong → `refresh()` + hộp thoại thông báo CONFIGURED / STILL NOT CONFIGURED kèm lý do.
  - Theo Javadoc: panel **không bao giờ đọc hay hiển thị app password** — nó chỉ yêu cầu notifier gửi mail, credential nằm trong notifier/`EmailConfig`.
  - Đây là panel chỉ hiển thị: không có trường nhập, không sửa cấu hình runtime.

#### AgentDialog
- **Định nghĩa**: hộp thoại modal (`ModalityType.APPLICATION_MODAL`) thêm hoặc sửa một agent.
- **Thành phần chính**: 9 ô nhập — `agentIdField`, `nameField`, `hostField`, `portField`, `secretField` (`JPasswordField`), `cpuField`, `memoryField`, `networkInField`, `networkOutField` (tất cả độ rộng 18 cột); trường `original` (`AgentRecord` đang sửa, `null` khi thêm mới) và `result` (`AgentRecord` trả về, `null` nếu hủy); form `GridBagLayout` 9 dòng; nút Save (mặc định của root pane) và Cancel.
- **Được gọi ở đâu**: `DashboardPanel.onAdd` → `AgentDialog.showAddDialog(window)`; `DashboardPanel.onEdit` → `AgentDialog.showEditDialog(window, selected.getRecord())`. Constructor là `private`, chỉ dùng qua 2 static factory.
- **Ghi chú**:
  - Thêm mới: `host` mặc định `127.0.0.1`, `port` = `ProtocolConstants.DEFAULT_AGENT_TCP_PORT`, CPU/Memory ngưỡng `85.0`, Network IN/OUT `50.0`.
  - Sửa: `agentIdField` không cho sửa (`setEditable(false)` kèm tooltip "không đổi được sau khi đăng ký"); trường secret để trống thì giữ nguyên secret đã lưu (tooltip giải thích); `showEditDialog` nhận `existing.copy()` nên bản gốc không bị sửa nếu người dùng hủy.
  - `onSave()`: điền dữ liệu vào record (mới → `new AgentRecord()`, cũ → `original`); secret lấy bằng `getPassword()` trả `mảng char`, chỉ chuyển sang `String` khi độ dài > 0, và mảng được `Arrays.fill(secret, '\0')` trong `finally` (không để chuỗi rỗng key tồn tại trong bộ nhớ); thêm mới mà secret rỗng → `IllegalArgumentException "Shared Secret must not be empty"`; parse số (`parseInt`, `parseDouble`) bắt lỗi và ném `IllegalArgumentException` có tên trường; cuối cùng gọi `AgentManagementService.validate(record)` rồi gán `result` và `dispose()`. Lỗi validation → hộp thoại "Invalid input".
  - Cancel → `dispose()` → `result` vẫn `null` → caller bỏ qua.
  - Kích thước: `pack()` rồi `setMinimumSize(420, cao)`, căn giữa owner.

#### ConfigureAgentDialog
- **Định nghĩa**: hộp thoại modal thu thập các thiết lập để gửi cho agent qua TCP dưới dạng thông điệp `CONFIG_UPDATE`.
- **Thành phần chính**: 6 ô `JTextField` (rộng 14 cột): `metricIntervalField`, `heartbeatIntervalField`, `cpuField`, `memoryField`, `networkInField`, `networkOutField`; trường `result` là `ConfigUpdateMessage`; header dạng HTML: *"Settings are sent to the agent over TCP. Leave a field empty to keep the agent's current value."*; nút "Send CONFIG_UPDATE" (mặc định) và Cancel.
- **Được gọi ở đâu**: `DashboardPanel.onConfigure` → `ConfigureAgentDialog.show(window, record)`; trả `ConfigUpdateMessage` (không null) thì `DashboardPanel` đẩy qua `AgentManagementService.pushConfiguration(record, update)` trong `SwingWorker`.
- **Ghi chú**:
  - 4 ô ngưỡng (CPU %, Memory %, Network IN/OUT Mbps) được điền sẵn từ `AgentRecord` (định dạng `%.1f`); 2 ô khoảng thời gian (metric/heartbeat, ms) để trống.
  - `onSend()`: mỗi ô dùng parser optional — `optionalLong` / `optionalDouble` trả `null` khi ô trống, ngược lại parse và ném `IllegalArgumentException` có tên trường nếu sai định dạng; gán vào `ConfigUpdateMessage` (các kiểu `Long`/`Double` đóng hộp, `null` = không thay đổi — theo Javadoc của lớp) rồi `result = update; dispose()`.
  - Không có validate phạm vi trong dialog (ví dụ ngưỡng ngoài 0..100 không bị chặn ở đây) — việc áp dụng do agent quyết định và kết quả trả về qua `ConfigAckMessage`.

## 4. Sơ đồ quan hệ (ASCII)

```
                    MonitoringMain (entry point, main)
                       |  new MonitorApplication(config) → start()
                       |  SwingUtilities.invokeLater
                       v
    +-------------------------------------------------------------+
    |  MainFrame (JFrame)                                         |
    |    |-- DashboardPanel -------> AgentTableModel ---> JTable  |
    |    |        |  (registry.snapshots / countByStatus)         |
    |    |        +--> AgentDialog ----------> AgentManagementService
    |    |        +--> ConfigureAgentDialog --> pushConfiguration  |
    |    |        +--> connect / disconnect / addAgent /           |
    |    |             updateAgent / deleteAgent  (AgentManagementService)
    |    |        +--(chọn hàng)--> listener.accept(agentId)       |
    |    |                              |                          |
    |    |-- ServerDetailPanel <--------+  selectAgent(agentId)    |
    |    |        |-- 3 x TimeSeriesChartPanel                     |
    |    |        +--> AgentRegistry.snapshot/snapshots            |
    |    |                                                          |
    |    |-- HistoryPanel --------> HistoryService.load (SwingWorker)
    |    |        |-- 2 x TimeSeriesChartPanel                     |
    |    |                                                          |
    |    |-- AlertPanel ---------> AlertRepository.findAllOpen/     |
    |    |        |                 findRecent + AlertService       |
    |    |        |                 .getOpenAlertCount              |
    |    |        +--> AlertTableModel (inner)                      |
    |    |                                                          |
    |    |-- SettingsPanel ------> EmailConfig / Notifier           |
    |    |                            .sendTestMessage()            |
    |    |                        MonitorApplication                |
    |    |                            .reloadEmailConfiguration()   |
    |    |                                                          |
    |    +-- UiEventListener (inner) --> SwingUtilities.invokeLater|
    +---------------------------^-----------------------------------+
                                | addListener (trong constructor)
                                |
                     MonitorEventBus  (CopyOnWriteArrayList)
        fireAgentUpdated / fireAgentListChanged   |   fireAlert
                     |                            |
        +------------+-----------+       +--------+--------+
        |                        |       |                 |
  AgentRegistry          TelemetryService  AlertService  (AggregationService
  (UDP receiver,         (PacketValidator) (Notifier)     chỉ fire cho
   heartbeat timer)                                    listener khác)
        |
   AgentSnapshot (bất biến) ──> mọi panel chỉ nhận snapshot, không đụng
                                AgentRuntimeState / socket / JDBC

Quy tắc luồng (threading):
  service callback (worker thread) --invokeLater--> EDT (vẽ UI)
  thao tác chặn (TCP, DB, SMTP)    --SwingWorker--> nền, done() trên EDT
  thanh trạng thái                 --Swing Timer 500ms--> EDT
```

### Ghi chú nguồn tham chiếu

- Composition root: `monitor-app/src/main/java/vn/edu/hutech/group10/monitoring/monitor/core/MonitorApplication.java` (ghép repository → service → socket → notifier; chỉ expose qua getter `getXxx()` để panel không với tới JDBC/`DatagramSocket`/SMTP).
- Entry point: `monitor-app/src/main/java/vn/edu/hutech/group10/monitoring/monitor/MonitoringMain.java` (đọc `--config`, nạp cấu hình, `application.start()`, rồi dựng `MainFrame` trên EDT; nếu start thất bại hiện `JOptionPane` lỗi và `System.exit(1)`).
- Sự kiện: `monitor/service/MonitorEventBus.java`, `monitor/service/MonitorEventListener.java`.
- Test liên quan: không có test cho package `ui`; các test hiện có nằm ở `config`, `network`, `notification`, `repository`, `service`.
