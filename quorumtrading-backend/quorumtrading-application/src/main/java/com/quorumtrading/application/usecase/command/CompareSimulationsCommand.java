package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.UUID;

/**
 * Command for grouping existing simulations into a benchmark for comparison.
 *
 * @param simulationIds list of simulation UUIDs to group (minimum 1)
 * @param name optional display name for the benchmark (nullable, auto-generated if null)
 * @param userId the user requesting the comparison
 */
public record CompareSimulationsCommand(
    List<UUID> simulationIds,
    String name,
    UserId userId
) {
    public CompareSimulationsCommand {
        if (simulationIds == null || simulationIds.isEmpty()) {
            throw new IllegalArgumentException("At least 1 simulation ID is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("UserId is required");
        }
    }
}
