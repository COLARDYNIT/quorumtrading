package com.quorumtrading.model.valueobject;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Represents an immutable time range with start and end instants.
 * Used for date range queries in market data caching.
 */
public record TimeRange(Instant start, Instant end) {

    public TimeRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end must not be null");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    /**
     * Create a TimeRange from LocalDate start and end (inclusive).
     * Converts dates to UTC midnight instants.
     */
    public static TimeRange of(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("start and end dates must not be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("end date must be on or after start date");
        }

        Instant startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        // End date is inclusive - set to end of day
        Instant endInstant = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        return new TimeRange(startInstant, endInstant);
    }

    /**
     * Get the start date of this time range in UTC.
     */
    public LocalDate getStart() {
        return start.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * Get the end date of this time range in UTC.
     */
    public LocalDate getEnd() {
        // Subtract one day since end is exclusive (next day's midnight)
        return end.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
    }

    /**
     * Calculate duration of this time range in seconds.
     */
    public long durationSeconds() {
        return Duration.between(start, end).getSeconds();
    }

    /**
     * Check if given instant is within this range (inclusive).
     */
    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && !instant.isAfter(end);
    }

    @Override
    public String toString() {
        return String.format("[%s - %s]", start, end);
    }
}
