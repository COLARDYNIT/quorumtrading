package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.UpdateMarketResearchUseCase;
import com.quorumtrading.application.usecase.command.UpdateMarketResearchCommand;
import com.quorumtrading.model.entity.MarketResearch;

import java.util.Objects;

/**
 * Service implementation for updating market research configurations.
 * Framework-agnostic, uses constructor injection.
 */
public class UpdateMarketResearchService implements UpdateMarketResearchUseCase {

    private final MarketResearchRepository marketResearchRepository;
    private final AuthorizationService authorizationService;

    public UpdateMarketResearchService(MarketResearchRepository marketResearchRepository,
                                       AuthorizationService authorizationService) {
        this.marketResearchRepository = marketResearchRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public MarketResearch execute(UpdateMarketResearchCommand command) {
        validateCommand(command);

        // Find existing research
        MarketResearch research = marketResearchRepository.findById(command.id())
            .orElseThrow(() -> new EntityNotFoundException("Market research not found: " + command.id()));

        // This service had no authorization at all while every sibling operation on
        // MarketResearch checked ownership, so any authenticated user could rewrite another
        // user's research instructions and target tickers — which drive automated strategy
        // generation and auto-backtests.
        authorizationService.requireEdit(command.requestingUserId(), research);

        // Apply updates (only non-null fields)
        if (command.name() != null) {
            research.updateName(command.name());
        }
        if (command.markdownInstructions() != null) {
            research.updateMarkdownInstructions(command.markdownInstructions());
        }
        if (command.targetAssets() != null) {
            research.updateTargetAssets(command.targetAssets());
        }
        if (command.interval() != null) {
            research.updateInterval(command.interval());
        }

        // Persist and return
        return marketResearchRepository.save(research);
    }

    private void validateCommand(UpdateMarketResearchCommand command) {
        Objects.requireNonNull(command, "Command cannot be null");
        if (!command.hasUpdates()) {
            throw new IllegalArgumentException("At least one field must be provided for update");
        }
    }
}
