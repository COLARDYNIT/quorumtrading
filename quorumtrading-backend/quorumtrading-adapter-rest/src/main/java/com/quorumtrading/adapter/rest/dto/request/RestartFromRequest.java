package com.quorumtrading.adapter.rest.dto.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request to restart a simulation from a specific consensus decision")
public record RestartFromRequest(
    @Schema(description = "ID of the consensus decision to restart from", required = true, example = "12345")
    long consensusDecisionId
) {}
