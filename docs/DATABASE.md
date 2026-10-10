# Database

Group 10 — Server Health Monitoring System

SQLite, one file, created with its schema the first time the monitoring client
starts. Nothing has to be prepared by hand and no database server is involved.

| | |
|---|---|
| Production file | `data\monitoring.db` |
| Demo profile file | `data\monitoring-demo.db` |
| Schema | `monitor-app/src/main/resources/db/schema.sql` |

The demo profile uses a separate file on purpose, so demonstrating the system
cannot disturb collected data.

---

## 1. Tables

### 1.1 `agents`

Every agent the client knows about.

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | primary key, autoincrement |
| `agent_id` | TEXT | **UNIQUE**; the identity that signs packets |
| `name` | TEXT | display name |
| `host` | TEXT | address of the agent's TCP control channel |
| `tcp_port` | INTEGER | control port |
| `shared_secret` | TEXT | key its UDP packets are signed with |
| `cpu_threshold` | REAL | percent, default 85 |
| `memory_threshold` | REAL | percent, default 85 |
| `network_in_threshold` | REAL | Mbps, default 50 |
| `network_out_threshold` | REAL | Mbps, default 50 |
| `status` | TEXT | `UNKNOWN`, `ONLINE`, `WARNING`, `OFFLINE` |
| `last_seen` | INTEGER | epoch ms of the last accepted packet |
| `created_at`, `updated_at` | INTEGER | epoch ms |

`shared_secret` is never shown in the agent table, never logged and never sent
anywhere. The dialog that edits it uses a password field.

A stored `status` is history, not evidence: at startup every agent is reset to
`UNKNOWN` until a packet arrives, so a client restarted after a week does not
claim an agent is online because it was online when the client last closed.

### 1.2 `metric_raw`

One row per accepted telemetry packet.

| Column | Type |
|---|---|
| `id` | INTEGER primary key |
| `agent_id` | TEXT → `agents(agent_id)` ON DELETE CASCADE |
| `timestamp` | INTEGER, epoch ms |
| `cpu_usage` | REAL, percent |
| `memory_usage` | REAL, percent |
| `connection_count` | INTEGER |
| `uptime_seconds` | INTEGER |
| `network_in_bps` | REAL, bytes per second |
| `network_out_bps` | REAL, bytes per second |

At one sample per second, four agents produce about 345 000 rows a day, which is
exactly why this table is not kept for ever.

### 1.3 `metric_5m`

One row per agent per aggregation bucket.

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | primary key |
| `agent_id` | TEXT | → `agents(agent_id)` ON DELETE CASCADE |
| `bucket_start` | INTEGER | epoch ms of the first moment in the bucket |
| `sample_count` | INTEGER | how many raw samples were folded in |
| `avg_cpu`, `max_cpu` | REAL | |
| `avg_memory`, `max_memory` | REAL | |
| `avg_connections` | REAL | |
| `avg_network_in_bps`, `max_network_in_bps` | REAL | |
| `avg_network_out_bps`, `max_network_out_bps` | REAL | |

`UNIQUE (agent_id, bucket_start)` — this is what makes a bucket impossible to
write twice, even if the aggregation job runs again over the same window after a
restart.

Both the average **and** the peak are kept. An average alone would hide the spike
that caused the incident, which is usually the only interesting thing in the
whole bucket.

### 1.4 `alerts`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | primary key |
| `agent_id` | TEXT | → `agents(agent_id)` ON DELETE CASCADE |
| `alert_type` | TEXT | `CPU_HIGH`, `MEMORY_HIGH`, `NETWORK_IN_HIGH`, `NETWORK_OUT_HIGH`, `AGENT_OFFLINE`, `AGENT_RECOVERED`, `SECURITY_INVALID_HMAC`, `SECURITY_REPLAY_PACKET` |
| `severity` | TEXT | `INFO`, `WARNING`, `CRITICAL` |
| `message` | TEXT | human readable |
| `opened_at` | INTEGER | epoch ms |
| `resolved_at` | INTEGER | null while still open |
| `status` | TEXT | `OPEN` or `RESOLVED` |
| `current_value` | REAL | the value that tripped it |
| `threshold` | REAL | what it was compared against |
| `unit` | TEXT | `%`, `Mbps`, or empty |

For `AGENT_OFFLINE`, `current_value` holds the last-seen timestamp and
`threshold` holds the timeout in milliseconds; that is what the offline e-mail
reads to say when the agent was last heard from.

---

## 2. Indexes

```sql
CREATE INDEX idx_metric_raw_agent_time  ON metric_raw (agent_id, timestamp);
CREATE INDEX idx_metric_5m_agent_bucket ON metric_5m  (agent_id, bucket_start);
CREATE INDEX idx_alerts_agent_status    ON alerts     (agent_id, status);
CREATE INDEX idx_alerts_opened_at       ON alerts     (opened_at);
```

Each one matches a query the application actually makes. History always filters
by agent and then scans a time range, which is exactly the leading-column order
of the first two. The alert engine asks "is there an open alert of this type for
this agent" on every single sample, so `(agent_id, status)` sits on the hot path.

---

## 3. Relationships

```
  agents 1 ----- * metric_raw     ON DELETE CASCADE
  agents 1 ----- * metric_5m      ON DELETE CASCADE
  agents 1 ----- * alerts         ON DELETE CASCADE
```

Deleting an agent removes its metrics and its alerts with it, so the database
cannot accumulate rows nothing can be joined back to. Foreign keys are off by
default in SQLite and are switched on per connection.

---

## 4. Connection settings

Applied on the single connection when it is opened:

| Pragma | Value | Why |
|---|---|---|
| `foreign_keys` | `ON` | the cascades above only work when this is on |
| `journal_mode` | `WAL` | a history query can read while telemetry writes |
| `busy_timeout` | `5000` | wait rather than fail if the file is briefly locked |
| `synchronous` | `NORMAL` | safe under WAL, and much faster than `FULL` |

**Concurrency.** One connection is shared and every unit of work runs inside
`DatabaseManager.execute`, which holds a single lock. That makes the writer count
exactly one by construction, which is the shape SQLite likes best. On top of the
driver's busy timeout there is a small bounded retry for `database is locked`,
which can still happen if another process has the file open.

Every statement and result set is opened in a try-with-resources block, so
nothing leaks even when a query fails. `inTransaction` commits on success and
rolls back on any failure, including a runtime exception.

---

## 5. Retention and aggregation

Per-second detail is far more than anyone needs beyond the last hour, so raw
samples are folded into fixed-width buckets and buckets eventually expire.

| | Production | Demo profile |
|---|---|---|
| Raw retention | 60 minutes | 60 seconds |
| Bucket width | 5 minutes | 10 seconds |
| Aggregate retention | 30 days | 300 seconds |
| Job interval | 300 seconds | 15 seconds |

One pass does exactly this:

1. compute `cutoff = now - rawRetention`, **aligned down to a bucket boundary**;
2. `INSERT OR IGNORE INTO metric_5m ... SELECT ... FROM metric_raw WHERE
   timestamp < cutoff GROUP BY agent_id, bucket_start`;
3. `DELETE FROM metric_raw WHERE timestamp < cutoff`;
4. `DELETE FROM metric_5m WHERE bucket_start < now - aggregateRetention`.

Steps 2 and 3 are one transaction, so detail is never dropped without its summary
existing. Aligning the cutoff down means a bucket that is still filling keeps its
raw rows until the next run, so no bucket is ever summarised half-finished. The
`INSERT OR IGNORE` plus the unique index make the whole pass safe to repeat.

The two profiles differ only in the numbers above. There is no demo-specific
branch anywhere in the code — `AggregationService` reads them from
`MonitorConfig`, and the unit tests drive the same method with fabricated
timestamps to check a 5-minute bucket and a 10-second bucket through the same
path.

---

## 6. Which table answers a history question

`HistoryService` decides, and the panel never knows:

| Requested range | Source |
|---|---|
| starts inside the raw retention window | `metric_raw`, every sample |
| older than that | `metric_5m`, bucket averages |

If the aggregated table is empty — a fresh database, before the first job has run
— the raw table is tried as a fallback so the panel is not blank. Long ranges are
down-sampled before plotting, because drawing more points than the screen has
pixels costs time and shows nothing.

---

## 7. Looking inside the database

The file is a normal SQLite database; any SQLite tool will open it. For example:

```sql
SELECT agent_id, status, datetime(last_seen/1000, 'unixepoch', 'localtime')
FROM agents;

SELECT COUNT(*) FROM metric_raw;

SELECT agent_id, datetime(bucket_start/1000,'unixepoch','localtime'),
       sample_count, ROUND(avg_cpu,1), ROUND(max_cpu,1)
FROM metric_5m ORDER BY bucket_start DESC LIMIT 20;

SELECT alert_type, agent_id, severity, status,
       datetime(opened_at/1000,'unixepoch','localtime'), message
FROM alerts ORDER BY opened_at DESC LIMIT 20;
```

Close the monitoring client first, or open the file read-only: the client holds
the connection while it runs.
