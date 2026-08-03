package com.quorumtrading.application.usecase.query;

import java.time.Instant;

/**
 * Query to get historical price performance for a ticker over a date range.
 */
public record GetTickerPerformanceQuery(
    String ticker,
    Instant startDate,
    Instant endDate
) {
    public GetTickerPerformanceQuery {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker is required");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("startDate is required");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("endDate is required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
    }
}
