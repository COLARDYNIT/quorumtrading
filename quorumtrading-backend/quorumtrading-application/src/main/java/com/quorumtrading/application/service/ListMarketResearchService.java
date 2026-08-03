package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.usecase.ListMarketResearchUseCase;
import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

/**
 * Service implementation for listing market research configurations.
 * Framework-agnostic, uses constructor injection.
 */
public class ListMarketResearchService implements ListMarketResearchUseCase {

    private final MarketResearchRepository marketResearchRepository;
    private final ResearchExecutionRepository researchExecutionRepository;

    public ListMarketResearchService(
            MarketResearchRepository marketResearchRepository,
            ResearchExecutionRepository researchExecutionRepository) {
        this.marketResearchRepository = marketResearchRepository;
        this.researchExecutionRepository = researchExecutionRepository;
    }

    @Override
    public List<MarketResearchSummary> execute(UserId requestingUserId) {
        List<MarketResearch> researchList;
        // See ListStrategiesService: a null caller used to mean "return everything".
        if (requestingUserId == null) {
            throw new AccessDeniedException(null, "MarketResearch", "list");
        }
        if (requestingUserId.isSystem()) {
            researchList = marketResearchRepository.findAll();
        } else {
            researchList = marketResearchRepository.findByOwnerId(requestingUserId);
        }

        return researchList.stream()
            .map(research -> {
                int strategyCount = researchExecutionRepository.countStrategiesCreatedByResearch(research.getId());
                return new MarketResearchSummary(research, strategyCount);
            })
            .toList();
    }
}
