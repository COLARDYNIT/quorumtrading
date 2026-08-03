package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event raised when a user's AI budget is fully depleted.
 * <p>
 * Listeners should pause AI-dependent operations for this user
 * until budget becomes available again.
 * </p>
 *
 * @param userId       The user whose budget was depleted
 * @param budgetLimit  The budget limit that was reached
 * @param currentSpend The current spend amount
 * @param occurredAt   When the depletion occurred
 */
public record BudgetDepletedEvent(
    UserId userId,
    BigDecimal budgetLimit,
    BigDecimal currentSpend,
    Instant occurredAt
) {

    public BudgetDepletedEvent {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public static BudgetDepletedEvent of(UserId userId, BigDecimal limit, BigDecimal spend) {
        return new BudgetDepletedEvent(userId, limit, spend, Instant.now());
    }
}
