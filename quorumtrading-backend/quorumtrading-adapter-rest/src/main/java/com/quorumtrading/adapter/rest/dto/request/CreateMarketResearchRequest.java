package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Request DTO for creating a new market research configuration.
 */
@Schema(description = "Request to create a new market research configuration")
public record CreateMarketResearchRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    @Schema(description = "Market research name",
            example = "Bitcoin Weekly Opportunity Scanner",
            required = true)
    String name,

    @Size(max = 512000, message = "Description must not exceed 500KB")
    @Schema(description = "Markdown-based research instructions for the LLM researcher agent",
            example = "# Research Instructions\n\nAnalyze Bitcoin for:\n- Technical momentum\n- Fundamental catalysts\n- Market sentiment shifts")
    String description,

    @Schema(description = "Optional list of ticker symbols as starting points for research, in TICKER or TICKER:TYPE form. "
            + "A bare ticker defaults to STOCK; append the asset type for crypto (e.g. \"BTC:CRYPTO\"). "
            + "If empty, the LLM will discover relevant assets based on instructions.",
            example = "[\"AAPL\", \"BTC:CRYPTO\"]")
    List<String> targetAssets,

    @NotNull(message = "Interval is required")
    @Valid
    @Schema(description = "Research execution interval",
            required = true)
    IntervalRequest interval,

    @Schema(description = "Enable automatic backtesting when viable strategy is found",
            defaultValue = "false")
    boolean autoBacktest,

    @Valid
    @Schema(description = "Backtest configuration (required when autoBacktest is true)")
    BacktestConfigRequest backtestConfig
) {
    /**
     * Nested DTO for research interval configuration.
     */
    @Schema(description = "Research execution interval configuration")
    public record IntervalRequest(
        @Min(value = 1, message = "Interval amount must be at least 1")
        @Schema(description = "Interval amount",
                example = "7",
                required = true)
        int amount,

        @NotNull(message = "Interval unit is required")
        @Schema(description = "Interval unit",
                example = "DAYS",
                required = true,
                enumeration = {"DAYS", "WEEKS", "MONTHS"})
        String unit
    ) {}

    /**
     * Nested DTO for backtest configuration.
     */
    @Schema(description = "Backtest configuration for automatic strategy testing")
    public record BacktestConfigRequest(
        @NotNull(message = "Backtest length is required")
        @Schema(description = "Backtest duration (ISO-8601 duration format)",
                example = "P30D",
                required = true)
        String backtestLength,

        @Schema(description = "Optional period start for historical backtesting (ISO-8601 instant)",
                example = "2024-01-01T00:00:00Z")
        String periodStart,

        @Schema(description = "Optional period end for historical backtesting (ISO-8601 instant)",
                example = "2024-01-31T23:59:59Z")
        String periodEnd
    ) {}
}
