package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for market data availability analysis.
 */
@Schema(description = "Market data availability analysis results")
public record DataAvailabilityResponse(
    @Schema(description = "Overall data coverage percentage across all symbols and types",
            example = "85.50",
            minimum = "0",
            maximum = "100")
    BigDecimal overallCoverage,

    @Schema(description = "Coverage percentage per data type",
            example = "{\"PRICE\": 95.0, \"NEWS\": 80.0, \"SOCIAL\": 75.0}")
    Map<String, BigDecimal> perTypeCoverage,

    @Schema(description = "Coverage percentage per symbol",
            example = "{\"BTC\": 90.0, \"ETH\": 85.0}")
    Map<String, BigDecimal> perSymbolCoverage,

    @Schema(description = "Daily coverage breakdown for each date in the range")
    List<DailyCoverageDTO> dailyCoverage,

    @Schema(description = "Identified gaps in data coverage")
    List<GapDTO> gaps,

    @Schema(description = "Whether data coverage meets the 80% sufficiency threshold",
            example = "true")
    boolean isSufficient
) {
    /**
     * Daily coverage metrics DTO.
     */
    @Schema(description = "Coverage metrics for a single day")
    public record DailyCoverageDTO(
        @Schema(description = "Date for this coverage metric",
                example = "2024-01-15")
        LocalDate date,

        @Schema(description = "Overall coverage percentage for this day",
                example = "90.0")
        BigDecimal percentage,

        @Schema(description = "Per-type coverage for this day")
        Map<String, BigDecimal> perType,

        @Schema(description = "Per-symbol coverage for this day")
        Map<String, BigDecimal> perSymbol
    ) {}

    /**
     * Data gap DTO.
     */
    @Schema(description = "Identified gap in market data")
    public record GapDTO(
        @Schema(description = "Symbol ticker",
                example = "BTC")
        String symbol,

        @Schema(description = "Data type with missing data",
                example = "PRICE",
                enumeration = {"PRICE", "NEWS", "SOCIAL"})
        String dataType,

        @Schema(description = "Gap start date",
                example = "2024-01-10")
        LocalDate from,

        @Schema(description = "Gap end date",
                example = "2024-01-12")
        LocalDate to,

        @Schema(description = "Actionability classification for this gap",
                example = "ACTIONABLE",
                enumeration = {"ACTIONABLE", "FUTURE", "NO_SOURCE", "BEFORE_HORIZON"})
        String classification,

        @Schema(description = "Provider that would fill this gap, or null if none",
                example = "alpaca",
                nullable = true)
        String provider,

        @Schema(description = "Estimated number of records a backfill would produce for this gap",
                example = "5",
                minimum = "0")
        int estimatedRecords
    ) {}
}
