package com.jitraj.riskengine.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.jitraj.riskengine.metrics.MetricsService;
import com.jitraj.riskengine.model.RiskSignal;

/**
 * Persistence layer for risk_signals hypertable in TimescaleDB.
 * Uses INSERT ... ON CONFLICT (event_id) DO NOTHING for idempotent writes.
 * Supports batched inserts and time-range queries.
 */
@Repository
public class SignalRepository {

    private static final Logger log = LoggerFactory.getLogger(SignalRepository.class);

    private static final String INSERT_SQL = """
            INSERT INTO risk_signals (event_id, time, ticker, raw_score, risk_score, label, latency_ms, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private static final String SELECT_LATEST = """
            SELECT event_id, time, ticker, raw_score, risk_score, label, latency_ms, status
            FROM risk_signals
            ORDER BY time DESC
            LIMIT ?
            """;

    private static final String SELECT_BY_TICKER_AND_TIME = """
            SELECT event_id, time, ticker, raw_score, risk_score, label, latency_ms, status
            FROM risk_signals
            WHERE ticker = ? AND time >= ? AND time <= ?
            ORDER BY time DESC
            LIMIT ?
            """;

    private static final String SELECT_BY_EVENT_ID = """
            SELECT event_id, time, ticker, raw_score, risk_score, label, latency_ms, status
            FROM risk_signals
            WHERE event_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MetricsService metricsService;

    @Value("${repository.batch-size:100}")
    private int batchSize = 100;

    @Value("${repository.max-retries:3}")
    private int maxRetries = 3;

    @Value("${repository.max-backoff-ms:5000}")
    private long maxBackoffMs = 5000L;

    private final RowMapper<RiskSignal> rowMapper = new RiskSignalRowMapper();

    // Batch buffer
    private final List<RiskSignal> batchBuffer = new ArrayList<>();

    public SignalRepository(JdbcTemplate jdbcTemplate, MetricsService metricsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricsService = metricsService;
    }

    /**
     * Saves a single risk signal. If batch buffering is in effect,
     * the signal is buffered and flushed when the batch is full.
     */
    public void save(RiskSignal signal) {
        synchronized (batchBuffer) {
            batchBuffer.add(signal);
            if (batchBuffer.size() >= batchSize) {
                flushBatch();
            }
        }
    }

    /**
     * Immediately saves a single risk signal without batching.
     */
    public void saveImmediate(RiskSignal signal) {
        executeWithRetry(signal);
    }

    /**
     * Flushes any buffered signals to the database.
     */
    public void flushBatch() {
        List<RiskSignal> toFlush;
        synchronized (batchBuffer) {
            if (batchBuffer.isEmpty()) return;
            toFlush = new ArrayList<>(batchBuffer);
            batchBuffer.clear();
        }

        for (RiskSignal signal : toFlush) {
            executeWithRetry(signal);
        }
    }

    /**
     * Executes an insert with exponential backoff retry.
     */
    private void executeWithRetry(RiskSignal signal) {
        int attempt = 0;
        long backoffMs = 100;

        while (attempt < maxRetries) {
            try {
                jdbcTemplate.update(INSERT_SQL,
                        signal.getEventId(),
                        Timestamp.from(signal.getTime()),
                        signal.getTicker(),
                        signal.getRawScore(),
                        signal.getRiskScore(),
                        signal.getLabel(),
                        signal.getLatencyMs(),
                        signal.getStatus()
                );
                metricsService.incrementDbWriteSuccess();
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxRetries) {
                    log.error("DB write failed after {} retries for eventId={}", maxRetries, signal.getEventId(), e);
                    metricsService.incrementDbWriteFailures();
                    return;
                }
                log.warn("DB write attempt {}/{} failed for eventId={}, retrying in {}ms",
                        attempt, maxRetries, signal.getEventId(), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
            }
        }
    }

    /**
     * Returns the most recent N signals ordered by time descending.
     */
    public List<RiskSignal> findLatest(int limit) {
        return jdbcTemplate.query(SELECT_LATEST, rowMapper, limit);
    }

    /**
     * Returns signals filtered by ticker within a closed [from, to] time range.
     */
    public List<RiskSignal> findByTickerAndTimeRange(String ticker, Instant from, Instant to, int limit) {
        return jdbcTemplate.query(SELECT_BY_TICKER_AND_TIME, rowMapper,
                ticker, Timestamp.from(from), Timestamp.from(to), limit);
    }

    /**
     * Returns a single signal by event ID, or empty if not found.
     */
    public Optional<RiskSignal> findByEventId(String eventId) {
        List<RiskSignal> results = jdbcTemplate.query(SELECT_BY_EVENT_ID, rowMapper, eventId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Checks database connectivity.
     */
    public boolean isHealthy() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Row mapper for RiskSignal.
     */
    private static class RiskSignalRowMapper implements RowMapper<RiskSignal> {
        @Override
        public RiskSignal mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RiskSignal(
                    rs.getString("event_id"),
                    rs.getTimestamp("time").toInstant(),
                    rs.getString("ticker"),
                    rs.getObject("raw_score") != null ? rs.getDouble("raw_score") : null,
                    rs.getObject("risk_score") != null ? rs.getDouble("risk_score") : null,
                    rs.getString("label"),
                    rs.getObject("latency_ms") != null ? rs.getDouble("latency_ms") : null,
                    rs.getString("status")
            );
        }
    }
}
