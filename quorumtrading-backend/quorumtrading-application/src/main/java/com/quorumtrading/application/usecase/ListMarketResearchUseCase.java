package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.dto.MarketResearchSummary;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

/**
 * Use case for listing all market research configurations with their strategy counts.
 */
public interface ListMarketResearchUseCase {

    /**
     * Execute the list market research use case.
     *
     * @return list of market research summaries including strategy counts
     */
    List<MarketResearchSummary> execute(UserId requestingUserId);
}
