# Multi-machine setup

Group 10 — Server Health Monitoring System

How to run the system the way it is meant to run: one monitoring client watching
four agents, each on its own machine.

---

## 1. The scenario

| Machine | Role | IP address |
|---|---|---|
| A | Monitoring client | 192.168.1.10 |
| B | Agent 01 | 192.168.1.11 |
| C | Agent 02 | 192.168.1.12 |
| D | Agent 03 | 192.168.1.13 |
| E | Agent 04 | 192.168.1.14 |

```
   MACHINE B            MACHINE C            MACHINE D            MACHINE E
   192.168.1.11         192.168.1.12         192.168.1.13         192.168.1.14
   AGENT-01             AGENT-02             AGENT-03             AGENT-04
   TCP 5000             TCP 5000             TCP 5000             TCP 5000
        |                    |                    |                    |
        |                    |  UDP -> 192.168.1.10:6000               |
        +--------------------+---------+----------+--------------------+
                                       |
                                       v
                             MACHINE A  192.168.1.10
                             MONITORING CLIENT
                             UDP 6000 listening
                             TCP client -> each agent:5000
```

**Every agent can use TCP 5000.** The four different ports in the local demo exist
only because four agents share one machine there. Each of these machines has its
own address, so there is no clash.

Substitute your real addresses throughout. Find them with `ipconfig`.

---

## 2. What each agent machine needs

Only three things. NetBeans is not needed, and neither is Maven or the source.

1. **A JRE or JDK, version 17 or newer.** Check with `java -version`.
2. **`agent-app-1.0.0-all.jar`** — it carries every dependency inside it.
3. **That agent's properties file.**

Create this layout on each agent machine, anywhere you like, for example
`C:\ServerHealthAgent`:

```
C:\ServerHealthAgent\
    agent-app-1.0.0-all.jar
    config\
        agent01.properties
    logs\
```

`logs\` is created automatically if it is missing.

---

## 3. The agent properties file

On machine B, `config\agent01.properties`:

```properties
agent.id=AGENT-01
agent.name=SRV-HANOI-01

# The monitoring client's address, not this machine's.
monitor.host=192.168.1.10
monitor.udp.port=6000

# This machine owns port 5000, so there is no need for a different one.
agent.tcp.port=5000

metric.interval.ms=1000
heartbeat.interval.ms=5000

# Must match exactly what is registered for this agent on the monitoring client.
security.sharedSecret=GROUP10_DEMO_SECRET_01

alert.cpu.threshold=85.0
alert.memory.threshold=85.0
alert.network.in.threshold.mbps=50.0
alert.network.out.threshold.mbps=50.0
```

Machines C, D and E are identical apart from `agent.id`, `agent.name` and
`security.sharedSecret`.

Two rules:

* **`agent.id` must be unique.** It is the identity the monitoring client looks
  up to find the right secret.
* **`security.sharedSecret` must match** what is registered on the client for
  that id, character for character. A mismatch makes every packet fail its
  signature check, and the agent will simply never appear online.

Give each agent a different secret. Then a secret that leaks cannot be used to
impersonate any other agent.

---

## 4. The firewall

Windows blocks inbound connections by default, so two rules are needed. Both
scripts must be run from a PowerShell window opened with **Run as
Administrator** — the build never touches the firewall by itself.

**On the monitoring client machine (A):**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\firewall-monitor.ps1
```

Allows inbound **UDP 6000**.

**On each agent machine (B–E):**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\firewall-agent.ps1
```

Allows inbound **TCP 5000**.

Outbound traffic needs no rule, which is why the agents need only the TCP rule
even though they send UDP.

Both scripts add rules for the Domain and Private profiles. If Windows has
classified the network as Public, either change it to Private in **Settings →
Network & Internet**, or pass `-Profile Any` knowing what that exposes.

To undo a rule later: `.\scripts\firewall-agent.ps1 -Remove`.

---

## 5. Order of operations

### 5.1 Start the monitoring client (machine A)

```bat
scripts\run-monitor.bat
```

Confirm the console says `UDP Listen Port : 6000`.

### 5.2 Register the four agents

On the Dashboard tab, **Add Agent**, four times:

| Agent ID | Name | Host / IP | TCP Port | Shared Secret |
|---|---|---|---|---|
| AGENT-01 | SRV-HANOI-01 | 192.168.1.11 | 5000 | `GROUP10_DEMO_SECRET_01` |
| AGENT-02 | SRV-HANOI-02 | 192.168.1.12 | 5000 | `GROUP10_DEMO_SECRET_02` |
| AGENT-03 | SRV-HANOI-03 | 192.168.1.13 | 5000 | `GROUP10_DEMO_SECRET_03` |
| AGENT-04 | SRV-HANOI-04 | 192.168.1.14 | 5000 | `GROUP10_DEMO_SECRET_04` |

They are stored in SQLite, so this is done once.

### 5.3 Start each agent

On each agent machine:

```bat
java -jar agent-app-1.0.0-all.jar --config="config\agent01.properties"
```

The banner confirms the identity, the monitoring host and the ports. The shared
secret is deliberately absent from it.

### 5.4 Confirm

Within a few seconds all four rows show `ONLINE` with live figures.

---

## 6. Checking the network

**Can the agent reach the client?** From an agent machine:

```bat
ping 192.168.1.10
```

**Can the client reach the agent's control port?** From machine A:

```powershell
Test-NetConnection 192.168.1.11 -Port 5000
```

`TcpTestSucceeded : True` means the agent is listening and the firewall allows
it. `False` means the agent is not running, the port is wrong, or the firewall
rule is missing.

**Is UDP getting through?** Nothing answers a UDP probe, so there is no
equivalent command. Verify it through the three things that do change:

1. the agent's `logs\agent-agent01-0.log` shows it sending without errors;
2. the monitoring client's status bar shows the received counter rising;
3. the metric appears in the window.

If the agent is sending and the counter never moves, the datagrams are being
dropped between the two — almost always the firewall on machine A.

---

## 7. What to copy where

| File | Machine A | Machines B–E |
|---|---|---|
| `monitor-app-1.0.0-all.jar` | yes | no |
| `agent-app-1.0.0-all.jar` | no | yes |
| `simulator-app-1.0.0-all.jar` | optional | no |
| `config\monitor.properties` | yes | no |
| `config\email-secrets.properties` | yes | no |
| `config\agentNN.properties` | no | one each |
| `data\` | created automatically | not used |
| `logs\` | created automatically | created automatically |

The database and every alert live only on the monitoring client. Agents keep no
state at all, which is why an agent machine needs nothing but a JRE.

---

## 8. Common problems

| Symptom | Cause |
|---|---|
| An agent stays `UNKNOWN` for ever | The datagrams are not arriving: firewall on machine A, wrong `monitor.host`, or different subnets. |
| An agent appears then goes `OFFLINE` and back | Packet loss on the link, or the agent machine sleeping. Check the agent's log for send errors. |
| Every packet is rejected, security warnings in the log | The shared secret does not match what is registered for that `agent.id`. |
| Packets rejected with a timestamp complaint | The two machines' clocks differ by more than 30 seconds. Enable automatic time on both, or raise `packet.timestamp.tolerance.ms`. |
| Connect works, Configure fails | `Test-NetConnection <agent-ip> -Port 5000` from machine A; usually the agent's firewall rule. |
| Two agents share one row | Two machines were given the same `agent.id`. Each must be unique. |

More in [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
