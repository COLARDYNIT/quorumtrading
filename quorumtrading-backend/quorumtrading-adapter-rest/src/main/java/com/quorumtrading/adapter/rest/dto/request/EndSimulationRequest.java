package com.quorumtrading.adapter.rest.dto.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to force-end a simulation")
public record EndSimulationRequest(
    @Schema(description = "Whether to liquidate all positions before ending", example = "true")
    boolean liquidatePositions
) {}
