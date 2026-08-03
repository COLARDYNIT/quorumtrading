package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.AddSimulationToBenchmarkCommand;

/**
 * Use case for adding an existing simulation to an existing benchmark.
 */
public interface AddSimulationToBenchmarkUseCase {

    /**
     * Add a simulation to a benchmark, validating that initial capital and date ranges match.
     *
     * @param command the command containing simulation ID, benchmark ID, and requesting user
     * @throws java.util.NoSuchElementException if benchmark or simulation not found
     * @throws com.quorumtrading.application.exception.AccessDeniedException if user doesn't own the benchmark or simulation
     * @throws IllegalArgumentException if simulation parameters don't match existing benchmark simulations
     * @throws IllegalStateException if simulation is already assigned to a different benchmark
     */
    void execute(AddSimulationToBenchmarkCommand command);
}
