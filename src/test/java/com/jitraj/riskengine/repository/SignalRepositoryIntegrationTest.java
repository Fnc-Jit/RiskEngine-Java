package com.jitraj.riskengine.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jitraj.riskengine.metrics.MetricsService;
import com.jitraj.riskengine.model.RiskSignal;
import com.jitraj.riskengine.scoring.ScoringService;

/**
 * Integration tests for SignalRepository.
 *
 * Runs against a real PostgreSQL instance via Testcontainers so that the
 * production SQL — including the PostgreSQL-specific
 * {@code INSERT ... ON CONFLICT (event_id) DO NOTHING} idempotency clause —
 * is exercised exactly as it runs against TimescaleDB in production.
 *
 * The test is skipped automatically when no Docker daemon is available
 * (disabledWithoutDocker = true), so CI/local builds remain green on machines
 * without Docker while still validating real SQL where Docker is present.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SignalRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("riskengine_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MetricsService metricsService;

    @MockBean
    private ScoringService scoringService;

    private SignalRepository repository;

    @BeforeEach
    void setUp() {
        // Plain table mirroring the production schema. The hypertable conversion
        // is a TimescaleDB concern and is not required for repository-level SQL tests.
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS risk_signals (
                    event_id TEXT PRIMARY KEY,
                    time TIMESTAMP WITH TIME ZONE NOT NULL,
                    ticker TEXT NOT NULL,
                    raw_score DOUBLE PRECISION,
                    risk_score DOUBLE PRECISION,
                    label TEXT,
                    latency_ms DOUBLE PRECISION,
                    status TEXT NOT NULL
                )
                """);
        jdbcTemplate.execute("DELETE FROM risk_signals");

        repository = new SignalRepository(jdbcTemplate, metricsService);
    }

    @Test
    @DisplayName("Should insert and retrieve a signal by eventId")
    void shouldInsertAndRetrieve() {
        RiskSignal signal = createSignal("evt-001", "AAPL", Instant.now(), 72.5, "ELEVATED", "SCORED");
        repository.saveImmediate(signal);

        Optional<RiskSignal> result = repository.findByEventId("evt-001");

        assertTrue(result.isPresent());
        assertEquals("evt-001", result.get().getEventId());
        assertEquals("AAPL", result.get().getTicker());
        assertEquals(72.5, result.get().getRiskScore(), 0.01);
        assertEquals("ELEVATED", result.get().getLabel());
        assertEquals("SCORED", result.get().getStatus());
    }

    @Test
    @DisplayName("Should handle idempotent insert on duplicate eventId")
    void shouldHandleDuplicateIdempotently() {
        Instant now = Instant.now();
        RiskSignal signal1 = createSignal("evt-dup", "AAPL", now, 72.5, "ELEVATED", "SCORED");
        RiskSignal signal2 = createSignal("evt-dup", "AAPL", now, 90.0, "CRITICAL", "SCORED");

        repository.saveImmediate(signal1);
        repository.saveImmediate(signal2); // Should be ignored (ON CONFLICT DO NOTHING)

        Optional<RiskSignal> result = repository.findByEventId("evt-dup");
        assertTrue(result.isPresent());
        // Should still have original score, not the duplicate's
        assertEquals(72.5, result.get().getRiskScore(), 0.01);
    }

    @Test
    @DisplayName("Should return empty when eventId not found")
    void shouldReturnEmptyForMissingEvent() {
        Optional<RiskSignal> result = repository.findByEventId("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should retrieve latest signals ordered by time descending")
    void shouldRetrieveLatestSignals() {
        Instant base = Instant.parse("2026-05-30T10:00:00Z");

        repository.saveImmediate(createSignal("evt-a", "AAPL", base, 30.0, "NORMAL", "SCORED"));
        repository.saveImmediate(createSignal("evt-b", "GOOG", base.plus(1, ChronoUnit.MINUTES), 55.0, "ELEVATED", "SCORED"));
        repository.saveImmediate(createSignal("evt-c", "MSFT", base.plus(2, ChronoUnit.MINUTES), 90.0, "CRITICAL", "SCORED"));

        List<RiskSignal> latest = repository.findLatest(2);

        assertEquals(2, latest.size());
        assertEquals("evt-c", latest.get(0).getEventId()); // Most recent first
        assertEquals("evt-b", latest.get(1).getEventId());
    }

    @Test
    @DisplayName("Should filter by ticker and time range")
    void shouldFilterByTickerAndTimeRange() {
        Instant from = Instant.parse("2026-05-30T10:00:00Z");
        Instant to = Instant.parse("2026-05-30T11:00:00Z");

        repository.saveImmediate(createSignal("evt-1", "AAPL", from.plus(10, ChronoUnit.MINUTES), 40.0, "NORMAL", "SCORED"));
        repository.saveImmediate(createSignal("evt-2", "AAPL", from.plus(30, ChronoUnit.MINUTES), 60.0, "ELEVATED", "SCORED"));
        repository.saveImmediate(createSignal("evt-3", "GOOG", from.plus(20, ChronoUnit.MINUTES), 50.0, "ELEVATED", "SCORED"));
        repository.saveImmediate(createSignal("evt-4", "AAPL", from.minus(10, ChronoUnit.MINUTES), 20.0, "NORMAL", "SCORED")); // Outside range

        List<RiskSignal> results = repository.findByTickerAndTimeRange("AAPL", from, to, 100);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(s -> "AAPL".equals(s.getTicker())));
    }

    @Test
    @DisplayName("Should persist signals with all three statuses")
    void shouldPersistAllStatuses() {
        Instant now = Instant.now();

        repository.saveImmediate(createSignal("evt-scored", "AAPL", now, 50.0, "ELEVATED", "SCORED"));
        repository.saveImmediate(new RiskSignal("evt-failed", now, "TSLA", null, null, null, null, "FAILED"));
        repository.saveImmediate(new RiskSignal("evt-warmup", now, "GOOG", null, null, null, null, "WARMING_UP"));

        assertEquals("SCORED", repository.findByEventId("evt-scored").get().getStatus());
        assertEquals("FAILED", repository.findByEventId("evt-failed").get().getStatus());
        assertEquals("WARMING_UP", repository.findByEventId("evt-warmup").get().getStatus());
    }

    @Test
    @DisplayName("Should report healthy when DB is available")
    void shouldReportHealthy() {
        assertTrue(repository.isHealthy());
    }

    private RiskSignal createSignal(String eventId, String ticker, Instant time,
                                     double riskScore, String label, String status) {
        return new RiskSignal(eventId, time, ticker, -0.5, riskScore, label, 12.3, status);
    }
}
