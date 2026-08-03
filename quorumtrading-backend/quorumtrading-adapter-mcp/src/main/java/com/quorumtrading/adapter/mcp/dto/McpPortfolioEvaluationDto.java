package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record McpPortfolioEvaluationDto(
    String simulationId,
    boolean success,
    boolean skipped,
    String errorMessage,
    McpPortfolioSnapshotDto latestSnapshot,
    String returnPercent
) {}
