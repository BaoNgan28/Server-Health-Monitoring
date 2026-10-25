# Cài đặt và build

Group 10 — Hệ thống giám sát server từ xa (Server Health Monitoring System)

Tài liệu tổng quan tiếng Việt cho việc cài đặt môi trường và build (biên dịch) dự
án. Nội dung lấy từ `README.md` (mục 4–7), `scripts\build.bat`,
`scripts\find-maven.bat` và cấu trúc thư mục thật trên đĩa. Các bước chi tiết
hơn nằm trong các tài liệu riêng, được liên kết ở từng phần.

Mục lục:

1. [Yêu cầu hệ thống](#1-yêu-cầu-hệ-thống)
2. [Cách build](#2-cách-build)
3. [Cách tìm Maven](#3-cách-tìm-maven-find-mavenbat)
4. [Cấu trúc jar sinh ra](#4-cấu-trúc-jar-sinh-ra)
5. [Mở và chạy trên NetBeans](#5-mở-và-chạy-trên-netbeans-tóm-tắt)
6. [Các thư mục sinh ra: `target\`, `release\`](#6-các-thư-mục-sinh-ra-target-release)
7. [Gỡ bỏ](#7-gỡ-bỏ)

---

## 1. Yêu cầu hệ thống

| Thành phần | Yêu cầu | Ghi chú |
|---|---|---|
| Hệ điều hành | Windows 10 hoặc Windows 11 | Dự án được phát triển và kiểm thử trên Windows 11 |
| JDK | JDK 17 trở lên | Dự án biên dịch bằng JDK 22, nhưng bytecode (đầu ra) nhắm mục tiêu `--release 17`, nên chạy được trên Java 17+ |
| Maven | Apache Maven 3.8 trở lên, **hoặc** bản Maven kèm Apache NetBeans | Không bắt buộc cài riêng: `scripts\build.bat` tự tìm bản kèm NetBeans (xem mục 3) |
| Mạng (lần build đầu) | Có Internet | Lần đầu Maven phải tải OSHI, Gson, SQLite JDBC, JFreeChart, Jakarta Mail, JUnit; các lần sau dùng kho lưu trữ cục bộ |
| Máy chạy Agent (kịch bản đa máy) | Chỉ cần JRE/JDK 17+, file `agent-app-1.0.0-all.jar` và file `.properties` tương ứng | Không cần NetBeans, không cần Maven, không cần source |

Kiểm tra môi trường:

```bat
java -version
mvn -version
```

Nếu `mvn` không có trên PATH, cứ dùng `scripts\build.bat` (xem mục 3).

---

## 2. Cách build

### 2.1 Cách chuẩn bị (khuyến nghị)

Từ bất kỳ thư mục nào, chạy:

```bat
<thư mục-gốc-dự-án>\scripts\build.bat
```

Việc `build.bat` tự làm:

1. `cd /d "%~dp0.."` — quay về thư mục gốc dự án (thư mục cha của `scripts\`),
   nên chạy được từ mọi vị trí, kể cả đường dẫn có khoảng trắng.
2. Gọi `scripts\find-maven.bat` để lấy lệnh Maven vào biến `MVN_CMD`; nếu không
   tìm thấy thì dừng với mã lỗi 1.
3. Chạy `%MVN_CMD% clean package` (xóa thư mục `target\` rồi biên dịch lại và
   đóng gói toàn bộ module).
4. In `BUILD SUCCESS` kèm danh sách 3 file jar, hoặc `BUILD FAILED` kèm mã lỗi;
   `pause` để cửa sổ không đóng ngay; trả lại mã lỗi thật cho tiến trình gọi.

### 2.2 Cách thủ công

Từ thư mục gốc dự án (nơi có `pom.xml` của cha):

```bat
mvn clean package
```

`mvn clean package` cũng chạy toàn bộ test (mục 19 của `README.md`); muốn build
nhanh, bỏ qua test bằng `mvn clean package -DskipTests` (lệnh Maven tiêu chuẩn,
không do dự án đặt riêng).

### 2.3 Cách build trên NetBeans

Chuột phải dự án → **Build with Dependencies** hoặc **Clean and Build**. Chi tiết
xem mục 5 và [`04-huong-dan-netbeans.md`](04-huong-dan-netbeans.md).

---

## 3. Cách tìm Maven (`find-maven.bat`)

Máy chỉ cài NetBeans thường không có `mvn` trên PATH. `scripts\find-maven.bat`
giải quyết điều này. Script **không chạy trực tiếp** — nó được `build.bat` và
`test.bat` gọi, và ghi kết quả vào biến `MVN_CMD`.

Thứ tự tìm, dừng ở bước đầu tìm thấy:

1. `where mvn` — nếu có `mvn` trên PATH thì dùng lệnh `mvn`.
2. Các đường dẫn Maven kèm NetBeans, theo thứ tự:

   ```text
   C:\Program Files\NetBeans-22\netbeans\java\maven\bin\mvn.cmd
   C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd
   C:\Program Files\NetBeans-20\netbeans\java\maven\bin\mvn.cmd
   C:\Program Files\Apache NetBeans\netbeans\java\maven\bin\mvn.cmd
   ```

3. Không tìm thấy ở đâu: in

   ```text
   [ERROR] Maven could not be found.
           Install Apache Maven and put it on PATH, or edit scripts\find-maven.bat
           to point at the Maven bundled with your NetBeans installation.
   ```

   → cài Maven và đưa vào PATH, **hoặc** mở `scripts\find-maven.bat` thêm đường
   dẫn Maven của bản NetBeans đang dùng (ví dụ bản NetBeans cài ở ổ D:).

Để biết bản Maven mà NetBeans đang dùng: **Tools → Options → Java → Maven →
Maven Home** (xem [`04-huong-dan-netbeans.md`](04-huong-dan-netbeans.md)).

---

## 4. Cấu trúc jar sinh ra

Sau khi build thành công:

```text
monitor-app\target\monitor-app-1.0.0-all.jar
agent-app\target\agent-app-1.0.0-all.jar
simulator-app\target\simulator-app-1.0.0-all.jar
```

| File jar | Vai trò | Cách chạy |
|---|---|---|
| `monitor-app-1.0.0-all.jar` | Monitoring Client (giao diện Swing giám sát) | `scripts\run-monitor.bat` |
| `agent-app-1.0.0-all.jar` | Agent chạy trên máy cần giám sát | `scripts\run-agent01.bat` … `run-agent04.bat` |
| `simulator-app-1.0.0-all.jar` | Trình tạo lưu lượng/kiểm thử bảo mật | `scripts\run-invalid-hmac-test.bat`, `run-replay-test.bat`, `run-load-test.bat` |

Điểm quan trọng:

* Các jar hậu tố **`-all` là tự chứa (self-contained)**: mỗi file mang theo toàn
  bộ phụ thuộc bên trong, nên máy chạy agent **chỉ cần một JRE** là đủ.
* Trong `target\` cũng có jar thường (ví dụ
  `agent-app\target\agent-app-1.0.0.jar`, không hậu tố `-all`) — jar đó **không**
  tự chứa phụ thuộc, đừng dùng để triển khai.
* Chạy jar (kiểu `java -jar … --config=…`): xem mục 1 của
  [`02-huong-dan-su-dung.md`](02-huong-dan-su-dung.md).

---

## 5. Mở và chạy trên NetBeans (tóm tắt)

Các bước từng dòng nằm trong [`04-huong-dan-netbeans.md`](04-huong-dan-netbeans.md); dưới đây
chỉ là phần khung:

1. **File → Open Project…** → chọn **thư mục gốc dự án** (thư mục chứa
   `pom.xml` của cha), không chọn thư mục con của module. Dự án mở ra với 4
   module: `monitoring-common`, `agent-app`, `monitor-app`, `simulator-app`.
2. Chờ Maven tải phụ thuộc xong (thanh tiến độ góc dưới bên phải) — lần đầu cần
   Internet.
3. Kiểm tra **Tools → Options → Java → Java Platforms** có JDK 17 trở lên.
4. Build: chuột phải dự án → **Build with Dependencies** / **Clean and Build**.
5. Main class của từng module:

   | Module | Main class |
   |---|---|
   | monitor-app | `vn.edu.hutech.group10.monitoring.monitor.MonitoringMain` |
   | agent-app | `vn.edu.hutech.group10.monitoring.agent.AgentMain` |
   | simulator-app | `vn.edu.hutech.group10.monitoring.simulator.SimulatorMain` |

6. **Quan trọng — thư mục làm việc (working directory):** ứng dụng dò `config\`,
   `data\`, `logs\` theo thư mục làm việc, trong khi NetBeans chạy module với
   thư mục của chính module đó (ví dụ `agent-app\`) → sẽ báo
   `Agent configuration file not found`. Khắc phục: **Properties → Run** của
   module, đặt *VM Options*:

   ```text
   -Dmonitoring.home=<thư mục gốc dự án>
   ```

   và *Arguments* cho agent:

   ```text
   --config=<thư mục gốc>\config\agent01.properties
   ```

   cho monitoring client:

   ```text
   --config=<thư mục gốc>\config\monitor.properties
   ```

   Cách thứ hai, đơn giản hơn: build trên NetBeans rồi chạy bằng script
   (`scripts\run-monitor.bat`, `scripts\run-agent01.bat`…) — các script này đã
   tự đặt `-Dmonitoring.home`.

7. Chạy test: chuột phải dự án → **Test**; một class → mở class đó và **Ctrl+F6**.
   Test tích hợp mở socket thật trên loopback với do hệ điều chọn, nên không đụng
   port của monitoring client đang chạy, và dùng database trong thư mục tạm.

Gợi ý mở code khi trình bày và bảng lỗi thường gặp: xem mục 8–9 của
[`04-huong-dan-netbeans.md`](04-huong-dan-netbeans.md).

---

## 6. Các thư mục sinh ra: `target\`, `release\`

### 6.1 `target\` — đầu ra biên dịch (mỗi module)

| Đường dẫn (ví dụ của `agent-app`) | Nội dung |
|---|---|
| `agent-app\target\classes\` | file `.class` đã biên dịch |
| `agent-app\target\test-classes\` | file `.class` của test |
| `agent-app\target\agent-app-1.0.0-all.jar` | jar tự chứa — dùng để triển khai |
| `agent-app\target\agent-app-1.0.0.jar` | jar thường, không tự chứa |
| `agent-app\target\surefire-reports\` | báo cáo chi tiết từng test (JUnit/Surefire) |
| `agent-app\target\maven-status\`, `generated-sources\`, `generated-test-sources\`, `maven-archiver\` | dữ liệu trung gian của Maven |

`target\` nằm trong `.gitignore` và **an toàn để xóa**: chạy lại
`scripts\build.bat` (hoặc `mvn clean package`) là dựng lại từ đầu.

### 6.2 `release\` — bản đóng gói

Trên đĩa hiện có:

```text
release\
  ServerHealthMonitoring-v1.0.0\
    agent-app-1.0.0-all.jar
    monitor-app-1.0.0-all.jar
    simulator-app-1.0.0-all.jar
    README.md
    config\   (monitor.properties, monitor-demo.properties, agent01..04.properties, email-secrets.properties[…])
    data\     docs\   logs\   scripts\
  ServerHealthMonitoring-v1.0.0.zip
```

* Đây là bản sao “mang đi được”: 3 jar ở thư mục gốc, kèm `config\`, `scripts\`,
  `docs\`, `README.md`; `data\` và `logs\` sẽ được ứng dụng tự tạo khi chạy.
* `release/*.zip` bị loại khỏi git (`.gitignore`).
* **Cách sinh ra thư mục `release\` không nằm trong `scripts\`** — chưa xác định
  (không có script đóng gói nào trong dự án).

### 6.3 Sơ đồ thư mục gốc (tóm tắt)

```text
ServerHealthMonitoring\
  pom.xml                 pom cha, khai báo phiên bản phụ thuộc
  README.md
  config\                 cấu hình ngoài, sửa không cần build lại
  data\                   database SQLite (tự tạo lần đầu)
  logs\                   file log xoay
  docs\                   tài liệu
  scripts\                build, test, run, firewall
  release\                bản đóng gói
  monitoring-common\      model, protocol, security, utility
  agent-app\              agent
  monitor-app\            monitoring client
  simulator-app\          trình tạo lưu lượng kiểm thử
```

Chi tiết: [`../02-kien-truc-va-nguyen-ly/01-cau-truc-du-an.md`](../02-kien-truc-va-nguyen-ly/01-cau-truc-du-an.md).

---

## 7. Gỡ bỏ

Không có trình gỡ cài đặt (uninstaller) — đây là ứng dụng Java portable, gỡ bằng
cách xóa:

1. Đóng monitoring client và mọi cửa sổ agent.
2. Xóa các thư mục `target\` của 4 module, hoặc chạy `mvn clean` từ thư mục gốc.
3. Xóa thư mục cài đặt (hoặc `release\ServerHealthMonitoring-v1.0.0\`) nếu không
   dùng nữa.
4. Dữ liệu và log (nếu muốn xóa hẳn): `data\monitoring.db` (và
   `data\monitoring-demo.db`, `-shm`, `-wal`) cùng `logs\*.log`. Xóa database
   nghĩa là mất danh sách agent đã đăng ký — phải **Add Agent** lại.

Cảnh báo: `config\email-secrets.properties` chứa credential (mật khẩu ứng dụng
Gmail). Nếu chia sẻ lại thư mục dự án, xóa hoặc đổi mật khẩu đó trước;
file này đã nằm trong `.gitignore` nên không bị commit.

---

Tài liệu liên quan:

* [`02-huong-dan-su-dung.md`](02-huong-dan-su-dung.md) — chạy monitor, agent, demo
* [`03-cau-hinh-he-thong.md`](03-cau-hinh-he-thong.md) — nghĩa từng khóa cấu hình
* [`04-huong-dan-netbeans.md`](04-huong-dan-netbeans.md) — thao tác NetBeans từng bước
* [`../06-huong-dan-script/01-cac-file-bat.md`](../06-huong-dan-script/01-cac-file-bat.md) — mô tả từng script
