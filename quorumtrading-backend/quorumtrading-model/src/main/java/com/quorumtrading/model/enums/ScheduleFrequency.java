package com.quorumtrading.model.enums;

import java.time.Duration;
import java.time.Instant;

/**
 * Frequency for scheduled simulation runs.
 */
public enum ScheduleFrequency {
    DAILY("Daily", Duration.ofDays(1)),
    WEEKLY("Weekly", Duration.ofDays(7)),
    MANUAL_ONLY("Manual Only", null);

    private final String displayName;
    private final Duration interval;

    ScheduleFrequency(String displayName, Duration interval) {
        this.displayName = displayName;
        this.interval = interval;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Calculate next run time from given instant.
     * @param from The reference instant
     * @return Next run instant, or null for MANUAL_ONLY
     */
    public Instant calculateNextRun(Instant from) {
        if (interval == null) {
            return null;
        }
        return from.plus(interval);
    }
}
