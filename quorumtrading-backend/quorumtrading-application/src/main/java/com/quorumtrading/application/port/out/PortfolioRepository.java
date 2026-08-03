package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.valueobject.SimulationId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PortfolioSnapshot entities.
 * Implemented by persistence adapters to provide data access.
 */
public interface PortfolioRepository {
    PortfolioSnapshot save(PortfolioSnapshot portfolio);
    Optional<PortfolioSnapshot> findLatestBySimulationId(SimulationId simulationId);

    /**
     * Finds the oldest (first) portfolio snapshot for a simulation.
     * This is typically the initial snapshot created at simulation start,
     * representing the true starting value of the portfolio.
     *
     * @param simulationId the simulation ID
     * @return the oldest snapshot, or empty if no snapshots exist
     */
    Optional<PortfolioSnapshot> findOldestBySimulationId(SimulationId simulationId);

    List<PortfolioSnapshot> findAllBySimulationId(SimulationId simulationId);
    List<PortfolioSnapshot> findBySimulationIdAndTimeRange(SimulationId simulationId, Instant start, Instant end);
    List<PortfolioSnapshot> findBySimulationId(SimulationId simulationId, Instant from, Instant to, Integer limit);

    /**
     * Finds snapshots with optional daily resolution and position exclusion for performance.
     *
     * @param simulationId the simulation ID
     * @param from optional start timestamp
     * @param to optional end timestamp
     * @param limit optional max results
     * @param resolution "all" for every snapshot, "daily" for one per day (last of each day)
     * @param excludePositions when true, returned snapshots have empty positions (saves serialization)
     */
    List<PortfolioSnapshot> findBySimulationId(SimulationId simulationId, Instant from, Instant to,
                                                Integer limit, String resolution, boolean excludePositions);

    int deleteBySimulationId(SimulationId simulationId);

    /**
     * Count portfolio snapshots at or after a given timestamp.
     */
    long countBySimulationIdAfter(SimulationId simulationId, Instant afterInclusive);

    /**
     * Delete portfolio snapshots at or after a given timestamp.
     * Returns the number of rows deleted.
     */
    long deleteBySimulationIdAfter(SimulationId simulationId, Instant afterInclusive);

    /**
     * Fetches one daily total-value point per simulation per day, for several simulations in a
     * single query. Projects the three columns it needs instead of mapping whole snapshot
     * entities, which is what makes a multi-simulation chart affordable.
     *
     * @param simulationIds the simulation UUIDs to query
     * @return the points in unspecified order; empty when {@code simulationIds} is empty.
     *         Callers that need chronological order must sort.
     */
    List<DailyPortfolioValue> findDailyTotalValueBySimulationIds(List<UUID> simulationIds);

    /**
     * One simulation's total portfolio value at one point in time.
     *
     * <p>This used to cross the port as a raw {@code Object[]} whose column order lived only in
     * a javadoc comment, leaving callers to cast by position — so reordering the projection
     * broke the chart at runtime rather than at compile time.</p>
     */
    record DailyPortfolioValue(UUID simulationId, Instant timestamp, BigDecimal totalValue) {
        public DailyPortfolioValue {
            if (simulationId == null) {
                throw new IllegalArgumentException("simulationId is required");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("timestamp is required");
            }
        }
    }
}
