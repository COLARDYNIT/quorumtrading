package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.DeleteSimulationCommand;

/**
 * Use case for deleting a simulation.
 */
public interface DeleteSimulationUseCase {

    /**
     * Execute the delete simulation use case.
     *
     * @param command the command containing the simulation ID to delete
     * @throws com.quorumtrading.application.port.out.SimulationNotFoundException if simulation not found
     */
    void execute(DeleteSimulationCommand command);
}
