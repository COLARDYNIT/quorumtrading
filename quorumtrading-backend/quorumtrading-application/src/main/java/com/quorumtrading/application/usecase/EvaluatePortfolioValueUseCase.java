package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.SimulationId;

/**
 * Use case for evaluating portfolio value based on current market prices.
 * Updates portfolio snapshots with latest market prices for active simulations.
 */
public interface EvaluatePortfolioValueUseCase {

    /**
     * Evaluate portfolio value for all active (RUNNING and not paused) simulations.
     *
     * @return result containing counts of processed, skipped, and failed evaluations
     */
    PortfolioEvaluationResult evaluateAllActive();

    /**
     * Evaluate portfolio value for a specific simulation.
     *
     * @param simulationId the simulation to evaluate
     * @return result indicating success, skip, or failure
     */
    SimulationEvaluationResult evaluateSimulation(SimulationId simulationId);
}
