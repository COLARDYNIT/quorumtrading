package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.UserId;

import java.util.UUID;

/**
 * Use case for deleting a specific research execution from history.
 */
public interface DeleteResearchExecutionUseCase {

    /**
     * Execute the delete research execution use case.
     *
     * <p>{@code expectedResearchId} is the parent the caller believes the execution belongs to.
     * The REST route is nested — {@code /market-research/{researchId}/executions/{executionId}} —
     * but the research id used to be accepted and then ignored, so any parent id in the path
     * deleted the execution regardless of whether it belonged there. Ownership was still checked,
     * so this was never cross-tenant, but a URL that names a parent must mean it.</p>
     *
     * @param executionId        the ID of the execution to delete
     * @param expectedResearchId the market research the execution must belong to; null skips the
     *                           check, for callers that reached the execution without a parent path
     * @param requestingUserId   the user requesting the deletion (null for system/internal calls)
     * @throws java.util.NoSuchElementException if the execution does not exist, or does not belong
     *                                          to {@code expectedResearchId}
     */
    void execute(UUID executionId, UUID expectedResearchId, UserId requestingUserId);
}
