# Xử lý sự cố

Nhóm 10 — Server Health Monitoring System

Xem trước ở: `logs\monitor-0.log` trên máy monitoring client và
`logs\agent-agentNN-0.log` trên từng máy agent. Hầu hết mọi thứ bên dưới đều hiện
ở đó kèm lý do.

---

## 1. Build

### `mvn` không được nhận diện

Maven không có trong PATH. Dùng `scripts\build.bat`, script này sẽ tìm bản copy đi kèm với
NetBeans. Nếu NetBeans được cài ở chỗ khác thường, thêm đường dẫn của nó vào danh sách trong
`scripts\find-maven.bat`.

### `Fatal error compiling: invalid target release: 17`

JDK cũ hơn 17. Kiểm tra bằng `java -version` và `mvn -version`, rồi đặt
`JAVA_HOME` tới JDK 17 trở lên.

### Không tải được dependencies

Lần build đầu cần internet để tải OSHI, Gson, SQLite JDBC,
JFreeChart, Jakarta Mail và JUnit. Sau proxy thì cấu hình trong
`%USERPROFILE%\.m2\settings.xml`. Sau một lần build thành công chúng được cache
cục bộ và máy có thể offline.

### Build lỗi trong `monitor-app` với test failure

Đọc report tương ứng dưới `monitor-app\target\surefire-reports\`. Các
integration test bind socket trên cổng do hệ điều hành chọn, nên chúng không
xung đột với client đang chạy — nhưng phần mềm bảo vệ endpoint quá gắt
vẫn có thể chặn lưu lượng loopback giữa hai tiến trình Java.

---

## 2. Khởi động

### `Address already in use: bind` on UDP 6000

Một monitoring client đang chạy rồi. Tìm nó:

```powershell
Get-NetUDPEndpoint -LocalPort 6000 | Select-Object OwningProcess
Get-Process -Id <OwningProcess>
```

Đóng nó, hoặc đổi `monitor.udp.port` trong `config\monitor.properties` — rồi
`monitor.udp.port` trong file properties của mọi agent cho khớp.

### `Address already in use` trên TCP port của agent

Hai agent bị đặt trùng `agent.tcp.port`. Trên một máy chúng phải khác nhau
(5001–5004). Trên các máy riêng biệt chúng có thể cùng là 5000.

### `Agent configuration file not found`

Đường dẫn được giải quyết tương đối so với working directory. Hãy chạy từ project
root, dùng các script, hoặc truyền absolute path:

```bat
java -jar agent-app-1.0.0-all.jar --config="F:\Project LTM\ServerHealthMonitoring\config\agent01.properties"
```

### `Missing required property 'agent.id'`

`agent.id` và `security.sharedSecret` là hai thiết lập bắt buộc duy nhất; mọi
cái khác đều có giá trị mặc định. Kiểm tra lỗi gõ hay dòng bị comment.

### Monitoring client hiện hộp thoại rồi thoát

Lý do nằm trong hộp thoại và trong `logs\monitor-0.log`. Thường là cổng UDP hoặc
thư mục `data\` không ghi được.

---

## 3. Agent không bao giờ xuất hiện

Agent kẹt ở `UNKNOWN` là agent có packet không được chấp nhận. Kiểm tra
theo thứ tự này.

### Agent có đang gửi không?

Console và log của nó sẽ cho biết lúc khởi động. Các dòng `UDP send to ... failed` lặp lại
nghĩa là nó không tới được client — kiểm tra `monitor.host`.

### Datagram có tới nơi không?

Thanh trạng thái của monitoring client hiển thị `packets: N received`. Nếu `received`
không nhích trong khi agent báo vẫn đang gửi, datagram đang bị rớt
ở giữa:

* **firewall trên máy client** — nguyên nhân thường gặp nhất. Chạy
  `scripts\firewall-monitor.ps1` dưới quyền Administrator.
* sai `monitor.host` hay `monitor.udp.port` trong file của agent;
* hai máy nằm khác subnet mà không có route giữa chúng.

### Packet tới nơi nhưng bị từ chối?

`received` tăng mà `accepted` không tăng. `logs\monitor-0.log` nêu lý do.

| Log message | Nguyên nhân | Cách sửa |
|---|---|---|
| `no shared secret registered for agent X` | `agent.id` đó chưa đăng ký trong client | Add Agent, với đúng id đó |
| `signature mismatch for agent X` | shared secret khác nhau | đặt `security.sharedSecret` và secret đã đăng ký giống hệt nhau |
| `packet clock drift of N ms exceeds 30000 ms` | đồng hồ hai máy lệch nhau | bật giờ tự động trên cả hai, hoặc tăng `packet.timestamp.tolerance.ms` |
| `protocol version 0.9` | build không khớp | triển khai cùng phiên bản jar mọi nơi |
| `sequence N was already seen in session S` | replay thật, hoặc bộ mô phỏng | bình thường trong lúc test replay |

### Agent hiện `ONLINE` rồi `OFFLINE` liên tục

Packet bị mất giữa các máy, hoặc máy agent đang sleep. Kiểm tra log của agent xem có lỗi gửi không, và tắt sleep trên máy
cần giám sát.

---

## 4. Alert

### Không có alert dù metric rõ ràng đang cao

* Ngưỡng đang cao hơn giá trị. Kiểm tra trong Edit Agent.
* Alert loại đó **đang mở** cho agent đó — đây là cố ý.
  Tab Alerts khi tích `Open alerts only` sẽ hiện những gì đang mở.
* Ngưỡng `0` sẽ tắt kiểm tra đó, là cố ý.

### Alert không clear

Muốn recovery, giá trị phải rơi xuống dưới ngưỡng *trừ dải hysteresis*
— hai điểm phần trăm cho CPU và bộ nhớ, năm phần trăm ngưỡng cho
tốc độ mạng. Giá trị lơ lửng đúng ngay ngưỡng sẽ cố tình giữ mở, để
nó không chập chờn đóng mở trên các mẫu xen kẽ.

### Mọi agent OFFLINE ngay khi client khởi động

Lẽ ra không xảy ra: agent giữ `UNKNOWN` cho tới khi client thực sự
nghe được nó, chính để restart không báo outage cho máy
chưa từng liên lạc. Nếu thấy hiện tượng này, các agent thực sự không gửi —
làm theo mục 3.

### Alert bảo mật xuất hiện lúc chạy bình thường

`SECURITY_INVALID_HMAC` nghĩa là có thứ gì gửi packet mạo `agent.id` với
key sai. Trong lúc test bảo mật thì đó là bình thường. Ngoài lúc đó, thường là
một agent cũ vẫn chạy với secret cũ sau khi secret đã đăng ký
bị đổi.

---

## 5. Điều khiển qua TCP

### Connect lỗi với `Connection refused`

Agent không chạy, hoặc sai cổng. Từ máy client:

```powershell
Test-NetConnection 192.168.1.11 -Port 5000
```

`TcpTestSucceeded : False` nghĩa là không có gì lắng nghe ở đó hoặc firewall máy agent
đang chặn. Chạy `scripts\firewall-agent.ps1` dưới quyền
Administrator trên máy agent.

### Connect bị timeout thay vì bị từ chối

Firewall đang lặng lẽ vứt packet thay vì từ chối. Sửa tương tự.

### CONFIG_UPDATE trả về `ERROR`

Agent đã từ chối giá trị, và message cho biết giá trị nào và vì sao. Các khoảng cho phép
xem trong [10-giao-thuc-goc.md](../02-kien-truc-va-nguyen-ly/10-giao-thuc-goc.md#83-config_update--config_ack). Không gì
được áp dụng; agent giữ nguyên như cũ.

### Kết nối đã được, sau đó request lại lỗi

Agent đã khởi động lại, nên socket chết. Client sẽ tự reconnect
ở request tiếp theo; nếu không được, tức agent không chạy.

---

## 6. Database

### `database is locked`

Tiến trình khác đang mở file — thường là trình duyệt SQLite. Đóng nó. Client
sẽ thử lại một lúc trước khi báo lỗi.

### Database cứ lớn dần không thu nhỏ

Với profile production, dữ liệu raw chỉ được gom sau một giờ, và job
chạy mỗi năm phút. Muốn xem nó diễn ra trong vài phút, dùng
`scripts\run-monitor-demo.bat`.

### Tôi muốn bắt đầu từ database sạch

Đóng monitoring client, rồi xóa `data\monitoring.db` cùng mọi file
`-shm` và `-wal` bên cạnh. Nó được tạo lại ở lần khởi động sau — cùng với
danh sách agent rỗng, nên phải đăng ký lại các agent.

### History trống với khoảng dài

Trước lần aggregation đầu tiên chưa có bucket nào, nên khoảng ba mươi ngày không có
gì để hiện. Panel sẽ dùng dữ liệu raw khi bảng tổng hợp rỗng;
nếu cả hai đều rỗng, tức chưa thu thập gì cho giai đoạn đó.

---

## 7. E-mail

Được trình bày chi tiết trong [06-cai-dat-email.md](06-cai-dat-email.md#7-if-it-does-not-work). Tóm tắt:

| Triệu chứng | Nguyên nhân |
|---|---|
| `NOT CONFIGURED` | placeholder vẫn còn; dòng `Reason:` nêu tên key |
| `535 Username and Password not accepted` | đã dùng mật khẩu tài khoản thay vì App Password, hoặc đã gõ kèm dấu cách |
| `Couldn't connect to host` | không có internet, hoặc TCP 587 outbound bị chặn |
| Không nhận được gì, không báo lỗi | kiểm tra thư mục spam của người nhận |

Chưa cấu hình e-mail vẫn là trạng thái được hỗ trợ. Ứng dụng chạy bình thường
và alert vẫn tới cửa sổ và database.

---

## 8. Giao diện

### Cửa sổ bị treo

Lẽ ra không: mọi thao tác chậm đều chạy trong background worker. Nếu xảy ra,
ghi lại đã bấm gì và kiểm tra `logs\monitor-0.log` xem có exception lúc đó
không.

### Biểu đồ trống

Chưa có gì được chấp nhận cho agent đó. Kiểm tra cột Status, rồi
xem mục 3.

### Bảng không refresh

Cập nhật được áp dụng vài lần mỗi giây. Nếu số liệu đứng yên trong khi
bộ đếm `accepted` trên thanh trạng thái vẫn tăng, đáng để báo cáo —
kiểm tra log xem có listener exception không.

### Thiếu cột Shared Secret

Cố ý. Secret không bao giờ hiển thị. Khi sửa agent, trường này để trống,
và để trống nghĩa là giữ nguyên secret đã lưu.

---

## 9. Message vô hại có thể bỏ qua

### `Unable to locate English counter names in registry Perflib 009`

OSHI ghi một lần lúc agent khởi động trên một số bản Windows. OSHI chuyển
sang cách đọc counter khác và mọi metric vẫn hoạt động.
Không cần làm gì về nó.

### `Email notification suppressed (smtp.appPassword is still the placeholder)`

Bình thường mỗi khi alert bắn mà e-mail chưa cấu hình. Đó là hệ thống
báo rằng nó có thứ để gửi nhưng cố ý không gửi.

---

## 10. Lấy thêm chi tiết

Tạm thời tăng log level bằng cách thêm vào dòng lệnh `java`:

```
-Djava.util.logging.ConsoleHandler.level=FINE
```

Log xoay vòng ở 2 MiB, năm file mỗi ứng dụng, và ghi nối tiếp qua
các lần khởi động, nên lần chạy hôm qua thường vẫn còn.

Shared secret của agent lẫn Gmail App Password không bao giờ xuất hiện trong
file log, ở mọi level.
