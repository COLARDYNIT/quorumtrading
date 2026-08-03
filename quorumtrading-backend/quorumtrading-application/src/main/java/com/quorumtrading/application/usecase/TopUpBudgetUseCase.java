package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.usecase.dto.UserBudgetDto;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.BudgetLimit;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Use case to top up a user's budget.
 * This is a mock implementation that increases the budget limit.
 *
 * <p>In a real implementation, this would integrate with a payment provider.</p>
 */
public class TopUpBudgetUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(TopUpBudgetUseCase.class);

    private final UserBudgetRepository userBudgetRepository;

    public TopUpBudgetUseCase(UserBudgetRepository userBudgetRepository) {
        this.userBudgetRepository = userBudgetRepository;
    }

    /**
     * Top up the user's budget by the specified amount.
     *
     * @param userId the user's ID
     * @param amount the amount to add (in USD)
     * @return the updated budget DTO
     */
    public UserBudgetDto execute(UserId userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Top-up amount must be positive");
        }

        LOG.info("Processing top-up of ${} for user {}", amount, userId.value());

        UserBudget budget = userBudgetRepository.findByUserId(userId)
            .orElseGet(() -> {
                // Create new budget with the top-up amount as limit
                UserBudget newBudget = UserBudget.createWithLimit(userId, BudgetLimit.usd(amount.doubleValue()));
                return userBudgetRepository.save(newBudget);
            });

        // Calculate new limit
        BigDecimal currentLimit = budget.getLimit()
            .map(l -> l.amount())
            .orElse(BigDecimal.ZERO);
        BigDecimal newLimit = currentLimit.add(amount);

        // Update the limit and record the top-up time
        budget.updateLimit(BudgetLimit.usd(newLimit.doubleValue()));
        budget.recordTopUp();

        // Save and return
        UserBudget saved = userBudgetRepository.save(budget);
        LOG.info("Budget topped up successfully. New limit: ${}", newLimit);

        return UserBudgetDto.fromDomain(saved);
    }
}
