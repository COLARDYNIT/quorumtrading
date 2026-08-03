package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.SimulationId;

/**
 * Use case for triggering scheduled consensus decisions.
 * Called by the scheduler to process active simulations.
 */
public interface TriggerScheduledConsensusUseCase {

    /**
     * Trigger consensus decision for all active (RUNNING and not paused) simulations.
     *
     * @return Result containing the number of simulations processed and any errors
     */
    ScheduledConsensusResult executeForAllActive();

    /**
     * Trigger consensus decision for a specific simulation.
     *
     * @param simulationId The ID of the simulation to process
     * @return Result indicating success or failure
     */
    SimulationConsensusResult executeForSimulation(SimulationId simulationId);
}
