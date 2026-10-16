# Test report

Group 10 — Server Health Monitoring System — version 1.0.0

Everything below was executed. Where something was **not** executed, it says so
and why; nothing is recorded as passing on the strength of the code looking
right.

---

## 1. Environment

| | |
|---|---|
| Operating system | Windows 11 Pro, 10.0.26200 |
| JDK used to build | Oracle JDK 22.0.1 (2024-04-16) |
| Bytecode target | `--release 17`, so the output runs on Java 17+ |
| Maven | Apache Maven 3.9.6, the copy bundled with Apache NetBeans 22 |
| Project root | `F:\Project LTM\ServerHealthMonitoring` |
| Machines available | **one** — see section 9 |

---

## 2. Build

```
mvn clean package
```

| | |
|---|---|
| Result | **BUILD SUCCESS** |
| Total time | 24.5 s |
| monitoring-common | SUCCESS |
| agent-app | SUCCESS |
| monitor-app | SUCCESS |
| simulator-app | SUCCESS |

Artifacts produced:

```
monitor-app\target\monitor-app-1.0.0-all.jar        17.1 MB
agent-app\target\agent-app-1.0.0-all.jar             4.9 MB
simulator-app\target\simulator-app-1.0.0-all.jar     0.4 MB
```

---

## 3. Automated tests

```
mvn clean test
```

| Module | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| monitoring-common | 50 | 0 | 0 | 0 |
| agent-app | 26 | 0 | 0 | 0 |
| monitor-app | 96 | 0 | 0 | 0 |
| **Total** | **172** | **0** | **0** | **0** |

Per class:

| Class | Tests | Result |
|---|---|---|
| `HmacUtilTest` | 5 | PASS |
| `PacketCanonicalizerTest` | 7 | PASS |
| `PacketValidatorTest` | 14 | PASS |
| `ReplayProtectorTest` | 13 | PASS |
| `JsonMessageCodecTest` | 11 | PASS |
| `AgentConfigLoaderTest` | 7 | PASS |
| `NetworkRateTrackerTest` | 8 | PASS |
| `AgentMetricsTest` | 4 | PASS |
| `ConfigUpdateServiceTest` | 7 | PASS |
| `EmailConfigLoaderTest` | 11 | PASS |
| `MonitorConfigLoaderTest` | 7 | PASS |
| `DatabaseManagerTest` | 10 | PASS |
| `MetricRepositoryTest` | 9 | PASS |
| `AggregationServiceTest` | 8 | PASS |
| `AlertServiceTest` | 14 | PASS |
| `HeartbeatMonitorTest` | 11 | PASS |
| `EmailNotifierTest` | 8 | PASS |
| `UdpIntegrationTest` | 9 | PASS |
| `TcpIntegrationTest` | 9 | PASS |

No test is disabled, ignored or skipped.

---

## 4. Local end-to-end run

Real processes on one machine: the monitoring backend plus four real agent
processes, each started from `agent-app-1.0.0-all.jar` with its own properties
file, for 110 seconds. The Swing layer is the only part not exercised by this
run; it is covered separately in section 8.

**Result: PASS.**

```
udp received : 469
udp accepted : 464
udp rejected : 5
open alerts  : 1
live threads : 8

metric_raw rows  : 389
  AGENT-01 : 107
  AGENT-02 :  77      (lower: it was stopped for ~26 s on purpose)
  AGENT-03 : 103
  AGENT-04 : 102
alerts rows      : 5
```

The five rejected packets are exactly the ones the two security simulators sent;
no genuine agent packet was refused. The gap between 464 accepted and 389 stored
rows is the heartbeats, which carry no metrics.

| Check | Result | Evidence |
|---|---|---|
| Four agents reach ONLINE | PASS | all four `ONLINE` from t=10 s onward |
| Real CPU | PASS | varied 3.9 – 57.1 % with machine load |
| Real memory | PASS | 51.8 – 55.2 % |
| Real connection count | PASS | 37 – 41 established TCP connections |
| Real uptime | PASS | 143 724 s, rising one per second |
| Network IN rate | PASS | 6.8 – 2260 kB/s, tracking real traffic |
| Network OUT rate | PASS | 1.6 – 85 kB/s |
| Database writes | PASS | 389 rows across the four agents |
| No thread growth | PASS | 8 live threads at the end |

### 4.1 TCP control — PASS

A `CONFIG_UPDATE` was pushed to AGENT-01 over TCP mid-run:

```
TCP CONFIG_ACK status=SUCCESS
  message=Applied metricIntervalMs=1000, cpuThreshold=2.0
  effectiveCpuThreshold=2.0  effectiveMetricIntervalMs=1000
```

The agent's own log confirms it applied the change, so this is a real round trip,
not a local UI update. Refusal of an invalid value is covered by
`TcpIntegrationTest` and by `ConfigUpdateServiceTest`.

### 4.2 CPU alert — PASS

AGENT-01 was registered with a 1 % CPU threshold so the alert path would fire
without loading the machine.

```
ALERT CPU_HIGH AGENT-01 OPEN  CPU HIGH is 55.7 %, above the threshold of 1.0 %
```

**One** row, from roughly 100 consecutive samples above the threshold. Duplicate
suppression confirmed under 60 consecutive violations in `AlertServiceTest`.

### 4.3 Offline and recovery — PASS

AGENT-02 was killed at t≈30 s and restarted at t≈56 s.

```
ALERT AGENT_OFFLINE   AGENT-02 OPEN      No valid packet received since 15:09:57
ALERT AGENT_OFFLINE   AGENT-02 RESOLVED
ALERT AGENT_RECOVERED AGENT-02 RESOLVED  Agent is online again
```

One offline event for the whole outage, and recovery detected within a second of
the restart even though the restarted agent starts counting from sequence 1
again.

### 4.4 Invalid HMAC — PASS

`simulator-app ... invalid-hmac` sent three forged packets claiming 100 % CPU.

```
SECURITY SECURITY_INVALID_HMAC from AGENT-01: signature mismatch for agent AGENT-01
```

AGENT-01's displayed CPU was unaffected, no row was written for them, and no CPU
alert was caused by them.

### 4.5 Replay — PASS

`simulator-app ... replay` sent a valid packet, then the same packet twice more,
then a fresh one.

```
SECURITY SECURITY_REPLAY_PACKET from AGENT-01:
    sequence 1 was already seen in session SIM-bcb734b9-...
```

First and last accepted, the middle two refused. The real AGENT-01 kept running
throughout.

---

## 5. Aggregation — PASS

Executed with the demo profile (`config\monitor-demo.properties`: raw kept 60 s,
10-second buckets, aggregates kept 300 s, job every 15 s) and four real agents,
for 150 seconds.

```
Aggregation scheduled every 15000 ms (raw kept 60000 ms, bucket 10000 ms,
                                      aggregates kept 300000 ms)
Aggregation wrote 3 bucket(s) ...
Aggregation wrote 8 bucket(s) ...
Aggregation wrote 4 bucket(s) ...
Aggregation wrote 8 bucket(s) ...
Aggregation wrote 4 bucket(s) ...
Aggregation wrote 8 bucket(s) ...

udp received : 692   accepted : 692   rejected : 0

metric_raw rows : 257   (64–65 per agent: the last ~60 s, as configured)
metric_5m rows  :  35
```

Raw data stopped growing at the retention window while buckets accumulated —
exactly the intended behaviour. Production timings (one hour, five-minute
buckets, thirty days) run through the same code path and are covered by
`AggregationServiceTest`, which drives it with fabricated timestamps rather than
waiting an hour.

---

## 6. Load test — PASS

```
simulator-app load --agents=20 --duration=60
```

| | |
|---|---|
| Identities | 20 |
| Packets sent | 1 201 |
| Send failures | 0 |
| Datagrams received by the client | 1 211 |
| Rejected as UNKNOWN_AGENT | 1 201 |
| Accepted | 10 (a separate, registered agent running at the time) |
| **Datagrams lost** | **0** |
| Live threads at the end | 6 |

Every load identity was refused because none of them is registered — which is
the correct behaviour and the point of the exercise. What was being checked is
that the receiver keeps up under a flood, refuses cleanly, loses nothing, and
does not grow threads. It did all four.

---

## 7. Database — PASS

Verified by `DatabaseManagerTest`, `MetricRepositoryTest` and by the runs above:

| Check | Result |
|---|---|
| Database created automatically on first start | PASS |
| Full schema applied, all four tables and four indexes | PASS |
| Re-opening an existing database keeps its data | PASS |
| `foreign_keys=ON`, `journal_mode=WAL` in force | PASS |
| Deleting an agent cascades to metrics and alerts | PASS |
| Transactions roll back on failure | PASS |
| Four concurrent writers, 100 inserts, no loss or error | PASS |
| Agents persisted across restarts | PASS |
| Raw metrics persisted | PASS |
| History from raw and from aggregates | PASS |
| Alerts inserted and resolved | PASS |

---

## 8. Swing client — PASS, with one part not automated

`monitor-app-1.0.0-all.jar` was started as a normal desktop application.

| Check | Result |
|---|---|
| Starts without error | PASS |
| Loads its configuration and creates/opens the database | PASS |
| Binds UDP 6000 | PASS |
| Loads the registered agents and open alerts | PASS |
| Starts the heartbeat and aggregation timers | PASS |
| Reports `Email notifications disabled: credentials not configured` and continues | PASS |
| Process stays alive and responsive | PASS |

```
Profile             : production
UDP Listen Port     : 6000
Database            : F:\...\data\monitoring.db
Email notifications disabled: credentials not configured
                     (smtp.appPassword is empty or still the placeholder)
Loaded 4 agent(s) from the database
UDP telemetry receiver listening on port 6000
Monitoring client started with profile 'production'
```

**NOT AUTOMATED:** visual inspection of the rendered window — chart appearance,
colours, column layout — was not captured programmatically. The panels' logic is
covered by the service-level tests they call, but confirming that the charts
*look* right is a manual step (see `docs/TESTING.md` section 3.1).

---

## 9. Not executed

### 9.1 Live Gmail delivery — **NOT EXECUTED**

**Reason:** the Gmail App Password and the recipient address in
`config\email-secrets.properties` are still the shipped placeholders
(`PASTE_GMAIL_APP_PASSWORD_HERE`, `PASTE_RECIPIENT_EMAIL_HERE`). No password was
guessed or invented, and no live SMTP session was attempted.

This is **not a project failure**. It is the designed behaviour for an
unconfigured system: e-mail is disabled safely, the log says why, the Settings
tab shows `NOT CONFIGURED`, and everything else runs normally.

What **was** tested, and passed:

| Check | Result |
|---|---|
| Subject lines for all six message kinds | PASS |
| Body contents, including values and thresholds | PASS |
| Delivery is asynchronous and does not block the caller | PASS (20 alerts queued in under a second) |
| An SMTP failure is contained, counted, and never thrown at the caller | PASS |
| The password is masked out of any error message | PASS |
| Placeholders leave e-mail disabled, with the reason recorded | PASS |
| Environment variables override the file | PASS |
| The no-op notifier reports it is off and sends nothing | PASS |
| A composite survives a broken channel | PASS |

To finish this check, follow `docs/EMAIL_SETUP.md`: fill in two values, restart or
press **Reload Email Configuration**, then **Send Test Email**.

### 9.2 Physical multi-machine deployment — **NOT EXECUTED**

**Reason:** only one machine was available. The four agents and the client all
ran on it over the loopback interface.

The loopback run is **not** a substitute: it does not exercise a real network
path, a real firewall, or two machines whose clocks differ. It is reported here
as what it is.

What this does and does not establish:

* **Established:** the protocol, the signing, the framing, the database, the
  alert engine and the liveness logic all work across four independent operating
  system processes.
* **Not established:** that traffic crosses a physical network and a Windows
  firewall on a particular LAN.

Everything needed for the multi-machine run is prepared: `agent.tcp.port` can be
5000 on every machine, the firewall scripts are written, and only the agent jar
plus one properties file has to be copied. See `docs/MULTI_MACHINE_SETUP.md`.

### 9.3 Long-duration production retention — **NOT EXECUTED**

**Reason:** confirming the 60-minute raw window and the 30-day aggregate window
in real time would take 30 days.

Both were verified instead by driving the same code with fabricated timestamps
(`AggregationServiceTest`), and the identical code path was verified end to end
in real time using the compressed demo profile (section 5).

---

## 10. Defects found and fixed during testing

Recorded because the tests were what found them.

| # | Defect | Found by | Fix |
|---|---|---|---|
| 1 | `volatile long` counters incremented from four worker threads lost updates | `UdpIntegrationTest` timing out on counts | `LongAdder` for every counter shared across threads |
| 2 | A shared worker pool let packet 40 overtake packet 39, so the replay check refused genuine packets (23 of 200 in a burst) | `UdpIntegrationTest.handlesABurst` | one single-thread worker per sender, chosen by source address, so a sender's order is kept; plus a sliding window instead of strict monotonic sequence |
| 3 | A restarted agent counting from 1 again looked like a replay and stayed OFFLINE for a further ~34 s | the local four-agent run | per-agent-run session id, covered by the signature; each session gets its own replay window |
| 4 | The simulator's large sequence numbers pushed the real agent's window out of range and knocked it offline | the local four-agent run | the simulator has its own session id, so senders cannot interfere |
| 5 | Summing byte counters across a changing interface list produced spurious ~570 Mbps spikes and false network alerts | the local four-agent run | rates measured per interface and then summed, so an adapter appearing cannot look like a burst |
| 6 | Restarting the client raised an OFFLINE alert for every agent, from a `last_seen` value loaded out of the database | starting the Swing client | only an agent this client has actually heard from can be declared down |

Each fix has a regression test.

---

## 11. Code quality sweep

| Check | Result |
|---|---|
| `TODO`, `FIXME`, `XXX`, `HACK` | none |
| `UnsupportedOperationException` thrown as a stub | none (the two occurrences are legitimate `catch` clauses) |
| Empty `catch` blocks | none — every one logs or acts |
| `System.out` / `System.err` outside the three `main` classes and the console simulator | none |
| Hard-coded passwords in Java source | none |
| Disabled or ignored tests | none |
| Placeholder credentials in `config\email-secrets.properties` | present **by design**, and documented |

---

## 12. Summary

| Area | Result |
|---|---|
| Build | PASS |
| Unit and integration tests (172) | PASS |
| UDP telemetry | PASS |
| TCP control and CONFIG_ACK | PASS |
| SQLite storage, schema, cascades | PASS |
| HMAC authentication | PASS |
| Replay protection | PASS |
| Heartbeat, offline and recovery | PASS |
| Alert engine and duplicate suppression | PASS |
| Aggregation and retention | PASS |
| Four-agent local run | PASS |
| Twenty-identity load test | PASS |
| Swing client startup and operation | PASS (appearance not automated) |
| E-mail implementation | PASS |
| **Live Gmail delivery** | **NOT EXECUTED** — placeholder credentials |
| **Physical multi-machine run** | **NOT EXECUTED** — one machine available |
| **30-day retention in real time** | **NOT EXECUTED** — verified with fabricated clocks and the demo profile |
