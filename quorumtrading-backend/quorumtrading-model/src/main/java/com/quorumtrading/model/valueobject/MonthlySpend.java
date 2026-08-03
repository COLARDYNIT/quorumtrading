package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Value object representing the current monthly spend.
 * Immutable and tracks the period it belongs to.
 */
public record MonthlySpend(
    BigDecimal amount,
    YearMonth period
) {

    public MonthlySpend {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Spend cannot be negative");
        }
        if (period == null) {
            period = YearMonth.now();
        }
    }

    /**
     * Create a zero spend for the current month.
     */
    public static MonthlySpend zero() {
        return new MonthlySpend(BigDecimal.ZERO, YearMonth.now());
    }

    /**
     * Create a zero spend for a specific period.
     */
    public static MonthlySpend zeroFor(YearMonth period) {
        return new MonthlySpend(BigDecimal.ZERO, period);
    }

    /**
     * Create a new MonthlySpend with the added cost.
     */
    public MonthlySpend add(BigDecimal cost) {
        Objects.requireNonNull(cost, "Cost cannot be null");
        if (cost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cost cannot be negative");
        }
        return new MonthlySpend(this.amount.add(cost), this.period);
    }

    /**
     * Check if this spend is for the current period.
     */
    public boolean isCurrentPeriod() {
        return period.equals(YearMonth.now());
    }

    /**
     * Check if this spend is less than the given limit.
     */
    public boolean isLessThan(BudgetLimit limit) {
        if (limit == null) {
            return true; // No limit means always under
        }
        return amount.compareTo(limit.amount()) < 0;
    }

    /**
     * Get the status relative to a budget limit.
     */
    public BudgetStatus getStatusFor(BudgetLimit limit) {
        if (limit == null) {
            return BudgetStatus.AVAILABLE;
        }
        if (limit.isExceededBy(amount)) {
            return BudgetStatus.DEPLETED;
        }
        if (limit.isWarningThresholdReached(amount)) {
            return BudgetStatus.WARNING;
        }
        return BudgetStatus.AVAILABLE;
    }
}
