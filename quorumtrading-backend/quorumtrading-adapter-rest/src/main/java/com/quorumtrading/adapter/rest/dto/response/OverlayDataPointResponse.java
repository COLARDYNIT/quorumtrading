package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A single data point in a buy-and-hold overlay curve")
public record OverlayDataPointResponse(
    @Schema(description = "Timestamp in ISO-8601 format", example = "2024-01-02T00:00:00Z")
    String timestamp,

    @Schema(description = "Portfolio value at this timestamp", example = "100234.56")
    BigDecimal portfolioValue
) {
}
