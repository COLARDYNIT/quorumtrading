package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.RemoveSimulationFromBenchmarkUseCase;
import com.quorumtrading.application.usecase.command.RemoveSimulationFromBenchmarkCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;

import java.util.NoSuchElementException;

/**
 * Service implementation for removing a simulation from its benchmark.
 */
public class RemoveSimulationFromBenchmarkService implements RemoveSimulationFromBenchmarkUseCase {

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public RemoveSimulationFromBenchmarkService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public void execute(RemoveSimulationFromBenchmarkCommand command) {
        Simulation simulation = simulationRepository.findById(command.simulationId())
            .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + command.simulationId().value()));

        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new NoSuchElementException("Strategy not found: " + simulation.getStrategyId().value()));

        authorizationService.requireEdit(command.userId(), simulation, strategy);

        simulation.unassignFromBenchmark(); // domain validation
        simulationRepository.updateBenchmarkId(command.simulationId(), null);
    }
}
