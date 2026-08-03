package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.ResearchAgentType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event fired when a research agent begins its analysis.
 * <p>
 * This event is observed by adapters (e.g., WebSocket adapter) to broadcast
 * agent progress updates to connected clients.
 * </p>
 *
 * @param researchId  The unique identifier of the market research configuration
 * @param executionId The unique identifier of this specific execution
 * @param agentType   The type of research agent that started
 * @param timestamp   The timestamp when the agent started
 */
public record ResearchAgentStartedEvent(
        UUID researchId,
        UUID executionId,
        ResearchAgentType agentType,
        Instant timestamp
) {
    /**
     * Compact constructor with validation.
     */
    public ResearchAgentStartedEvent {
        Objects.requireNonNull(researchId, "Research ID cannot be null");
        Objects.requireNonNull(executionId, "Execution ID cannot be null");
        Objects.requireNonNull(agentType, "Agent type cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    }

    /**
     * Factory method for convenient event creation with automatic timestamp.
     *
     * @param researchId  The unique identifier of the market research configuration
     * @param executionId The unique identifier of this specific execution
     * @param agentType   The type of research agent that started
     * @return A new ResearchAgentStartedEvent with current timestamp
     */
    public static ResearchAgentStartedEvent of(
            UUID researchId,
            UUID executionId,
            ResearchAgentType agentType
    ) {
        return new ResearchAgentStartedEvent(researchId, executionId, agentType, Instant.now());
    }
}
