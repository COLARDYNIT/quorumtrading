package com.quorumtrading.application.usecase.dto;

import java.util.UUID;

/**
 * Response returned after composing a benchmark from existing simulations.
 *
 * @param benchmarkId UUID of the benchmark (created or existing)
 * @param name        Display name of the benchmark
 * @param totalCount  Total number of simulations in the benchmark after the operation
 * @param addedCount  Number of simulations newly added in this operation
 */
public record ComposeBenchmarkResponse(
    UUID benchmarkId,
    String name,
    int totalCount,
    int addedCount
) {
}
