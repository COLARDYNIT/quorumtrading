package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.RemoveSimulationFromBenchmarkCommand;

/**
 * Use case for removing a simulation from its benchmark.
 */
public interface RemoveSimulationFromBenchmarkUseCase {

    /**
     * Remove a simulation from its current benchmark.
     * The benchmark is kept even if it becomes empty.
     *
     * @param command the command containing simulation ID and requesting user
     * @throws java.util.NoSuchElementException if simulation not found
     * @throws com.quorumtrading.application.exception.AccessDeniedException if user lacks permissions
     * @throws IllegalStateException if simulation is not assigned to any benchmark
     */
    void execute(RemoveSimulationFromBenchmarkCommand command);
}
