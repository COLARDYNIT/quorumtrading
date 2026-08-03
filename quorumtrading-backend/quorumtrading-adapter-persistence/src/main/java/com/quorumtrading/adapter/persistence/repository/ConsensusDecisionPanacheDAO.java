package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.ConsensusDecisionJpaEntity;
import com.quorumtrading.model.enums.ExecutionOutcome;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ConsensusDecisionPanacheDAO implements PanacheRepository<ConsensusDecisionJpaEntity> {

    public List<ConsensusDecisionJpaEntity> findBySimulationId(UUID simulationId, Instant from, Instant to,
                                                      Boolean executed, ExecutionOutcome executionOutcome, List<String> actions, Integer limit) {
        // When filtering by actions, we need to use native SQL because HQL doesn't support PostgreSQL JSON operators
        if (actions != null && !actions.isEmpty()) {
            return findBySimulationIdNative(simulationId, from, to, executed, executionOutcome, actions, limit);
        }

        // Use standard Panache HQL query for other cases
        StringBuilder query = new StringBuilder("simulationId = :simulationId");
        Parameters params = Parameters.with("simulationId", simulationId);

        if (from != null) {
            query.append(" AND decisionTimestamp >= :from");
            params.and("from", from);
        }

        if (to != null) {
            query.append(" AND decisionTimestamp <= :to");
            params.and("to", to);
        }

        if (executed != null) {
            query.append(" AND executed = :executed");
            params.and("executed", executed);
        }

        if (executionOutcome != null) {
            query.append(" AND executionOutcome = :executionOutcome");
            params.and("executionOutcome", executionOutcome.name());
        }

        query.append(" ORDER BY decisionTimestamp DESC");

        if (limit != null && limit > 0) {
            return find(query.toString(), params).page(0, limit).list();
        } else {
            // Default limit of 100
            return find(query.toString(), params).page(0, 100).list();
        }
    }

    /**
     * Native SQL query to filter consensus decisions by action field within finalAction JSON column.
     * Uses PostgreSQL JSON operators which are not supported in HQL.
     */
    private List<ConsensusDecisionJpaEntity> findBySimulationIdNative(UUID simulationId, Instant from, Instant to,
                                                                       Boolean executed, ExecutionOutcome executionOutcome, List<String> actions, Integer limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM consensus_decisions WHERE simulation_id = :simulationId");

        if (from != null) {
            sql.append(" AND decision_timestamp >= :from");
        }

        if (to != null) {
            sql.append(" AND decision_timestamp <= :to");
        }

        if (executed != null) {
            sql.append(" AND executed = :executed");
        }

        if (executionOutcome != null) {
            sql.append(" AND execution_outcome = :executionOutcome");
        }

        // Build OR clause for JSON field comparison using PostgreSQL JSON operators
        sql.append(" AND (");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("final_action->>'action' = :action").append(i);
        }
        sql.append(")");

        sql.append(" ORDER BY decision_timestamp DESC");

        if (limit != null && limit > 0) {
            sql.append(" LIMIT :limit");
        } else {
            sql.append(" LIMIT 100");
        }

        var query = getEntityManager().createNativeQuery(sql.toString(), ConsensusDecisionJpaEntity.class);
        query.setParameter("simulationId", simulationId);

        if (from != null) {
            query.setParameter("from", from);
        }

        if (to != null) {
            query.setParameter("to", to);
        }

        if (executed != null) {
            query.setParameter("executed", executed);
        }

        if (executionOutcome != null) {
            query.setParameter("executionOutcome", executionOutcome.name());
        }

        for (int i = 0; i < actions.size(); i++) {
            query.setParameter("action" + i, actions.get(i));
        }

        if (limit != null && limit > 0) {
            query.setParameter("limit", limit);
        }

        return query.getResultList();
    }

    /**
     * Find the most recent consensus decision for a simulation.
     * Used to determine when the last consensus was made for interval checking.
     */
    public Optional<ConsensusDecisionJpaEntity> findLatestBySimulationId(UUID simulationId) {
        return find("simulationId = :simulationId ORDER BY decisionTimestamp DESC",
                Parameters.with("simulationId", simulationId))
                .firstResultOptional();
    }

    /**
     * Find consensus decisions by simulation ID with optional ticker filter and ID-based pagination.
     * Results are ordered by ID descending (newest first).
     */
    public List<ConsensusDecisionJpaEntity> findBySimulationIdAndTicker(
            UUID simulationId,
            String ticker,
            List<String> actions,
            Integer limit,
            Long beforeConsensusId,
            Long afterConsensusId
    ) {
        // When filtering by actions, we need to use native SQL because HQL doesn't support PostgreSQL JSON operators
        if (actions != null && !actions.isEmpty()) {
            return findBySimulationIdAndTickerNative(simulationId, ticker, actions, limit, beforeConsensusId, afterConsensusId);
        }

        // Use standard Panache HQL query when no action filter
        StringBuilder query = new StringBuilder("simulationId = :simulationId");
        Parameters params = Parameters.with("simulationId", simulationId);

        // Filter by ticker/symbol if provided
        if (ticker != null && !ticker.isEmpty()) {
            query.append(" AND symbol = :ticker");
            params.and("ticker", ticker);
        }

        // ID-based pagination
        if (beforeConsensusId != null) {
            query.append(" AND id < :beforeId");
            params.and("beforeId", beforeConsensusId);
        }

        if (afterConsensusId != null) {
            query.append(" AND id > :afterId");
            params.and("afterId", afterConsensusId);
        }

        // Order by ID descending (newest first)
        query.append(" ORDER BY id DESC");

        // Apply limit
        int pageSize = (limit != null && limit > 0) ? limit : 20; // Default to 20

        return find(query.toString(), params).page(0, pageSize).list();
    }

    /**
     * Native SQL query to filter consensus decisions by action field within finalAction JSON column.
     * Uses PostgreSQL JSON operators which are not supported in HQL.
     */
    private List<ConsensusDecisionJpaEntity> findBySimulationIdAndTickerNative(
            UUID simulationId,
            String ticker,
            List<String> actions,
            Integer limit,
            Long beforeConsensusId,
            Long afterConsensusId
    ) {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM consensus_decisions WHERE simulation_id = :simulationId");

        // Filter by ticker/symbol if provided
        if (ticker != null && !ticker.isEmpty()) {
            sql.append(" AND symbol = :ticker");
        }

        // Build OR clause for JSON field comparison using PostgreSQL JSON operators
        sql.append(" AND (");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("final_action->>'action' = :action").append(i);
        }
        sql.append(")");

        // ID-based pagination
        if (beforeConsensusId != null) {
            sql.append(" AND id < :beforeId");
        }

        if (afterConsensusId != null) {
            sql.append(" AND id > :afterId");
        }

        // Order by ID descending (newest first)
        sql.append(" ORDER BY id DESC");

        // Apply limit
        int pageSize = (limit != null && limit > 0) ? limit : 20; // Default to 20
        sql.append(" LIMIT :limit");

        var query = getEntityManager().createNativeQuery(sql.toString(), ConsensusDecisionJpaEntity.class);
        query.setParameter("simulationId", simulationId);

        if (ticker != null && !ticker.isEmpty()) {
            query.setParameter("ticker", ticker);
        }

        for (int i = 0; i < actions.size(); i++) {
            query.setParameter("action" + i, actions.get(i));
        }

        if (beforeConsensusId != null) {
            query.setParameter("beforeId", beforeConsensusId);
        }

        if (afterConsensusId != null) {
            query.setParameter("afterId", afterConsensusId);
        }

        query.setParameter("limit", pageSize);

        return query.getResultList();
    }

    /**
     * Check if a consensus decision already exists for a given simulation, symbol, and timestamp window.
     * Used for deduplication to prevent duplicate consensus decisions being created.
     *
     * @param simulationId simulation unique identifier
     * @param symbol the ticker symbol (e.g., "BTC", "AAPL")
     * @param timestamp the decision timestamp to check around
     * @param windowSeconds tolerance window in seconds (decisions within +/- this window are considered duplicates)
     * @return true if a consensus decision already exists within the window, false otherwise
     */
    public boolean existsBySimulationIdAndSymbolAndTimestampWindow(
            UUID simulationId,
            String symbol,
            Instant timestamp,
            int windowSeconds
    ) {
        Instant windowStart = timestamp.minusSeconds(windowSeconds);
        Instant windowEnd = timestamp.plusSeconds(windowSeconds);

        long count = count(
            "simulationId = :simulationId AND symbol = :symbol AND decisionTimestamp >= :windowStart AND decisionTimestamp <= :windowEnd",
            Parameters.with("simulationId", simulationId)
                .and("symbol", symbol)
                .and("windowStart", windowStart)
                .and("windowEnd", windowEnd)
        );

        return count > 0;
    }

    /**
     * Aggregate LLM cost for decisions at or after a given timestamp.
     */
    public Object[] sumCostBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        var query = getEntityManager().createNativeQuery(
            "SELECT COALESCE(SUM(prompt_tokens), 0), " +
            "       COALESCE(SUM(completion_tokens), 0), " +
            "       COALESCE(SUM(total_tokens), 0), " +
            "       COALESCE(SUM(raw_cost_usd), 0), " +
            "       COUNT(*) " +
            "FROM consensus_decisions " +
            "WHERE simulation_id = :simulationId " +
            "  AND decision_timestamp >= :cutoff " +
            // Gate on recorded usage, not on money. A free local engine writes raw_cost_usd = 0 on
            // every row, so filtering by cost discarded the whole simulation and reported zero
            // decisions and zero tokens — see sumCostBySimulationId.
            "  AND total_tokens > 0"
        );
        query.setParameter("simulationId", simulationId);
        query.setParameter("cutoff", afterInclusive);
        return (Object[]) query.getSingleResult();
    }

    /**
     * Count consensus decisions at or after a given timestamp.
     */
    public long countBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        return count("simulationId = :simulationId AND decisionTimestamp >= :cutoff",
            Parameters.with("simulationId", simulationId).and("cutoff", afterInclusive));
    }

    /**
     * Count decisions that actually reached the broker as a trade.
     *
     * <p>HOLD decisions are excluded: a strategy that decided to hold every single day is
     * meaningfully different from one that traded, and a benchmark that cannot tell them
     * apart ranks a strategy that never traded above one that did.</p>
     */
    public long countExecutedTradesBySimulationId(UUID simulationId) {
        // final_action is JSONB, not a plain column — the action lives under its 'action' key,
        // so this must go through the PostgreSQL JSON operator rather than comparing the
        // column itself (which fails outright: 'Token "BUY" is invalid'). Uses the
        // (final_action->>'action') expression index from V123.
        var query = getEntityManager().createNativeQuery(
            "SELECT count(*) FROM consensus_decisions "
            + "WHERE simulation_id = :simulationId "
            + "AND executed = true "
            + "AND final_action->>'action' IN ('BUY', 'SELL')");
        query.setParameter("simulationId", simulationId);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Count decisions where consensus wanted to trade but nothing executed.
     *
     * <p>Keyed on {@code execution_outcome}, which since the truthful-outcome vocabulary
     * distinguishes a refused BUY (BLOCKED_BY_RISK_LIMITS / BELOW_MINIMUM_TRADE_VALUE) and a
     * broker rejection (REJECTED) from a genuine do-nothing HOLD (NO_TRADE_REQUIRED).
     * Rows written before that vocabulary existed were reclassified by V132 from their
     * conversion notes.</p>
     */
    public long countNotExecutedBySimulationId(UUID simulationId) {
        return count("simulationId = ?1 and executionOutcome in ?2",
            simulationId,
            java.util.List.of("BLOCKED_BY_RISK_LIMITS", "BELOW_MINIMUM_TRADE_VALUE", "REJECTED"));
    }

    /**
     * Delete consensus decisions at or after a given timestamp.
     */
    public long deleteBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        return delete("simulationId = :simulationId AND decisionTimestamp >= :cutoff",
            Parameters.with("simulationId", simulationId).and("cutoff", afterInclusive));
    }

    /**
     * Aggregate LLM cost data for all consensus decisions in a simulation.
     * Returns summed token counts and raw cost for decisions with raw_cost_usd > 0.
     */
    public Object[] sumCostBySimulationId(UUID simulationId) {
        var query = getEntityManager().createNativeQuery(
            "SELECT COALESCE(SUM(prompt_tokens), 0), " +
            "       COALESCE(SUM(completion_tokens), 0), " +
            "       COALESCE(SUM(total_tokens), 0), " +
            "       COALESCE(SUM(raw_cost_usd), 0), " +
            "       COUNT(*) " +
            "FROM consensus_decisions " +
            "WHERE simulation_id = :simulationId " +
            // Gate on recorded usage rather than on money. The self-host edition defaults to a free
            // local engine, which writes raw_cost_usd = 0 on every decision, so a cost filter
            // matched nothing and get_simulation_cost reported decisionCount 0 and 0 tokens for
            // simulations that had really run — 198 stored decisions, 0 with cost, 187 with tokens.
            "  AND total_tokens > 0"
        );
        query.setParameter("simulationId", simulationId);
        return (Object[]) query.getSingleResult();
    }
}
