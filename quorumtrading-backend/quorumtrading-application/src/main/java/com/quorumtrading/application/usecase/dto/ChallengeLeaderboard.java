package com.quorumtrading.application.usecase.dto;

import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ranked entries of one challenge, computed once in the application layer and rendered by
 * every driving adapter (REST, MCP).
 *
 * <p>Values are unrounded and untyped-for-display on purpose: each adapter formats to its own
 * contract. What must <em>not</em> vary per adapter is the arithmetic — this type exists
 * because REST and MCP previously each computed the return percentage themselves and
 * disagreed, MCP counting DCA contributions as profit.</p>
 *
 * @param totalParticipants every simulation attached to the challenge, including ones filtered
 *                          out of {@code entries} because they are pending or belong to another
 *                          user's in-progress backtest
 * @param entries           visible entries, best first, with {@link Entry#rank()} pre-assigned
 */
public record ChallengeLeaderboard(int totalParticipants, List<Entry> entries) {

    public ChallengeLeaderboard {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /**
     * One ranked simulation.
     *
     * @param rank                 1-based position within the visible set
     * @param returnPercent        return on total invested (seed + Σ contributions) as a
     *                             percentage, or {@code null} when it cannot be computed
     * @param llmCostUsd           archived LLM spend, or {@code null} if never archived
     * @param executionTimeSeconds wall-clock runtime, or {@code null} if not started
     * @param ownedByViewer        true when the query's viewer owns the strategy behind this entry
     */
    public record Entry(
        int rank,
        SimulationId simulationId,
        String simulationName,
        String strategyName,
        String ownerDisplayName,
        Money currentValue,
        BigDecimal returnPercent,
        SimulationStatus status,
        BigDecimal llmCostUsd,
        Long executionTimeSeconds,
        boolean ownedByViewer
    ) {
        /** Copy of this entry at a different rank. */
        public Entry withRank(int newRank) {
            return new Entry(newRank, simulationId, simulationName, strategyName, ownerDisplayName,
                currentValue, returnPercent, status, llmCostUsd, executionTimeSeconds, ownedByViewer);
        }
    }
}
