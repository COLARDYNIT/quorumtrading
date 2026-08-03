package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.ComposeBenchmarkCommand;
import com.quorumtrading.application.usecase.dto.ComposeBenchmarkResponse;

/**
 * Use case for composing a benchmark from existing simulations.
 * Can create a new benchmark or add simulations to an existing one.
 */
public interface ComposeBenchmarkUseCase {

    /**
     * Compose a benchmark from existing simulations.
     * If benchmarkId is null, creates a new benchmark with dates derived from the simulations.
     * If benchmarkId is present, adds the simulations to the existing benchmark.
     * Already-assigned simulations are skipped (idempotent).
     *
     * @param command the command containing simulation IDs, optional benchmark ID, optional name, and user
     * @return response with benchmark details and counts
     * @throws java.util.NoSuchElementException if any simulation or benchmark not found
     * @throws com.quorumtrading.application.exception.AccessDeniedException if user lacks permissions
     */
    ComposeBenchmarkResponse execute(ComposeBenchmarkCommand command);
}
