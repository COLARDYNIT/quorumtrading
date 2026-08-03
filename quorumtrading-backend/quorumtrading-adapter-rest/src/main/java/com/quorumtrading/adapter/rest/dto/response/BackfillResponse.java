package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.time.Instant;

/**
 * Response DTO for backfill job status.
 */
@Schema(description = "Backfill job status and progress")
public record BackfillResponse(
    @Schema(description = "Unique backfill job identifier",
            example = "550e8400-e29b-41d4-a716-446655440000")
    String jobId,

    @Schema(description = "Current job status",
            example = "PENDING",
            enumeration = {"PENDING", "IN_PROGRESS", "COMPLETED", "FAILED"})
    String status,

    @Schema(description = "Whether backfill was triggered or data was already sufficient",
            example = "true")
    boolean backfillTriggered,

    @Schema(description = "Human-readable status message",
            example = "Backfill job queued")
    String message,

    @Schema(description = "Progress percentage (0-100)",
            example = "0",
            minimum = "0",
            maximum = "100")
    Integer progressPercentage,

    @Schema(description = "Timestamp when job was created",
            example = "2025-10-27T10:00:00Z")
    Instant createdAt
) {}
