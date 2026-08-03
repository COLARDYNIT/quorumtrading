package com.quorumtrading.adapter.rest.dto.response;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response DTO for admin statistics.
 */
@RegisterForReflection
@Schema(description = "Admin statistics for the platform")
public record AdminStatsResponse(
    @Schema(description = "Total number of users", example = "150")
    long userCount,

    @Schema(description = "Total number of market research configurations", example = "45")
    long researchCount,

    @Schema(description = "Total number of strategies", example = "78")
    long strategyCount,

    @Schema(description = "Total number of simulations", example = "234")
    long simulationCount
) {}
