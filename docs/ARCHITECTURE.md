# Architecture

Group 10 — Server Health Monitoring System

---

## 1. The shape of the system

```
                    MACHINE B ... MACHINE E
                  +-------------------------+
                  |          AGENT          |
                  |                         |
                  |  OSHI                   |
                  |    |                    |
                  |    v                    |
                  |  SystemMetricsCollector |
                  |    |                    |
                  |    v                    |
                  |  TelemetryScheduler     |   TcpControlServer  <---+
                  |    |                    |         ^              |
                  |    v                    |         |              |
                  |  TelemetryPacket        |         |              |
                  |    |                    |         |              |
                  |    v                    |         |              |
                  |  PacketSigner (HMAC)    |         |              |
                  |    |                    |         |              |
                  |    v                    |         |              |
                  |  UdpTelemetrySender     |         |              |
                  +---------|---------------+---------|--------------+
                            |                         |
              UDP 6000      |                         |   TCP 5000
              telemetry     |                         |   CONFIG_UPDATE
              heartbeat     |                         |   CONFIG_ACK
                            v                         |
   MACHINE A  +------------------------------------------------------+
              |             MONITORING CLIENT                        |
              |                                                      |
              |  UdpTelemetryReceiver                                |
              |    |                                                 |
              |    v                                                 |
              |  TelemetryService                                    |
              |    |                                                 |
              |    v                                                 |
              |  PacketValidator  --- rejected ---> security log     |
              |    |                                 + alert panel   |
              |    | accepted                                        |
              |    +---------------+-----------------+-------------+ |
              |    |               |                 |             | |
              |    v               v                 v             v |
              | AgentRegistry  MetricRepository  AlertService   GUI   |
              |                  (SQLite)           |                |
              |                                     v                |
              |                                EmailNotifier ---> SMTP
              |                                                      |
              |  HeartbeatMonitor      AggregationService            |
              |  AgentTcpConnectionManager --------------------------+
              +------------------------------------------------------+
```

---

## 2. Layering

```
   UI          MainFrame, DashboardPanel, ServerDetailPanel,
               HistoryPanel, AlertPanel, SettingsPanel
                 |
                 v  (only ever calls services)
   Service     TelemetryService, AgentRegistry, HeartbeatMonitor,
               AlertService, AggregationService, HistoryService,
               AgentManagementService
                 |
                 v
   Network     UdpTelemetryReceiver, AgentTcpConnection(Manager)
   Repository  DatabaseManager, AgentRepository, MetricRepository,
               AlertRepository
   Notify      Notifier, EmailNotifier, NoOpNotifier, CompositeNotifier
```

The rule, enforced by what each class is given in its constructor: **the user
interface never opens a socket, never writes SQL and never talks to SMTP.** A
panel is handed services; only services hold repositories, sockets and the
notifier. `MonitorApplication` is the single place where the wiring happens, so
the dependency direction is visible in one file.

That is not decoration. It is what makes the GUI testable by proxy — the whole
receive path has integration tests that never open a window — and what keeps a
slow query or a dead TCP peer from freezing the interface.

---

## 3. The receive path, step by step

```
  DatagramSocket.receive()            udp-receiver thread, does nothing else
        |
        v
  copy bytes, pick worker by sender   same sender -> same worker, order kept
        |
        v
  JSON -> TelemetryPacket             udp-worker-N thread
        |
        v
  PacketValidator
        |  1. shape and ranges       0..100 %, no negatives, finite doubles
        |  2. protocol version
        |  3. known agent            unknown -> dropped, no signature computed
        |  4. HmacSHA256             over the canonical string, constant-time
        |  5. timestamp freshness    within +/- 30 s of this machine's clock
        |  6. replay window          per (agent, session), 64-packet window
        |
        +-- any failure ------> counted, logged, recorded as a security event
        |                       and nothing else happens
        v  accepted
  HeartbeatMonitor.markAlive          ONLINE, and AGENT_RECOVERED if it was down
        |
        v
  AgentRegistry.applySample           live view, then event -> GUI
        |
        v
  MetricRepository.insertRaw          one row in metric_raw
        |
        v
  AlertService.evaluate               thresholds, open/resolve, e-mail
```

The ordering matters. Liveness is updated before thresholds, so a machine coming
back reports its recovery before it reports that its CPU is high. And nothing
downstream of the validator runs for a rejected packet, which is precisely why a
forged datagram claiming 100% CPU cannot show up on the dashboard, cannot land in
`metric_raw`, and cannot raise an alert.

---

## 4. The control path

```
  DashboardPanel "Configure"
        |
        v  SwingWorker, never the event dispatch thread
  AgentManagementService.pushConfiguration
        |
        v
  AgentTcpConnectionManager.ensureConnected     reconnects if the socket died
        |
        v
  AgentTcpConnection.sendConfigUpdate
        |
        v  4-byte length + UTF-8 JSON
  === TCP ===
        |
        v
  TcpControlServer -> TcpClientSessionHandler   one pool thread per session
        |
        v
  ConfigUpdateService.apply                     validates everything, then
        |                                       applies everything, or nothing
        v
  CONFIG_ACK  SUCCESS | ERROR + reason
        |
  === TCP ===
        |
        v
  monitoring client stores the new threshold    only when the ack said SUCCESS
        |
        v
  dialog shows the agent's own answer
```

The local database is updated **after** the acknowledgement, never before. If the
agent refuses a value, the screen keeps showing what the agent is actually using.

---

## 5. Threads

| Thread | Owner | What it does |
|---|---|---|
| `udp-receiver` | monitoring client | only `receive()` and a copy |
| `udp-worker-0..3` | monitoring client | validate, store, alert; one per sender group |
| `monitor-scheduler-1..2` | monitoring client | heartbeat sweep, aggregation |
| `email-sender` | monitoring client | one thread behind a bounded queue |
| Swing EDT | monitoring client | painting and input only |
| `SwingWorker` pool | monitoring client | connect, config push, history, test e-mail |
| `agent-scheduler-1..2` | agent | telemetry tick, heartbeat tick |
| `agent-tcp-accept` | agent | `accept()` loop |
| `agent-tcp-session-1..8` | agent | one control session each |

Every pool is fixed in size and every queue is bounded. Nothing in this project
creates a thread per packet or per connection attempt, which is what keeps the
twenty-identity load test from turning into a thread explosion.

Two rules are followed throughout:

* **Never touch Swing from a worker thread.** Service events arrive on worker
  threads and are forwarded with `SwingUtilities.invokeLater`.
* **Never block the event dispatch thread.** Connecting, pushing configuration,
  loading history and sending mail all run in a `SwingWorker`.

A third rule applies inside the schedulers: a scheduled task that throws is
silently cancelled by `ScheduledExecutorService`, which would stop an agent
reporting with no visible error. Every tick therefore catches and logs
everything.

---

## 6. Failure isolation

The system keeps running when any one of these happens:

| Failure | What happens |
|---|---|
| one agent is stopped | it goes OFFLINE, one alert; the others are untouched |
| a datagram is malformed | counted, logged, dropped; the loop continues |
| a forged or replayed packet | rejected before anything is updated |
| a TCP peer disappears | that session's thread ends; the accept loop lives |
| the database is briefly locked | the work is retried, then reported |
| SMTP fails or the internet is down | logged with the server's reason; alerts still reach the GUI and the database |
| a metric is unavailable from the OS | that metric reports a safe value and a warning is logged at most once a minute |
| a GUI listener throws | logged and skipped; telemetry processing is unaffected |

---

## 7. Where secrets live

* An agent's shared secret is in its properties file and in the `agents` table.
  It is never printed at startup, never logged, never sent over the wire, and
  never shown in the agent table. The dialog that edits it uses a
  `JPasswordField` and clears the character array it reads.
* The Gmail App Password is read from `config/email-secrets.properties` (or from
  the environment) at runtime, is held only by `EmailConfig`, and is masked out
  of any SMTP error message before that message reaches a log or a dialog.
* Neither value appears in `toString()` anywhere in the project.
