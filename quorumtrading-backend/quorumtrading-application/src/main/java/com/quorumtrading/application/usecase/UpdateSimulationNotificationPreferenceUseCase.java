package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.UpdateSimulationNotificationPreferenceCommand;

/**
 * Use case for toggling per-simulation BUY/SELL decision notifications.
 */
public interface UpdateSimulationNotificationPreferenceUseCase {

    /**
     * Flip the {@code notify_decisions} flag on the simulation.
     *
     * @param command the simulation, requesting user, and target value
     * @throws java.util.NoSuchElementException if simulation not found
     * @throws SecurityException                if the requesting user does not own the simulation
     */
    void execute(UpdateSimulationNotificationPreferenceCommand command);
}
