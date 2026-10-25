# Giao thức

Nhóm 10 — Hệ thống Giám sát Sức khỏe Máy chủ (Server Health Monitoring System) — phiên bản giao thức **1.0**

Mọi thứ trên cả hai kênh truyền tải đều là JSON UTF-8. Phiên bản giao thức được so sánh bằng tuyệt đối, vì vậy packet từ bất kỳ phiên bản nào khác đều bị từ chối.

---

## 1. Cổng (Ports)

| Vai trò | Mặc định | Demo bốn agent cục bộ |
|---|---|---|
| Monitoring client, lắng nghe UDP | 6000 | 6000 |
| Agent, lắng nghe TCP điều khiển | 5000 | 5001, 5002, 5003, 5004 |

Bốn cổng TCP khác nhau chỉ tồn tại vì bản demo đặt bốn agent trên cùng một máy. Khi mỗi agent sở hữu một máy riêng, tất cả đều dùng cổng 5000.

---

## 2. Vì sao dùng hai kênh truyền tải

**UDP cho telemetry.** Một mẫu metric rất nhỏ, một giây sau lại có mẫu khác, và một mẫu bị mất sẽ trở nên vô giá trị vào thời điểm có thể truyền lại. Một kết nối cho mỗi agent sẽ tốn trạng thái và handshake mà không mang lại lợi ích.

**TCP cho điều khiển.** Một thay đổi cấu hình không được phép mất đi một cách lặng lẽ, và acknowledgement từ agent chính là bằng chứng cho thấy nó đã đến nơi và được chấp nhận.

---

## 3. Các message UDP

### 3.1 TELEMETRY

Được gửi mỗi `metric.interval.ms` (mặc định 1000).

```json
{
  "protocolVersion": "1.0",
  "type": "TELEMETRY",
  "agentId": "AGENT-01",
  "hostname": "SRV-HANOI-01",
  "sessionId": "0f0b9a61-3a3b-4a64-9a3d-1d0c2b5f7e11",
  "timestamp": 1790668331245,
  "sequence": 417,
  "cpuUsage": 23.5,
  "memoryUsage": 61.25,
  "connectionCount": 42,
  "uptimeSeconds": 143284,
  "networkInBytesPerSecond": 12345.5,
  "networkOutBytesPerSecond": 6789.25,
  "hmac": "9f2b...64 hex characters..."
}
```

| Trường | Ý nghĩa |
|---|---|
| `timestamp` | thời điểm lấy mẫu, tính bằng epoch milliseconds |
| `sequence` | tăng nghiêm ngặt trong một session |
| `sessionId` | một giá trị cho mỗi lần chạy (**run**) của agent; giá trị mới sau mỗi lần khởi động lại |
| `cpuUsage`, `memoryUsage` | phần trăm, 0..100 |
| `connectionCount` | số kết nối TCP đã thiết lập, v4 + v6 |
| `uptimeSeconds` | thời gian hoạt động (uptime) của máy |
| `network*BytesPerSecond` | thông lượng, là **tốc độ (rate)**, không phải tổng cộng dồn |
| `hmac` | HmacSHA256 trên chuỗi canonical, hex chữ thường |

### 3.2 HEARTBEAT

Được gửi mỗi `heartbeat.interval.ms` (mặc định 5000). Cùng các trường định danh, không có metric, nên rất nhẹ và vẫn tới đều ngay cả khi việc thu thập metric đang gặp khó khăn.

```json
{
  "protocolVersion": "1.0",
  "type": "HEARTBEAT",
  "agentId": "AGENT-01",
  "hostname": "SRV-HANOI-01",
  "sessionId": "0f0b9a61-3a3b-4a64-9a3d-1d0c2b5f7e11",
  "timestamp": 1790668336245,
  "sequence": 418,
  "hmac": "3c81...64 hex characters..."
}
```

Telemetry và heartbeat dùng chung một bộ đếm sequence cho mỗi agent, vì monitoring client chỉ giữ một replay window bao phủ cả hai.

Một datagram bị giới hạn ở mức 4096 byte; các message trên đều dưới 1 kB.

---

## 4. Xác thực

### 4.1 Chuỗi canonical

Chữ ký **không** được tính trên JSON thô. Hai bộ mã hóa có thể sắp xếp trường hoặc định dạng số khác nhau trong khi mô tả cùng một mẫu, và khi đó chữ ký sẽ phụ thuộc vào bộ mã hóa thay vì phụ thuộc vào dữ liệu.

Telemetry, các trường được nối bằng `|`:

```
protocolVersion | type | agentId | hostname | sessionId | timestamp | sequence |
cpuUsage | memoryUsage | connectionCount | uptimeSeconds |
networkInBytesPerSecond | networkOutBytesPerSecond
```

Heartbeat:

```
protocolVersion | type | agentId | hostname | sessionId | timestamp | sequence
```

Các quy tắc cả hai phía phải áp dụng giống hệt nhau:

* trường `hmac` **không bao giờ** là một phần của chuỗi mà nó ký;
* các số thực (double) được hiển thị bằng `Locale.ROOT` và đúng bốn chữ số thập phân, nhờ đó một máy dùng dấu phẩy thập phân tạo ra cùng dãy byte với máy dùng dấu chấm;
* `-0.0` được chuẩn hóa thành `0.0000`;
* chuỗi null trở thành rỗng, không bao giờ là chuỗi `null`;
* số double không hữu hạn (non-finite) bị từ chối thay vì được ký dưới dạng `NaN`.

Ví dụ cụ thể:

```
1.0|TELEMETRY|AGENT-01|SRV-HANOI-01|0f0b9a61-...|1790668331245|417|
23.5000|61.2500|42|143284|12345.5000|6789.2500
```

(hiển thị đã xuống dòng cho dễ đọc; trong chuỗi thực tế không có ngắt dòng).

### 4.2 Ký và kiểm tra

```
hmac = lowercase_hex( HmacSHA256( sharedSecret, canonicalString ) )
```

Bên nhận tính lại và so sánh bằng `MessageDigest.isEqual`, hàm này không để lộ có bao nhiêu ký tự đầu đã khớp.

Mỗi agent có secret **riêng**, vì vậy một secret bị lộ không thể dùng để mạo danh agent khác.

---

## 5. Thứ tự validation

Một packet được kiểm tra theo thứ tự sau, và lỗi đầu tiên sẽ dừng toàn bộ các bước còn lại:

1. **shape** — có session id, sequence không âm, các số double hữu hạn, phần trăm trong khoảng 0..100, các bộ đếm không âm;
2. **phiên bản giao thức** — phải bằng `1.0`;
3. **agent đã biết** — `agentId` chưa đăng ký thì không có secret, nên packet bị loại bỏ trước khi tính bất kỳ chữ ký nào;
4. **chữ ký** — HmacSHA256 trên chuỗi canonical, so sánh trong thời gian hằng số (constant-time);
5. **timestamp** — nằm trong `packet.timestamp.tolerance.ms` (mặc định 30 000) so với đồng hồ của bên nhận, theo cả hai chiều;
6. **replay** — xem bên dưới.

Packet lỗi ở bất kỳ bước nào sẽ không cập nhật gì cả: không dashboard, không dòng `metric_raw`, không alert. Nó được đếm, log, và ghi nhận thành security event.

---

## 6. Chống replay

Mỗi agent đánh số packet trong phạm vi một session. Monitoring client giữ, cho mỗi `(agentId, sessionId)`, sequence cao nhất đã chấp nhận cộng với một map 64-bit ghi nhận 64 packet trước đó đã thấy packet nào — đúng mô hình IPsec sử dụng.

| Trường hợp | Kết quả |
|---|---|
| sequence mới hơn mọi sequence đã thấy | chấp nhận, trượt cửa sổ tiến lên |
| sequence nằm trong cửa sổ, bit chưa bật | chấp nhận — packet chỉ đến muộn |
| sequence nằm trong cửa sổ, bit đã bật | **từ chối**, packet lặp lại |
| sequence cũ hơn cửa sổ | **từ chối** |

Dùng cửa sổ thay vì “phải lớn hơn packet trước”, vì UDP không đảm bảo thứ tự: một packet có thể vượt packet khác trên mạng, và bên nhận dàn công việc qua nhiều luồng có thể hoàn thành chúng không theo thứ tự. Đòi hỏi sequence tăng nghiêm ngặt sẽ loại bỏ oan các packet thật.

**Vì sao cửa sổ tính theo session.** Agent khởi động lại sẽ đếm lại từ 1. So với một cửa sổ duy nhất cho mỗi agent, điều này không thể phân biệt với replay, và máy vừa khởi động lại sẽ nằm OFFLINE một cách vô lý. Một session id mới mở ra cửa sổ mới trong khi session trước giữ cửa sổ riêng của nó, nhờ đó bản capture từ session cũ replay vào session mới vẫn rơi vào cửa sổ cũ, nơi bit của nó đã bật, và vẫn bị từ chối. Bốn session gần nhất của mỗi agent được ghi nhớ.

Điều này cũng ngăn hai sender gây nhiễu lẫn nhau: simulator kiểm thử dùng session id riêng, nên packet nó gửi với sequence rất lớn không thể đẩy cửa sổ của agent thật ra khỏi phạm vi.

Rủi ro còn lại của mọi cơ chế timestamp-cộng-sequence là bản capture bị replay trong ngưỡng dung sai timestamp tới một bên nhận chưa từng thấy bản gốc. Ngưỡng dung sai 30 giây chính là giới hạn của rủi ro này.

---

## 7. Đóng khung TCP (TCP framing)

Một frame gồm:

```
+--------------------------+------------------------------+
| 4 bytes, big-endian      | that many bytes of UTF-8 JSON|
| payload length           |                              |
+--------------------------+------------------------------+
```

TCP là dòng byte không có ranh giới message. Một lần `read()` có thể trả về nửa message, hoặc hai message cùng lúc, vì vậy bên đọc dùng `DataInputStream.readFully` để đọc tiếp cho đến khi đủ payload đã khai báo.

Việc đọc theo dòng cố ý **không** được dùng: payload JSON không đảm bảo không chứa ký tự xuống dòng, và một thông điệp lỗi chứa ngắt dòng sẽ tách một message thành hai và làm mất đồng bộ stream mãi mãi.

Độ dài trên 1 MiB bị từ chối thay vì cấp phát, nhờ đó tiền tố độc hại hoặc hỏng không thể làm cạn bộ nhớ. Đóng kết nối sạch giữa các frame được hiểu là hết stream, không phải lỗi.

---

## 8. Các message TCP

### 8.1 PING / PONG

```json
{"type":"PING","timestamp":1790668331245}
```
```json
{"type":"PONG","agentId":"AGENT-01","timestamp":1790668331248}
```

### 8.2 AGENT_INFO_REQUEST / AGENT_INFO_RESPONSE

```json
{"type":"AGENT_INFO_REQUEST","timestamp":1790668331245}
```
```json
{
  "type": "AGENT_INFO_RESPONSE",
  "agentId": "AGENT-01",
  "agentName": "LOCAL-AGENT-01",
  "hostname": "SRV-HANOI-01",
  "osName": "Windows",
  "osVersion": "11 build 26200",
  "cpuModel": "Intel(R) Core(TM) i7",
  "cpuLogicalCores": 12,
  "totalMemoryBytes": 17098688512,
  "uptimeSeconds": 143284,
  "metricIntervalMs": 1000,
  "heartbeatIntervalMs": 5000,
  "agentVersion": "1.0.0"
}
```

### 8.3 CONFIG_UPDATE / CONFIG_ACK

```json
{
  "type": "CONFIG_UPDATE",
  "requestId": "0a7c4f2e-9d13-4a80-9d66-7f4c1f2e33aa",
  "metricIntervalMs": 2000,
  "heartbeatIntervalMs": null,
  "cpuThreshold": 5.0,
  "memoryThreshold": null,
  "networkInThresholdMbps": null,
  "networkOutThresholdMbps": null
}
```

**Trường `null` nghĩa là “giữ nguyên thiết lập này.”** Đó là lý do mọi thiết lập đều là kiểu boxed: monitoring client có thể đẩy một threshold mà không cần nhắc lại các threshold còn lại.

Agent validate mọi trường **trước khi áp dụng bất kỳ trường nào**, nhờ đó message có một giá trị xấu sẽ để agent y nguyên như cũ thay vì cập nhật nửa vời:

| Thiết lập | Khoảng chấp nhận |
|---|---|
| `metricIntervalMs` | 200 … 3 600 000 |
| `heartbeatIntervalMs` | 1 000 … 3 600 000 |
| `cpuThreshold`, `memoryThreshold` | lớn hơn 0, tối đa 100 |
| `networkInThresholdMbps`, `networkOutThresholdMbps` | hữu hạn, không âm |

Thành công:

```json
{
  "type": "CONFIG_ACK",
  "requestId": "0a7c4f2e-9d13-4a80-9d66-7f4c1f2e33aa",
  "status": "SUCCESS",
  "message": "Applied metricIntervalMs=2000, cpuThreshold=5.0",
  "effectiveMetricIntervalMs": 2000,
  "effectiveHeartbeatIntervalMs": 5000,
  "effectiveCpuThreshold": 5.0,
  "effectiveMemoryThreshold": 85.0,
  "effectiveNetworkInThresholdMbps": 50.0,
  "effectiveNetworkOutThresholdMbps": 50.0
}
```

Từ chối:

```json
{
  "type": "CONFIG_ACK",
  "requestId": "0a7c4f2e-9d13-4a80-9d66-7f4c1f2e33aa",
  "status": "ERROR",
  "message": "metricIntervalMs must be between 200 and 3600000 ms, got 5",
  "effectiveMetricIntervalMs": 1000,
  "...": "unchanged values"
}
```

Acknowledgement luôn báo cáo những gì **đang thực sự có hiệu lực** trên agent, dù cập nhật được áp dụng hay bị từ chối. Monitoring client chỉ ghi threshold mới vào database của mình sau `SUCCESS`, nhờ đó màn hình không bao giờ hiển thị thiết lập mà agent đã từ chối.

### 8.4 ERROR

Được gửi khi một request hoàn toàn không thể hiểu được.

```json
{"type":"ERROR","agentId":"AGENT-01","timestamp":1790668331248,
 "message":"Unrecognised or malformed control message"}
```

---

## 9. Trạng thái sống (Liveness)

| Thiết lập | Mặc định |
|---|---|
| khoảng telemetry | 1 000 ms |
| khoảng heartbeat | 5 000 ms |
| timeout heartbeat | 15 000 ms |
| quét liveness | mỗi 2 000 ms |

Bất kỳ packet nào được chấp nhận — telemetry hay heartbeat — đều làm mới thời điểm last-seen của agent. Với heartbeat 5 giây và timeout 15 giây, hai heartbeat có thể mất trước khi một agent khỏe mạnh bị tuyên bố là down.

Agent không nhận được gì quá timeout sẽ chuyển `OFFLINE` và chỉ nhận đúng một event `AGENT_OFFLINE`, bất kể sweep chạy thường xuyên thế nào. Packet hợp lệ tiếp theo đưa nó về `ONLINE` và ghi nhận `AGENT_RECOVERED`. Agent chưa từng được nghe thấy vẫn ở `UNKNOWN` thay vì bị báo là sự cố mất kết nối.
