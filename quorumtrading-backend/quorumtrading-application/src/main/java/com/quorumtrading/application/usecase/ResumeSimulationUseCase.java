package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.ResumeSimulationCommand;

/**
 * Use case for resuming a simulation from its last checkpoint.
 *
 * This use case is idempotent:
 * - If simulation is already running, it has no effect
 * - If simulation is complete, it has no effect
 * - If simulation is paused/failed, it resumes from last portfolio snapshot
 * - If simulation has no checkpoints, it starts from beginning
 */
public interface ResumeSimulationUseCase {

    /**
     * Resume or restart a simulation.
     *
     * @param command Contains simulation ID to resume
     */
    void execute(ResumeSimulationCommand command);
}
