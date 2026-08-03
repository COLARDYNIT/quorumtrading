package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;

/**
 * Value object representing a gap in market data coverage.
 * Identifies missing data for a specific symbol, data type, and time period.
 *
 * <p><b>Symbol can be null for market-wide data types:</b></p>
 * <ul>
 *   <li>PRICE: Symbol-specific (symbol required)</li>
 *   <li>NEWS: Market-wide (symbol is null, represents general market news)</li>
 *   <li>SOCIAL: Market-wide (symbol is null, represents general social sentiment)</li>
 * </ul>
 */
public record MarketDataGap(
    Symbol symbol,
    EventType dataType,
    TimeRange period
) {
    /**
     * Compact constructor with validation.
     * Symbol can be null for market-wide data types (NEWS, SOCIAL).
     */
    public MarketDataGap {
        if (dataType == null) throw new IllegalArgumentException("DataType cannot be null");
        if (period == null) throw new IllegalArgumentException("Period cannot be null");
        // Symbol is required for PRICE data, optional for NEWS/SOCIAL (market-wide)
        if (symbol == null && dataType == EventType.PRICE) {
            throw new IllegalArgumentException("Symbol is required for PRICE data type");
        }
    }

    /**
     * Check if this is a market-wide gap (no specific symbol).
     * @return true if this gap represents market-wide data
     */
    public boolean isMarketWide() {
        return symbol == null;
    }

    /**
     * Get a display-friendly description of this gap.
     * @return human-readable gap description
     */
    public String getDisplayName() {
        String symbolPart = symbol != null ? symbol.ticker() : "MARKET";
        return String.format("%s - %s (%s to %s)",
            symbolPart, dataType, period.getStart(), period.getEnd());
    }
}
