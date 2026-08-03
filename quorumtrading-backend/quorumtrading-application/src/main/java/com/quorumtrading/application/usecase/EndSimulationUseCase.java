package com.quorumtrading.application.usecase;

/**
 * Use case for ending simulations that have reached their end date.
 * Called by the scheduler to mark expired simulations as completed and paused.
 */
public interface EndSimulationUseCase {

    /**
     * End all simulations that have reached their end date.
     * Marks simulations as COMPLETED and paused if endDate <= now.
     *
     * @return Result containing the number of simulations ended and any errors
     */
    EndSimulationResult endExpiredSimulations();
}
