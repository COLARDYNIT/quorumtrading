package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Preview of what will happen when restarting a simulation from a specific decision.
 * Shows counts of data to be deleted and the portfolio state at the restart point.
 */
public record RestartFromPreview(
    long consensusDecisionsToDelete,
    long llmDecisionsToDelete,
    long portfolioSnapshotsToDelete,
    BigDecimal archivedCostUsd,
    int archivedTokens,
    BigDecimal portfolioCashAtRestart,
    BigDecimal portfolioValueAtRestart,
    Instant restartTimestamp
) {}
