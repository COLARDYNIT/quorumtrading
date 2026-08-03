package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

public record ForceEndSimulationCommand(
    SimulationId simulationId,
    boolean liquidatePositions,
    UserId requestingUserId
) {
    /** Backward-compatible constructor for internal/system callers. */
    public ForceEndSimulationCommand(SimulationId simulationId, boolean liquidatePositions) {
        this(simulationId, liquidatePositions, null);
    }
}
