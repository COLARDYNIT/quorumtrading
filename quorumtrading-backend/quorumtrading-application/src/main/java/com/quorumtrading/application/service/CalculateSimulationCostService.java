package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository.AggregatedLLMCost;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CalculateSimulationCostUseCase;
import com.quorumtrading.application.usecase.query.CalculateSimulationCostQuery;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.service.PlatformMarkup;
import com.quorumtrading.model.valueobject.SimulationCost;
import com.quorumtrading.model.valueobject.SimulationId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.NoSuchElementException;

/**
 * Service that calculates the aggregated AI cost for a simulation.
 * Returns billed cost (raw cost * markup rate) — the user-facing price.
 * Framework-agnostic, uses constructor injection.
 */
public class CalculateSimulationCostService implements CalculateSimulationCostUseCase {

    private final ConsensusDecisionRepository consensusDecisionRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public CalculateSimulationCostService(
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
    public SimulationCost execute(CalculateSimulationCostQuery query) {
        SimulationId simulationId = new SimulationId(query.simulationId());

        Simulation simulation = simulationRepository.findById(simulationId)
            .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + query.simulationId()));

        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new NoSuchElementException("Strategy not found"));
        authorizationService.requireView(query.requestingUserId(), simulation, strategy);

        AggregatedLLMCost aggregated = consensusDecisionRepository.sumCostBySimulationId(query.simulationId());

        // Add archived cost from deleted decisions (restart-from-decision feature)
        BigDecimal totalRawCost = aggregated.rawCost().add(simulation.getArchivedLLMCostUsd());
        int totalPrompt = aggregated.promptTokens() + simulation.getArchivedLLMPromptTokens();
        int totalCompletion = aggregated.completionTokens() + simulation.getArchivedLLMCompletionTokens();
        int totalTokens = aggregated.totalTokens() + simulation.getArchivedLLMTotalTokens();
        int totalDecisions = aggregated.decisionCount() + simulation.getArchivedLLMDecisionCount();

        BigDecimal billedCost = totalRawCost
            .multiply(PlatformMarkup.rate())
            .setScale(8, RoundingMode.HALF_UP);

        boolean partial = !simulation.isTerminal();
        int progress = simulation.getProgress();

        return new SimulationCost(
            billedCost,
            totalPrompt,
            totalCompletion,
            totalTokens,
            totalDecisions,
            partial,
            progress
        );
    }
}
