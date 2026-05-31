-- Schema initialization script for Docker entrypoint
-- This runs automatically when the TimescaleDB container starts

CREATE EXTENSION IF NOT EXISTS timescaledb;

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

SELECT create_hypertable('risk_signals', 'time', if_not_exists => TRUE, migrate_data => TRUE);

CREATE INDEX IF NOT EXISTS idx_risk_signals_ticker_time ON risk_signals (ticker, time DESC);
CREATE INDEX IF NOT EXISTS idx_risk_signals_status ON risk_signals (status);

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
