package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record McpMarketResearchDetailsDto(
    McpMarketResearchDto research,
    List<McpResearchExecutionDto> executions,
    McpResearchExecutionDto latestExecution,
    int totalExecutions,
    long successfulExecutions
) {}
