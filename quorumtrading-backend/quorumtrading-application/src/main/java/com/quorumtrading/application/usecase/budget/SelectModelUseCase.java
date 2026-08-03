package com.quorumtrading.application.usecase.budget;

import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.service.ModelSelectionService;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Use case for selecting the appropriate LLM model for a user and context.
 */
public class SelectModelUseCase {

    private final UserBudgetRepository repository;
    private final ModelSelectionService modelSelectionService;

    public SelectModelUseCase(
        UserBudgetRepository repository,
        ModelSelectionService modelSelectionService
    ) {
        this.repository = repository;
        this.modelSelectionService = modelSelectionService;
    }

    /**
     * Select the model to use for a given user and context.
     * <p>
     * Selection priority:
     * <ol>
     *   <li>User's preference for the context</li>
     *   <li>System default for the context</li>
     * </ol>
     *
     * @param userId  The user ID
     * @param context The trading context
     * @return The selected model ID
     */
    public String selectModel(UserId userId, TradingContext context) {
        UserBudget userBudget = repository.findByUserId(userId).orElse(null);
        return modelSelectionService.selectModelId(userBudget, context);
    }

    /**
     * Select model for a context when user is unknown (e.g., system operations).
     *
     * @param context The trading context
     * @return The system default model ID for the context
     */
    public String selectDefaultModel(TradingContext context) {
        return modelSelectionService.selectModelId(null, context);
    }
}
