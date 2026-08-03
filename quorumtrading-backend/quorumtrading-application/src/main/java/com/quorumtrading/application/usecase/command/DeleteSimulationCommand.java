package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for deleting a simulation.
 *
 * @param simulationId ID of the simulation to delete
 * @param requestingUserId ID of the user requesting deletion (for authorization)
 */
public record DeleteSimulationCommand(
    SimulationId simulationId,
    UserId requestingUserId
) {
}
