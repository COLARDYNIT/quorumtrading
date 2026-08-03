package com.quorumtrading.model.valueobject;

/**
 * Value object representing the outcome of a research execution.
 */
public enum ResearchOutcome {
    STRATEGY_CREATED,
    NO_OPPORTUNITY,
    FAILED;

    /**
     * Check if this outcome represents a successful research execution
     * (even if no strategy was created).
     */
    public boolean isSuccessful() {
        return this == STRATEGY_CREATED || this == NO_OPPORTUNITY;
    }
}
