package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record McpResearchExecutionDto(
    String id,
    String marketResearchId,
    String outcome,
    String researcherConclusion,
    String viabilityScore,
    String strategyId,
    String simulationId,
    String executedAt,
    List<McpResearchAgentDecisionDto> agentDecisions,
    String llmCostUsd
) {}
