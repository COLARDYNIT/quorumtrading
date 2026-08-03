package com.quorumtrading.application.usecase.query;

import java.time.Instant;

/**
 * Query for retrieving market events with optional filters.
 *
 * @param from start timestamp (required)
 * @param to end timestamp (required)
 * @param symbol optional trading symbol filter
 * @param source optional data source filter
 * @param eventType optional event type filter
 * @param limit optional maximum number of results
 */
public record QueryMarketEventsQuery(
    Instant from,
    Instant to,
    String symbol,
    String source,
    String eventType,
    Integer limit
) {
}
