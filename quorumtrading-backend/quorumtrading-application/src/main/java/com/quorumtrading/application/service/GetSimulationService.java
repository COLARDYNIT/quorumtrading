package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.GetSimulationUseCase;
import com.quorumtrading.application.usecase.query.GetSimulationQuery;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;

/**
 * Service implementation for retrieving a single simulation.
 * Framework-agnostic, uses constructor injection.
 */
public class GetSimulationService implements GetSimulationUseCase {

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public GetSimulationService(SimulationRepository simulationRepository, StrategyRepository strategyRepository, AuthorizationService authorizationService) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public Simulation execute(GetSimulationQuery query) {
        Simulation simulation = simulationRepository.findById(query.simulationId())
            .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + query.simulationId()));

        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + simulation.getStrategyId()));
        authorizationService.requireView(query.requestingUserId(), simulation, strategy);

        return simulation;
    }
}
