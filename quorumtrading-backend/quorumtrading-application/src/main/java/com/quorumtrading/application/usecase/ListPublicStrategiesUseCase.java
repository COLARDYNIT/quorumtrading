package com.quorumtrading.application.usecase;

import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import java.util.List;

public interface ListPublicStrategiesUseCase {
    List<Strategy> execute();

    /**
     * Finds a single public strategy by ID.
     * Only returns the strategy if it is both publicly visible and active.
     *
     * @param id the strategy ID
     * @return the strategy
     * @throws java.util.NoSuchElementException if not found, not public, or not active
     */
    Strategy findPublicById(StrategyId id);
}
