# Remote Server Health Monitoring System

Course project for **Computer Network Programming** — **Group 10**, class **23DTHA5**.

A Java-based system for monitoring the health of remote computers. Agents collect system metrics and report them to a central Monitoring Client, which provides a Swing dashboard, historical information, alerts, and monitoring utilities.

**Final release target:** `v1.0.0` (publish the tag only after the final Week 3 `develop → main` pull request has been merged and verified).

## Features

- **System monitoring:** CPU, memory, network throughput, connection count, uptime, and Agent status.
- **Central dashboard:** manage multiple Agents, inspect current metrics, charts, details, and history.
- **Communication:** UDP telemetry/heartbeats and TCP Agent-control/configuration messages.
- **Protocol security:** HMAC verification and replay protection.
- **Persistence and alerts:** SQLite-backed monitoring data, alert processing, and optional SMTP email notifications when configured.
- **Testing tools:** security and load simulators, automated tests, and documented manual QA scenarios.

## Project modules

- `monitoring-common` — shared message models, protocol, security and utilities.
- `agent-app` — Agent runtime installed on monitored machines.
- `monitor-app` — central Monitoring Client and Swing GUI.
- `simulator-app` — security/load simulators used for testing.

## Requirements

- JDK **17**.
- Apache Maven (or the Maven distribution bundled with NetBeans).
- Windows for the provided `.bat` launch scripts and local demo workflow.
- An available UDP port for the Monitoring Client and distinct Agent TCP ports when multiple Agents run on the same computer.

## Build and test

From the project root, run:

```powershell
mvn -B clean test
mvn -B clean package
```

On Windows, the helper scripts are also available:

```powershell
.\scripts\test.bat
.\scripts\build.bat
```

See [`docs/TESTING.md`](docs/TESTING.md) and [`docs/TEST_REPORT.md`](docs/TEST_REPORT.md) for test procedures, recorded outcomes, evidence, and any known limitations. A successful build does not by itself establish that every manual scenario passed.

## Local monitoring demo

Build the JAR files first. For a local demo using the demo Monitoring profile, start the Monitor and the four Agents in **separate terminals**:

```powershell
.\scripts\run-monitor-demo.bat
.\scripts\run-agent01.bat
.\scripts\run-agent02.bat
.\scripts\run-agent03.bat
.\scripts\run-agent04.bat
```

The provided local Agent configurations use IDs `AGENT-01` through `AGENT-04`, TCP ports `5001`–`5004`, and send telemetry to the Monitor's UDP port `6000` on `127.0.0.1`. Register each Agent in the Monitoring Dashboard if it is not already present, using the matching configuration and demo shared secret. **Never use demo secrets in production.**

For multi-machine setup, firewall configuration, and troubleshooting, consult the documents below.

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — system architecture and module interaction.
- [`docs/NETBEANS_SETUP.md`](docs/NETBEANS_SETUP.md) — NetBeans and development environment.
- [`docs/PROTOCOL.md`](docs/PROTOCOL.md) — communication protocol and security.
- [`docs/DATABASE.md`](docs/DATABASE.md) — database and persistence.
- [`docs/EMAIL_SETUP.md`](docs/EMAIL_SETUP.md) — optional SMTP email setup.
- [`docs/MULTI_MACHINE_SETUP.md`](docs/MULTI_MACHINE_SETUP.md) — multi-machine deployment.
- [`docs/TESTING.md`](docs/TESTING.md) — test and demo procedures.
- [`docs/TEST_REPORT.md`](docs/TEST_REPORT.md) — final manual QA and verification results.
- [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) — presentation/demo sequence.
- [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) — troubleshooting.

## Git workflow

- `main` — stable milestone/release branch.
- `develop` — integration branch used as the base for task branches.
- Every task must have a GitHub Issue before code is changed.
- Task branch format required by the course: `feature/<MSSV>-issue-<ISSUE_NUMBER>-<short-name>`.
- Feature pull requests target `develop`; milestone pull requests promote `develop` to `main`.
- Every pull request must be reviewed by at least one other team member, with actual test/CI evidence before merging. Repository settings may not technically enforce this rule on a private repository, so the team follows it as a process requirement.
- The final release tag `v1.0.0` must point to the verified final commit on `main`.

## Team contribution matrix

The table summarizes completed work recorded in the team's **merged pull requests** through Issue #20. Pull-request numbers are different from GitHub Issue numbers.

| MSSV | Họ và tên | Công việc đã thực hiện | Merge Requests / Pull Requests |
|---|---|---|---|
| 2380601424 | Nguyễn Thanh Bảo Ngân | Repository initialization and Week 1 synchronization; Agent foundation and Agent UDP/TCP runtime; Week 1 contribution matrix; Agent deployment configurations/scripts; final QA test report | #22, #23, #24, #31, #32, #33, #41, #48 |
| 2380601465 | Lê Huỳnh Ngọc | Shared protocol; HMAC signing and replay protection; Monitor TCP/UDP and heartbeat; security simulator and firewall tools; replay-test stabilization | #25, #30, #35, #38, #46 |
| 2182310415 | Bùi Tấn Quốc Đạt | Data foundation and SQLite persistence; alert aggregation backend; Gmail SMTP notifications | #29, #34, #39 |
| 2380613311 | Nguyễn Phương Nam | Monitor configuration foundation; Agent management and dashboard; Monitor startup and final integration | #27, #36, #47 |
| 2380601581 | Trương Thị Yến Nhi | Visualization foundation; Server Detail, History and Alerts UI; Settings and Email controls UI | #26, #37, #40, #42, #43, #44 |

## Security

- Never commit `config/email-secrets.properties`, real passwords, SMTP app passwords, API tokens, or private keys.
- Use `config/email-secrets.properties.example` only as a template.
- Replace demo shared secrets before deploying outside a controlled local test environment.
- Exclude generated `target/` files, runtime databases, logs, and other local artifacts from commits.

## Release process

1. Merge the final README PR (Issue #21) into `develop` after peer review and passing CI.
2. Create the final milestone PR with **base `main`** and **compare `develop`**. Include `Closes #15` through `Closes #21` for genuinely completed Week 3 Issues (and any other completed Week 3 Issues still open).
3. Obtain a peer approval and passing checks, then merge the milestone PR into `main`. Keep the long-lived `develop` branch.
4. Verify `main` and create the annotated tag `v1.0.0` on its final merge commit, then push the tag.
5. Record the release URL and CI/tag evidence in the final Issue/PR documentation.

## Development environment

Clone the repository into a directory of your choice. All commands in this README are relative to the root of the cloned repository; **no special drive letter or external source-reference folder is required**.

```powershell
git clone https://github.com/BaoNgan28/Server-Health-Monitoring.git
cd Server-Health-Monitoring
```

Repository access is required if the GitHub repository remains private. Follow [`docs/NETBEANS_SETUP.md`](docs/NETBEANS_SETUP.md) for the Java/NetBeans setup, and use `develop` for new feature branches and `main` for stable releases.
