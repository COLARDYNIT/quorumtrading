package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.LLMDecisionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.QueryLLMDecisionsUseCase;
import com.quorumtrading.application.usecase.query.QueryLLMDecisionsQuery;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.SimulationId;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service implementation for querying LLM decisions.
 * Framework-agnostic, uses constructor injection.
 *
 * <p>LLM decisions carry no owner of their own; ownership resolves through the parent
 * simulation, the same way {@link QueryConsensusDecisionsService} does it. This query used to
 * take no user at all, so the raw agent prompts and reasoning for any simulation UUID were
 * readable by anyone.</p>
 */
public class QueryLLMDecisionsService implements QueryLLMDecisionsUseCase {

    private final LLMDecisionRepository llmDecisionRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public QueryLLMDecisionsService(LLMDecisionRepository llmDecisionRepository,
                                    SimulationRepository simulationRepository,
                                    StrategyRepository strategyRepository,
                                    AuthorizationService authorizationService) {
        this.llmDecisionRepository = llmDecisionRepository;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<LLMDecision> execute(QueryLLMDecisionsQuery query) {
        SimulationId simulationId = new SimulationId(query.simulationId());
        Simulation simulation = simulationRepository.findById(simulationId)
            .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + query.simulationId()));
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new NoSuchElementException("Strategy not found"));
        authorizationService.requireView(query.requestingUserId(), simulation, strategy);

        return llmDecisionRepository.findBySimulationId(
            query.simulationId(),
            query.from(),
            query.to(),
            query.agentId(),
            query.limit(),
            query.beforeLLMDecisionId(),
            query.afterLLMDecisionId()
        );
    }
}
