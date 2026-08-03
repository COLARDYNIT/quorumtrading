package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Buy-and-hold overlay result for a single reference asset")
public record OverlayResponse(
    @Schema(description = "Ticker symbol", example = "SPY")
    String symbol,

    @Schema(description = "Asset type: STOCK or CRYPTO", example = "STOCK")
    String assetType,

    @Schema(description = "Overlay status: READY or NO_DATA", example = "READY")
    String status,

    @Schema(description = "Buy-and-hold equity curve data points")
    List<OverlayDataPointResponse> dataPoints,

    @Schema(description = "Total return percentage", example = "12.34")
    BigDecimal returnPercent,

    @Schema(description = "Error message if status is NO_DATA")
    String errorMessage
) {
}
