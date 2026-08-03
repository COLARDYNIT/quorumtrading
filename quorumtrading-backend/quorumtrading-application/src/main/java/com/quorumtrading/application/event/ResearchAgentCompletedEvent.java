package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.ResearchAgentType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event fired when a research agent completes its analysis.
 * <p>
 * This event is observed by adapters (e.g., WebSocket adapter) to broadcast
 * agent completion updates to connected clients.
 * </p>
 *
 * @param researchId  The unique identifier of the market research configuration
 * @param executionId The unique identifier of this specific execution
 * @param agentType   The type of research agent that completed
 * @param durationMs  The duration of the agent's analysis in milliseconds
 * @param timestamp   The timestamp when the agent completed
 */
public record ResearchAgentCompletedEvent(
        UUID researchId,
        UUID executionId,
        ResearchAgentType agentType,
        long durationMs,
        Instant timestamp
) {
    /**
     * Compact constructor with validation.
     */
    public ResearchAgentCompletedEvent {
        Objects.requireNonNull(researchId, "Research ID cannot be null");
        Objects.requireNonNull(executionId, "Execution ID cannot be null");
        Objects.requireNonNull(agentType, "Agent type cannot be null");
        if (durationMs < 0) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    }

    /**
     * Factory method for convenient event creation with automatic timestamp.
     *
     * @param researchId  The unique identifier of the market research configuration
     * @param executionId The unique identifier of this specific execution
     * @param agentType   The type of research agent that completed
     * @param durationMs  The duration of the agent's analysis in milliseconds
     * @return A new ResearchAgentCompletedEvent with current timestamp
     */
    public static ResearchAgentCompletedEvent of(
            UUID researchId,
            UUID executionId,
            ResearchAgentType agentType,
            long durationMs
    ) {
        return new ResearchAgentCompletedEvent(researchId, executionId, agentType, durationMs, Instant.now());
    }
}
