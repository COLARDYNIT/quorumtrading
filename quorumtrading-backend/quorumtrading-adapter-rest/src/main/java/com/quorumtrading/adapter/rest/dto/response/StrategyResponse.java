package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * Response DTO for strategy details.
 * Version is the Hibernate @Version field for optimistic locking (starts at 1, auto-incremented).
 */
@Schema(description = "Strategy details response")
public record StrategyResponse(
    @Schema(description = "Unique strategy identifier (UUID)",
            example = "660f9511-f39c-52e5-b827-557766551111")
    String id,

    @Schema(description = "Strategy name",
            example = "BTC Momentum Strategy")
    String name,

    @Schema(description = "Human-readable description for the strategy library",
            example = "Momentum-based BTC strategy using RSI and MACD crossovers")
    String description,

    @Schema(description = "Hibernate @Version field for optimistic locking (starts at 1)",
            example = "1")
    Integer version,

    @Schema(description = "Strategy configuration in markdown format")
    String markdownContent,

    @Schema(description = "Timestamp when strategy was created",
            example = "2025-10-27T10:00:00Z")
    Instant createdAt,

    @Schema(description = "UUID of strategy owner",
            example = "550e8400-e29b-41d4-a716-446655440000")
    String ownerId,

    @Schema(description = "Whether this strategy is publicly visible",
            example = "false")
    boolean publicVisible
) {}
