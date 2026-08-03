package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Response DTO for benchmark entry (a simulation's ranking within a benchmark).
 */
@Schema(description = "Benchmark entry for a simulation")
public record BenchmarkEntryResponse(
    @Schema(description = "Simulation unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    String id,

    @Schema(description = "Simulation name", example = "BTC Momentum Q1 2023")
    String name,

    @Schema(description = "Strategy name", example = "Momentum 15min")
    String strategyName,

    @Schema(description = "Chart color for this entry", example = "#4AA3FF")
    String color,

    @Schema(description = "Current portfolio value", example = "15234.56")
    BigDecimal value,

    @Schema(description = "Return percentage", example = "52.35")
    BigDecimal returnPercent,

    @Schema(description = "Current simulation status", example = "COMPLETED")
    String status
) {
}
