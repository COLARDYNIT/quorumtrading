package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for starting a simulation execution.
 *
 * @param simulationId ID of the simulation to start
 * @param requestingUserId the user requesting the start; {@link UserId#SYSTEM} for
 *                         platform-triggered starts (schedulers, events). Never null —
 *                         a null user is denied by the authorization layer.
 */
public record StartSimulationCommand(
    SimulationId simulationId,
    UserId requestingUserId
) {
    /**
     * Platform-triggered start with no requesting user (schedulers, event handlers).
     * Callers acting on behalf of a user must pass that user instead.
     */
    public static StartSimulationCommand system(SimulationId simulationId) {
        return new StartSimulationCommand(simulationId, UserId.SYSTEM);
    }
}
