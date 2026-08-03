package com.quorumtrading.application.usecase.dto;

import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ranked entries of one benchmark, computed once in the application layer and rendered by
 * every driving adapter (REST, MCP).
 *
 * <p>Entries are ordered best-first by the shared ranking rule: strategies that executed at
 * least one trade come before strategies that executed none, and within each group by return
 * descending. A run that never opened a position has produced no evidence about the strategy,
 * so its flat 0% must not outrank a strategy that took real positions in a down market.</p>
 */
public record BenchmarkLeaderboard(List<Entry> entries) {

    public BenchmarkLeaderboard {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /**
     * One ranked simulation.
     *
     * @param rank                1-based position after ranking
     * @param benchmarkOrdinal    index of this simulation in the benchmark's own membership
     *                            order, independent of rank. Adapters that assign a stable
     *                            per-simulation chart colour must key off this, not off rank,
     *                            or a simulation changes colour whenever the ranking moves.
     * @param currentValue        latest snapshot value, falling back to {@code initialCash} when
     *                            the simulation has not produced a snapshot yet; {@code null}
     *                            only when there is neither
     * @param hasPortfolioData    whether a usable portfolio snapshot exists at all. False means
     *                            {@code currentValue} is a seed-cash placeholder rather than a
     *                            measurement, so a caller presenting a performance table may
     *                            legitimately omit the row instead of showing a fabricated 0%.
     * @param returnPercent       return against the actual initial value, or {@code null} when
     *                            it cannot be computed
     * @param executedTradeCount  number of executed BUY/SELL decisions; zero drives the
     *                            traded-first ranking rule described above
     */
    public record Entry(
        int rank,
        int benchmarkOrdinal,
        SimulationId simulationId,
        String simulationName,
        String strategyName,
        String tradingMode,
        Money initialCash,
        Money currentValue,
        boolean hasPortfolioData,
        BigDecimal returnPercent,
        SimulationStatus status,
        long executedTradeCount
    ) {
        /** Copy of this entry at a different rank. */
        public Entry withRank(int newRank) {
            return new Entry(newRank, benchmarkOrdinal, simulationId, simulationName, strategyName,
                tradingMode, initialCash, currentValue, hasPortfolioData, returnPercent, status,
                executedTradeCount);
        }

        /** True when this entry carries a real measurement that can be ranked against others. */
        public boolean measurable() {
            return hasPortfolioData && returnPercent != null;
        }

        /** True when this run produced no evidence at all — it never executed a trade. */
        public boolean traded() {
            return executedTradeCount > 0;
        }
    }
}
