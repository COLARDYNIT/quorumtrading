package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.UserId;

import java.util.UUID;

/**
 * Use case for deleting a market research configuration and all its execution history.
 */
public interface DeleteMarketResearchUseCase {

    /**
     * Execute the delete market research use case.
     *
     * @param researchId the ID of the market research to delete
     * @param requestingUserId the user requesting deletion (for authorization)
     * @throws java.util.NoSuchElementException if market research not found
     */
    void execute(UUID researchId, UserId requestingUserId);
}
