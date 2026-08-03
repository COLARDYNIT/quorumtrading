package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * OHLCV price bar for a single time period.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * <p><b>Note:</b> No JPA/JSON annotations - this is a pure domain object.</p>
 */
public record PriceBar(
    Instant timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) {
    public PriceBar {
        Objects.requireNonNull(timestamp, "timestamp is required");
        Objects.requireNonNull(close, "close price is required");
        // open, high, low, volume can be null for sparse data
    }
}
