# Thiết lập đa máy

Nhóm 10 — Hệ thống Giám sát Sức khỏe Máy chủ

Cách chạy hệ thống đúng như thiết kế: một monitoring client giám sát bốn agent, mỗi agent trên một máy riêng.

---

## 1. Kịch bản

| Máy | Vai trò | Địa chỉ IP |
|---|---|---|
| A | Client giám sát | 192.168.1.10 |
| B | Agent 01 | 192.168.1.11 |
| C | Agent 02 | 192.168.1.12 |
| D | Agent 03 | 192.168.1.13 |
| E | Agent 04 | 192.168.1.14 |

```
    MACHINE B            MACHINE C            MACHINE D            MACHINE E
    192.168.1.11         192.168.1.12         192.168.1.13         192.168.1.14
    AGENT-01             AGENT-02             AGENT-03             AGENT-04
    TCP 5000             TCP 5000             TCP 5000             TCP 5000
         |                    |                    |                    |
         |                    |  UDP -> 192.168.1.10:6000               |
         +--------------------+---------+----------+--------------------+
                                        |
                                        v
                              MACHINE A  192.168.1.10
                              MONITORING CLIENT
                              UDP 6000 listening
                              TCP client -> each agent:5000
```

**Mọi agent đều có thể dùng TCP 5000.** Bốn port khác nhau trong demo cục bộ chỉ tồn tại vì bốn agent chia sẻ một máy. Mỗi máy ở đây có địa chỉ riêng, nên không xung đột.

Thay bằng địa chỉ thật của bạn trong toàn bộ hướng dẫn. Tìm chúng bằng `ipconfig`.

---

## 2. Mỗi máy agent cần gì

Chỉ ba thứ. Không cần NetBeans, cũng không cần Maven hay mã nguồn.

1. **JRE hoặc JDK, phiên bản 17 trở lên.** Kiểm tra bằng `java -version`.
2. **`agent-app-1.0.0-all.jar`** — nó đã chứa mọi dependency bên trong.
3. **File properties của agent đó.**

Tạo cấu trúc này trên mỗi máy agent, ở bất kỳ đâu bạn muốn, ví dụ `C:\ServerHealthAgent`:

```
C:\ServerHealthAgent\
    agent-app-1.0.0-all.jar
    config\
        agent01.properties
    logs\
```

`logs\` được tạo tự động nếu chưa có.

---

## 3. File properties của agent

Trên máy B, `config\agent01.properties`:

```properties
agent.id=AGENT-01
agent.name=SRV-HANOI-01

# The monitoring client's address, not this machine's.
monitor.host=192.168.1.10
monitor.udp.port=6000

# This machine owns port 5000, so there is no need for a different one.
agent.tcp.port=5000

metric.interval.ms=1000
heartbeat.interval.ms=5000

# Must match exactly what is registered for this agent on the monitoring client.
security.sharedSecret=GROUP10_DEMO_SECRET_01

alert.cpu.threshold=85.0
alert.memory.threshold=85.0
alert.network.in.threshold.mbps=50.0
alert.network.out.threshold.mbps=50.0
```

Các máy C, D và E giống hệt, trừ `agent.id`, `agent.name` và `security.sharedSecret`.

Hai quy tắc:

* **`agent.id` phải duy nhất.** Đó là định danh mà monitoring client dùng để tra cứu secret đúng.
* **`security.sharedSecret` phải khớp** với giá trị đã đăng ký trên client cho id đó, từng ký tự một. Không khớp sẽ làm mọi gói tin trượt kiểm tra chữ ký, và agent sẽ không bao giờ hiện online.

Đặt secret khác nhau cho mỗi agent. Khi đó một secret bị lộ không thể dùng để mạo danh agent khác.

---

## 4. Tường lửa

Windows chặn kết nối inbound theo mặc định, nên cần hai rule. Cả hai script phải chạy từ cửa sổ PowerShell mở bằng **Run as Administrator** — bản build không bao giờ tự động đụng tới firewall.

**Trên máy monitoring client (A):**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\firewall-monitor.ps1
```

Cho phép inbound **UDP 6000**.

**Trên mỗi máy agent (B–E):**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\firewall-agent.ps1
```

Cho phép inbound **TCP 5000**.

Lưu lượng outbound không cần rule, đó là lý do các agent chỉ cần rule TCP dù chúng gửi UDP.

Cả hai script thêm rule cho các profile Domain và Private. Nếu Windows đã phân loại mạng là Public, hãy chuyển thành Private trong **Settings → Network & Internet**, hoặc truyền `-Profile Any` khi đã hiểu rõ điều đó sẽ mở ra gì.

Để gỡ rule sau này: `.\scripts\firewall-agent.ps1 -Remove`.

---

## 5. Trình tự thực hiện

### 5.1 Khởi động monitoring client (máy A)

```bat
scripts\run-monitor.bat
```

Xác nhận console báo `UDP Listen Port : 6000`.

### 5.2 Đăng ký bốn agent

Trên thẻ Dashboard, **Add Agent**, bốn lần:

| ID Agent | Tên | Host / IP | Cổng TCP | Shared Secret |
|---|---|---|---|---|
| AGENT-01 | SRV-HANOI-01 | 192.168.1.11 | 5000 | `GROUP10_DEMO_SECRET_01` |
| AGENT-02 | SRV-HANOI-02 | 192.168.1.12 | 5000 | `GROUP10_DEMO_SECRET_02` |
| AGENT-03 | SRV-HANOI-03 | 192.168.1.13 | 5000 | `GROUP10_DEMO_SECRET_03` |
| AGENT-04 | SRV-HANOI-04 | 192.168.1.14 | 5000 | `GROUP10_DEMO_SECRET_04` |

Chúng được lưu trong SQLite, nên chỉ cần làm một lần.

### 5.3 Khởi động mỗi agent

Trên mỗi máy agent:

```bat
java -jar agent-app-1.0.0-all.jar --config="config\agent01.properties"
```

Banner xác nhận định danh, monitoring host và các port. Shared secret cố tình không hiện trong đó.

### 5.4 Xác nhận

Trong vài giây tất cả bốn dòng hiện `ONLINE` kèm số liệu trực tiếp.

---

## 6. Kiểm tra mạng

**Agent có tới được client không?** Từ một máy agent:

```bat
ping 192.168.1.10
```

**Client có tới được cổng điều khiển của agent không?** Từ máy A:

```powershell
Test-NetConnection 192.168.1.11 -Port 5000
```

`TcpTestSucceeded : True` nghĩa là agent đang lắng nghe và firewall cho phép. `False` nghĩa là agent chưa chạy, sai port, hoặc thiếu rule firewall.

**UDP có thông không?** Không có gì trả lời UDP probe, nên không có lệnh tương đương. Hãy kiểm tra qua ba thứ có thay đổi:

1. `logs\agent-agent01-0.log` của agent cho thấy đang gửi mà không lỗi;
2. thanh trạng thái của monitoring client cho thấy bộ đếm đã nhận tăng lên;
3. metric xuất hiện trong cửa sổ.

Nếu agent đang gửi mà bộ đếm không nhúc nhích, các datagram đang bị rơi giữa hai bên — hầu như luôn là do firewall trên máy A.

---

## 7. Copy gì tới đâu

| File | Máy A | Các máy B–E |
|---|---|---|
| `monitor-app-1.0.0-all.jar` | có | không |
| `agent-app-1.0.0-all.jar` | không | có |
| `simulator-app-1.0.0-all.jar` | tùy chọn | không |
| `config\monitor.properties` | có | không |
| `config\email-secrets.properties` | có | không |
| `config\agentNN.properties` | không | mỗi máy một file |
| `data\` | tự tạo | không dùng |
| `logs\` | tự tạo | tự tạo |

Cơ sở dữ liệu và mọi cảnh báo chỉ nằm trên monitoring client. Các agent không giữ trạng thái nào, đó là lý do máy agent chỉ cần JRE.

---

## 8. Các sự cố thường gặp

| Triệu chứng | Nguyên nhân |
|---|---|
| Một agent mãi ở `UNKNOWN` | Các datagram không tới nơi: firewall trên máy A, sai `monitor.host`, hoặc khác subnet. |
| Agent hiện lên rồi chuyển `OFFLINE` rồi lại trở lại | Mất gói trên đường truyền, hoặc máy agent bị sleep. Kiểm tra log của agent xem có lỗi gửi không. |
| Mọi gói tin bị từ chối, log có cảnh báo bảo mật | Shared secret không khớp với giá trị đã đăng ký cho `agent.id` đó. |
| Gói tin bị từ chối kèm khiếu nại timestamp | Đồng hồ hai máy lệch nhau quá 30 giây. Bật giờ tự động trên cả hai, hoặc tăng `packet.timestamp.tolerance.ms`. |
| Connect được, Configure thất bại | Chạy `Test-NetConnection <agent-ip> -Port 5000` từ máy A; thường là do rule firewall của agent. |
| Hai agent chung một dòng | Hai máy bị đặt trùng `agent.id`. Mỗi máy phải duy nhất. |

Xem thêm trong [10-xu-ly-su-co.md](10-xu-ly-su-co.md).
