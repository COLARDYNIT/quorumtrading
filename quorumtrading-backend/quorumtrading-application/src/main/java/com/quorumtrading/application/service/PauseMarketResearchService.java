package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.PauseMarketResearchUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Objects;
import java.util.UUID;

/**
 * Service implementation for pausing market research configurations.
 * Framework-agnostic, uses constructor injection.
 */
public class PauseMarketResearchService implements PauseMarketResearchUseCase {

    private final MarketResearchRepository marketResearchRepository;
    private final AuthorizationService authorizationService;

    public PauseMarketResearchService(MarketResearchRepository marketResearchRepository, AuthorizationService authorizationService) {
        this.marketResearchRepository = marketResearchRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public void execute(UUID marketResearchId, UserId requestingUserId) {
        Objects.requireNonNull(marketResearchId, "Market research ID cannot be null");

        // Find by ID
        MarketResearch research = marketResearchRepository.findById(marketResearchId)
            .orElseThrow(() -> new EntityNotFoundException("Market research not found: " + marketResearchId));

        // Authorization check
        authorizationService.requireEdit(requestingUserId, research);

        // Call domain method
        research.pause();

        // Persist changes
        marketResearchRepository.save(research);
    }
}
