package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Request DTO for updating an existing market research configuration.
 * All fields are optional - only provided fields will be updated.
 */
@Schema(description = "Request to update an existing market research configuration")
public record UpdateMarketResearchRequest(
    @Schema(description = "Updated market research name",
            example = "Bitcoin Weekly Opportunity Scanner v2")
    String name,

    @Schema(description = "Updated markdown-based research instructions for the LLM researcher agent",
            example = "# Updated Research Instructions\n\nAnalyze Bitcoin for:\n- Technical momentum\n- Fundamental catalysts")
    String description,

    @Schema(description = "Updated list of ticker symbols to research, in TICKER or TICKER:TYPE form. "
            + "A bare ticker defaults to STOCK; append the asset type for crypto (e.g. \"BTC:CRYPTO\").",
            example = "[\"AAPL\", \"BTC:CRYPTO\", \"ETH:CRYPTO\"]")
    List<String> targetAssets,

    @Valid
    @Schema(description = "Updated research execution interval")
    IntervalRequest interval
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
}
