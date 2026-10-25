# Mở dự án trong Apache NetBeans

Nhóm 10 — Hệ thống Giám sát Sức khỏe Máy chủ

---

## 1. Mở dự án

1. Khởi động Apache NetBeans.
2. **File → Open Project…**
3. Chọn thư mục

   ```
   F:\Project LTM\ServerHealthMonitoring
   ```

   NetBeans hiển thị nó với biểu tượng dự án Maven, vì thư mục đó chứa `pom.xml` cha. Hãy chọn chính thư mục đó, không phải một trong các module.
4. **Open Project**.

NetBeans mở nó thành một dự án với bốn module:

```
ServerHealthMonitoring
   Modules
      monitoring-common
      agent-app
      monitor-app
      simulator-app
```

Lần đầu tiên, Maven tải các dependency — OSHI, Gson, SQLite JDBC, JFreeChart, Jakarta Mail, JUnit. Hãy đợi thanh tiến trình ở góc dưới bên phải chạy xong trước khi build. Việc này cần kết nối internet một lần; sau đó mọi thứ đã nằm trong repository cục bộ.

---

## 2. Maven

NetBeans đã kèm Maven, nên không cần cài thêm gì để build từ trong IDE. Để kiểm tra bản đang dùng: **Tools → Options → Java → Maven → Maven Home**.

Nếu bạn cũng muốn build từ command prompt mà `mvn` chưa có trong PATH, `scripts\build.bat` sẽ tìm bản kèm sẵn cho bạn.

---

## 3. Nền tảng Java

Dự án biên dịch với `--release 17`, nên bytecode tạo ra chạy trên Java 17 trở lên. Dùng JDK mới hơn để build vẫn được; JDK 22 đã được dùng cho bản build tạo ra các jar trong `release\`.

Kiểm tra trong **Tools → Options → Java → Java Platforms** rằng đã đăng ký JDK 17 trở lên.

---

## 4. Build

**Right-click `ServerHealthMonitoring` → Build with Dependencies**, hoặc **Clean and Build**.

Lệnh đó chạy `mvn clean package`, đồng thời chạy toàn bộ test suite. Nó tạo ra:

```
monitor-app\target\monitor-app-1.0.0-all.jar
agent-app\target\agent-app-1.0.0-all.jar
simulator-app\target\simulator-app-1.0.0-all.jar
```

---

## 5. Các class chính

| Module | Class chính |
|---|---|
| monitor-app | `vn.edu.hutech.group10.monitoring.monitor.MonitoringMain` |
| agent-app | `vn.edu.hutech.group10.monitoring.agent.AgentMain` |
| simulator-app | `vn.edu.hutech.group10.monitoring.simulator.SimulatorMain` |

---

## 6. Chạy từ IDE

**Điều này rất quan trọng:** các ứng dụng giải quyết `config\`, `data\` và `logs\` tương đối so với thư mục làm việc (working directory). NetBeans chạy một module với chính thư mục của module đó làm working directory, nên module chạy thẳng từ IDE sẽ tìm `agent-app\config\…` và không thấy gì.

Có hai cách xử lý.

### 6.1 Truyền rõ project root (khuyên dùng)

Chuột phải vào một module → **Properties → Run**, và đặt:

*VM Options*

```
-Dmonitoring.home=F:\Project LTM\ServerHealthMonitoring
```

*Arguments*, cho agent:

```
--config=F:\Project LTM\ServerHealthMonitoring\config\agent01.properties
```

*Arguments*, cho monitoring client:

```
--config=F:\Project LTM\ServerHealthMonitoring\config\monitor.properties
```

`monitoring.home` là thứ ghim `config`, `data` và `logs` về project root bất kể process được khởi động từ đâu.

### 6.2 Dùng các script

`scripts\run-monitor.bat` và `scripts\run-agent01.bat` đã làm đúng như trên. Build trong NetBeans, rồi chạy các script — đó cũng là cách chạy dự án trên máy hoàn toàn không có IDE.

---

## 7. Chạy test từ IDE

* Toàn bộ dự án: chuột phải `ServerHealthMonitoring` → **Test**.
* Một module: chuột phải vào nó → **Test**.
* Một class: mở nó và nhấn **Ctrl+F6**.

Các integration test mở socket thật trên giao diện loopback, trên các port do hệ điều hành chọn, nên không xung đột với monitoring client đang chạy. Chúng tạo cơ sở dữ liệu trong thư mục tạm và không bao giờ đụng tới `data\monitoring.db`.

---

## 8. Trình bày mã nguồn

Những nơi hữu ích nên mở khi demo:

| Cái gì | Ở đâu |
|---|---|
| Chuỗi chuẩn (canonical string) được ký | `monitoring-common` → `common.security.PacketCanonicalizer` |
| Cổng kiểm tra mọi gói tin phải qua | `common.security.PacketValidator` |
| Cửa sổ chống replay trượt (sliding replay window) | `common.security.ReplayProtector` |
| Framing TCP theo tiền tố độ dài | `common.protocol.MessageFraming` |
| Thu thập metric qua OSHI | `agent-app` → `agent.metrics.OshiSystemMetricsCollector` |
| Tốc độ từ các bộ đếm | `agent.metrics.NetworkRateTracker` |
| Đường nhận dữ liệu | `monitor-app` → `monitor.service.TelemetryService` |
| Máy trạng thái cảnh báo | `monitor.service.AlertService` |
| Tổng hợp | `monitor.service.AggregationService` |
| Cách mọi thứ được đấu nối | `monitor.core.MonitorApplication` |

---

## 9. Nếu có sự cố

| Triệu chứng | Cách khắc phục |
|---|---|
| Huy hiệu đỏ trên mọi file sau khi mở | Maven chưa giải quyết xong. Hãy đợi, rồi **Right-click project → Build with Dependencies**. |
| `Cannot find symbol` cho OSHI, Gson, JFreeChart | Các dependency chưa được tải. Kiểm tra kết nối internet và build lại. |
| Agent báo `Agent configuration file not found` | Working directory đang là thư mục module. Dùng mục 6.1. |
| `Address already in use` trên UDP 6000 | Một monitoring client đã đang chạy. Tắt nó, hoặc đổi `monitor.udp.port`. |
| Dự án mở thành bốn dự án riêng rẽ | Đã mở thư mục module thay vì thư mục cha. Đóng chúng và mở chính `ServerHealthMonitoring`. |

Xem thêm trong [10-xu-ly-su-co.md](10-xu-ly-su-co.md).
