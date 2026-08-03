package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Chat session summary for listing")
public record ChatSessionSummaryResponse(
    @Schema(description = "Session ID") UUID sessionId,
    @Schema(description = "Auto-generated title") String title,
    @Schema(description = "LLM model used") String model,
    @Schema(description = "When session was created") Instant createdAt,
    @Schema(description = "When session was last active") Instant lastActivityAt
) {}
