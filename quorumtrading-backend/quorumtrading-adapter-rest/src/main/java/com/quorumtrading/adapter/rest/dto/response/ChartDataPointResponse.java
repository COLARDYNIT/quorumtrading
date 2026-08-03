package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for chart data points.
 * <p>
 * Represents a single decimated data point optimized for chart rendering.
 * Contains portfolio value and cash available at a specific timestamp.
 * </p>
 */
@Schema(description = "Chart data point containing portfolio metrics at a specific timestamp")
public record ChartDataPointResponse(
    @Schema(description = "Timestamp of this data point", example = "2025-11-19T10:30:00Z")
    Instant timestamp,

    @Schema(description = "Total portfolio value in USD at this timestamp", example = "125789.50")
    BigDecimal portfolioValue,

    @Schema(description = "Cash available in USD at this timestamp", example = "15234.25")
    BigDecimal cashAvailable
) {
}
