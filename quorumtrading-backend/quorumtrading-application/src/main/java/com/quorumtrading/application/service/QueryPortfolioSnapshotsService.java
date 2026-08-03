package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.QueryPortfolioSnapshotsUseCase;
import com.quorumtrading.application.usecase.query.QueryPortfolioSnapshotsQuery;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.SimulationId;

import java.util.List;

/**
 * Service implementation for querying portfolio snapshots.
 * Framework-agnostic, uses constructor injection.
 */
public class QueryPortfolioSnapshotsService implements QueryPortfolioSnapshotsUseCase {

    private final PortfolioRepository portfolioRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public QueryPortfolioSnapshotsService(PortfolioRepository portfolioRepository,
                                          SimulationRepository simulationRepository,
                                          StrategyRepository strategyRepository,
                                          AuthorizationService authorizationService) {
        this.portfolioRepository = portfolioRepository;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<PortfolioSnapshot> execute(QueryPortfolioSnapshotsQuery query) {
        // Authorization check
        SimulationId simulationId = new SimulationId(query.simulationId());
        Simulation simulation = simulationRepository.findById(simulationId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Simulation not found: " + query.simulationId()));
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new java.util.NoSuchElementException("Strategy not found"));
        authorizationService.requireView(query.requestingUserId(), simulation, strategy);

        return portfolioRepository.findBySimulationId(
            new SimulationId(query.simulationId()),
            query.from(),
            query.to(),
            query.limit(),
            query.resolution(),
            query.excludePositions()
        );
    }
}
