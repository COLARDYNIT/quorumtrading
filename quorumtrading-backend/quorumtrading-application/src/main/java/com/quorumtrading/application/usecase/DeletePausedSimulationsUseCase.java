package com.quorumtrading.application.usecase;

/**
 * Use case for deleting all paused simulations.
 * Returns the number of simulations deleted.
 */
public interface DeletePausedSimulationsUseCase {

    /**
     * Execute the delete paused simulations use case.
     *
     * @return the number of simulations deleted
     */
    int execute(com.quorumtrading.model.valueobject.UserId requestingUserId);
}
