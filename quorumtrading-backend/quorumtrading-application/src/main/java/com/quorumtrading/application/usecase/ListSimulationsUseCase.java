package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.ListSimulationsQuery;
import com.quorumtrading.model.entity.Simulation;

import java.util.List;

/**
 * Use case for listing simulations with optional filtering.
 */
public interface ListSimulationsUseCase {

    /**
     * Execute the list strategies use case.
     *
     * @param query the query containing filter criteria
     * @return list of simulations matching the criteria
     */
    List<Simulation> execute(ListSimulationsQuery query);
}
