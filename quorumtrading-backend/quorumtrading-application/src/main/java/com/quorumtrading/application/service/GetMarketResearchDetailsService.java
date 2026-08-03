package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.usecase.GetMarketResearchDetailsUseCase;
import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service implementation for retrieving market research details.
 * Framework-agnostic, uses constructor injection.
 */
public class GetMarketResearchDetailsService implements GetMarketResearchDetailsUseCase {

    private final MarketResearchRepository marketResearchRepository;
    private final ResearchExecutionRepository executionRepository;
    private final AuthorizationService authorizationService;

    public GetMarketResearchDetailsService(
        MarketResearchRepository marketResearchRepository,
        ResearchExecutionRepository executionRepository,
        AuthorizationService authorizationService
    ) {
        this.marketResearchRepository = marketResearchRepository;
        this.executionRepository = executionRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public MarketResearchDetails execute(UUID marketResearchId, UserId requestingUserId) {
        Objects.requireNonNull(marketResearchId, "Market research ID cannot be null");

        // Find research by ID
        MarketResearch research = marketResearchRepository.findById(marketResearchId)
            .orElseThrow(() -> new EntityNotFoundException("Market research not found: " + marketResearchId));

        // Authorization check
        authorizationService.requireView(requestingUserId, research);

        // Find executions
        List<ResearchExecution> executions = executionRepository
            .findByMarketResearchId(marketResearchId);

        // Find latest execution
        Optional<ResearchExecution> latestExecution = executionRepository
            .findLatestByMarketResearchId(marketResearchId);

        // Build details DTO
        return new MarketResearchDetails(research, executions, latestExecution);
    }
}
