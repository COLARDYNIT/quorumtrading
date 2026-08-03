package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.CreateStrategyCommand;
import com.quorumtrading.model.valueobject.StrategyId;

/**
 * Use case for creating a new trading strategy.
 * Creates a strategy version with markdown configuration.
 */
public interface CreateStrategyUseCase {

    /**
     * Execute the create strategy use case.
     *
     * @param command the command containing strategy creation parameters
     * @return the ID of the newly created strategy
     */
    StrategyId execute(CreateStrategyCommand command);
}
