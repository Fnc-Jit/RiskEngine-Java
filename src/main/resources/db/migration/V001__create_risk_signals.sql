-- TimescaleDB schema for RiskEngine
-- This migration creates the risk_signals hypertable and required indexes.

-- Create the main risk signals table
CREATE TABLE IF NOT EXISTS risk_signals (
    event_id TEXT PRIMARY KEY,
    time TIMESTAMPTZ NOT NULL,
    ticker TEXT NOT NULL,
    raw_score DOUBLE PRECISION,
    risk_score DOUBLE PRECISION,
    label TEXT,
    latency_ms DOUBLE PRECISION,
    status TEXT NOT NULL
);

-- Convert to TimescaleDB hypertable (keyed on event time)
-- The migrate_data option handles existing rows if the table already has data
SELECT create_hypertable('risk_signals', 'time', if_not_exists => TRUE, migrate_data => TRUE);

-- Index for efficient ticker + time range queries
CREATE INDEX IF NOT EXISTS idx_risk_signals_ticker_time ON risk_signals (ticker, time DESC);

-- Index for status filtering
CREATE INDEX IF NOT EXISTS idx_risk_signals_status ON risk_signals (status);

-- Optional benchmark runs table
CREATE TABLE IF NOT EXISTS benchmark_runs (
    run_id TEXT PRIMARY KEY,
    executed_at TIMESTAMPTZ NOT NULL,
    throughput_eps DOUBLE PRECISION,
    p50_ms DOUBLE PRECISION,
    p95_ms DOUBLE PRECISION,
    p99_ms DOUBLE PRECISION,
    error_rate DOUBLE PRECISION,
    notes TEXT
);
