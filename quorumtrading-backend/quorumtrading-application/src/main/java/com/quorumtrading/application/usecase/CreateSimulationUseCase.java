package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.model.valueobject.SimulationId;

/**
 * Use case for creating a new backtesting simulation.
 * Creates a simulation with specified strategy, time range, and initial portfolio.
 */
public interface CreateSimulationUseCase {

    /**
     * Execute the create simulation use case.
     *
     * @param command the command containing simulation creation parameters
     * @return the ID of the newly created simulation
     */
    SimulationId execute(CreateSimulationCommand command);
}
