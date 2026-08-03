package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Query for retrieving decimated chart data for a simulation.
 * <p>
 * This query is optimized for rendering large datasets in charts by reducing
 * the number of data points to a manageable amount while preserving the visual
 * shape of the data.
 * </p>
 *
 * @param simulationId     The simulation to get chart data for
 * @param targetPoints     Target number of data points to return (default: 500)
 *                         The actual number may be slightly different depending on the algorithm
 * @param requestingUserId The user requesting the data (null for system/internal calls)
 */
public record GetSimulationChartDataQuery(
    SimulationId simulationId,
    int targetPoints,
    UserId requestingUserId
) {
    /**
     * Default constructor with 500 target points (optimal for chart rendering).
     */
    public GetSimulationChartDataQuery(SimulationId simulationId) {
        this(simulationId, 500, null);
    }

    /**
     * Backward-compatible constructor without userId.
     */
    public GetSimulationChartDataQuery(SimulationId simulationId, int targetPoints) {
        this(simulationId, targetPoints, null);
    }

    /**
     * Validation to ensure target points is reasonable.
     */
    public GetSimulationChartDataQuery {
        if (targetPoints < 10) {
            throw new IllegalArgumentException("Target points must be at least 10");
        }
        if (targetPoints > 10000) {
            throw new IllegalArgumentException("Target points cannot exceed 10000");
        }
    }
}
