package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.UserId;

import java.util.UUID;

/**
 * Use case for resuming a paused market research configuration.
 * <p>
 * This use case orchestrates:
 * <ul>
 *   <li>Finding the market research by ID</li>
 *   <li>Resuming the research (status change to ACTIVE)</li>
 *   <li>Persisting the updated state</li>
 * </ul>
 * </p>
 */
public interface ResumeMarketResearchUseCase {

    /**
     * Execute the use case to resume a market research.
     *
     * @param marketResearchId The UUID of the market research to resume
     * @throws com.quorumtrading.application.exception.EntityNotFoundException if market research not found
     * @throws NullPointerException if marketResearchId is null
     */
    void execute(UUID marketResearchId, UserId requestingUserId);
}
