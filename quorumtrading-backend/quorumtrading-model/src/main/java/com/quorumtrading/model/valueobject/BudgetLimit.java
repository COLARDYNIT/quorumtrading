package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object representing a monthly budget limit.
 * Immutable and self-validating.
 */
public record BudgetLimit(
    BigDecimal amount,
    String currency
) {

    private static final String DEFAULT_CURRENCY = "USD";

    public BudgetLimit {
        if (amount == null) {
            throw new IllegalArgumentException("Budget limit amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Budget limit cannot be negative");
        }
        if (currency == null || currency.isBlank()) {
            currency = DEFAULT_CURRENCY;
        }
        currency = currency.toUpperCase();
    }

    /**
     * Create a USD budget limit.
     */
    public static BudgetLimit usd(BigDecimal amount) {
        return new BudgetLimit(amount, DEFAULT_CURRENCY);
    }

    /**
     * Create a USD budget limit from a double value.
     */
    public static BudgetLimit usd(double amount) {
        return new BudgetLimit(BigDecimal.valueOf(amount), DEFAULT_CURRENCY);
    }

    /**
     * Check if a spend amount exceeds this limit.
     */
    public boolean isExceededBy(BigDecimal spend) {
        Objects.requireNonNull(spend, "Spend cannot be null");
        return spend.compareTo(amount) >= 0;
    }

    /**
     * Check if a spend amount is at or above the warning threshold (80%).
     */
    public boolean isWarningThresholdReached(BigDecimal spend) {
        Objects.requireNonNull(spend, "Spend cannot be null");
        BigDecimal threshold = amount.multiply(BigDecimal.valueOf(0.80));
        return spend.compareTo(threshold) >= 0;
    }

    /**
     * Calculate remaining budget.
     */
    public BigDecimal remaining(BigDecimal spend) {
        Objects.requireNonNull(spend, "Spend cannot be null");
        BigDecimal remaining = amount.subtract(spend);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    /**
     * Calculate usage percentage.
     */
    public BigDecimal usagePercentage(BigDecimal spend) {
        Objects.requireNonNull(spend, "Spend cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        return spend.multiply(BigDecimal.valueOf(100))
            .divide(amount, 2, java.math.RoundingMode.HALF_UP);
    }
}
