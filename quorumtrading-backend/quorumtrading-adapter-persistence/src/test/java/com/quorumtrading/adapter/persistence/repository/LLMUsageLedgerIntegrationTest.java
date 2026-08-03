package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.application.port.out.LLMUsageRepository.DailyUsageRecord;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UsageSource;
import com.quorumtrading.model.valueobject.UserId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterisation tests for the spend ledger read path — the three-way UNION over
 * {@code consensus_decisions}, {@code research_executions} and {@code llm_usage_log} that every
 * money figure on {@code /settings/usage} and {@code /settings/budget} is derived from.
 *
 * <p>This read path had <strong>no test at all</strong> before this class. It is the arithmetic
 * behind a number that makes a claim about the user's money, so it is pinned here: the union sum,
 * the inclusive window semantics, and — the reason this file exists — which calendar day a row is
 * bucketed into.</p>
 *
 * <h2>The two column types are not interchangeable</h2>
 * <p>The three sources do not agree on how they store time, and that difference decides what
 * correct SQL looks like for each:</p>
 * <ul>
 *   <li>{@code consensus_decisions.created_at} and {@code llm_usage_log.created_at} are
 *       {@code timestamptz}. {@code DATE(x)} on those resolves in the <em>session</em> timezone,
 *       so the bucket moves when Postgres is not running UTC.</li>
 *   <li>{@code research_executions.executed_at} is {@code timestamp without time zone} holding a
 *       UTC instant. {@code DATE(x)} on it is already session-independent, and applying
 *       {@code AT TIME ZONE 'UTC'} to it would <em>introduce</em> the very shift being fixed
 *       elsewhere.</li>
 * </ul>
 *
 * <p>See {@code docs/adr/0003-a-day-starts-at-midnight-utc.md}.</p>
 */
@QuarkusTest
@DisplayName("LLM usage ledger — the three-way union read path")
class LLMUsageLedgerIntegrationTest {

    /**
     * A session zone far enough east that a late-evening UTC instant falls on the following
     * calendar day locally. UTC+14 is the largest offset in use, which makes the divergence
     * unambiguous rather than marginal.
     */
    private static final String EASTERN_SESSION_ZONE = "Pacific/Kiritimati";

    @Inject
    LLMUsageRepository llmUsageRepository;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    EntityManager em;

    @Inject
    MeterRegistry meterRegistry;

    private UserId userId;
    private SimulationId simulationId;
    private UUID researchId;

    @BeforeEach
    @Transactional
    void setUp() {
        // A fresh owner per test keeps the union sums free of any other test's fixtures.
        userId = new UserId(UUID.randomUUID());
        em.createNativeQuery("INSERT INTO users (id, email) VALUES (:id, :email)")
            .setParameter("id", userId.value())
            .setParameter("email", "ledger-" + userId.value() + "@test.local")
            .executeUpdate();

        Strategy strategy = strategyRepository.save(new Strategy(
            new StrategyId(UUID.randomUUID()),
            "Ledger fixture strategy",
            "## Ledger fixture",
            userId
        ));

        Simulation simulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Ledger fixture simulation",
            strategy.getId(),
            Instant.now().minus(30, ChronoUnit.DAYS),
            Instant.now(),
            new Money(BigDecimal.valueOf(100000), Money.Currency.USD)
        );
        simulation.setOwnerUserId(userId);
        simulationId = simulationRepository.save(simulation).getSimulationId();

        researchId = UUID.randomUUID();
        em.createNativeQuery("""
            INSERT INTO market_research (id, name, interval_amount, interval_unit, status, owner_id)
            VALUES (:id, 'Ledger fixture research', 1, 'DAYS', 'ACTIVE', :owner)
            """)
            .setParameter("id", researchId)
            .setParameter("owner", userId.value())
            .executeUpdate();
    }

    /**
     * These fixtures are written with native SQL rather than through the domain repositories, so
     * they carry only the columns this read path reads. That is fine for the queries under test
     * but not for a repository elsewhere doing {@code findAll} and reconstructing every row it
     * finds — so the rows must not outlive the test that made them.
     */
    @AfterEach
    @Transactional
    void tearDown() {
        deleteWhere("DELETE FROM research_executions WHERE market_research_id = :id", researchId);
        deleteWhere("DELETE FROM market_research WHERE id = :id", researchId);
        deleteWhere("DELETE FROM consensus_decisions WHERE simulation_id = :id", simulationId.value());
        deleteWhere("DELETE FROM simulations WHERE id = :id", simulationId.value());
        deleteWhere("DELETE FROM llm_usage_log WHERE user_id = :id", userId.value());
        deleteWhere("DELETE FROM orchestrator_strategies WHERE owner_id = :id", userId.value());
        deleteWhere("DELETE FROM users WHERE id = :id", userId.value());
    }

    private void deleteWhere(String sql, UUID id) {
        em.createNativeQuery(sql).setParameter("id", id).executeUpdate();
    }

    // ---------------------------------------------------------------- fixtures
    // No @Transactional on these: they are self-invoked, so the annotation would be inert.
    // Every test method carries it instead, and these run inside that transaction.

    /** A consensus decision costing {@code cost}, stamped at an exact UTC instant. */
    private void givenConsensusDecision(Instant at, String cost, int tokens) {
        em.createNativeQuery("""
            INSERT INTO consensus_decisions
                (simulation_id, decision_timestamp, llm_decision_ids, final_action,
                 created_at, raw_cost_usd, total_tokens)
            VALUES (:sim, :at, ARRAY[]::bigint[], CAST('{}' AS jsonb), :at, :cost, :tokens)
            """)
            .setParameter("sim", simulationId.value())
            .setParameter("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
            .setParameter("cost", new BigDecimal(cost))
            .setParameter("tokens", tokens)
            .executeUpdate();
    }

    /**
     * A research execution costing {@code cost}. {@code executed_at} is a naive timestamp column,
     * so the instant is written as its UTC wall-clock reading — which is what the application
     * layer stores there too.
     */
    private void givenResearchExecution(Instant at, String cost, int tokens) {
        em.createNativeQuery("""
            INSERT INTO research_executions
                (id, market_research_id, outcome, outcome_reasoning, executed_at,
                 raw_cost_usd, total_tokens)
            VALUES (:id, :research, 'NO_OPPORTUNITY', 'fixture', :at, :cost, :tokens)
            """)
            .setParameter("id", UUID.randomUUID())
            .setParameter("research", researchId)
            .setParameter("at", LocalDateTime.ofInstant(at, ZoneOffset.UTC))
            .setParameter("cost", new BigDecimal(cost))
            .setParameter("tokens", tokens)
            .executeUpdate();
    }

    /** A directly-logged ledger row costing {@code cost}, stamped at an exact UTC instant. */
    private void givenLedgerRow(Instant at, String cost, int tokens) {
        em.createNativeQuery("""
            INSERT INTO llm_usage_log (user_id, source, raw_cost_usd, total_tokens, created_at)
            VALUES (:user, :source, :cost, :tokens, :at)
            """)
            .setParameter("user", userId.value())
            .setParameter("source", UsageSource.CHAT.name())
            .setParameter("cost", new BigDecimal(cost))
            .setParameter("tokens", tokens)
            .setParameter("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
            .executeUpdate();
    }

    private void setSessionZone(String zone) {
        // SET LOCAL is undone when this transaction ends, so the pooled connection is not polluted.
        em.createNativeQuery("SET LOCAL TIME ZONE '" + zone + "'").executeUpdate();
    }

    private static Instant utcNoonOn(LocalDate day) {
        return day.atTime(12, 0).toInstant(ZoneOffset.UTC);
    }

    private static Instant lateUtcEveningOn(LocalDate day) {
        return day.atTime(23, 30).toInstant(ZoneOffset.UTC);
    }

    // ---------------------------------------------------------------- the union

    @Test
    @Transactional
    @DisplayName("one day's cost is the sum of all three sources, not any one of them")
    void unionSumsAllThreeSources() {
        LocalDate day = LocalDate.of(2026, 7, 25);
        givenConsensusDecision(utcNoonOn(day), "0.01000000", 100);
        givenResearchExecution(utcNoonOn(day), "0.02000000", 200);
        givenLedgerRow(utcNoonOn(day), "0.04000000", 400);

        List<DailyUsageRecord> daily = llmUsageRepository.getDailyUsage(userId, day, day);

        assertThat(daily).hasSize(1);
        assertThat(daily.get(0).date()).isEqualTo(day);
        assertThat(daily.get(0).rawCostUsd())
            .as("consensus + research + ledger are summed into one day")
            .isEqualByComparingTo("0.07000000");
        assertThat(daily.get(0).totalTokens()).isEqualTo(700);

        assertThat(llmUsageRepository.getTotalRawCost(userId, day, day))
            .as("the totals query sums the same three sources as the daily query")
            .isEqualByComparingTo("0.07000000");
    }

    @Test
    @Transactional
    @DisplayName("raw cost keeps its full 8-decimal precision through the union")
    void unionPreservesEightDecimalPrecision() {
        LocalDate day = LocalDate.of(2026, 7, 25);
        givenLedgerRow(utcNoonOn(day), "0.12864991", 1234);

        assertThat(llmUsageRepository.getTotalRawCost(userId, day, day))
            .as("the audited install's ledger sum survives the read path unrounded")
            .isEqualByComparingTo("0.12864991");
    }

    @Test
    @Transactional
    @DisplayName("another user's spend never appears in this user's ledger")
    void unionIsScopedToTheOwner() {
        LocalDate day = LocalDate.of(2026, 7, 25);
        givenLedgerRow(utcNoonOn(day), "0.05000000", 500);

        UserId stranger = new UserId(UUID.randomUUID());
        assertThat(llmUsageRepository.getTotalRawCost(stranger, day, day)).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- window semantics

    @Test
    @Transactional
    @DisplayName("the window includes both endpoints and excludes the days either side")
    void windowIsInclusiveOnBothEnds() {
        LocalDate from = LocalDate.of(2026, 7, 20);
        LocalDate to = LocalDate.of(2026, 7, 22);

        givenLedgerRow(utcNoonOn(from.minusDays(1)), "1.00000000", 1);
        givenLedgerRow(utcNoonOn(from), "0.01000000", 1);
        givenLedgerRow(utcNoonOn(to), "0.02000000", 1);
        givenLedgerRow(utcNoonOn(to.plusDays(1)), "1.00000000", 1);

        assertThat(llmUsageRepository.getTotalRawCost(userId, from, to))
            .as("BETWEEN is inclusive at both ends; the flanking days are excluded")
            .isEqualByComparingTo("0.03000000");

        assertThat(llmUsageRepository.getDailyUsage(userId, from, to))
            .extracting(DailyUsageRecord::date)
            .containsExactly(to, from);
    }

    // ---------------------------------------------------------------- day bucketing

    /**
     * A ledger row at 23:30 UTC belongs to that UTC day (ADR-0003) no matter where the database
     * thinks it is. Before {@code (created_at AT TIME ZONE 'UTC')::date}, a plain {@code DATE()}
     * on a {@code timestamptz} asked the session timezone and an eastern Postgres filed this row
     * under tomorrow — while the Java half of the same read path was computing its window in UTC.
     *
     * <p>The two halves only ever agreed because the container happened to run UTC.</p>
     */
    @Test
    @Transactional
    @DisplayName("a timestamptz row is bucketed on its UTC day even in a UTC+14 session")
    void timestamptzBucketingIgnoresTheSessionZone() {
        LocalDate utcDay = LocalDate.of(2026, 7, 25);
        givenLedgerRow(lateUtcEveningOn(utcDay), "0.03000000", 300);

        setSessionZone(EASTERN_SESSION_ZONE);

        List<DailyUsageRecord> daily =
            llmUsageRepository.getDailyUsage(userId, utcDay.minusDays(2), utcDay.plusDays(2));

        assertThat(daily).hasSize(1);
        assertThat(daily.get(0).date())
            .as("23:30 UTC is still the 25th, whatever zone the session is set to")
            .isEqualTo(utcDay);
    }

    /**
     * The same instant under a UTC session. Together with the test above: the bucket a row lands
     * in no longer depends on how the database happens to be configured.
     */
    @Test
    @Transactional
    @DisplayName("the same row under a UTC session is bucketed on the same UTC day")
    void timestamptzBucketingIsCorrectUnderUtc() {
        LocalDate utcDay = LocalDate.of(2026, 7, 25);
        givenLedgerRow(lateUtcEveningOn(utcDay), "0.03000000", 300);

        setSessionZone("UTC");

        List<DailyUsageRecord> daily =
            llmUsageRepository.getDailyUsage(userId, utcDay.minusDays(2), utcDay.plusDays(2));

        assertThat(daily).hasSize(1);
        assertThat(daily.get(0).date()).isEqualTo(utcDay);
    }

    // ---------------------------------------------------------------- write failures

    /**
     * A ledger write that fails must not be silent, and must not be swallowed <em>here</em>: this
     * insert shares a transaction with the budget update that pays for it, so swallowing would let
     * the budget commit alone and charge a user for a call nothing recorded.
     *
     * <p>The failure is forced with a real one — a {@code user_id} with no {@code users} row, which
     * the foreign key rejects.</p>
     */
    @Test
    @DisplayName("a failed ledger write is counted and propagated, not swallowed")
    void failedLedgerWriteIsCountedAndPropagated() {
        double before = ledgerFailureCount();

        assertThatThrownBy(() -> llmUsageRepository.logUsage(
            new UserId(UUID.randomUUID()), UsageSource.CHAT, new BigDecimal("0.01"), 100))
            .as("propagated so the shared transaction rolls the budget update back too")
            .isInstanceOf(RuntimeException.class);

        assertThat(ledgerFailureCount())
            .as("a persistently broken ledger has to be visible as a metric, not just a log line")
            .isEqualTo(before + 1);
    }

    private double ledgerFailureCount() {
        Counter counter = meterRegistry.find(LLMUsageRepositoryImpl.LEDGER_WRITE_FAILURES_METRIC)
            .tag("source", UsageSource.CHAT.name())
            .counter();
        return counter == null ? 0d : counter.count();
    }

    /**
     * {@code research_executions.executed_at} is a naive {@code timestamp}, so its bucket is
     * already immune to the session zone. Pinned so the Phase 1 sweep does not "fix" it into
     * breaking — applying {@code AT TIME ZONE 'UTC'} to a naive column shifts it by the session
     * offset, which is the opposite of the intended effect.
     */
    @Test
    @Transactional
    @DisplayName("a naive timestamp row is already immune to the session zone")
    void naiveTimestampBucketingIgnoresTheSessionZone() {
        LocalDate utcDay = LocalDate.of(2026, 7, 25);
        givenResearchExecution(lateUtcEveningOn(utcDay), "0.06000000", 600);

        setSessionZone(EASTERN_SESSION_ZONE);

        List<DailyUsageRecord> daily =
            llmUsageRepository.getDailyUsage(userId, utcDay.minusDays(2), utcDay.plusDays(2));

        assertThat(daily).hasSize(1);
        assertThat(daily.get(0).date())
            .as("a column with no zone cannot be re-interpreted by the session's zone")
            .isEqualTo(utcDay);
    }
}
