package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.UserId;

import java.util.UUID;

/**
 * Use case for executing a market research configuration.
 * <p>
 * Execution is asynchronous - this method returns immediately with an execution ID.
 * Progress events are published via CDI events (ResearchExecutionStartedEvent,
 * ResearchAgentStartedEvent, ResearchAgentCompletedEvent) and can be observed
 * by WebSocket adapters for real-time progress updates.
 * </p>
 */
public interface ExecuteMarketResearchUseCase {

    /**
     * Start asynchronous execution of a market research configuration.
     * <p>
     * This method returns immediately after validation. The actual research
     * execution happens asynchronously, with progress events published for
     * WebSocket broadcasting.
     * </p>
     *
     * @param marketResearchId the market research ID
     * @return the execution ID (for tracking progress via events/WebSocket)
     * @throws java.util.NoSuchElementException if research not found
     * @throws IllegalStateException if research is not active
     */
    UUID execute(UUID marketResearchId, UserId requestingUserId);
}
