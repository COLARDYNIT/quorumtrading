package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for deleting a strategy.
 *
 * @param strategyId ID of the strategy to delete
 * @param requestingUserId ID of the user requesting deletion (for authorization)
 */
public record DeleteStrategyCommand(
    StrategyId strategyId,
    UserId requestingUserId
) {
}
