package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Preview of what will be deleted/archived when restarting from a decision")
public record RestartFromPreviewResponse(
    @Schema(description = "Number of consensus decisions to delete")
    long consensusDecisionsToDelete,
    @Schema(description = "Number of LLM decisions to delete")
    long llmDecisionsToDelete,
    @Schema(description = "Number of portfolio snapshots to delete")
    long portfolioSnapshotsToDelete,
    @Schema(description = "Raw LLM cost that will be archived (USD)")
    BigDecimal archivedCostUsd,
    @Schema(description = "Total tokens that will be archived")
    int archivedTokens,
    @Schema(description = "Portfolio cash at restart point")
    BigDecimal portfolioCashAtRestart,
    @Schema(description = "Portfolio total value at restart point")
    BigDecimal portfolioValueAtRestart,
    @Schema(description = "Timestamp the simulation will restart from")
    Instant restartTimestamp
) {}
