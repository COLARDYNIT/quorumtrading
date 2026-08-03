package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.CreateBenchmarkCommand;
import com.quorumtrading.application.usecase.dto.CreateBenchmarkResponse;

/**
 * Use case for creating a benchmark with multiple simulations.
 * <p>
 * This use case orchestrates:
 * <ul>
 *   <li>Validation of all strategies</li>
 *   <li>Creation of benchmark entity</li>
 *   <li>Creation of simulation for each strategy</li>
 *   <li>Publishing events for simulation execution</li>
 * </ul>
 * </p>
 */
public interface CreateBenchmarkUseCase {

    /**
     * Execute the use case to create a benchmark with simulations.
     *
     * @param command The command containing strategy IDs, dates, and optional name
     * @return Response with benchmark ID, simulation IDs, and name
     * @throws IllegalArgumentException if any strategy ID is not found
     */
    CreateBenchmarkResponse execute(CreateBenchmarkCommand command);
}
