package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.QueryConsensusDecisionsUseCase;
import com.quorumtrading.application.usecase.query.QueryConsensusDecisionsQuery;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.SimulationId;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service implementation for querying consensus decisions.
 * Framework-agnostic, uses constructor injection.
 */
public class QueryConsensusDecisionsService implements QueryConsensusDecisionsUseCase {

    private final ConsensusDecisionRepository consensusDecisionRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public QueryConsensusDecisionsService(
            ConsensusDecisionRepository consensusDecisionRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService) {
        this.consensusDecisionRepository = consensusDecisionRepository;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<ConsensusDecision> execute(QueryConsensusDecisionsQuery query) {
        // Authorization check
        SimulationId simulationId = new SimulationId(query.simulationId());
        Simulation simulation = simulationRepository.findById(simulationId)
            .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + query.simulationId()));
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new NoSuchElementException("Strategy not found"));
        authorizationService.requireView(query.requestingUserId(), simulation, strategy);

        return consensusDecisionRepository.findBySimulationId(
            query.simulationId(),
            query.from(),
            query.to(),
            query.executed(),
            query.executionOutcome(),
            query.actions(),
            query.limit()
        );
    }
}
