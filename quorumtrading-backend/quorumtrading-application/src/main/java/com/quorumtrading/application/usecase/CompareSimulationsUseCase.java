package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.CompareSimulationsCommand;
import com.quorumtrading.application.usecase.dto.CompareSimulationsResponse;

/**
 * Use case for grouping existing simulations into a benchmark for comparison.
 * Unlike {@link CreateBenchmarkUseCase}, this does not create new simulations —
 * it re-parents existing ones into a new benchmark entity.
 */
public interface CompareSimulationsUseCase {

    /**
     * Group existing simulations into a benchmark.
     *
     * @param command The command containing simulation IDs, optional name, and user
     * @return Response with benchmark ID, linked simulation IDs, and any warnings
     * @throws IllegalArgumentException if fewer than 2 simulation IDs, or any simulation not found
     * @throws com.quorumtrading.application.exception.AccessDeniedException if user doesn't own a simulation
     * @throws IllegalStateException if a simulation is already assigned to a different benchmark
     */
    CompareSimulationsResponse execute(CompareSimulationsCommand command);
}
