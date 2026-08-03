package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.UserId;

import java.util.UUID;

/**
 * Use case for pausing a market research configuration.
 * <p>
 * This use case orchestrates:
 * <ul>
 *   <li>Finding the market research by ID</li>
 *   <li>Pausing the research (status change to PAUSED)</li>
 *   <li>Persisting the updated state</li>
 * </ul>
 * </p>
 */
public interface PauseMarketResearchUseCase {

    /**
     * Execute the use case to pause a market research.
     *
     * @param marketResearchId The UUID of the market research to pause
     * @throws com.quorumtrading.application.exception.EntityNotFoundException if market research not found
     * @throws NullPointerException if marketResearchId is null
     */
    void execute(UUID marketResearchId, UserId requestingUserId);
}
