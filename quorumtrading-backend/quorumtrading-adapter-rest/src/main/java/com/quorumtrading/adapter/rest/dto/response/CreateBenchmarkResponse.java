package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for benchmark creation.
 */
@Schema(description = "Response after creating a benchmark with simulations, or a standalone simulation for single-strategy requests")
public record CreateBenchmarkResponse(
    @Schema(description = "Unique benchmark identifier. Null when a single strategy was submitted (standalone simulation).",
            example = "770e8400-e29b-41d4-a716-446655440000", nullable = true)
    String benchmarkId,

    @Schema(description = "List of simulation IDs created for this benchmark", example = "[\"880f9511-f39c-52e5-b827-557766551111\"]")
    List<String> simulationIds,

    @Schema(description = "Final benchmark name (custom or auto-generated)", example = "Backtest: Jan 1, 2023 - Dec 31, 2023")
    String benchmarkName
) {
}
