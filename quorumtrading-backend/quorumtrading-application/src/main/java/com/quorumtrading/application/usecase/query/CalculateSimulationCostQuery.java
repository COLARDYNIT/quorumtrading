package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.UserId;

import java.util.UUID;

/**
 * Query for calculating the AI cost of a simulation.
 *
 * @param simulationId     the simulation to calculate cost for
 * @param requestingUserId the user requesting the data (null for system/internal calls)
 */
public record CalculateSimulationCostQuery(UUID simulationId, UserId requestingUserId) {

    public CalculateSimulationCostQuery {
        if (simulationId == null) {
            throw new IllegalArgumentException("Simulation ID cannot be null");
        }
    }
}
