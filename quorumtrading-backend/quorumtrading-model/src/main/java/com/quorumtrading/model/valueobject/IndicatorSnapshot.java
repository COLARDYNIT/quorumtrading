package com.quorumtrading.model.valueobject;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Calculated indicator values for a single ticker at a point in time.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * <p>Contains both calculated values and per-indicator warnings when data is insufficient.</p>
 *
 * <p><b>Note:</b> No JPA/JSON annotations - this is a pure domain object.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * Map&lt;IndicatorType, IndicatorValue&gt; values = new EnumMap&lt;&gt;(IndicatorType.class);
 * values.put(IndicatorType.MOVING_AVERAGE, new IndicatorValue.MovingAverageValue(sma50, sma200));
 * values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(rsi14));
 *
 * Map&lt;IndicatorType, String&gt; warnings = new EnumMap&lt;&gt;(IndicatorType.class);
 * warnings.put(IndicatorType.MOVING_AVERAGE, "Only 150 days of data, need 200 for SMA-200");
 *
 * IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, warnings);
 * </pre>
 *
 * @param ticker The stock symbol for which indicators were calculated
 * @param calculatedAt The timestamp when the indicators were calculated
 * @param values Map of indicator type to calculated value
 * @param warnings Map of indicator type to warning message (when data is insufficient)
 *
 * @see IndicatorType
 * @see IndicatorValue
 */
public record IndicatorSnapshot(
    Symbol ticker,
    Instant calculatedAt,
    Map<IndicatorType, IndicatorValue> values,
    Map<IndicatorType, String> warnings
) {
    /**
     * Canonical constructor with null validation.
     */
    public IndicatorSnapshot {
        Objects.requireNonNull(ticker, "ticker is required");
        Objects.requireNonNull(calculatedAt, "calculatedAt is required");
        Objects.requireNonNull(values, "values is required");
        Objects.requireNonNull(warnings, "warnings is required");
    }

    /**
     * Create an empty snapshot when no data is available.
     * All indicator types will have the same warning message.
     *
     * <p>This factory method is useful when price data cannot be fetched
     * or when there is no historical data for the ticker.</p>
     *
     * @param ticker The stock symbol
     * @param reason The reason why indicators could not be calculated
     * @return A new IndicatorSnapshot with empty values and warnings for all indicator types
     */
    public static IndicatorSnapshot empty(Symbol ticker, String reason) {
        Objects.requireNonNull(ticker, "ticker is required");
        Objects.requireNonNull(reason, "reason is required");

        Map<IndicatorType, String> warnings = new EnumMap<>(IndicatorType.class);
        for (IndicatorType type : IndicatorType.values()) {
            warnings.put(type, reason);
        }
        return new IndicatorSnapshot(ticker, Instant.now(), Map.of(), warnings);
    }
}
