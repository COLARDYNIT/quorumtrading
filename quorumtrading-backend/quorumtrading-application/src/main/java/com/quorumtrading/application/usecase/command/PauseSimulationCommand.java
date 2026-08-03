package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for pausing a simulation execution.
 *
 * @param simulationId ID of the simulation to pause
 * @param requestingUserId ID of the user requesting the pause (for authorization, nullable for system-triggered pauses)
 */
public record PauseSimulationCommand(
    SimulationId simulationId,
    UserId requestingUserId
) {
    /**
     * Backward-compatible constructor for system-triggered pauses.
     */
    public PauseSimulationCommand(SimulationId simulationId) {
        this(simulationId, null);
    }
}
