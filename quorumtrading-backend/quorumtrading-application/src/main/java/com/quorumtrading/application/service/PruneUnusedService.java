package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.PruneUnusedUseCase;
import com.quorumtrading.application.usecase.command.PruneUnusedCommand;
import com.quorumtrading.application.usecase.result.PruneUnusedResult;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Prunes unused strategies (0 simulations) and unused research (0 created strategies).
 */
public class PruneUnusedService implements PruneUnusedUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(PruneUnusedService.class);

    private final StrategyRepository strategyRepository;
    private final SimulationRepository simulationRepository;
    private final MarketResearchRepository marketResearchRepository;
    private final ResearchExecutionRepository researchExecutionRepository;

    public PruneUnusedService(
            StrategyRepository strategyRepository,
            SimulationRepository simulationRepository,
            MarketResearchRepository marketResearchRepository,
            ResearchExecutionRepository researchExecutionRepository) {
        this.strategyRepository = strategyRepository;
        this.simulationRepository = simulationRepository;
        this.marketResearchRepository = marketResearchRepository;
        this.researchExecutionRepository = researchExecutionRepository;
    }

    @Override
    public PruneUnusedResult execute(PruneUnusedCommand command) {
        List<String> strategyNames = command.removeUnusedStrategies()
            ? pruneUnusedStrategies(command)
            : List.of();
        List<String> researchNames = command.removeUnusedResearch()
            ? pruneUnusedResearch(command)
            : List.of();

        LOG.info("{} unused resources for user {}: {} strategies, {} research",
            command.dryRun() ? "Previewed" : "Pruned",
            command.userId().value(), strategyNames.size(), researchNames.size());

        return new PruneUnusedResult(strategyNames.size(), researchNames.size(),
            strategyNames, researchNames, command.dryRun());
    }

    /**
     * @return the names of the unused strategies, deleting them unless this is a dry run. The
     *     selection logic is shared between preview and deletion deliberately: a preview computed
     *     by different code from the deletion is a preview you cannot trust.
     */
    private List<String> pruneUnusedStrategies(PruneUnusedCommand command) {
        List<Strategy> strategies = strategyRepository.findByOwnerId(command.userId());
        List<String> affected = new ArrayList<>();

        for (Strategy strategy : strategies) {
            if (simulationRepository.findByStrategyId(strategy.getId()).isEmpty()) {
                affected.add(strategy.getName());
                if (!command.dryRun()) {
                    strategyRepository.deleteById(strategy.getId());
                    LOG.debug("Pruned unused strategy: {} ({})", strategy.getName(), strategy.getId().value());
                }
            }
        }

        return affected;
    }

    /** @return the names of the unused research records, deleting them unless this is a dry run. */
    private List<String> pruneUnusedResearch(PruneUnusedCommand command) {
        List<MarketResearch> researchList = marketResearchRepository.findByOwnerId(command.userId());
        List<String> affected = new ArrayList<>();

        for (MarketResearch research : researchList) {
            int strategiesCreated = researchExecutionRepository.countStrategiesCreatedByResearch(research.getId());
            if (strategiesCreated == 0) {
                affected.add(research.getName());
                if (!command.dryRun()) {
                    marketResearchRepository.delete(research.getId());
                    LOG.debug("Pruned unused research: {} ({})", research.getName(), research.getId());
                }
            }
        }

        return affected;
    }
}
