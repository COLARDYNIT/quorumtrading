package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.DeleteStrategyCommand;

/**
 * Use case for deleting a strategy.
 */
public interface DeleteStrategyUseCase {

    /**
     * Execute the delete strategy use case.
     *
     * @param command the command containing the strategy ID to delete
     * @throws com.quorumtrading.application.port.out.StrategyNotFoundException if strategy not found
     * @throws com.quorumtrading.application.port.out.StrategyInUseException if strategy is used by active simulations
     */
    void execute(DeleteStrategyCommand command);
}
