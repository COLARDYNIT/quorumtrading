package com.quorumtrading.application.usecase.dto;

import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.BudgetStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data Transfer Object for user budget information.
 */
public record UserBudgetDto(
    String id,
    String userId,
    BigDecimal limitAmount,
    BigDecimal currentSpend,
    BigDecimal lifetimeSpend,
    BigDecimal remainingBudget,
    BigDecimal usagePercentage,
    BudgetStatus status,
    boolean isUnlimited,
    BigDecimal dailySpendLimit,
    Instant lastTopUpAt,
    BigDecimal todaySpend,
    BigDecimal weeklySpend
) {

    /**
     * Create from domain entity (without computed spend fields).
     */
    public static UserBudgetDto fromDomain(UserBudget budget) {
        return fromDomain(budget, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Create from domain entity with computed today/weekly spend.
     */
    public static UserBudgetDto fromDomain(UserBudget budget, BigDecimal todaySpend, BigDecimal weeklySpend) {
        return new UserBudgetDto(
            budget.getId().value().toString(),
            budget.getUserId().value().toString(),
            budget.getLimit().map(l -> l.amount()).orElse(null),
            budget.getCurrentSpend().amount(),
            budget.getLifetimeSpend(),
            budget.getRemainingBudget(),
            budget.getUsagePercentage(),
            budget.getStatus(),
            budget.isUnlimited(),
            budget.getDailySpendLimit().orElse(null),
            budget.getLastTopUpAt().orElse(null),
            todaySpend,
            weeklySpend
        );
    }
}
