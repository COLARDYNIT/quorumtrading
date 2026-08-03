package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.UpdateStrategyCommand;

/**
 * Use case for updating an existing strategy.
 */
public interface UpdateStrategyUseCase {

    /**
     * Execute the update strategy use case.
     *
     * @param command the command containing the strategy ID and updates
     * @throws com.quorumtrading.application.port.out.StrategyNotFoundException if strategy not found
     */
    void execute(UpdateStrategyCommand command);
}
