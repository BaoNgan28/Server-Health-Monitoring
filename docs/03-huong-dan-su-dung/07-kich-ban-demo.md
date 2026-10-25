# Kịch bản demo

Nhóm 10 — Server Health Monitoring System — khoảng 12–15 phút

---

## Trước khi bắt đầu

Chuẩn bị trước, không làm trong lúc demo:

```bat
scripts\build.bat
```

- [ ] Build đã xong, mọi test đều pass.
- [ ] Bốn agent đã được đăng ký trong monitoring client (5001–5004,
      `GROUP10_DEMO_SECRET_01`…`_04`). Chúng được lưu lại, nên đây là việc một lần.
- [ ] Ngưỡng CPU của AGENT-01 đã về lại `85.0` — bước 9 sẽ hạ nó trực tiếp.
- [ ] `config\email-secrets.properties` có App Password thật nếu muốn demo
      phần e-mail trực tiếp. Không có nó, mọi thứ khác vẫn chạy và
      Settings sẽ báo trung thực là `NOT CONFIGURED`.
- [ ] Đã tắt thông báo Windows và mọi thứ gây ồn.
- [ ] Đã mở trình xem SQLite trên `data\monitoring.db`, hoặc sẵn một command prompt.
- [ ] Mở `docs\02-kien-truc-va-nguyen-ly\09-kien-truc-goc.md` trong cửa sổ thứ hai cho bước 1.

Các mốc thời gian bên dưới chỉ là gợi ý, không phải lời thoại để đọc nguyên văn.

---

## 1. Kiến trúc — 1:30

Cho xem sơ đồ ở đầu file `09-kien-truc-goc.md`.

Chỉ nói ba ý, không thêm:

* Các agent chạy trên máy được giám sát; một Swing client giám sát tất cả.
* **UDP cho telemetry** — nhỏ, mỗi giây một lần, mất một bản thì bản
  tiếp theo thay thế ngay, nên truyền lại cũng chẳng được gì.
* **TCP cho control** — thay đổi cấu hình không được mất đi trong im lặng, và
  acknowledgement của agent là bằng chứng nó đã tới nơi.

> "Hai giao thức, mỗi thứ làm đúng việc nó thực sự giỏi."

---

## 2. Khởi động monitoring client — 0:30

```bat
scripts\run-monitor.bat
```

Chỉ vào banner trên console: cổng UDP, đường dẫn database, thư mục log. Lưu ý
database do ứng dụng tự tạo, không phải tạo bằng tay.

---

## 3. Khởi động bốn agent — 1:00

```bat
scripts\run-local-demo.bat
```

Hoặc bốn cửa sổ riêng nếu dễ thuyết minh hơn.

Cho xem banner của một agent: id, tên, hostname, monitoring host, các cổng, chu kỳ.

> "Hãy chú ý thứ *không* có ở đây: shared secret. Nó không bao giờ bị in ra, không
> bị ghi log, không bao giờ gửi qua mạng."

Quan sát bốn dòng chuyển sang `ONLINE` và dòng tổng đọc `TOTAL 4 / ONLINE 4`.

---

## 4. Dashboard — 1:00

Để chạy vài giây. Đi qua các cột: Name, IP, CPU, RAM, Connections,
Network IN, Network OUT, Status, Last Seen — cập nhật khoảng mỗi giây một lần.

> "Đây là số liệu thật từ máy này, đọc qua OSHI. Không có gì ở đây là
> mô phỏng."

---

## 5. CPU và bộ nhớ — 0:45

Tab Server Detail, chọn AGENT-01. Cho xem CPU và RAM dưới dạng số và biểu đồ.

Mở một thứ gì nặng — trình duyệt với vài tab — rồi để khán giả xem đường
CPU phản ứng.

---

## 6. Lưu lượng mạng — 0:45

Chỉ vào Network IN và OUT.

> "Hệ điều hành chỉ cho tổng số byte kể từ khi boot. Tổng cộng không phải
> tốc độ. Agent lấy hiệu giữa hai lần đọc rồi chia cho
> khoảng thời gian giữa chúng — theo từng interface, nên một card mạng xuất hiện hay biến mất
> không thể trông như một burst lưu lượng chưa từng xảy ra."

---

## 7. Biểu đồ — 0:45

Ba biểu đồ trực tiếp: CPU, bộ nhớ, network in và out.

> "Cửa sổ giữ 120 điểm. Không phải vì 120 là con số đặc biệt, mà vì một
> ứng dụng chạy qua đêm không được tích một điểm dữ liệu mỗi
> giây mãi mãi. Đối tượng chart chỉ tạo một lần; chỉ dữ liệu thay đổi."

---

## 8. Cấu hình qua TCP — 1:30

**Phần quan trọng nhất.**

Dashboard → chọn AGENT-01 → **Connect**. Hộp thoại hiển thị OS, CPU
model, số core và bộ nhớ của agent — lấy qua TCP ngay lúc này.

Rồi **Configure** → Metric Interval `2000` → **Send CONFIG_UPDATE**.

Cho xem hộp thoại `CONFIG_ACK`: `SUCCESS`, và các giá trị hiệu lực. Chuyển sang console
AGENT-01 và cho xem `Applied metricIntervalMs=2000` và `Telemetry interval
is now 2000 ms`. Dashboard thấy rõ cập nhật thưa đi một nửa.

> "Đây không phải giao diện đổi một con số cục bộ. Client đã gửi một message,
> agent đã kiểm tra, áp dụng rồi trả lời. Những gì bạn đang xem
> chính là lời trả lời đó."

Giờ gửi một giá trị sai — Metric Interval `5`:

```
Status : ERROR
Message: metricIntervalMs must be between 200 and 3600000 ms, got 5
```

> "Agent kiểm tra mọi thứ trước khi áp dụng bất cứ gì, nên một giá trị sai
> không làm thay đổi gì cả. Và client chỉ ghi giá trị mới vào database của nó
> sau SUCCESS — nên màn hình không bao giờ hiển thị một thiết lập mà agent
> đã từ chối."

Đặt interval về lại `1000`.

---

## 9. Hạ ngưỡng CPU — 0:45

Đọc CPU hiện tại của AGENT-01 — ví dụ 20%. **Configure** → CPU Threshold `5.0` →
gửi.

---

## 10. Cảnh báo CPU — 1:00

Tab Alerts: một `CPU_HIGH`, `OPEN`, in đậm.

Chờ ba mươi giây khi metric vẫn trên ngưỡng. Refresh.

> "Vẫn chỉ một dòng. Ba mươi mẫu vượt ngưỡng mà chỉ tạo một alert,
> vì alert là một *sự cố*, không phải một phép đo. Nếu không, một giờ
> CPU cao sẽ thành 3600 alert và 3600 e-mail."

Đề cập dải hysteresis: muốn recovery thì giá trị phải quay về thấp hơn ngưỡng hai điểm
*dưới* ngưỡng, nếu không một metric nằm đúng ngay ngưỡng sẽ chập chờn đóng mở
trên các mẫu xen kẽ.

---

## 11. E-mail cảnh báo — 1:00

*Nếu e-mail đã cấu hình:* cho xem hộp thư đến —
`[WARNING] CPU HIGH - AGENT-01`, gồm agent, server, giá trị hiện tại,
ngưỡng và thời gian. Một message, không phải ba mươi.

*Nếu chưa:* Settings → Notifications, cho thấy `NOT CONFIGURED`, file chính xác cần
sửa, và hai key. Rồi bấm **Send Test Email** để hiện hộp thoại nêu tên
file đó.

> "Credential nằm ngoài jar là có chủ ý. Chỉ cần điền vào rồi khởi động lại
> là xong — không đổi source, không rebuild."

---

## 12. Dừng AGENT-02 — 0:30

Đóng cửa sổ AGENT-02. Nói sang chuyện khác trong lúc chờ timeout
chạy.

---

## 13. Phát hiện offline — 0:45

Sau 15 giây AGENT-02 chuyển `OFFLINE`, dòng tổng đọc `ONLINE 3 /
OFFLINE 1`, và một alert `AGENT_OFFLINE` xuất hiện.

> "Vòng quét chạy mỗi hai giây. Nó đã chạy nhiều lần mà vẫn chỉ có
> một alert. Một lần mất kết nối là một sự kiện, không phải một alert mỗi vòng quét."

---

## 14. E-mail offline — 0:30

`[CRITICAL] SERVER OFFLINE - AGENT-02`, kèm thời gian last-seen và lý do.

---

## 15. Khởi động lại AGENT-02 — 0:30

```bat
scripts\run-agent02.bat
```

Nó quay lại `ONLINE` trong một hai giây.

> "Đáng chú ý: agent khởi động lại sẽ đếm packet lại từ 1. Với
> kiểm tra replay ngây thơ thì trông y hệt một cuộc tấn công, và máy
> sẽ nằm offline vô lý. Mỗi lần chạy agent mang một session id, được bao phủ bởi
> chữ ký, nên lần khởi động lại mở một cửa sổ mới — trong khi bản capture từ session cũ
> vẫn bị từ chối."

---

## 16. E-mail recovery — 0:15

`[RECOVERED] SERVER ONLINE - AGENT-02`, và outage giờ hiển thị `RESOLVED`.

---

## 17. Packet giả mạo — 1:30

```bat
scripts\run-invalid-hmac-test.bat
```

Nó gửi ba packet khai CPU 100%: một cái chữ ký rác, một cái ký
bằng key sai, và một packet thật nhưng giá trị CPU bị nâng lên sau khi
ký.

Chỉ vào dashboard: **AGENT-01 vẫn hiển thị CPU thật.** Rồi tab Alerts:
`SECURITY_INVALID_HMAC`. Rồi số dòng trong `metric_raw` — không đổi dù
có ba packet kia.

> "Mọi packet UDP đều được ký bằng HmacSHA256, và không phải trên JSON thô — mà trên một
> chuỗi canonical, nên cùng một mẫu luôn tạo ra cùng dãy byte bất kể
> encoder hay locale của máy. Không gì ở phía sau bước kiểm tra này từng thấy
> packet trượt kiểm tra: không cửa sổ, không database, không alert engine."

---

## 18. Replay — 1:00

```bat
scripts\run-replay-test.bat
```

Một packet hợp lệ, cùng packet đó lặp lại hai lần nữa, rồi một packet mới. Alerts hiển thị
`SECURITY_REPLAY_PACKET`.

> "Chữ ký trên packet replay hoàn toàn hợp lệ — nó vốn là packet thật.
> Chỉ số sequence mới phân biệt bản copy với bản gốc. Client
> giữ cửa sổ trượt 64-packet cho mỗi session, vì UDP không đảm bảo
> thứ tự: nếu đòi mỗi sequence đơn thuần phải lớn hơn cái trước sẽ vứt bỏ
> packet thật chỉ đến trễ."

---

## 19. Lịch sử — 1:00

Tab History → AGENT-01 → **Last 1 Hour** → **Load**. Nhãn dưới các nút điều khiển
cho biết dữ liệu đến từ `metric_raw` hay từ `metric_5m`.

> "Panel chỉ hỏi khoảng thời gian. Nó không bao giờ viết SQL và không bao giờ biết bảng nào
> đã trả lời."

---

## 20. Tổng hợp dữ liệu — 1:30

Giải thích bài toán trước: bốn agent mỗi giây một mẫu là khoảng 345 000
dòng một ngày.

| | Production | Demo profile |
|---|---|---|
| Giữ raw | 60 minutes | 60 seconds |
| Bucket | 5 minutes | 10 seconds |
| Giữ aggregates | 30 days | 300 seconds |

> "Cùng code, khác con số. Không hề có nhánh demo nào trong đó."

Nếu còn thời gian, cho xem nó đang diễn ra với `scripts\run-monitor-demo.bat` chạy
vài phút:

```sql
SELECT COUNT(*) FROM metric_raw;
SELECT agent_id, sample_count, ROUND(avg_cpu,1), ROUND(max_cpu,1) FROM metric_5m
ORDER BY bucket_start DESC LIMIT 10;
```

> "Mỗi bucket giữ cả trung bình **và** đỉnh. Chỉ trung bình thôi sẽ che mất
> spike gây ra sự cố — mà đó thường là thứ duy nhất đáng chú ý
> trong bucket."

---

## Kết luận — 0:30

> "Bốn agent, một client, hai giao thức, packet có ký, chống replay,
> lưu trữ và tổng hợp tự động, alert mỗi sự cố chỉ bắn một lần, và
> e-mail để tới được người không nhìn màn hình. 171 test tự động,
> gồm integration test lái socket thật."

Chuẩn bị cho các câu hỏi này:

| Câu hỏi | Trả lời |
|---|---|
| Tại sao dùng UDP, nó không phải không đáng tin cậy sao? | Mất một mẫu không tốn gì; mẫu tiếp theo tới sau một giây. Control, nơi mất mát *thật sự* quan trọng, chạy trên TCP. |
| Tại sao không ký trực tiếp JSON? | Hai encoder có thể sắp xếp field hay định dạng số khác nhau. Chuỗi canonical làm chữ ký phụ thuộc vào dữ liệu, không phụ thuộc encoder. |
| Nếu đồng hồ lệch nhau thì sao? | Packet ngoài dung sai 30 giây sẽ bị từ chối. Có thể cấu hình được. |
| Có ai replay lưu lượng cũ được không? | Chỉ trong cửa sổ 30 giây đó, và chỉ packet client chưa từng thấy. Mọi thứ còn lại đều chạm cửa sổ sequence. |
| Tại sao SQLite mà không phải MySQL? | Đây là ứng dụng desktop. Không cần cài server, không cần chạy service, và file chính là toàn bộ database. |
| Mật khẩu ở đâu? | Trong `config\email-secrets.properties`, ngoài jar, git-ignored, không bao giờ ghi log, và bị che khỏi message lỗi. |
