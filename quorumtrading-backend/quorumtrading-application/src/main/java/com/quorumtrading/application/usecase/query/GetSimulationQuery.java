package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Query for retrieving a single simulation by ID.
 *
 * @param simulationId       ID of the simulation to retrieve
 * @param requestingUserId   the user requesting the simulation (for authorization)
 */
public record GetSimulationQuery(
    SimulationId simulationId,
    UserId requestingUserId
) {
    /** Backward-compatible constructor for system-level access. */
    public GetSimulationQuery(SimulationId simulationId) {
        this(simulationId, null);
    }
}
