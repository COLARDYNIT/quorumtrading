package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.GetStrategyQuery;
import com.quorumtrading.model.entity.Strategy;

/**
 * Use case for retrieving a single strategy by ID.
 */
public interface GetStrategyUseCase {

    /**
     * Execute the get strategy use case.
     *
     * @param query the query containing the strategy ID
     * @return the strategy if found
     * @throws IllegalArgumentException if strategy not found
     */
    Strategy execute(GetStrategyQuery query);
}
