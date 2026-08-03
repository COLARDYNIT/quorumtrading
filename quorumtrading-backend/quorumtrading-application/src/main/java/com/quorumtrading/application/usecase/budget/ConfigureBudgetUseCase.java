package com.quorumtrading.application.usecase.budget;

import com.quorumtrading.application.event.BudgetRestoredEvent;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.BudgetLimit;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Use case for configuring a user's AI budget settings.
 */
public class ConfigureBudgetUseCase {

    private final UserBudgetRepository repository;
    private final EventPublisher<BudgetRestoredEvent> budgetRestoredPublisher;

    public ConfigureBudgetUseCase(
        UserBudgetRepository repository,
        EventPublisher<BudgetRestoredEvent> budgetRestoredPublisher
    ) {
        this.repository = repository;
        this.budgetRestoredPublisher = budgetRestoredPublisher;
    }

    /**
     * Set the monthly budget limit for a user.
     *
     * @param userId The user ID
     * @param limit  The budget limit (null to remove limit)
     * @return The updated UserBudget
     */
    public UserBudget setLimit(UserId userId, BigDecimal limit) {
        UserBudget budget = getOrCreate(userId);

        if (limit == null) {
            budget.removeLimit();
        } else {
            budget.updateLimit(BudgetLimit.usd(limit));
        }

        UserBudget saved = repository.save(budget);
        publishEvents(budget);
        return saved;
    }

    /**
     * Set model preference for a trading context.
     *
     * @param userId   The user ID
     * @param context  The trading context
     * @param modelId  The preferred model ID (null to clear)
     * @return The updated UserBudget
     */
    public UserBudget setModelPreference(UserId userId, TradingContext context, String modelId) {
        UserBudget budget = getOrCreate(userId);

        if (modelId == null || modelId.isBlank()) {
            budget.clearModelPreference(context);
        } else {
            budget.setModelPreference(context, ModelId.of(modelId));
        }

        return repository.save(budget);
    }

    /**
     * Set multiple model preferences at once.
     *
     * @param userId      The user ID
     * @param preferences Map of context to model ID
     * @return The updated UserBudget
     */
    public UserBudget setModelPreferences(UserId userId, Map<TradingContext, String> preferences) {
        UserBudget budget = getOrCreate(userId);

        for (Map.Entry<TradingContext, String> entry : preferences.entrySet()) {
            String modelId = entry.getValue();
            if (modelId == null || modelId.isBlank()) {
                budget.clearModelPreference(entry.getKey());
            } else {
                budget.setModelPreference(entry.getKey(), ModelId.of(modelId));
            }
        }

        return repository.save(budget);
    }

    /**
     * Configure both limit and preferences in one operation.
     */
    public UserBudget configure(ConfigureBudgetCommand command) {
        UserBudget budget = getOrCreate(command.userId());

        if (command.limit() != null) {
            budget.updateLimit(BudgetLimit.usd(command.limit()));
        } else if (command.removeLimit()) {
            budget.removeLimit();
        }

        if (command.preferences() != null) {
            for (Map.Entry<TradingContext, String> entry : command.preferences().entrySet()) {
                String modelId = entry.getValue();
                if (modelId == null || modelId.isBlank()) {
                    budget.clearModelPreference(entry.getKey());
                } else {
                    budget.setModelPreference(entry.getKey(), ModelId.of(modelId));
                }
            }
        }

        UserBudget saved = repository.save(budget);
        publishEvents(budget);
        return saved;
    }

    private UserBudget getOrCreate(UserId userId) {
        return repository.findByUserId(userId)
            .orElseGet(() -> UserBudget.createUnlimited(userId));
    }

    private void publishEvents(UserBudget budget) {
        for (Object event : budget.getAndClearDomainEvents()) {
            if (event instanceof UserBudget.BudgetRestoredInfo info) {
                budgetRestoredPublisher.publish(
                    BudgetRestoredEvent.of(info.userId(), info.limit(), info.spend(), info.period())
                );
            }
        }
    }

    /**
     * Command for full budget configuration.
     */
    public record ConfigureBudgetCommand(
        UserId userId,
        BigDecimal limit,
        boolean removeLimit,
        Map<TradingContext, String> preferences
    ) {
        public static ConfigureBudgetCommand withLimit(UserId userId, BigDecimal limit) {
            return new ConfigureBudgetCommand(userId, limit, false, null);
        }

        public static ConfigureBudgetCommand unlimited(UserId userId) {
            return new ConfigureBudgetCommand(userId, null, true, null);
        }
    }
}
