package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.model.valueobject.UsageSource;
import com.quorumtrading.model.valueobject.UserId;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of LLMUsageRepository using native SQL queries.
 * Aggregates LLM costs from consensus_decisions (via simulations/orchestrator_strategies)
 * and research_executions (via market_research).
 *
 * <p><a id="UTC_DAY_NOTE"></a><b>UTC_DAY_NOTE — why the three sources bucket days differently.</b>
 * A day starts at 00:00 UTC (ADR-0003), and the Java side of this read path computes its window
 * with {@code LocalDate.now(ZoneOffset.UTC)}. The SQL has to agree, and what that takes depends on
 * the column:</p>
 * <ul>
 *   <li>{@code consensus_decisions.created_at} and {@code llm_usage_log.created_at} are
 *       {@code timestamptz}. Casting one straight to {@code date} renders it in the
 *       <em>session</em> timezone, so a 23:30 UTC row is filed under tomorrow on a Postgres
 *       running east of UTC. {@code (x AT TIME ZONE 'UTC')::date} pins it to the UTC day.</li>
 *   <li>{@code research_executions.executed_at} is {@code timestamp} <em>without</em> time zone,
 *       already holding a UTC reading. It has no zone to re-interpret, so a plain
 *       {@code ::date} is correct — and {@code AT TIME ZONE 'UTC'} would be actively wrong there,
 *       converting it <em>to</em> a {@code timestamptz} and reintroducing the session-zone shift
 *       this note exists to remove.</li>
 * </ul>
 * <p>Both behaviours are pinned by {@code LLMUsageLedgerIntegrationTest}, so making the three
 * branches superficially uniform will fail the build.</p>
 */
@ApplicationScoped
public class LLMUsageRepositoryImpl implements LLMUsageRepository {

    private static final Logger LOG = Logger.getLogger(LLMUsageRepositoryImpl.class);

    @Inject
    EntityManager entityManager;

    /**
     * Counts ledger rows this process failed to write. A missing row is silent by design — the
     * originating LLM call must not fail for it (the answer is already paid for) — so without a
     * metric a persistently broken ledger shows up only as log lines nobody is reading.
     */
    static final String LEDGER_WRITE_FAILURES_METRIC = "quorumtrading.llm.usage.ledger.write.failures";

    /**
     * {@code Instance} because a registry is only present when a Micrometer implementation is on
     * the runtime classpath — it is in the assembled application, but not in the isolated adapter
     * test context. Telemetry is never a reason to fail a ledger write.
     */
    @Inject
    Instance<MeterRegistry> meterRegistry;

    @Override
    @Transactional
    public void logUsage(UserId userId, UsageSource source, BigDecimal rawCost, int totalTokens) {
        if (userId == null || source == null) {
            return;
        }
        String sql = "INSERT INTO llm_usage_log (user_id, source, raw_cost_usd, total_tokens) VALUES (:userId, :source, :rawCost, :totalTokens)";
        try {
            entityManager.createNativeQuery(sql)
                .setParameter("userId", userId.value())
                .setParameter("source", source.name())
                .setParameter("rawCost", rawCost != null ? rawCost : BigDecimal.ZERO)
                .setParameter("totalTokens", totalTokens)
                .executeUpdate();
        } catch (RuntimeException e) {
            // Deliberately rethrown rather than swallowed here. This insert shares a transaction
            // with the budget update that pays for it (see TransactionRunner), and swallowing the
            // failure would let the budget commit alone — a user charged for a call the ledger has
            // no record of. The caller catches it outside the boundary, so the LLM call still
            // succeeds; by then both writes have been rolled back together.
            countLedgerWriteFailure(source);
            LOG.errorf(e, "Failed to log LLM usage for user %s, source %s — "
                + "ledger row and the budget update in the same transaction are both rolled back",
                userId.value(), source);
            throw e;
        }
    }

    private void countLedgerWriteFailure(UsageSource source) {
        if (meterRegistry != null && meterRegistry.isResolvable()) {
            meterRegistry.get().counter(LEDGER_WRITE_FAILURES_METRIC, "source", source.name()).increment();
        }
    }

    @Override
    @Transactional
    public List<DailyUsageRecord> getDailyUsage(UserId userId, LocalDate from, LocalDate to) {
        if (userId == null) {
            return List.of();
        }

        // Native query to aggregate daily costs from consensus decisions, research executions, and usage log
        // IMPORTANT: Uses cd.created_at (wall-clock time) not cd.decision_timestamp (simulated time)
        // because backtests have historical decision_timestamps that don't reflect when the LLM call happened
        //
        // Day bucketing is UTC on both sides (ADR-0003): the Java side computes its window with
        // LocalDate.now(ZoneOffset.UTC), so the SQL must not resolve days in the session timezone.
        // See UTC_DAY_NOTE for why the three sources are not written the same way.
        String sql = """
            WITH consensus_costs AS (
                SELECT
                    (cd.created_at AT TIME ZONE 'UTC')::date as usage_date,
                    COALESCE(SUM(cd.raw_cost_usd), 0) as raw_cost,
                    COALESCE(SUM(cd.total_tokens), 0) as tokens
                FROM consensus_decisions cd
                JOIN simulations s ON cd.simulation_id = s.id
                JOIN orchestrator_strategies st ON s.strategy_id = st.id
                WHERE st.owner_id = :userId
                  AND (cd.created_at AT TIME ZONE 'UTC')::date BETWEEN :fromDate AND :toDate
                GROUP BY (cd.created_at AT TIME ZONE 'UTC')::date
            ),
            research_costs AS (
                SELECT
                    re.executed_at::date as usage_date,
                    COALESCE(SUM(re.raw_cost_usd), 0) as raw_cost,
                    COALESCE(SUM(re.total_tokens), 0) as tokens
                FROM research_executions re
                JOIN market_research mr ON re.market_research_id = mr.id
                WHERE mr.owner_id = :userId
                  AND re.executed_at::date BETWEEN :fromDate AND :toDate
                GROUP BY re.executed_at::date
            ),
            logged_costs AS (
                SELECT
                    (ul.created_at AT TIME ZONE 'UTC')::date as usage_date,
                    COALESCE(SUM(ul.raw_cost_usd), 0) as raw_cost,
                    COALESCE(SUM(ul.total_tokens), 0) as tokens
                FROM llm_usage_log ul
                WHERE ul.user_id = :userId
                  AND (ul.created_at AT TIME ZONE 'UTC')::date BETWEEN :fromDate AND :toDate
                GROUP BY (ul.created_at AT TIME ZONE 'UTC')::date
            ),
            combined AS (
                SELECT usage_date, raw_cost, tokens FROM consensus_costs
                UNION ALL
                SELECT usage_date, raw_cost, tokens FROM research_costs
                UNION ALL
                SELECT usage_date, raw_cost, tokens FROM logged_costs
            )
            SELECT
                usage_date,
                SUM(raw_cost) as total_raw_cost,
                SUM(tokens) as total_tokens
            FROM combined
            GROUP BY usage_date
            ORDER BY usage_date DESC
            """;

        try {
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("userId", userId.value());
            query.setParameter("fromDate", from);
            query.setParameter("toDate", to);

            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            List<DailyUsageRecord> records = new ArrayList<>();
            for (Object[] row : results) {
                LocalDate date = toLocalDate(row[0]);
                BigDecimal rawCost = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
                int tokens = row[2] != null ? ((Number) row[2]).intValue() : 0;
                records.add(new DailyUsageRecord(date, rawCost, tokens));
            }

            LOG.debugf("Retrieved %d daily usage records for user %s", records.size(), userId.value());
            return records;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to get daily usage for user %s", userId.value());
            return List.of();
        }
    }

    @Override
    @Transactional
    public BigDecimal getTotalRawCost(UserId userId, LocalDate from, LocalDate to) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }

        // Native query to sum total costs from all sources
        // IMPORTANT: Uses cd.created_at (wall-clock time) not cd.decision_timestamp (simulated time)
        // Day bucketing is UTC on both sides (ADR-0003) — see UTC_DAY_NOTE.
        String sql = """
            WITH consensus_costs AS (
                SELECT COALESCE(SUM(cd.raw_cost_usd), 0) as raw_cost
                FROM consensus_decisions cd
                JOIN simulations s ON cd.simulation_id = s.id
                JOIN orchestrator_strategies st ON s.strategy_id = st.id
                WHERE st.owner_id = :userId
                  AND (cd.created_at AT TIME ZONE 'UTC')::date BETWEEN :fromDate AND :toDate
            ),
            research_costs AS (
                SELECT COALESCE(SUM(re.raw_cost_usd), 0) as raw_cost
                FROM research_executions re
                JOIN market_research mr ON re.market_research_id = mr.id
                WHERE mr.owner_id = :userId
                  AND re.executed_at::date BETWEEN :fromDate AND :toDate
            ),
            logged_costs AS (
                SELECT COALESCE(SUM(ul.raw_cost_usd), 0) as raw_cost
                FROM llm_usage_log ul
                WHERE ul.user_id = :userId
                  AND (ul.created_at AT TIME ZONE 'UTC')::date BETWEEN :fromDate AND :toDate
            )
            SELECT
                (SELECT raw_cost FROM consensus_costs) +
                (SELECT raw_cost FROM research_costs) +
                (SELECT raw_cost FROM logged_costs) as total
            """;

        try {
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("userId", userId.value());
            query.setParameter("fromDate", from);
            query.setParameter("toDate", to);

            Object result = query.getSingleResult();
            BigDecimal total = result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;

            LOG.debugf("Total raw cost for user %s from %s to %s: %.8f USD",
                userId.value(), from, to, total);
            return total;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to get total cost for user %s", userId.value());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Convert database date result to LocalDate.
     * Handles both java.sql.Date and java.time.LocalDate return types.
     */
    private LocalDate toLocalDate(Object dateValue) {
        if (dateValue instanceof LocalDate localDate) {
            return localDate;
        } else if (dateValue instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        } else if (dateValue != null) {
            throw new IllegalArgumentException("Unexpected date type: " + dateValue.getClass());
        }
        return null;
    }
}
