package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.model.entity.Simulation;

/**
 * Use case for retrieving a single simulation by ID.
 */
public interface GetSimulationUseCase {

    /**
     * Execute the get simulation use case.
     *
     * @param query the query containing the simulation ID
     * @return the simulation if found
     * @throws IllegalArgumentException if simulation not found
     */
    Simulation execute(GetSimulationQuery query);
}
