package com.quorumtrading.application.usecase.dto;

import com.quorumtrading.model.entity.MarketResearch;

/**
 * DTO for market research summary including strategy count.
 *
 * @param research the market research entity
 * @param strategyCount the number of strategies created by this research
 */
public record MarketResearchSummary(
    MarketResearch research,
    int strategyCount
) {
}
