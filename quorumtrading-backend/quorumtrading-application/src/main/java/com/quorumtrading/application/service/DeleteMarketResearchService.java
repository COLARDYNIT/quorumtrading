package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.DeleteMarketResearchUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.UserId;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Service implementation for deleting market research configurations.
 * Framework-agnostic, uses constructor injection.
 */
public class DeleteMarketResearchService implements DeleteMarketResearchUseCase {

    private final MarketResearchRepository marketResearchRepository;
    private final AuthorizationService authorizationService;

    public DeleteMarketResearchService(MarketResearchRepository marketResearchRepository, AuthorizationService authorizationService) {
        this.marketResearchRepository = marketResearchRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public void execute(UUID researchId, UserId requestingUserId) {
        MarketResearch research = marketResearchRepository.findById(researchId)
            .orElseThrow(() -> new NoSuchElementException("Market research not found: " + researchId));

        authorizationService.requireDelete(requestingUserId, research);

        marketResearchRepository.delete(researchId);
    }
}
