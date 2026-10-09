-- Server Health Monitoring System - Group 10
-- SQLite schema. Applied on every start; every statement is idempotent, so an
-- existing database is upgraded in place rather than recreated.

CREATE TABLE IF NOT EXISTS agents (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_id                TEXT    NOT NULL UNIQUE,
    name                    TEXT    NOT NULL,
    host                    TEXT    NOT NULL,
    tcp_port                INTEGER NOT NULL,
    shared_secret           TEXT    NOT NULL,
    cpu_threshold           REAL    NOT NULL DEFAULT 85.0,
    memory_threshold        REAL    NOT NULL DEFAULT 85.0,
    network_in_threshold    REAL    NOT NULL DEFAULT 50.0,
    network_out_threshold   REAL    NOT NULL DEFAULT 50.0,
    status                  TEXT    NOT NULL DEFAULT 'UNKNOWN',
    last_seen               INTEGER NOT NULL DEFAULT 0,
    created_at              INTEGER NOT NULL,
    updated_at              INTEGER NOT NULL
);

-- One row per accepted telemetry packet. Kept for the raw retention window,
-- then folded into metric_5m and deleted.
CREATE TABLE IF NOT EXISTS metric_raw (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_id           TEXT    NOT NULL,
    timestamp          INTEGER NOT NULL,
    cpu_usage          REAL    NOT NULL,
    memory_usage       REAL    NOT NULL,
    connection_count   INTEGER NOT NULL,
    uptime_seconds     INTEGER NOT NULL,
    network_in_bps     REAL    NOT NULL,
    network_out_bps    REAL    NOT NULL,
    FOREIGN KEY (agent_id) REFERENCES agents (agent_id) ON DELETE CASCADE
);

-- One row per agent per aggregation bucket. The UNIQUE constraint is what makes
-- a bucket impossible to write twice, even if the job runs twice over the same
-- window after a restart.
CREATE TABLE IF NOT EXISTS metric_5m (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_id                TEXT    NOT NULL,
    bucket_start            INTEGER NOT NULL,
    sample_count            INTEGER NOT NULL,
    avg_cpu                 REAL    NOT NULL,
    max_cpu                 REAL    NOT NULL,
    avg_memory              REAL    NOT NULL,
    max_memory              REAL    NOT NULL,
    avg_connections         REAL    NOT NULL,
    avg_network_in_bps      REAL    NOT NULL,
    max_network_in_bps      REAL    NOT NULL,
    avg_network_out_bps     REAL    NOT NULL,
    max_network_out_bps     REAL    NOT NULL,
    UNIQUE (agent_id, bucket_start),
    FOREIGN KEY (agent_id) REFERENCES agents (agent_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS alerts (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_id        TEXT    NOT NULL,
    alert_type      TEXT    NOT NULL,
    severity        TEXT    NOT NULL,
    message         TEXT,
    opened_at       INTEGER NOT NULL,
    resolved_at     INTEGER,
    status          TEXT    NOT NULL,
    current_value   REAL    NOT NULL DEFAULT 0,
    threshold       REAL    NOT NULL DEFAULT 0,
    unit            TEXT,
    FOREIGN KEY (agent_id) REFERENCES agents (agent_id) ON DELETE CASCADE
);

-- History queries always filter by agent and then scan a time range.
CREATE INDEX IF NOT EXISTS idx_metric_raw_agent_time ON metric_raw (agent_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_metric_5m_agent_bucket ON metric_5m (agent_id, bucket_start);

-- The alert engine looks up "is there an open alert of this type for this agent"
-- on every sample, so this index is on the hot path.
CREATE INDEX IF NOT EXISTS idx_alerts_agent_status ON alerts (agent_id, status);
CREATE INDEX IF NOT EXISTS idx_alerts_opened_at ON alerts (opened_at);
