package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.PauseSimulationUseCase;
import com.quorumtrading.application.usecase.command.PauseSimulationCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service implementation for pausing simulation execution.
 * Loads the simulation, sets its paused flag, and cancels any open broker orders.
 */
public class PauseSimulationService implements PauseSimulationUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(PauseSimulationService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;
    private final BrokerPort brokerPort;

    public PauseSimulationService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService,
            BrokerPort brokerPort) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
        this.brokerPort = brokerPort;
    }

    @Override
    public void execute(PauseSimulationCommand command) {
        // Find simulation
        Simulation simulation = simulationRepository.findById(command.simulationId())
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Simulation not found with ID: " + command.simulationId()));

        // Authorization check (skip for system-triggered pauses)
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new java.util.NoSuchElementException(
                    "Strategy not found with ID: " + simulation.getStrategyId()));
        authorizationService.requireEdit(command.requestingUserId(), simulation, strategy);

        // Validate state transition (domain rule)
        simulation.pause();

        // Cancel any open broker orders to prevent fills after pause
        try {
            brokerPort.cancelAllOpenOrders(command.simulationId());
        } catch (Exception e) {
            LOG.warn("Failed to cancel open orders on pause for simulation {}: {}",
                    command.simulationId(), e.getMessage());
            // Don't block the pause if order cancellation fails
        }

        // Direct SQL update to avoid stale overwrites from in-memory executor entities
        simulationRepository.updatePauseState(command.simulationId(), true, simulation.isPausedForExternalReason(), simulation.getExternalPauseReason());
    }
}
