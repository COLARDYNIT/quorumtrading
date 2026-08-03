package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Toggle per-simulation BUY/SELL decision notifications. Only the owner of
 * {@code simulationId} is authorised to flip this; the service rejects mismatched
 * users with a {@link SecurityException}.
 *
 * @param simulationId    the simulation to update
 * @param userId          the caller — must match the simulation's owner
 * @param notifyDecisions new value; HOLDs never fire regardless
 */
public record UpdateSimulationNotificationPreferenceCommand(
        SimulationId simulationId,
        UserId userId,
        boolean notifyDecisions) {

    public UpdateSimulationNotificationPreferenceCommand {
        if (simulationId == null) {
            throw new IllegalArgumentException("simulationId is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
    }
}
