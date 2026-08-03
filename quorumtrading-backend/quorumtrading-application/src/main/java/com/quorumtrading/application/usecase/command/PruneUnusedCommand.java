package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.UserId;

/**
 * @param dryRun report what would be removed without removing anything. Pruning is irreversible
 *     and "unused" means zero simulations — which every strategy is until its first backtest — so
 *     a preview is the only way to see that a batch of just-written strategies is about to be
 *     deleted. The MCP tool previews by default and requires an explicit confirmation to delete.
 */
public record PruneUnusedCommand(
    UserId userId,
    boolean removeUnusedStrategies,
    boolean removeUnusedResearch,
    boolean dryRun
) {
    /** Existing callers keep the deleting behaviour they were written against. */
    public PruneUnusedCommand(UserId userId, boolean removeUnusedStrategies, boolean removeUnusedResearch) {
        this(userId, removeUnusedStrategies, removeUnusedResearch, false);
    }
}
