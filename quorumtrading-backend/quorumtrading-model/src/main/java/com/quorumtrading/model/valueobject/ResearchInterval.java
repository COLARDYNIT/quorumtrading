package com.quorumtrading.model.valueobject;

import java.util.Objects;

/**
 * Value object representing a research execution interval.
 * Ensures interval is at least 1 day.
 */
public record ResearchInterval(int amount, Unit unit) {

    public enum Unit {
        DAYS, WEEKS, MONTHS
    }

    public ResearchInterval {
        if (amount < 1) {
            throw new IllegalArgumentException("Interval amount must be at least 1");
        }
        if (unit == null) {
            throw new IllegalArgumentException("Interval unit cannot be null");
        }
    }
}
