package com.quorumtrading.application.usecase.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response returned after grouping existing simulations into a benchmark.
 *
 * @param benchmarkId the created benchmark UUID
 * @param benchmarkName the benchmark display name
 * @param simulationIds the simulation UUIDs linked to the benchmark
 * @param warnings warnings about parameter mismatches between simulations
 */
public record CompareSimulationsResponse(
    UUID benchmarkId,
    String benchmarkName,
    List<UUID> simulationIds,
    List<String> warnings
) {
}
