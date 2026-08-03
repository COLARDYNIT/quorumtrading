package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

/**
 * Event raised when a user's AI budget becomes available again.
 * <p>
 * This occurs when:
 * <ul>
 *   <li>A new billing period starts and spend resets</li>
 *   <li>The user increases their budget limit</li>
 * </ul>
 * Listeners should resume previously paused AI operations.
 * </p>
 *
 * @param userId       The user whose budget was restored
 * @param newLimit     The new budget limit (may be same or increased)
 * @param currentSpend The current spend (usually 0 after reset)
 * @param period       The billing period
 * @param occurredAt   When the restoration occurred
 */
public record BudgetRestoredEvent(
    UserId userId,
    BigDecimal newLimit,
    BigDecimal currentSpend,
    YearMonth period,
    Instant occurredAt
) {

    public BudgetRestoredEvent {
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (period == null) {
            period = YearMonth.now();
        }
    }

    public static BudgetRestoredEvent of(UserId userId, BigDecimal limit, BigDecimal spend, YearMonth period) {
        return new BudgetRestoredEvent(userId, limit, spend, period, Instant.now());
    }
}
