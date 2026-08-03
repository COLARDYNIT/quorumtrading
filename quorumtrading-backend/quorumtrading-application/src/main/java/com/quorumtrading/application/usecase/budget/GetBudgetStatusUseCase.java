package com.quorumtrading.application.usecase.budget;

import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.BudgetStatus;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Use case for querying budget status information.
 */
public class GetBudgetStatusUseCase {

    private final UserBudgetRepository repository;

    public GetBudgetStatusUseCase(UserBudgetRepository repository) {
        this.repository = repository;
    }

    /**
     * Get the complete budget information for a user.
     *
     * @param userId The user ID
     * @return Budget info or empty if no budget configured
     */
    public Optional<BudgetInfo> getBudgetInfo(UserId userId) {
        return repository.findByUserId(userId)
            .map(this::toBudgetInfo);
    }

    /**
     * Get the budget status for a user.
     *
     * @param userId The user ID
     * @return Budget status (AVAILABLE if no budget configured)
     */
    public BudgetStatus getStatus(UserId userId) {
        return repository.findByUserId(userId)
            .map(UserBudget::getStatus)
            .orElse(BudgetStatus.AVAILABLE);
    }

    /**
     * Get remaining budget for a user.
     *
     * @param userId The user ID
     * @return Remaining budget amount, null if unlimited
     */
    public BigDecimal getRemainingBudget(UserId userId) {
        return repository.findByUserId(userId)
            .map(UserBudget::getRemainingBudget)
            .orElse(null);
    }

    /**
     * Check if user has any budget configured.
     */
    public boolean hasBudgetConfigured(UserId userId) {
        return repository.existsByUserId(userId);
    }

    private BudgetInfo toBudgetInfo(UserBudget budget) {
        return new BudgetInfo(
            budget.getUserId(),
            budget.getLimit().map(l -> l.amount()).orElse(null),
            budget.getCurrentSpend().amount(),
            budget.getRemainingBudget(),
            budget.getUsagePercentage(),
            budget.getStatus(),
            budget.getModelPreferences(),
            budget.isUnlimited()
        );
    }

    /**
     * Complete budget information DTO.
     */
    public record BudgetInfo(
        UserId userId,
        BigDecimal limit,
        BigDecimal currentSpend,
        BigDecimal remaining,
        BigDecimal usagePercentage,
        BudgetStatus status,
        ModelPreferences modelPreferences,
        boolean unlimited
    ) {}
}
