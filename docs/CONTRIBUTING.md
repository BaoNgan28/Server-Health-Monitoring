# Quy trình đóng góp bắt buộc

Tài liệu này tóm tắt quy trình làm việc của Nhóm 10 để tuân thủ yêu cầu Git/GitHub của học phần.

## 1. Định danh Git cá nhân

Mỗi thành viên tự cấu hình Git trên máy của mình:

```bash
git config --global user.name "MSSV_HoVaTen"
git config --global user.email "EMAIL_CA_NHAN"
```

Không commit hoặc push hộ thành viên khác.

## 2. Issue trước khi code

Mọi task phải có GitHub Issue trước khi bắt đầu. Mỗi Issue:

- chỉ Assign đúng 1 người;
- có Label;
- có Milestone;
- mô tả acceptance criteria.

## 3. Branch

Hai branch dài hạn:

- `main`: stable/release.
- `develop`: integration.

Mọi task branch được tạo từ `develop` với format:

```text
feature/<MSSV>-issue-<ISSUE_NUMBER>-<short-name>
```

## 4. Commit message

Chỉ dùng các loại được quy định trong học phần:

- `feat: ...`
- `fix: ...`
- `refactor: ...`
- `docs: ...`

## 5. Pull Request

PR phải:

- trỏ vào `develop` đối với task thông thường;
- mô tả đã làm gì;
- nêu ảnh hưởng đến module khác;
- đính kèm screenshot/log chạy thành công;
- có ít nhất 1 reviewer khác Approve;
- không được tự Approve.

## 6. Repository hygiene

Không commit:

- `target/`, `bin/`, `build/`;
- `.idea/`, `.vscode/`, `nbproject/private/`;
- `.jar`, `.zip`, `.rar`;
- database runtime, logs;
- `config/email-secrets.properties` hoặc credential thật.

## 7. README contribution matrix

README phải được cập nhật định kỳ với công việc đã hoàn thành và số Pull Request của từng thành viên.
