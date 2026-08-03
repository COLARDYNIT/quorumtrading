package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.SimulationId;

/**
 * Result of processing consensus for a single simulation.
 *
 * @param simulationId The ID of the simulation processed
 * @param success      Whether the consensus was successfully triggered
 * @param skipped      Whether the simulation was skipped (not active, no data, etc.)
 * @param errorMessage Error message if failed
 */
public record SimulationConsensusResult(
        SimulationId simulationId,
        boolean success,
        boolean skipped,
        String errorMessage
) {
    public static SimulationConsensusResult success(SimulationId simulationId) {
        return new SimulationConsensusResult(simulationId, true, false, null);
    }

    public static SimulationConsensusResult skipped(SimulationId simulationId, String reason) {
        return new SimulationConsensusResult(simulationId, false, true, reason);
    }

    public static SimulationConsensusResult failed(SimulationId simulationId, String error) {
        return new SimulationConsensusResult(simulationId, false, false, error);
    }
}
