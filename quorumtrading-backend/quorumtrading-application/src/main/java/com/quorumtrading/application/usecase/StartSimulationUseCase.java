package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.StartSimulationCommand;

/**
 * Use case for starting a simulation execution.
 * Triggers the backtesting engine to run through the simulation time period.
 */
public interface StartSimulationUseCase {

    /**
     * Execute the start simulation use case.
     * This is typically an asynchronous operation that fires events during execution.
     *
     * @param command the command containing the simulation ID to start
     * @throws com.quorumtrading.application.port.out.SimulationNotFoundException if simulation not found
     */
    void execute(StartSimulationCommand command);
}
