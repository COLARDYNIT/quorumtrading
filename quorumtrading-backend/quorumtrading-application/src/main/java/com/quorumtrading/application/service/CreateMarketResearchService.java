package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.CreateMarketResearchUseCase;
import com.quorumtrading.application.usecase.ExecuteMarketResearchUseCase;
import com.quorumtrading.application.usecase.command.CreateMarketResearchCommand;
import com.quorumtrading.model.entity.MarketResearch;
import org.jboss.logging.Logger;

import java.util.Objects;

/**
 * Service implementation for creating market research configurations.
 * Framework-agnostic, uses constructor injection.
 * <p>
 * After creation, immediately triggers execution since the research is scheduled
 * for "now". This avoids waiting for the next scheduler run (which could be up to 1 hour).
 * </p>
 */
public class CreateMarketResearchService implements CreateMarketResearchUseCase {

    private static final Logger LOG = Logger.getLogger(CreateMarketResearchService.class);

    private final MarketResearchRepository marketResearchRepository;
    private final ExecuteMarketResearchUseCase executeMarketResearchUseCase;

    public CreateMarketResearchService(
            MarketResearchRepository marketResearchRepository,
            ExecuteMarketResearchUseCase executeMarketResearchUseCase) {
        this.marketResearchRepository = marketResearchRepository;
        this.executeMarketResearchUseCase = executeMarketResearchUseCase;
    }

    @Override
    public MarketResearch execute(CreateMarketResearchCommand command) {
        validateCommand(command);

        // Create domain entity using domain constructor
        MarketResearch research = new MarketResearch(
            command.name(),
            command.markdownInstructions(),
            command.targetAssets(),
            command.interval(),
            command.enableAutoBacktest(),
            command.backtestConfiguration(),
            command.ownerId()
        );

        // Persist the research
        MarketResearch savedResearch = marketResearchRepository.save(research);

        // Trigger immediate execution since nextScheduledRun is set to "now"
        // This avoids waiting for the next scheduler cycle (up to 1 hour)
        try {
            LOG.infof("Triggering immediate execution for newly created market research: %s", savedResearch.getId());
            executeMarketResearchUseCase.execute(savedResearch.getId(), command.ownerId());
        } catch (Exception e) {
            // Log but don't fail the creation - scheduler will pick it up eventually
            LOG.warnf(e, "Failed to trigger immediate execution for market research %s, scheduler will pick it up", savedResearch.getId());
        }

        return savedResearch;
    }

    private void validateCommand(CreateMarketResearchCommand command) {
        Objects.requireNonNull(command, "Command cannot be null");
    }
}
