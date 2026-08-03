package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.ScheduledSimulation;
import com.quorumtrading.model.valueobject.ScheduledSimulationId;
import com.quorumtrading.model.valueobject.StrategyId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository port for ScheduledSimulation aggregate.
 *
 * <p>This is an outgoing port in hexagonal architecture.
 * Implementations are provided by the persistence adapter.</p>
 */
public interface ScheduledSimulationRepository {

    /**
     * Save a new or update an existing scheduled simulation.
     *
     * @param scheduledSimulation The scheduled simulation to save
     */
    void save(ScheduledSimulation scheduledSimulation);

    /**
     * Find a scheduled simulation by its ID.
     *
     * @param id The scheduled simulation ID
     * @return The scheduled simulation if found
     */
    Optional<ScheduledSimulation> findById(ScheduledSimulationId id);

    /**
     * Find a scheduled simulation by strategy ID.
     *
     * @param strategyId The strategy ID
     * @return The scheduled simulation for the strategy if found
     */
    Optional<ScheduledSimulation> findByStrategyId(StrategyId strategyId);

    /**
     * Find all scheduled simulations that are due for execution.
     *
     * @param now The current instant to check against nextRunAt
     * @return List of scheduled simulations that are due
     */
    List<ScheduledSimulation> findDueSchedules(Instant now);

    /**
     * Find all enabled scheduled simulations.
     *
     * @return List of enabled scheduled simulations
     */
    List<ScheduledSimulation> findAllEnabled();

    /**
     * Delete a scheduled simulation.
     *
     * @param id The ID of the scheduled simulation to delete
     */
    void delete(ScheduledSimulationId id);

    /**
     * Delete a scheduled simulation by strategy ID.
     *
     * @param strategyId The strategy ID
     */
    void deleteByStrategyId(StrategyId strategyId);
}
