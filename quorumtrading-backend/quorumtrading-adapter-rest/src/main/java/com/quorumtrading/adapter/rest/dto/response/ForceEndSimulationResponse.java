package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Result of force-ending a simulation")
public record ForceEndSimulationResponse(
    @Schema(description = "Whether the operation succeeded") boolean success,
    @Schema(description = "Whether positions were liquidated") boolean liquidated,
    @Schema(description = "Number of positions liquidated") int liquidatedCount,
    @Schema(description = "Human-readable result message") String message
) {}
