package com.quorumtrading.model.valueobject;

/**
 * Represents the current status of a user's AI budget.
 */
public enum BudgetStatus {

    /**
     * Budget is available (less than 80% used).
     */
    AVAILABLE,

    /**
     * Budget warning threshold reached (80-99% used).
     */
    WARNING,

    /**
     * Budget is fully depleted (100% used).
     * Operations should be paused.
     */
    DEPLETED;

    /**
     * Check if operations can proceed with this status.
     */
    public boolean canProceed() {
        return this != DEPLETED;
    }
}
