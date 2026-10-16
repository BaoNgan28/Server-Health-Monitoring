# Troubleshooting

Group 10 — Server Health Monitoring System

Where to look first: `logs\monitor-0.log` on the monitoring client machine and
`logs\agent-agentNN-0.log` on each agent machine. Almost everything below shows
up there with the reason attached.

---

## 1. Build

### `mvn` is not recognised

Maven is not on PATH. Use `scripts\build.bat`, which finds the copy bundled with
NetBeans. If NetBeans is installed somewhere unusual, add its path to the list in
`scripts\find-maven.bat`.

### `Fatal error compiling: invalid target release: 17`

The JDK is older than 17. Check with `java -version` and `mvn -version`, and set
`JAVA_HOME` to a JDK 17 or newer.

### Dependencies cannot be downloaded

The first build needs internet access to fetch OSHI, Gson, SQLite JDBC,
JFreeChart, Jakarta Mail and JUnit. Behind a proxy, configure it in
`%USERPROFILE%\.m2\settings.xml`. After one successful build they are cached
locally and the machine can go offline.

### The build fails in `monitor-app` with a test failure

Read the named report under `monitor-app\target\surefire-reports\`. The
integration tests bind sockets on ports the operating system picks, so they do
not clash with a running client — but a very aggressive endpoint-protection
product can still block loopback traffic between two Java processes.

---

## 2. Starting up

### `Address already in use: bind` on UDP 6000

A monitoring client is already running. Find it:

```powershell
Get-NetUDPEndpoint -LocalPort 6000 | Select-Object OwningProcess
Get-Process -Id <OwningProcess>
```

Close it, or change `monitor.udp.port` in `config\monitor.properties` — and then
`monitor.udp.port` in every agent's properties file to match.

### `Address already in use` on an agent's TCP port

Two agents were given the same `agent.tcp.port`. On one machine they must differ
(5001–5004). On separate machines they can all be 5000.

### `Agent configuration file not found`

The path is resolved against the working directory. Either run from the project
root, use the scripts, or pass an absolute path:

```bat
java -jar agent-app-1.0.0-all.jar --config="F:\Project LTM\ServerHealthMonitoring\config\agent01.properties"
```

### `Missing required property 'agent.id'`

`agent.id` and `security.sharedSecret` are the only mandatory settings; every
other one has a default. Check for a typo or a commented-out line.

### The monitoring client shows a dialog and exits

The reason is in the dialog and in `logs\monitor-0.log`. Usually the UDP port or
an unwritable `data\` folder.

---

## 3. Agents never appear

An agent that stays `UNKNOWN` is one whose packets are not being accepted. Work
through it in this order.

### Is the agent sending?

Its console and log say so at startup. Repeated `UDP send to ... failed` lines
mean it cannot reach the client at all — check `monitor.host`.

### Are the datagrams arriving?

The monitoring client's status bar shows `packets: N received`. If `received`
never moves while the agent says it is sending, the datagrams are being dropped
in between:

* **firewall on the client machine** — the usual cause. Run
  `scripts\firewall-monitor.ps1` as Administrator.
* wrong `monitor.host` or `monitor.udp.port` in the agent's file;
* the two machines are on different subnets with no route between them.

### Are they arriving but being rejected?

`received` rises while `accepted` does not. `logs\monitor-0.log` names the reason.

| Log message | Cause | Fix |
|---|---|---|
| `no shared secret registered for agent X` | that `agent.id` is not registered in the client | Add Agent, with exactly that id |
| `signature mismatch for agent X` | the shared secret differs | make `security.sharedSecret` and the registered secret identical |
| `packet clock drift of N ms exceeds 30000 ms` | the machines' clocks differ | enable automatic time on both, or raise `packet.timestamp.tolerance.ms` |
| `protocol version 0.9` | mismatched builds | deploy the same jar version everywhere |
| `sequence N was already seen in session S` | a genuine replay, or the simulator | expected during the replay test |

### The agent shows `ONLINE` then `OFFLINE` repeatedly

Packets are being lost between the machines, or the agent machine is going to
sleep. Check the agent's log for send failures, and disable sleep on a machine
that is meant to be monitored.

---

## 4. Alerts

### No alert even though the metric is clearly high

* The threshold is above the value. Check it in Edit Agent.
* An alert of that type is **already open** for that agent — that is deliberate.
  The Alerts tab with `Open alerts only` ticked shows what is open.
* A threshold of `0` disables that check, on purpose.

### An alert will not clear

Recovery needs the value to fall below the threshold *minus the hysteresis band*
— two percentage points for CPU and memory, five per cent of the threshold for
network rates. A value hovering right at the threshold stays open on purpose, so
it cannot flap open and shut on alternate samples.

### Every agent went OFFLINE the moment the client started

This should not happen: an agent stays `UNKNOWN` until this client has actually
heard from it, precisely so that a restart does not report outages for machines
nobody has tried to reach. If you see it, the agents genuinely are not sending —
work through section 3.

### Security alerts appear during normal running

`SECURITY_INVALID_HMAC` means something sent a packet claiming an `agent.id` with
the wrong key. During the security tests that is expected. Outside them, it is
usually a stale agent still running with an old secret after the registered one
was changed.

---

## 5. TCP control

### Connect fails with `Connection refused`

The agent is not running, or the port is wrong. From the client machine:

```powershell
Test-NetConnection 192.168.1.11 -Port 5000
```

`TcpTestSucceeded : False` means nothing is listening there or the agent
machine's firewall is blocking it. Run `scripts\firewall-agent.ps1` as
Administrator on the agent machine.

### Connect times out instead of being refused

A firewall is dropping the packets silently rather than rejecting them. Same fix.

### CONFIG_UPDATE returns `ERROR`

The agent refused the value, and the message says which one and why. Allowed
ranges are in [PROTOCOL.md](PROTOCOL.md#83-config_update--config_ack). Nothing was
applied; the agent is exactly as it was.

### The connection worked, then later requests fail

The agent was restarted, so the socket died. The client reconnects automatically
on the next request; if it cannot, the agent is not running.

---

## 6. Database

### `database is locked`

Another process has the file open — usually a SQLite browser. Close it. The
client retries briefly before reporting the failure.

### The database grows and never shrinks

With the production profile, raw data is only folded after an hour, and the job
runs every five minutes. To see it happen in minutes instead, use
`scripts\run-monitor-demo.bat`.

### I want to start from a clean database

Close the monitoring client, then delete `data\monitoring.db` together with any
`-shm` and `-wal` file beside it. It is recreated on the next start — along with
an empty agent list, so the agents have to be registered again.

### History is empty for a long range

Before the first aggregation run there are no buckets, so a thirty-day range has
nothing to show. The panel falls back to raw data when the aggregated table is
empty; if both are empty, nothing has been collected for that period yet.

---

## 7. E-mail

Covered in detail in [EMAIL_SETUP.md](EMAIL_SETUP.md#7-if-it-does-not-work). In
short:

| Symptom | Cause |
|---|---|
| `NOT CONFIGURED` | a placeholder is still in place; the `Reason:` line names the key |
| `535 Username and Password not accepted` | the account password was used instead of an App Password, or it was typed with its spaces |
| `Couldn't connect to host` | no internet, or outbound TCP 587 is blocked |
| Nothing arrives, no error | check the recipient's spam folder |

E-mail never being configured is a supported state. The application runs normally
and alerts still reach the window and the database.

---

## 8. Interface

### The window freezes

It should not: every slow operation runs in a background worker. If it happens,
note what was clicked and check `logs\monitor-0.log` for an exception at that
moment.

### The charts are empty

Nothing has been accepted for that agent yet. Check the Status column, then
section 3.

### The table does not refresh

Updates are applied a couple of times a second. If the numbers are frozen while
the status bar's `accepted` counter is still rising, that is worth reporting —
check the log for a listener exception.

### The Shared Secret column is missing

Deliberately. Secrets are never displayed. Editing an agent leaves the field
empty, and leaving it empty keeps the stored secret unchanged.

---

## 9. Harmless messages you can ignore

### `Unable to locate English counter names in registry Perflib 009`

Logged once by OSHI at agent startup on some Windows installations. OSHI falls
back to a different way of reading the counters and every metric still works.
Nothing needs to be done about it.

### `Email notification suppressed (smtp.appPassword is still the placeholder)`

Expected whenever an alert fires while e-mail is not configured. It is the
system saying it had something to send and deliberately did not.

---

## 10. Getting more detail

Temporarily raise the log level by adding to the `java` command line:

```
-Djava.util.logging.ConsoleHandler.level=FINE
```

Logs rotate at 2 MiB, five files per application, and are appended across
restarts, so a run from yesterday is usually still there.

Neither the agent's shared secret nor the Gmail App Password ever appears in a
log file, at any level.
