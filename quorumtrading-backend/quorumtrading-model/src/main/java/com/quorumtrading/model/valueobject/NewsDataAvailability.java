package com.quorumtrading.model.valueobject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Represents the earliest date from which news data is available for backtesting.
 * Computed as the minimum across all enabled news adapters.
 */
public record NewsDataAvailability(LocalDate earliestNewsDate) {

    /**
     * Returns true if the given instant falls before the earliest available news date.
     */
    public boolean isBeforeCutoff(Instant date) {
        return LocalDate.ofInstant(date, ZoneOffset.UTC).isBefore(earliestNewsDate);
    }
}
