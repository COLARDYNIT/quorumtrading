package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Query for calculating strategy performance metrics.
 *
 * @param strategyId       The strategy to calculate performance for
 * @param requestingUserId The user requesting the data (null for system/internal calls)
 */
public record CalculateStrategyPerformanceQuery(StrategyId strategyId, UserId requestingUserId) {

    /**
     * Backward-compatible constructor without userId.
     */
    public CalculateStrategyPerformanceQuery(StrategyId strategyId) {
        this(strategyId, null);
    }

    public CalculateStrategyPerformanceQuery {
        if (strategyId == null) {
            throw new IllegalArgumentException("Strategy ID cannot be null");
        }
    }
}
