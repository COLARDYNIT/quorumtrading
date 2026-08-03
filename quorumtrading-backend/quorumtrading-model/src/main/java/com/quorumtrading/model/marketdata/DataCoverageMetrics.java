package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.valueobject.Symbol;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Value object representing data coverage metrics for market data.
 * Provides overall coverage percentage and breakdowns by data type and symbol.
 */
public record DataCoverageMetrics(
    BigDecimal overallPercentage,
    Map<EventType, BigDecimal> perTypePercentage,
    Map<Symbol, BigDecimal> perSymbolPercentage
) {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Check if the data coverage is complete (100%).
     */
    public boolean isComplete() {
        return overallPercentage.compareTo(HUNDRED) >= 0;
    }

    /**
     * Check if the data coverage meets or exceeds the given threshold.
     *
     * @param threshold the minimum required coverage percentage
     * @return true if coverage is sufficient
     */
    public boolean isSufficient(BigDecimal threshold) {
        return overallPercentage.compareTo(threshold) >= 0;
    }
}
