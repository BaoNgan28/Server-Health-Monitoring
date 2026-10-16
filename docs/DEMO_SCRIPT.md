# Demonstration script

Group 10 — Server Health Monitoring System — about 12–15 minutes

---

## Before you start

Done ahead of time, not during the demonstration:

```bat
scripts\build.bat
```

- [ ] The build finished, all tests green.
- [ ] The four agents are registered in the monitoring client (5001–5004,
      `GROUP10_DEMO_SECRET_01`…`_04`). They persist, so this is a one-off.
- [ ] AGENT-01's CPU threshold is back at `85.0` — step 9 lowers it live.
- [ ] `config\email-secrets.properties` has a real App Password if the live
      e-mail part is to be shown. Without it, everything else still works and
      Settings honestly reports `NOT CONFIGURED`.
- [ ] Windows notifications and anything noisy are off.
- [ ] A SQLite viewer is open on `data\monitoring.db`, or a command prompt ready.
- [ ] `docs\ARCHITECTURE.md` open in a second window for step 1.

Timings below are a guide, not a script to read out.

---

## 1. Architecture — 1:30

Show the diagram at the top of `ARCHITECTURE.md`.

Three things to say, and no more:

* Agents run on the machines being watched; one Swing client watches all of them.
* **UDP for telemetry** — small, once a second, a lost one is replaced by the
  next, so retransmission would buy nothing.
* **TCP for control** — a configuration change must not be silently lost, and the
  agent's acknowledgement is the proof it arrived.

> "Two transports, each doing what it is actually good at."

---

## 2. Start the monitoring client — 0:30

```bat
scripts\run-monitor.bat
```

Point at the console banner: UDP port, database path, log directory. Note that
the database was created by the application, not by hand.

---

## 3. Start the four agents — 1:00

```bat
scripts\run-local-demo.bat
```

Or four separate windows if that is easier to narrate.

Show an agent's banner: id, name, hostname, monitoring host, ports, intervals.

> "Notice what is *not* there: the shared secret. It is never printed, never
> logged, never sent over the wire."

Watch the four rows turn `ONLINE` and the summary read `TOTAL 4 / ONLINE 4`.

---

## 4. The dashboard — 1:00

Let it run for a few seconds. Walk the columns: Name, IP, CPU, RAM, Connections,
Network IN, Network OUT, Status, Last Seen — updating about once a second.

> "These are real numbers from this machine, read through OSHI. Nothing here is
> simulated."

---

## 5. CPU and memory — 0:45

Server Detail tab, select AGENT-01. Show CPU and RAM as numbers and as charts.

Open something heavy — a browser with a few tabs — and let the audience watch the
CPU line answer.

---

## 6. Network traffic — 0:45

Point at Network IN and OUT.

> "The operating system gives a total number of bytes since boot. A total is not
> a rate. The agent measures the difference between two readings and divides by
> the time between them — per interface, so an adapter appearing or disappearing
> cannot look like a burst of traffic that never happened."

---

## 7. Charts — 0:45

Three live charts: CPU, memory, network in and out.

> "The window keeps 120 points. Not because 120 is special, but because an
> application left running overnight must not accumulate one data point per
> second for ever. The chart object is built once; only the data changes."

---

## 8. TCP configuration — 1:30

**The most important part.**

Dashboard → select AGENT-01 → **Connect**. A dialog shows the agent's OS, CPU
model, core count and memory — fetched over TCP right now.

Then **Configure** → Metric Interval `2000` → **Send CONFIG_UPDATE**.

Show the `CONFIG_ACK` dialog: `SUCCESS`, and the effective values. Switch to the
AGENT-01 console and show `Applied metricIntervalMs=2000` and `Telemetry interval
is now 2000 ms`. The dashboard visibly updates half as often.

> "This is not the interface changing a local number. The client sent a message,
> the agent validated it, applied it and replied. The reply is what you are
> looking at."

Now send a bad one — Metric Interval `5`:

```
Status : ERROR
Message: metricIntervalMs must be between 200 and 3600000 ms, got 5
```

> "The agent validates everything before applying anything, so one bad value
> leaves it exactly as it was. And the client only writes the new value to its own
> database after a SUCCESS — so the screen can never show a setting the agent
> refused."

Set the interval back to `1000`.

---

## 9. Lower the CPU threshold — 0:45

Read AGENT-01's current CPU — say 20%. **Configure** → CPU Threshold `5.0` →
send.

---

## 10. The CPU alert — 1:00

Alerts tab: one `CPU_HIGH`, `OPEN`, in bold.

Wait thirty seconds with the metric still above the threshold. Refresh.

> "Still one row. Thirty samples crossed the threshold and produced one alert,
> because an alert is an *incident*, not a measurement. Without that, an hour of
> high CPU would be 3600 alerts and 3600 e-mails."

Mention the hysteresis band: recovery needs the value to come back two points
*below* the threshold, or a metric sitting exactly on it would flap open and shut
on alternate samples.

---

## 11. The alert e-mail — 1:00

*If e-mail is configured:* show the inbox —
`[WARNING] CPU HIGH - AGENT-01`, with the agent, the server, the current value,
the threshold and the time. One message, not thirty.

*If not:* Settings → Notifications, showing `NOT CONFIGURED`, the exact file to
edit, and the two keys. Then click **Send Test Email** to show the dialog naming
that file.

> "The credential lives outside the jar on purpose. Filling it in and restarting
> is all it takes — no source change, no rebuild."

---

## 12. Stop AGENT-02 — 0:30

Close the AGENT-02 window. Start talking about something else while the timeout
runs.

---

## 13. Offline detection — 0:45

After 15 seconds AGENT-02 turns `OFFLINE`, the summary reads `ONLINE 3 /
OFFLINE 1`, and one `AGENT_OFFLINE` alert appears.

> "The sweep runs every two seconds. It has now run several times and there is
> still one alert. An outage is one event, not one per sweep."

---

## 14. The offline e-mail — 0:30

`[CRITICAL] SERVER OFFLINE - AGENT-02`, with the last-seen time and the reason.

---

## 15. Restart AGENT-02 — 0:30

```bat
scripts\run-agent02.bat
```

It returns to `ONLINE` within a second or two.

> "Worth noting: a restarted agent starts counting its packets from 1 again. To
> a naive replay check that looks exactly like an attack, and the machine would
> sit there offline for no reason. Each agent run carries a session id, covered by
> the signature, so a restart opens a fresh window — while a capture from the old
> session is still rejected."

---

## 16. The recovery e-mail — 0:15

`[RECOVERED] SERVER ONLINE - AGENT-02`, and the outage now shows `RESOLVED`.

---

## 17. Forged packets — 1:30

```bat
scripts\run-invalid-hmac-test.bat
```

It sends three packets claiming 100% CPU: one with a junk signature, one signed
with the wrong key, and one genuine packet whose CPU value was raised after
signing.

Point at the dashboard: **AGENT-01 still shows its real CPU.** Then the Alerts
tab: `SECURITY_INVALID_HMAC`. Then the row count in `metric_raw` — unchanged by
those three.

> "Every UDP packet is signed with HmacSHA256, and not over the raw JSON — over a
> canonical string, so the same sample always produces the same bytes whatever
> the encoder or the machine's locale. Nothing downstream of that check ever sees
> a packet that fails it: not the window, not the database, not the alert engine."

---

## 18. Replay — 1:00

```bat
scripts\run-replay-test.bat
```

A valid packet, the same packet twice more, then a fresh one. Alerts shows
`SECURITY_REPLAY_PACKET`.

> "The signature on a replayed packet is perfectly valid — it was a real packet.
> Only the sequence number tells the copy apart from the original. The client
> keeps a 64-packet sliding window per session, because UDP does not promise
> order: insisting each sequence simply be larger than the last would throw away
> genuine packets that merely arrived late."

---

## 19. History — 1:00

History tab → AGENT-01 → **Last 1 Hour** → **Load**. The label under the controls
says whether the data came from `metric_raw` or from `metric_5m`.

> "The panel asks for a time range. It never writes SQL and never knows which
> table answered."

---

## 20. Aggregation — 1:30

Explain the problem first: four agents at one sample a second is about 345 000
rows a day.

| | Production | Demo profile |
|---|---|---|
| Raw retention | 60 minutes | 60 seconds |
| Bucket | 5 minutes | 10 seconds |
| Aggregates kept | 30 days | 300 seconds |

> "Same code, different numbers. There is no demo branch anywhere in it."

If there is time, show it happening with `scripts\run-monitor-demo.bat` running
for a couple of minutes:

```sql
SELECT COUNT(*) FROM metric_raw;
SELECT agent_id, sample_count, ROUND(avg_cpu,1), ROUND(max_cpu,1) FROM metric_5m
ORDER BY bucket_start DESC LIMIT 10;
```

> "Each bucket keeps the average **and** the peak. An average alone would hide
> the spike that caused the incident — which is usually the only interesting
> thing in the bucket."

---

## Closing — 0:30

> "Four agents, one client, two protocols, signed packets, replay protection,
> automatic storage and aggregation, alerts that fire once per incident, and
> e-mail to reach someone who is not looking at the screen. 171 automated tests,
> including integration tests that drive real sockets."

Be ready for these:

| Question | Answer |
|---|---|
| Why UDP, is it not unreliable? | Losing one sample costs nothing; the next arrives a second later. Control, where loss *does* matter, is on TCP. |
| Why not sign the JSON directly? | Two encoders can order fields or format numbers differently. The canonical string makes the signature depend on the data, not on the encoder. |
| What if the clocks differ? | Packets outside a 30-second tolerance are rejected. It is configurable. |
| Could someone replay old traffic? | Only within that 30-second window, and only a packet the client never saw. Everything else hits the sequence window. |
| Why SQLite and not MySQL? | It is a desktop application. No server to install, no service to run, and the file is the whole database. |
| Where is the password? | In `config\email-secrets.properties`, outside the jar, git-ignored, never logged, and masked out of error messages. |
