package com.quorumtrading.model.valueobject;

/**
 * Value object representing the status of a MarketResearch.
 */
public enum ResearchStatus {
    ACTIVE,
    PAUSED,
    COMPLETED;

    /**
     * Check if this status represents an active research.
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * Check if transition to target status is allowed.
     */
    public boolean canTransitionTo(ResearchStatus target) {
        // COMPLETED is a terminal state
        if (this == COMPLETED) {
            return false;
        }
        // ACTIVE <-> PAUSED allowed
        if (this == ACTIVE && target == PAUSED) {
            return true;
        }
        if (this == PAUSED && target == ACTIVE) {
            return true;
        }
        // Allow transition to COMPLETED from any state
        if (target == COMPLETED) {
            return true;
        }
        return false;
    }
}
