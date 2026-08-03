package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for measured provider data horizons — the earliest date a provider
 * has data for each requested (symbol, dataType).
 */
@Schema(description = "Measured earliest-available dates per (symbol, data type)")
public record DataHorizonResponse(
        @Schema(description = "One entry per requested (symbol, data type)")
        List<Entry> horizons
) {

    @Schema(description = "Earliest-available data measurement for one (symbol, data type)")
    public record Entry(
            @Schema(description = "Symbol ticker", example = "BTC")
            String symbol,

            @Schema(description = "Data type", example = "PRICE", enumeration = {"PRICE", "NEWS", "SOCIAL"})
            String dataType,

            @Schema(description = "Earliest available date as ISO-8601, or null if unknown / no data",
                    example = "2024-07-31")
            String earliestAvailable,

            @Schema(description = "Whether a concrete earliest-available date is known", example = "true")
            boolean hasData,

            @Schema(description = "When this horizon was last probed/updated (ISO-8601 instant)",
                    example = "2026-05-31T00:00:00Z")
            String probedAt
    ) {}
}
