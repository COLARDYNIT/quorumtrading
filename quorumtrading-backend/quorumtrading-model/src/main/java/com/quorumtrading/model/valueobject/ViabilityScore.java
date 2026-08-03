package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representing a strategy viability score (0-100).
 */
public record ViabilityScore(BigDecimal value) {

    public ViabilityScore {
        if (value == null) {
            throw new IllegalArgumentException("Viability score cannot be null");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Viability score must be between 0 and 100, got: " + value);
        }
    }
}
