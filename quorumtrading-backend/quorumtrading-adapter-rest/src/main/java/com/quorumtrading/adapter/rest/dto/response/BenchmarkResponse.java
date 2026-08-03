package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for benchmark details.
 */
@Schema(description = "Benchmark details response")
public record BenchmarkResponse(
    @Schema(description = "Unique benchmark identifier", example = "770e8400-e29b-41d4-a716-446655440000")
    String id,

    @Schema(description = "Benchmark name (custom or auto-generated)", example = "Backtest: Jan 1, 2023 - Dec 31, 2023")
    String name,

    @Schema(description = "Benchmark/simulation start date", example = "2023-01-01T00:00:00Z")
    Instant startDate,

    @Schema(description = "Benchmark/simulation end date. Null indicates live mode.", example = "2023-12-31T23:59:59Z", nullable = true)
    Instant endDate,

    @Schema(description = "List of simulation IDs participating in this benchmark")
    List<String> simulationIds,

    @Schema(description = "Timestamp when benchmark was created", example = "2025-11-10T10:00:00Z")
    Instant createdAt,

    @Schema(description = "Whether this is a live benchmark (true) or historical backtest (false)", example = "false")
    Boolean isLive
) {
}
