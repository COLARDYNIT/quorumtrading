package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Objects;

/**
 * Command for removing a simulation from its benchmark.
 *
 * @param simulationId ID of the simulation to remove from its benchmark
 * @param userId ID of the requesting user (for authorization)
 */
public record RemoveSimulationFromBenchmarkCommand(
    SimulationId simulationId,
    UserId userId
) {
    public RemoveSimulationFromBenchmarkCommand {
        Objects.requireNonNull(simulationId, "simulationId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
