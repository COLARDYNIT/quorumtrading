package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event raised when a user's AI budget reaches the warning threshold (80%).
 * <p>
 * Listeners may notify the user that their budget is running low.
 * </p>
 *
 * @param userId           The user approaching their budget limit
 * @param budgetLimit      The budget limit
 * @param currentSpend     The current spend amount
 * @param usagePercentage  The current usage percentage (80-99)
 * @param occurredAt       When the warning threshold was crossed
 */
public record BudgetWarningEvent(
    UserId userId,
    BigDecimal budgetLimit,
    BigDecimal currentSpend,
    BigDecimal usagePercentage,
    Instant occurredAt
) {

    public BudgetWarningEvent {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public static BudgetWarningEvent of(UserId userId, BigDecimal limit, BigDecimal spend, BigDecimal percentage) {
        return new BudgetWarningEvent(userId, limit, spend, percentage, Instant.now());
    }
}
