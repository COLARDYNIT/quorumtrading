package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A single data point in a buy-and-hold reference curve.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * <p>Represents the portfolio value at a given timestamp if the investor
 * had simply bought and held a reference asset from the start date.</p>
 */
public record BuyAndHoldDataPoint(
    Instant timestamp,
    BigDecimal portfolioValue
) {
    public BuyAndHoldDataPoint {
        Objects.requireNonNull(timestamp, "timestamp is required");
        Objects.requireNonNull(portfolioValue, "portfolioValue is required");
    }
}
