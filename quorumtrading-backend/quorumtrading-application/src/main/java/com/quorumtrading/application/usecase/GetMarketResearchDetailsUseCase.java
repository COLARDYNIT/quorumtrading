package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import com.quorumtrading.model.valueobject.UserId;

import java.util.UUID;

/**
 * Use case for retrieving comprehensive market research details.
 * <p>
 * This use case orchestrates:
 * <ul>
 *   <li>Finding the market research by ID</li>
 *   <li>Retrieving all executions for this research</li>
 *   <li>Finding the latest execution</li>
 *   <li>Building a comprehensive details DTO</li>
 * </ul>
 * </p>
 */
public interface GetMarketResearchDetailsUseCase {

    /**
     * Execute the use case to retrieve market research details.
     *
     * @param marketResearchId The UUID of the market research
     * @return Comprehensive details including research, executions, and statistics
     * @throws com.quorumtrading.application.exception.EntityNotFoundException if market research not found
     * @throws NullPointerException if marketResearchId is null
     */
    MarketResearchDetails execute(UUID marketResearchId, UserId requestingUserId);
}
