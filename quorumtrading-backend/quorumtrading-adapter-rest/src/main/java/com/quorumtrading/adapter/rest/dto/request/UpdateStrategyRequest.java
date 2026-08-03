package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request DTO for updating an existing strategy.
 * Both fields are optional, but at least one must be provided.
 */
@Schema(description = "Request to update an existing trading strategy")
public record UpdateStrategyRequest(
    @Size(min = 1, max = 255, message = "Strategy name must be between 1 and 255 characters")
    @Schema(description = "Updated strategy name (optional, null means no change)",
            example = "BTC Momentum Strategy v2")
    String name,

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "Updated description (optional, null means no change)")
    String description,

    @Size(max = 512000, message = "Markdown content must not exceed 500KB")
    @Schema(description = "Updated strategy configuration in markdown format (optional, null means no change)")
    String markdownContent
) {}
