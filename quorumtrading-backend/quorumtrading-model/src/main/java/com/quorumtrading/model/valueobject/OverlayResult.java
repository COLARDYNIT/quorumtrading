package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Result of computing a buy-and-hold overlay for a single reference asset.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * <p>Contains the full time series and return percentage for a buy-and-hold
 * scenario against a benchmark's date range and initial capital.</p>
 */
public record OverlayResult(
    String symbol,
    String assetType,
    OverlayStatus status,
    List<BuyAndHoldDataPoint> dataPoints,
    BigDecimal returnPercent,
    String errorMessage
) {

    public enum OverlayStatus {
        READY,
        NO_DATA
    }

    public OverlayResult {
        Objects.requireNonNull(symbol, "symbol is required");
        Objects.requireNonNull(assetType, "assetType is required");
        Objects.requireNonNull(status, "status is required");
    }

    public static OverlayResult ready(String symbol, String assetType,
                                       List<BuyAndHoldDataPoint> dataPoints,
                                       BigDecimal returnPercent) {
        return new OverlayResult(symbol, assetType, OverlayStatus.READY, dataPoints, returnPercent, null);
    }

    public static OverlayResult noData(String symbol, String assetType, String errorMessage) {
        return new OverlayResult(symbol, assetType, OverlayStatus.NO_DATA, List.of(), null, errorMessage);
    }
}
