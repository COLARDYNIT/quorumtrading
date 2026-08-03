package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.PauseSimulationCommand;

/**
 * Use case for pausing a simulation execution.
 * Sets the simulation's paused flag to true.
 */
public interface PauseSimulationUseCase {

    /**
     * Execute the pause simulation use case.
     * Marks the simulation as paused, preventing further execution.
     *
     * @param command the command containing the simulation ID to pause
     * @throws java.util.NoSuchElementException if simulation not found
     */
    void execute(PauseSimulationCommand command);
}
