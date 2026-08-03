package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for updating an existing strategy.
 *
 * @param strategyId         ID of the strategy to update
 * @param name               updated name (null means no change)
 * @param description        updated description (null means no change)
 * @param content            updated markdown content (null means no change)
 * @param requestingUserId   user requesting the update (null for system/internal calls)
 */
public record UpdateStrategyCommand(
    StrategyId strategyId,
    String name,
    String description,
    String content,
    UserId requestingUserId
) {
    /** Backward-compatible constructor for content-only internal/system callers. */
    public UpdateStrategyCommand(StrategyId strategyId, String content) {
        this(strategyId, null, null, content, null);
    }

    /** Backward-compatible constructor for content-only callers with user context. */
    public UpdateStrategyCommand(StrategyId strategyId, String content, UserId requestingUserId) {
        this(strategyId, null, null, content, requestingUserId);
    }

    /** Backward-compatible constructor without description. */
    public UpdateStrategyCommand(StrategyId strategyId, String name, String content, UserId requestingUserId) {
        this(strategyId, name, null, content, requestingUserId);
    }
}
