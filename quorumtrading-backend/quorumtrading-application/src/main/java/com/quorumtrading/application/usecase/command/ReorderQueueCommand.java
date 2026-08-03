package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

/**
 * Command to reorder the simulation queue.
 * Updates the execution order of pending simulations.
 *
 * @param orderedSimulationIds List of simulation IDs in desired execution order (first = next to run)
 * @param requestingUserId     The user requesting the reorder (null for system/internal calls)
 */
public record ReorderQueueCommand(List<SimulationId> orderedSimulationIds, UserId requestingUserId) {

    /**
     * Backward-compatible constructor without userId.
     */
    public ReorderQueueCommand(List<SimulationId> orderedSimulationIds) {
        this(orderedSimulationIds, null);
    }

    public ReorderQueueCommand {
        if (orderedSimulationIds == null || orderedSimulationIds.isEmpty()) {
            throw new IllegalArgumentException("Ordered simulation IDs list cannot be null or empty");
        }
    }
}
