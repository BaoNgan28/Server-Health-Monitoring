# Opening the project in Apache NetBeans

Group 10 — Server Health Monitoring System

---

## 1. Open it

1. Start Apache NetBeans.
2. **File → Open Project…**
3. Select the folder

   ```
   F:\Project LTM\ServerHealthMonitoring
   ```

   NetBeans shows it with the Maven project icon, because that folder holds the
   parent `pom.xml`. Select the folder itself, not one of the modules.
4. **Open Project**.

NetBeans opens it as one project with four modules:

```
ServerHealthMonitoring
   Modules
      monitoring-common
      agent-app
      monitor-app
      simulator-app
```

The first time, Maven downloads the dependencies — OSHI, Gson, SQLite JDBC,
JFreeChart, Jakarta Mail, JUnit. Wait for the progress bar at the bottom right to
finish before building. This needs an internet connection once; after that
everything is in the local repository.

---

## 2. Maven

NetBeans ships with Maven, so nothing extra has to be installed to build from
inside the IDE. To check which copy it uses: **Tools → Options → Java → Maven →
Maven Home**.

If you also want to build from a command prompt and `mvn` is not on your PATH,
`scripts\build.bat` finds the bundled copy for you.

---

## 3. Java platform

The project compiles with `--release 17`, so the produced bytecode runs on Java
17 and newer. A newer JDK is fine to build with; JDK 22 was used for the build
that produced the jars in `release\`.

Check under **Tools → Options → Java → Java Platforms** that a JDK 17 or newer is
registered.

---

## 4. Build

**Right-click `ServerHealthMonitoring` → Build with Dependencies**, or **Clean and
Build**.

That runs `mvn clean package`, which also runs the whole test suite. It produces:

```
monitor-app\target\monitor-app-1.0.0-all.jar
agent-app\target\agent-app-1.0.0-all.jar
simulator-app\target\simulator-app-1.0.0-all.jar
```

---

## 5. Main classes

| Module | Main class |
|---|---|
| monitor-app | `vn.edu.hutech.group10.monitoring.monitor.MonitoringMain` |
| agent-app | `vn.edu.hutech.group10.monitoring.agent.AgentMain` |
| simulator-app | `vn.edu.hutech.group10.monitoring.simulator.SimulatorMain` |

---

## 6. Running from the IDE

**This matters:** the applications resolve `config\`, `data\` and `logs\`
relative to the working directory. NetBeans runs a module with that module's own
folder as the working directory, so a module run straight from the IDE would look
for `agent-app\config\…` and find nothing.

Two ways to deal with it.

### 6.1 Pass the project root explicitly (recommended)

Right-click a module → **Properties → Run**, and set:

*VM Options*

```
-Dmonitoring.home=F:\Project LTM\ServerHealthMonitoring
```

*Arguments*, for the agent:

```
--config=F:\Project LTM\ServerHealthMonitoring\config\agent01.properties
```

*Arguments*, for the monitoring client:

```
--config=F:\Project LTM\ServerHealthMonitoring\config\monitor.properties
```

`monitoring.home` is what pins `config`, `data` and `logs` to the project root
wherever the process was started from.

### 6.2 Use the scripts

`scripts\run-monitor.bat` and `scripts\run-agent01.bat` already do exactly the
above. Build in NetBeans, then run the scripts — which is also how the project is
run on a machine that has no IDE at all.

---

## 7. Running the tests from the IDE

* Whole project: right-click `ServerHealthMonitoring` → **Test**.
* One module: right-click it → **Test**.
* One class: open it and press **Ctrl+F6**.

The integration tests open real sockets on the loopback interface, on ports the
operating system picks, so they do not clash with a monitoring client that
happens to be running. They create their databases in temporary folders and never
touch `data\monitoring.db`.

---

## 8. Presenting the code

Useful places to open during a demonstration:

| What | Where |
|---|---|
| The canonical string that gets signed | `monitoring-common` → `common.security.PacketCanonicalizer` |
| The validation gate every packet passes | `common.security.PacketValidator` |
| The sliding replay window | `common.security.ReplayProtector` |
| TCP length-prefix framing | `common.protocol.MessageFraming` |
| Metric collection through OSHI | `agent-app` → `agent.metrics.OshiSystemMetricsCollector` |
| Rates from counters | `agent.metrics.NetworkRateTracker` |
| The receive path | `monitor-app` → `monitor.service.TelemetryService` |
| The alert state machine | `monitor.service.AlertService` |
| Aggregation | `monitor.service.AggregationService` |
| How everything is wired | `monitor.core.MonitorApplication` |

---

## 9. If something goes wrong

| Symptom | Fix |
|---|---|
| Red badges on every file after opening | Maven has not finished resolving. Wait, then **Right-click project → Build with Dependencies**. |
| `Cannot find symbol` for OSHI, Gson, JFreeChart | Dependencies were not downloaded. Check the internet connection and build again. |
| The agent says `Agent configuration file not found` | The working directory is the module folder. Use section 6.1. |
| `Address already in use` on UDP 6000 | A monitoring client is already running. Close it, or change `monitor.udp.port`. |
| The project opens as four separate projects | The module folder was opened instead of the parent. Close them and open `ServerHealthMonitoring` itself. |

More in [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
