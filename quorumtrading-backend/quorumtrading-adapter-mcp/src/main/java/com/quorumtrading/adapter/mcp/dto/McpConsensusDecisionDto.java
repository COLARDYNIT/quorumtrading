package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * MCP DTO for a flat consensus decision (without agent breakdown).
 */
@RegisterForReflection
public record McpConsensusDecisionDto(
    Long id,
    String simulationId,
    String timestamp,
    String consensusMethod,
    McpActionDto finalAction,
    boolean executed,
    String executionOutcome,
    String executionPrice,
    String quantity,
    String symbol,
    String investmentType,
    String llmCostUsd,
    String notes
) {}
