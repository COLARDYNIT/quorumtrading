package com.quorumtrading.application.usecase.command;

import java.time.Instant;

/**
 * Command for triggering market data collection.
 *
 * @param category optional category filter (e.g., 'crypto', 'forex')
 * @param from optional start date for historical data collection
 * @param to optional end date for historical data collection
 */
public record TriggerDataCollectionCommand(
    String category,
    Instant from,
    Instant to
) {
    /**
     * Check if a date range is specified.
     */
    public boolean hasDateRange() {
        return from != null && to != null;
    }

    /**
     * Constructor for backwards compatibility (no date range).
     */
    public TriggerDataCollectionCommand(String category) {
        this(category, null, null);
    }
}
