package com.quorumtrading.model.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a unique identifier for a UserBudget aggregate.
 */
public record UserBudgetId(UUID value) {

    public UserBudgetId {
        Objects.requireNonNull(value, "UserBudgetId value cannot be null");
    }

    /**
     * Create a new random UserBudgetId.
     */
    public static UserBudgetId generate() {
        return new UserBudgetId(UUID.randomUUID());
    }

    /**
     * Create from an existing UUID.
     */
    public static UserBudgetId of(UUID value) {
        return new UserBudgetId(value);
    }

    /**
     * Parse from a string representation.
     */
    public static UserBudgetId fromString(String value) {
        return new UserBudgetId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
