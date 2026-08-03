package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Objects;

/**
 * Command for adding an existing simulation to an existing benchmark.
 *
 * @param simulationId ID of the simulation to add
 * @param benchmarkId ID of the benchmark to add the simulation to
 * @param userId ID of the requesting user (for authorization)
 */
public record AddSimulationToBenchmarkCommand(
    SimulationId simulationId,
    BenchmarkId benchmarkId,
    UserId userId
) {
    public AddSimulationToBenchmarkCommand {
        Objects.requireNonNull(simulationId, "simulationId must not be null");
        Objects.requireNonNull(benchmarkId, "benchmarkId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
