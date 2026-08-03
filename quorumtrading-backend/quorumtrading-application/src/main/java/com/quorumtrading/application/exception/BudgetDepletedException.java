package com.quorumtrading.application.exception;

import com.quorumtrading.model.valueobject.UserId;

/**
 * Exception thrown when attempting an LLM operation with depleted budget.
 */
public class BudgetDepletedException extends RuntimeException {

    private final UserId userId;

    public BudgetDepletedException(UserId userId) {
        super("AI budget depleted for user: " + userId);
        this.userId = userId;
    }

    public UserId getUserId() {
        return userId;
    }
}
