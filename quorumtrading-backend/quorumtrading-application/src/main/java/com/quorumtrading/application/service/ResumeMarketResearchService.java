package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.ResumeMarketResearchUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Objects;
import java.util.UUID;

/**
 * Service implementation for resuming market research configurations.
 * Framework-agnostic, uses constructor injection.
 */
public class ResumeMarketResearchService implements ResumeMarketResearchUseCase {

    private final MarketResearchRepository marketResearchRepository;
    private final AuthorizationService authorizationService;

    public ResumeMarketResearchService(MarketResearchRepository marketResearchRepository, AuthorizationService authorizationService) {
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
        research.resume();

        // Persist changes
        marketResearchRepository.save(research);
    }
}
