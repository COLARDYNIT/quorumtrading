package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.usecase.DeleteResearchExecutionUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.valueobject.UserId;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Service implementation for deleting research executions.
 * Framework-agnostic, uses constructor injection.
 */
public class DeleteResearchExecutionService implements DeleteResearchExecutionUseCase {

    private final ResearchExecutionRepository researchExecutionRepository;
    private final MarketResearchRepository marketResearchRepository;
    private final AuthorizationService authorizationService;

    public DeleteResearchExecutionService(ResearchExecutionRepository researchExecutionRepository,
                                          MarketResearchRepository marketResearchRepository,
                                          AuthorizationService authorizationService) {
        this.researchExecutionRepository = researchExecutionRepository;
        this.marketResearchRepository = marketResearchRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public void execute(UUID executionId, UUID expectedResearchId, UserId requestingUserId) {
        ResearchExecution execution = researchExecutionRepository.findById(executionId)
            .orElseThrow(() -> new NoSuchElementException("Execution not found: " + executionId));

        // The caller reached this execution through a parent research in the URL; honour it.
        // Reported as "not found" rather than a mismatch so a wrong parent cannot be used to probe
        // which execution ids exist.
        if (expectedResearchId != null && !expectedResearchId.equals(execution.getMarketResearchId())) {
            throw new NoSuchElementException(
                "Execution " + executionId + " does not belong to market research " + expectedResearchId);
        }

        // Authorization check
        MarketResearch research = marketResearchRepository.findById(execution.getMarketResearchId())
            .orElseThrow(() -> new NoSuchElementException("Market research not found: " + execution.getMarketResearchId()));
        authorizationService.requireDelete(requestingUserId, research);

        researchExecutionRepository.delete(executionId);
    }
}
