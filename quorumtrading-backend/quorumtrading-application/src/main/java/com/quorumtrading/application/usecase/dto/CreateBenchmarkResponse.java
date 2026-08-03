package com.quorumtrading.application.usecase.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response returned after successfully creating a benchmark with simulations.
 *
 * @param benchmarkId   UUID of the created benchmark
 * @param simulationIds   List of UUIDs for created simulations (one per strategy)
 * @param benchmarkName Name of the benchmark (custom or auto-generated)
 */
public record CreateBenchmarkResponse(
    UUID benchmarkId,
    List<UUID> simulationIds,
    String benchmarkName
) {
}
