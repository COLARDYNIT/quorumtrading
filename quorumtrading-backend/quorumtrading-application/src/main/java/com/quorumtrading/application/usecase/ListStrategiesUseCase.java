package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.ListStrategiesQuery;
import com.quorumtrading.model.entity.Strategy;

import java.util.List;

/**
 * Use case for listing strategies with optional filtering.
 */
public interface ListStrategiesUseCase {

    /**
     * Execute the list strategies use case.
     *
     * @param query the query containing filter criteria
     * @return list of strategies matching the criteria
     */
    List<Strategy> execute(ListStrategiesQuery query);
}
