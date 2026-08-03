package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.valueobject.SimulationId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ConsensusDecision entities.
 * Implemented by persistence adapters to provide data access.
 */
public interface ConsensusDecisionRepository {
    ConsensusDecision save(ConsensusDecision decision);
    List<ConsensusDecision> findBySimulationId(UUID simulationId, Instant from, Instant to, Boolean executed, ExecutionOutcome executionOutcome, List<String> actions, Integer limit);
    Optional<ConsensusDecision> findLatestBySimulationId(SimulationId simulationId);

    /**
     * Find consensus decisions by simulation ID with optional ticker filter and ID-based pagination.
     *
     * @param simulationId simulation unique identifier
     * @param ticker optional ticker symbol filter (e.g., "BTC", "AAPL")
     * @param actions optional list of actions to filter by (e.g., ["BUY", "SELL"])
     * @param limit optional maximum number of results
     * @param beforeConsensusId optional: get decisions with ID < this value (for pagination backwards)
     * @param afterConsensusId optional: get decisions with ID > this value (for pagination forwards)
     * @return list of consensus decisions matching the criteria, ordered by ID descending (newest first)
     */
    List<ConsensusDecision> findBySimulationIdAndTicker(
        UUID simulationId,
        String ticker,
        List<String> actions,
        Integer limit,
        Long beforeConsensusId,
        Long afterConsensusId
    );

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
    boolean existsBySimulationIdAndSymbolAndTimestampWindow(
        UUID simulationId,
        String symbol,
        Instant timestamp,
        int windowSeconds
    );

    /**
     * Find a single consensus decision by its primary key.
     *
     * @param id consensus decision ID
     * @return the consensus decision if found
     */
    Optional<ConsensusDecision> findById(Long id);

    /**
     * Aggregate LLM cost data for all consensus decisions in a simulation.
     * Sums prompt tokens, completion tokens, total tokens, and raw cost
     * across all decisions with raw_cost_usd > 0.
     *
     * @param simulationId simulation unique identifier
     * @return aggregated cost data
     */
    AggregatedLLMCost sumCostBySimulationId(UUID simulationId);

    /**
     * Aggregate LLM cost for decisions in a simulation at or after a given timestamp.
     * Used by restart-from-decision to archive costs before deletion.
     *
     * @param simulationId simulation unique identifier
     * @param afterInclusive cutoff timestamp (inclusive)
     * @return aggregated cost data for matching decisions
     */
    AggregatedLLMCost sumCostBySimulationIdAfter(UUID simulationId, Instant afterInclusive);

    /**
     * Count consensus decisions at or after a given timestamp.
     */
    long countBySimulationIdAfter(UUID simulationId, Instant afterInclusive);

    /**
     * Count the decisions in a simulation that actually reached the broker as a BUY or SELL.
     *
     * <p>Distinguishes a strategy that traded from one that held throughout. Without it a
     * benchmark cannot tell a flat 0.00% return earned by never trading from one earned by
     * trading to a wash, and ranks the former above strategies that took real positions.</p>
     *
     * @param simulationId simulation unique identifier
     * @return number of executed BUY/SELL decisions
     */
    long countExecutedTradesBySimulationId(UUID simulationId);

    /**
     * Count the decisions in a simulation where consensus wanted to trade but nothing
     * executed: refused by the strategy's risk controls, below the minimum trade value,
     * or rejected by the broker.
     *
     * <p>The "decided plenty, traded nothing" signal. A completed run reports a headline
     * return even when most of its BUYs were refused; this count is what lets a caller
     * notice that before trusting the number.</p>
     *
     * @param simulationId simulation unique identifier
     * @return number of BUY/SELL decisions that did not execute
     */
    long countNotExecutedBySimulationId(UUID simulationId);

    /**
     * Delete consensus decisions at or after a given timestamp.
     * Returns the number of rows deleted.
     */
    long deleteBySimulationIdAfter(UUID simulationId, Instant afterInclusive);

    /**
     * Aggregated LLM cost data from consensus decisions.
     */
    record AggregatedLLMCost(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        BigDecimal rawCost,
        int decisionCount
    ) {
        public static final AggregatedLLMCost ZERO = new AggregatedLLMCost(0, 0, 0, BigDecimal.ZERO, 0);
    }
}
