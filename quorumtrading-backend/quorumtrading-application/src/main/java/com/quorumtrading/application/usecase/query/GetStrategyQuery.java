package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Query for retrieving a single strategy by ID.
 *
 * @param strategyId         ID of the strategy to retrieve
 * @param requestingUserId   the user requesting the strategy (for authorization)
 */
public record GetStrategyQuery(
    StrategyId strategyId,
    UserId requestingUserId
) {
    /** Backward-compatible constructor for system-level access. */
    public GetStrategyQuery(StrategyId strategyId) {
        this(strategyId, null);
    }
}
