package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Compact chart data response for benchmark detail page.
 * Uses columnar format to minimize payload: simulations[i] maps to points[][i+1].
 */
@Schema(description = "Compact benchmark chart data in columnar format")
public record BenchmarkChartDataResponse(

    @Schema(description = "Simulation metadata; index i maps to points[][i+1]")
    List<SimulationInfo> simulations,

    @Schema(description = "Columnar data: each row is [epoch_ms, val1, val2, ...] where valN corresponds to simulations[N-1]")
    List<List<Object>> points
) {

    @Schema(description = "Simulation metadata for chart legend")
    public record SimulationInfo(
        @Schema(description = "Simulation unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "Simulation name", example = "BTC Momentum Q1 2023")
        String name,

        @Schema(description = "Strategy name", example = "Momentum 15min")
        String strategyName,

        @Schema(description = "Initial cash amount", example = "100000.00")
        BigDecimal initialCash
    ) {}
}
