# Chạy dự án thủ công — không cần double-click file `.bat`

Tài liệu này dành cho ai muốn **gõ lệnh trực tiếp** thay vì bấm các file trong
`scripts\`. Nội dung bên dưới **trùng khớp từng chữ** với lệnh mà các file `.bat`
đang chạy — chỉ khác là bạn gõ thay vì script tự gõ giúp.

> Quy tắc duy nhất cần nhớ: **mọi lệnh đều gõ ở thư mục gốc dự án**
> (thư mục chứa `pom.xml`).

---

## 0. Chuẩn bị (kiểm tra 1 lần)

Mở Command Prompt (nhấn `Win + R` → gõ `cmd` → Enter) và gõ 2 lệnh sau:

```bat
java -version
mvn -v
```

* Thấy dòng `java version "17"` (hoặc cao hơn) → OK.
* Thấy dòng `Apache Maven ...` → OK.
* Nếu lệnh nào báo `'xxx' is not recognized` → xem mục [Lỗi thường gặp](#lỗi-thường-gặp).

**Tiếp theo, chuyển vào thư mục dự án** (đổi đường dẫn nếu máy bạn để chỗ khác):

```bat
cd /d "C:\Users\gilgames\OneDrive\Desktop\ServerHealthMonitoring"
```

Mọi lệnh trong tài liệu này đều chạy sau khi đã `cd` vào đây.

---

## 1. Xem trước: hệ thống gồm mấy "hộp"

```
   [Agent 01]   [Agent 02]   [Agent 03]   [Agent 04]     ← 4 chương trình console
        \             |            |            /
         \____ UDP cổng 6000 (gửi chỉ số mỗi giây) ___/
                              ↓
                    [Monitoring Client]                  ← 1 chương trình giao diện (Swing)
                              ↓
                     SQLite  data\monitoring.db
```

Nghĩa là bạn sẽ mở **nhiều cửa sổ cmd**:

* **1 cửa sổ** cho Monitoring Client
* **4 cửa sổ** cho 4 Agent (hoặc ít hơn — chạy thử 1 agent cũng được)

Và thứ tự **bắt buộc**: **build → client → agent**.

---

## 2. Bước 1 — Build (chỉ cần làm 1 lần hoặc khi sửa code)

```bat
mvn clean package
```

* Mất khoảng 30 giây đến vài phút lần đầu (Maven phải tải thư viện).
* Thấy dòng `BUILD SUCCESS` là xong.

**Kết quả:** 3 file `.jar` xuất hiện trong các thư mục `target\`:

| File jar | Dùng để làm gì |
|---|---|
| `monitor-app\target\monitor-app-1.0.0-all.jar` | Chạy Monitoring Client |
| `agent-app\target\agent-app-1.0.0-all.jar` | Chạy Agent |
| `simulator-app\target\simulator-app-1.0.0-all.jar` | Tạo lưu lượng kiểm thử (không cần cho chạy thường) |

> File `-all.jar` là bản "gói đủ đồ" — máy khác chỉ cần có Java là chạy được,
> không cần cài Maven.

---

## 3. Bước 2 — Mở Monitoring Client (cửa sổ cmd thứ 1)

```bat
java -Dmonitoring.home="%CD%" -jar monitor-app\target\monitor-app-1.0.0-all.jar --config="config\monitor.properties"
```

**Giải thích 1 dòng mỗi phần:**

| Phần | Ý nghĩa |
|---|---|
| `-Dmonitoring.home="%CD%"` | Chỉ cho chương trình biết "thư mục gốc dự án là đây", để nó tìm đúng `config\`, `data\`, `logs\` dù bạn mở từ đâu. |
| `-jar ...all.jar` | Chạy file jar đã build ở bước 1. |
| `--config="config\monitor.properties"` | Dùng hồ sơ cấu hình production. |

**Khi nào chạy xong bước này:**

* Cửa sổ giao diện Swing mở ra → **chờ khoảng 6 giây** rồi mới sang bước 3
  (client cần kịp mở cổng UDP 6000 trước khi agent gửi dữ liệu vào).
* Lần đầu tiên, file `data\monitoring.db` được tự tạo — không cần chuẩn bị gì.

**Muốn chạy bản demo** (dữ liệu được nén để thấy biểu đồ tổng hợp chỉ sau vài
phút, dùng database riêng `monitoring-demo.db`): đổi cuối lệnh thành

```bat
--config="config\monitor-demo.properties"
```

⚠️ Không chạy 2 bản cùng lúc — cả hai đều "nghe" cổng 6000.

---

## 4. Bước 3 — Mở các Agent (mỗi agent 1 cửa sổ cmd mới)

**Agent 01** — mở cmd thứ 2, gõ (nhớ `cd` vào thư mục gốc trước):

```bat
cd /d "C:\Users\gilgames\OneDrive\Desktop\ServerHealthMonitoring"
java -Dmonitoring.home="%CD%" -jar agent-app\target\agent-app-1.0.0-all.jar --config="config\agent01.properties"
```

**Agent 02** — mở cmd thứ 3:

```bat
cd /d "C:\Users\gilgames\OneDrive\Desktop\ServerHealthMonitoring"
java -Dmonitoring.home="%CD%" -jar agent-app\target\agent-app-1.0.0-all.jar --config="config\agent02.properties"
```

**Agent 03** — mở cmd thứ 4:

```bat
cd /d "C:\Users\gilgames\OneDrive\Desktop\ServerHealthMonitoring"
java -Dmonitoring.home="%CD%" -jar agent-app\target\agent-app-1.0.0-all.jar --config="config\agent03.properties"
```

**Agent 04** — mở cmd thứ 5:

```bat
cd /d "C:\Users\gilgames\OneDrive\Desktop\ServerHealthMonitoring"
java -Dmonitoring.home="%CD%" -jar agent-app\target\agent-app-1.0.0-all.jar --config="config\agent04.properties"
```

* Mỗi lệnh chỉ khác nhau ở chỗ `agent0N.properties`.
* Console của agent sẽ in ra định danh, địa chỉ, chu kỳ gửi — **không bao giờ**
  in ra secret.
* Chạy thử nhanh thì **1 agent là đủ**, không cần mở đủ 4.

---

## 5. Bước 4 — Đăng ký agent trên giao diện

Agent **tự gửi** dữ liệu, nhưng client chỉ hiển thị những agent nào được **đăng
ký**. Vào tab **Dashboard** → nút **Add Agent** và nhập:

| Tên agent | Host | Port | Secret |
|---|---|---|---|
| AGENT-01 | `127.0.0.1` | `5001` | `GROUP10_DEMO_SECRET_01` |
| AGENT-02 | `127.0.0.1` | `5002` | `GROUP10_DEMO_SECRET_02` |
| AGENT-03 | `127.0.0.1` | `5003` | `GROUP10_DEMO_SECRET_03` |
| AGENT-04 | `127.0.0.1` | `5004` | `GROUP10_DEMO_SECRET_04` |

* 4 port khác nhau là vì 4 agent chung 1 máy; nếu chạy rải 4 máy thì đều là `5000`.
* Secret phải **trùng khớp** với secret trong file config tương ứng, sai là client
  từ chối gói tin (và báo cảnh báo bảo mật).
* Thông tin này được lưu vào SQLite → **lần sau mở lại không phải nhập lại**.

**Thế là xong.** Chờ khoảng 10 giây, Dashboard sẽ hiện số liệu chạy.

---

## 6. Bảng tra nhanh: file `.bat` ↔ lệnh gõ tay

| Nếu không chạy file này... | ...thay bằng lệnh này (tại thư mục gốc) |
|---|---|
| `scripts\build.bat` | `mvn clean package` |
| `scripts\test.bat` | `mvn clean test` |
| `scripts\run-monitor.bat` | `java -Dmonitoring.home="%CD%" -jar monitor-app\target\monitor-app-1.0.0-all.jar --config="config\monitor.properties"` |
| `scripts\run-monitor-demo.bat` | `java -Dmonitoring.home="%CD%" -jar monitor-app\target\monitor-app-1.0.0-all.jar --config="config\monitor-demo.properties"` |
| `scripts\run-agent01.bat` | `java -Dmonitoring.home="%CD%" -jar agent-app\target\agent-app-1.0.0-all.jar --config="config\agent01.properties"` |
| `scripts\run-agent02..04.bat` | Giống trên, đổi `agent01.properties` → `agent02/03/04.properties` |
| `scripts\run-local-demo.bat` | Tự mở 5 cửa sổ theo đúng thứ tự ở trên (bạn tự mở từng cửa sổ) |
| `scripts\find-maven.bat` | Không cần — bạn đã có `mvn` trong PATH |
| `scripts\run-invalid-hmac-test.bat` | `java -Dmonitoring.home="%CD%" -jar simulator-app\target\simulator-app-1.0.0-all.jar invalid-hmac --host=127.0.0.1 --port=6000 --agent=AGENT-01` |
| `scripts\run-replay-test.bat` | `java -Dmonitoring.home="%CD%" -jar simulator-app\target\simulator-app-1.0.0-all.jar replay --host=127.0.0.1 --port=6000 --agent=AGENT-01 --secret=GROUP10_DEMO_SECRET_01` |
| `scripts\run-load-test.bat` | `java -Dmonitoring.home="%CD%" -jar simulator-app\target\simulator-app-1.0.0-all.jar load --host=127.0.0.1 --port=6000 --agents=20 --duration=60` |

Ba lệnh nhóm kiểm thử ở cuối đều cần Monitoring Client **đang chạy**.

---

## 7. Dừng chương trình

* Monitoring Client: đóng cửa sổ giao diện (dấu `X`).
* Agent: đóng cửa sổ console của nó.
* Chạy tay thì **không có `pause`** — đóng cửa sổ là tiến trình tự chết.

---

## 8. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|
| `'mvn' is not recognized` | Maven không có trong PATH | Chạy `scripts\build.bat` (script tự tìm Maven kèm NetBeans), hoặc cài Maven + đưa lên PATH |
| `'java' is not recognized` | Chưa cài JDK / chưa thêm PATH | Cài JDK 17+, chọn "Add to PATH" khi cài |
| `Unable to access jarfile ...` | Chưa build hoặc sai thư mục | Chạy `mvn clean package` và chắc chắn đang ở thư mục gốc |
| Client báo `Port 6000 already in use` | Đã có bản client/demo đang chạy khác | Đóng bản cũ, hoặc đổi port trong `config\*.properties` |
| Agent chạy mà Dashboard không hiện số liệu | Chưa đăng ký agent, hoặc sai secret | Đăng ký lại đúng port + secret ở Bước 4 |
| Dashboard hiện `OFFLINE` | Client hoặc agent đã bị tắt, hoặc quá 15 giây không nhận gói | Kiểm tra cả 5 cửa sổ còn đang mở không |
| Build báo lỗi `release version 17 not supported` | JDK quá cũ | Cài JDK 17 trở lên |

Chi tiết: [`10-xu-ly-su-co.md`](10-xu-ly-su-co.md).

---

## 9. Tóm tắt trong 5 dòng

```
1. cd /d "C:\Users\gilgames\OneDrive\Desktop\ServerHealthMonitoring"
2. mvn clean package                              ← build 1 lần
3. java ... monitor-app-1.0.0-all.jar ...monitor.properties   ← mở client, chờ 6 giây
4. java ... agent-app-1.0.0-all.jar ...agent01.properties      ← mở agent (cửa sổ mới)
5. Vào Dashboard → Add Agent → 127.0.0.1 : 5001 + secret
```

---

## 10. Tài liệu liên quan

* Chạy bằng file script: [`../06-huong-dan-script/01-cac-file-bat.md`](../06-huong-dan-script/01-cac-file-bat.md)
* Cài đặt & build chi tiết: [`01-cai-dat-va-build.md`](01-cai-dat-va-build.md)
* Hướng dẫn thao tác giao diện: [`02-huong-dan-su-dung.md`](02-huong-dan-su-dung.md)
* Giải thích từng file cấu hình: [`03-cau-hinh-he-thong.md`](03-cau-hinh-he-thong.md)
