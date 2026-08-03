package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command to resume a simulation from its last checkpoint.
 *
 * @param simulationId     the simulation to resume
 * @param requestingUserId the user asking for the resume; null only for system-triggered
 *                         resumes (startup recovery), which bypass the ownership check
 */
public record ResumeSimulationCommand(SimulationId simulationId, UserId requestingUserId) {

    /**
     * System-triggered resume with no requesting user (startup recovery and schedulers).
     * Callers acting on behalf of a user must pass that user instead.
     */
    public static ResumeSimulationCommand system(SimulationId simulationId) {
        return new ResumeSimulationCommand(simulationId, UserId.SYSTEM);
    }
}
