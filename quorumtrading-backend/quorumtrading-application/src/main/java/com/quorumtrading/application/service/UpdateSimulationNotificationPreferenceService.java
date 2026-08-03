package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.UpdateSimulationNotificationPreferenceUseCase;
import com.quorumtrading.application.usecase.command.UpdateSimulationNotificationPreferenceCommand;
import com.quorumtrading.model.entity.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;

/**
 * Applies the {@code notify_decisions} toggle and persists. Ownership is verified
 * here rather than at the REST layer because other adapters (MCP) need the same
 * guard.
 */
public class UpdateSimulationNotificationPreferenceService
        implements UpdateSimulationNotificationPreferenceUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(
            UpdateSimulationNotificationPreferenceService.class);

    private final SimulationRepository simulationRepository;

    public UpdateSimulationNotificationPreferenceService(SimulationRepository simulationRepository) {
        this.simulationRepository = simulationRepository;
    }

    @Override
    public void execute(UpdateSimulationNotificationPreferenceCommand command) {
        Simulation simulation = simulationRepository.findById(command.simulationId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Simulation not found: " + command.simulationId().value()));

        if (simulation.getOwnerUserId() == null
                || !simulation.getOwnerUserId().equals(command.userId())) {
            throw new SecurityException(
                    "Simulation does not belong to user: " + command.userId().value());
        }

        if (command.notifyDecisions()) {
            simulation.enableDecisionNotifications();
        } else {
            simulation.disableDecisionNotifications();
        }
        simulationRepository.save(simulation);

        LOG.info("Set notifyDecisions={} on simulation {} (user {})",
                command.notifyDecisions(),
                command.simulationId().value(),
                command.userId().value());
    }
}
