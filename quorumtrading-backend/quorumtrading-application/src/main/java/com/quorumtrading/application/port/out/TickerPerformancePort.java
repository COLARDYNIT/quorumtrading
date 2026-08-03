package com.quorumtrading.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Port for computing ticker performance aggregates directly in the database.
 * Uses a single SQL query instead of loading all price bars into memory.
 *
 * <p><b>Hexagonal Architecture:</b> Outbound port (driven)</p>
 */
public interface TickerPerformancePort {

    /**
     * Compute performance aggregates for a ticker over a date range.
     *
     * @return aggregated result, or empty if no data exists
     */
    Optional<PerformanceAggregate> getPerformanceAggregate(String ticker, Instant from, Instant to);

    /**
     * Aggregated performance data from a single SQL query.
     */
    record PerformanceAggregate(
        BigDecimal startPrice,
        Instant startDate,
        BigDecimal endPrice,
        Instant endDate,
        BigDecimal periodHigh,
        BigDecimal periodLow,
        long dataPoints
    ) {}
}
