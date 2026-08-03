package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Objects;

/**
 * Command for composing a benchmark from existing simulations.
 *
 * @param simulationIds IDs of the simulations to include (at least one required)
 * @param benchmarkId optional benchmark ID (null = create new, present = add to existing)
 * @param name optional display name (null for auto-generated, ignored when adding to existing)
 * @param userId ID of the requesting user (for authorization)
 */
public record ComposeBenchmarkCommand(
    List<SimulationId> simulationIds,
    BenchmarkId benchmarkId,
    String name,
    UserId userId
) {
    public ComposeBenchmarkCommand {
        Objects.requireNonNull(simulationIds, "simulationIds must not be null");
        if (simulationIds.isEmpty()) {
            throw new IllegalArgumentException("At least one simulation ID is required");
        }
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
