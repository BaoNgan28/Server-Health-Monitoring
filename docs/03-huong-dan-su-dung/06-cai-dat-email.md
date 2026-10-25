# Thiết lập e-mail

Nhóm 10 — Hệ thống Giám sát Sức khỏe Máy chủ

E-mail là kênh cảnh báo từ xa: là thứ tiếp cận được tới quản trị viên khi người đó không ngồi trước monitoring client.

**Hiện tại tính năng này đang tắt**, vì file bên dưới vẫn còn chứa các placeholder mặc định. Đây là trạng thái bình thường, an toàn — ứng dụng vẫn chạy hoàn toàn bình thường, cảnh báo vẫn hiện trong cửa sổ và trong cơ sở dữ liệu, và không bao giờ thử đăng nhập SMTP bằng placeholder. Chỉ cần điền hai giá trị là bật lên.

---

## 1. Những gì bạn cần

* Tài khoản Gmail gửi: **bocon2663@gmail.com**
* Một **App Password Gmail** cho tài khoản đó — 16 ký tự, không phải mật khẩu tài khoản. Google hiển thị nó thành bốn nhóm bốn ký tự; nhập **không có dấu cách**.
* Một địa chỉ nhận. Bất kỳ địa chỉ e-mail hợp lệ nào cũng được, và có thể trùng với địa chỉ gửi.

App Password yêu cầu bật Xác minh 2 bước (2-Step Verification) trên tài khoản Google. Nó được tạo tại **Google Account → Security → 2-Step Verification → App passwords**.

---

## 2. Cấu hình

Mở file này trong bất kỳ trình soạn thảo văn bản nào:

```
F:\Project LTM\ServerHealthMonitoring\config\email-secrets.properties
```

Hiện tại nội dung của nó là:

```properties
smtp.username=bocon2663@gmail.com
smtp.appPassword=PASTE_GMAIL_APP_PASSWORD_HERE
smtp.recipient=PASTE_RECIPIENT_EMAIL_HERE

smtp.host=smtp.gmail.com
smtp.port=587
smtp.starttls=true
smtp.auth=true
```

Thay đổi đúng hai dòng:

```properties
smtp.username=bocon2663@gmail.com
smtp.appPassword=abcdefghijklmnop
smtp.recipient=your.address@example.com
```

Giữ nguyên `smtp.host`, `smtp.port`, `smtp.starttls` và `smtp.auth`. Lưu file lại.

**Không thay đổi mã nguồn. Không cần build lại Maven.** File được đọc từ bên ngoài file jar lúc runtime, đó chính là lý do nó nằm trong `config\` thay vì bên trong ứng dụng đã đóng gói.

---

## 3. Bật lên

Hoặc khởi động lại monitoring client, hoặc, không cần khởi động lại:

**Settings → Reload Email Configuration**

Sau đó kiểm tra **Settings → Notifications**:

```
Email Notification Status : CONFIGURED
Sender                    : bocon2663@gmail.com
Recipient                 : your.address@example.com
SMTP                      : smtp.gmail.com:587 STARTTLS
```

---

## 4. Gửi e-mail thử nghiệm

**Settings → Send Test Email.**

Nút này gọi notifier thật qua một phiên SMTP thật. Khi thành công, một hộp thoại hiện:

```
Test email sent successfully.
```

và người nhận sẽ nhận được:

> **Subject:** `[TEST] Group 10 Server Health Monitoring`
>
> ```
> GROUP 10 - SERVER HEALTH MONITORING
>
> This is a test notification.
>
> SMTP email notification is configured successfully.
>
> Sender: bocon2663@gmail.com
> Recipient: your.address@example.com
> Time: 2026-09-29 15:04:11
> ```

Nếu thất bại, hộp thoại hiển thị lý do mà chính mail server trả về. Mật khẩu không bao giờ hiển thị, và được che khỏi thông báo trước khi tới hộp thoại hay log.

Khi e-mail vẫn chưa được cấu hình, nút này không thử đăng nhập. Nó hiển thị hộp thoại nêu tên file cần sửa và hai key cần thiết lập.

---

## 5. Những gì được gửi qua e-mail

| Sự kiện | Tiêu đề |
|---|---|
| CPU vượt ngưỡng | `[WARNING] CPU HIGH - AGENT-01` |
| Bộ nhớ vượt ngưỡng | `[WARNING] MEMORY HIGH - AGENT-01` |
| Mạng vượt ngưỡng | `[WARNING] NETWORK TRAFFIC HIGH - AGENT-01` |
| Agent ngừng phản hồi | `[CRITICAL] SERVER OFFLINE - AGENT-02` |
| Agent đã trở lại | `[RECOVERED] SERVER ONLINE - AGENT-02` |
| Nút test | `[TEST] Group 10 Server Health Monitoring` |

Nội dung cảnh báo CPU:

```
GROUP 10 - SERVER HEALTH MONITORING

Alert Type: CPU HIGH
Severity: WARNING

Agent:
AGENT-01

Server:
LOCAL-AGENT-01

Current CPU:
25.4 %

Threshold:
5.0 %

Detected At:
2026-09-29 15:04:11
```

Nội dung khi offline:

```
GROUP 10 - SERVER HEALTH MONITORING

CRITICAL ALERT

Agent:
AGENT-02

Server:
LOCAL-AGENT-02

Status:
OFFLINE

Reason:
No valid heartbeat received for more than 15 seconds.

Last Seen:
2026-09-29 15:03:47

Detected At:
2026-09-29 15:04:05
```

**Một e-mail cho mỗi sự cố, không phải mỗi mẫu.** CPU vượt ngưỡng liên tục trong một giờ chỉ tạo một thông điệp, không phải 3600. Thông điệp mới chỉ được gửi sau khi điều kiện đã hết và lại phát sinh.

**Các gói tin giả mạo không bao giờ được gửi e-mail.** `SECURITY_INVALID_HMAC` và `SECURITY_REPLAY_PACKET` được ghi trong bảng cảnh báo và log, nhưng một loạt gói tin giả mạo không được biến thành một loạt mail.

---

## 6. Dùng biến môi trường thay thế

Biến môi trường được ưu tiên hơn file, phù hợp với triển khai thực tế nơi mật khẩu không nên nằm trên đĩa:

```bat
set SMTP_USERNAME=bocon2663@gmail.com
set SMTP_APP_PASSWORD=abcdefghijklmnop
set ALERT_RECIPIENT_EMAIL=your.address@example.com
```

Khởi động monitoring client từ chính cửa sổ lệnh đó. Bất kỳ biến nào không được đặt sẽ dùng giá trị trong file, vì vậy có thể kết hợp cả hai.

---

## 7. Nếu không hoạt động

| Triệu chứng | Nguyên nhân và cách khắc phục |
|---|---|
| Trạng thái vẫn `NOT CONFIGURED` | Còn placeholder, hoặc giá trị không phải địa chỉ e-mail hợp lệ. Dòng `Reason:` trong Settings nêu key vi phạm. |
| `535 Username and Password not accepted` | Đã dùng mật khẩu tài khoản thay vì App Password, hoặc App Password nhập kèm dấu cách. Xóa dấu cách. |
| `Couldn't connect to host, port: smtp.gmail.com, 587` | Không có internet, hoặc firewall hay proxy chặn outbound TCP 587. |
| `javax.net.ssl` handshake errors | Đồng hồ hệ thống sai lệch nghiêm trọng, hoặc TLS bị can thiệp trên đường mạng. |
| Không có gì tới nơi, không báo lỗi | Kiểm tra thư mục spam của người nhận; Gmail có thể xếp thông điệp đầu tiên từ người gửi mới vào đó. |

Mọi lần thử đều được ghi log trong `logs\monitor-0.log`, kèm lý do của chính server và không bao giờ kèm mật khẩu.

---

## 8. Ghi chú bảo mật

* `config/email-secrets.properties` đã có trong `.gitignore`, nên App Password thật không thể bị commit nhầm. Template được theo dõi là `email-secrets.properties.example`.
* Mật khẩu không bao giờ hard-code trong mã nguồn Java, không bao giờ ghi vào log, không bao giờ hiển thị trong GUI, và không bao giờ nằm trong thông điệp exception — nó được thay bằng `***` trước khi hiển thị bất kỳ lý do lỗi nào.
* Việc gửi chạy trên một background thread sau một hàng đợi giới hạn (bounded queue), nên mail server ngừng phản hồi không thể chặn UDP receiver, Swing event dispatch thread hay heartbeat timer, và không thể làm bộ nhớ tăng.
* Thu hồi App Password bất cứ lúc nào từ cùng trang Google Account; monitoring client sau đó sẽ báo lỗi và vẫn tiếp tục chạy.
