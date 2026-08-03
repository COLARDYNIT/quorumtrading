package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record McpResearchAgentDecisionDto(
    String id,
    String agentType,
    String advice,
    String reasoning,
    String decidedAt,
    String modelUsed,
    int totalTokens,
    String llmCostUsd
) {}
