package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Use case to update the user's daily spend limit.
 * Setting limit to null clears the limit.
 */
public class UpdateDailySpendLimitUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateDailySpendLimitUseCase.class);

    private final UserBudgetRepository userBudgetRepository;

    public UpdateDailySpendLimitUseCase(UserBudgetRepository userBudgetRepository) {
        this.userBudgetRepository = userBudgetRepository;
    }

    /**
     * Update the daily spend limit for the specified user.
     *
     * @param userId the user's ID
     * @param dailyLimit the new daily limit (null to clear)
     */
    public void execute(UserId userId, BigDecimal dailyLimit) {
        if (dailyLimit != null && dailyLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Daily spend limit must be positive");
        }

        UserBudget budget = userBudgetRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserBudget newBudget = UserBudget.createUnlimited(userId);
                return userBudgetRepository.save(newBudget);
            });

        if (dailyLimit != null) {
            budget.setDailySpendLimit(dailyLimit);
            LOG.info("Daily spend limit set to ${} for user {}", dailyLimit, userId.value());
        } else {
            budget.clearDailySpendLimit();
            LOG.info("Daily spend limit cleared for user {}", userId.value());
        }

        userBudgetRepository.save(budget);
    }
}
