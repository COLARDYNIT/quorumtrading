package com.quorumtrading.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event fired when a research execution begins.
 * <p>
 * This event is observed by adapters (e.g., WebSocket adapter) to broadcast
 * execution start notifications to connected clients.
 * </p>
 *
 * @param researchId  The unique identifier of the market research configuration
 * @param executionId The unique identifier of this specific execution
 * @param timestamp   The timestamp when the execution started
 */
public record ResearchExecutionStartedEvent(
        UUID researchId,
        UUID executionId,
        Instant timestamp
) {
    /**
     * Compact constructor with validation.
     */
    public ResearchExecutionStartedEvent {
        Objects.requireNonNull(researchId, "Research ID cannot be null");
        Objects.requireNonNull(executionId, "Execution ID cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    }

    /**
     * Factory method for convenient event creation with automatic timestamp.
     *
     * @param researchId  The unique identifier of the market research configuration
     * @param executionId The unique identifier of this specific execution
     * @return A new ResearchExecutionStartedEvent with current timestamp
     */
    public static ResearchExecutionStartedEvent of(UUID researchId, UUID executionId) {
        return new ResearchExecutionStartedEvent(researchId, executionId, Instant.now());
    }
}
