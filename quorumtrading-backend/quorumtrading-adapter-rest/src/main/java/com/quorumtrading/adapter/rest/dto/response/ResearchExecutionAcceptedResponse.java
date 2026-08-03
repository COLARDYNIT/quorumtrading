package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/**
 * Response DTO for accepted asynchronous research execution.
 * <p>
 * This response is returned immediately when execution is triggered.
 * The client should subscribe to WebSocket for progress updates.
 * </p>
 */
@Schema(description = "Accepted research execution response")
public record ResearchExecutionAcceptedResponse(
    @Schema(description = "Execution ID for tracking progress", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID executionId,

    @Schema(description = "Market research ID", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID researchId,

    @Schema(description = "Status message", example = "Execution started. Subscribe to WebSocket for progress updates.")
    String message
) {
    /**
     * Factory method for creating an accepted response.
     *
     * @param executionId The unique execution ID
     * @param researchId  The market research ID
     * @return The response DTO
     */
    public static ResearchExecutionAcceptedResponse of(UUID executionId, UUID researchId) {
        return new ResearchExecutionAcceptedResponse(
            executionId,
            researchId,
            "Execution started. Subscribe to WebSocket for progress updates."
        );
    }
}
