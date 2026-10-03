# Remote Server Health Monitoring System

Course project for **Computer Network Programming** — Group 10, class **23DTHA5**.

## Project modules

- `monitoring-common` — shared message models, protocol, security and utilities.
- `agent-app` — Agent runtime installed on monitored machines.
- `monitor-app` — central Monitoring Client and Swing GUI.
- `simulator-app` — security/load simulators used for testing.

## Git workflow

- `main` — stable milestone/release branch.
- `develop` — protected integration branch used as the base for all task branches.
- Every task must have a GitHub Issue before code is changed.
- Task branch format required by the course: `feature/<MSSV>-issue-<ISSUE_NUMBER>-<short-name>`.
- Every Pull Request requires at least one peer review and evidence that the code ran successfully.

## Team contribution matrix

| MSSV | Họ và tên | Công việc đã thực hiện | Merge Requests / Pull Requests |
|---|---|---|---|
| 2380601424 | Nguyễn Thanh Bảo Ngân | Repository Owner / Agent Backend | Cập nhật sau từng tuần |
| 2380601465 | Lê Huỳnh Ngọc | Network / Protocol / Security | Cập nhật sau từng tuần |
| 2182310415 | Bùi Tấn Quốc Đạt | Database / Aggregation / Alert / Email | Cập nhật sau từng tuần |
| 2380613311 | Nguyễn Phương Nam | Monitor Integration / Dashboard | Cập nhật sau từng tuần |
| 2380601581 | Trương Thị Yến Nhi | Visualization / History / UI | Cập nhật sau từng tuần |

## Security

Never commit `config/email-secrets.properties` or any real password/token. Use `config/email-secrets.properties.example` only.

## Local source reference

The verified offline source is kept outside this Git working copy at:

```text
F:\Project LTM\ServerHealthMonitoring
```

The Git working copy should be stored separately, for example:

```text
F:\Project LTM\ServerHealthMonitoring-Git
```
