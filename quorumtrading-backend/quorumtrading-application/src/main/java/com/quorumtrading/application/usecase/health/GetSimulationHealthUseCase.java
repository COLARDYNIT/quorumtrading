package com.quorumtrading.application.usecase.health;

import com.quorumtrading.application.usecase.query.GetSimulationQuery;

/**
 * Compute the live execution health of a simulation — one read model for every driving
 * adapter, so MCP, REST and the UI report the same diagnosis of a stalling run.
 */
public interface GetSimulationHealthUseCase {

    /**
     * @param query simulation id + requesting user (ownership is enforced here, not in adapters)
     * @return the health read model, never null
     * @throws IllegalArgumentException when the simulation does not exist
     */
    SimulationExecutionHealth execute(GetSimulationQuery query);
}
