package com.quorumtrading.application.usecase.result;

import com.quorumtrading.model.valueobject.DividendInfo;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Result of a ticker performance query over a date range.
 */
public record TickerPerformanceResult(
    String ticker,
    boolean dataAvailable,
    Instant startDate,
    Instant endDate,
    BigDecimal startPrice,
    BigDecimal endPrice,
    BigDecimal periodHigh,
    BigDecimal periodLow,
    BigDecimal changePercent,
    BigDecimal changeAbsolute,
    int dataPoints,
    String message,
    DividendInfo dividendInfo,
    String dividendNote
) {
    public static TickerPerformanceResult withData(
            String ticker, Instant startDate, Instant endDate,
            BigDecimal startPrice, BigDecimal endPrice,
            BigDecimal periodHigh, BigDecimal periodLow,
            BigDecimal changePercent, BigDecimal changeAbsolute,
            int dataPoints) {
        return new TickerPerformanceResult(
            ticker, true, startDate, endDate,
            startPrice, endPrice, periodHigh, periodLow,
            changePercent, changeAbsolute, dataPoints, null,
            null, null);
    }

    public static TickerPerformanceResult noData(String ticker, String message) {
        return new TickerPerformanceResult(
            ticker, false, null, null,
            null, null, null, null,
            null, null, 0, message,
            null, null);
    }

    /**
     * Returns a copy of this result enriched with dividend information.
     */
    public TickerPerformanceResult withDividendInfo(DividendInfo dividendInfo, String dividendNote) {
        return new TickerPerformanceResult(
            ticker, dataAvailable, startDate, endDate,
            startPrice, endPrice, periodHigh, periodLow,
            changePercent, changeAbsolute, dataPoints, message,
            dividendInfo, dividendNote);
    }
}
