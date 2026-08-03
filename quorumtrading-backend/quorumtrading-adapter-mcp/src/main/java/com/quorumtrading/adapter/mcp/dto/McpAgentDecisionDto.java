package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * MCP DTO for an individual agent's LLM decision within a consensus.
 */
@RegisterForReflection
public record McpAgentDecisionDto(
    Long id,
    String agentId,
    String role,
    String ticker,
    String recommendation,
    String confidenceScore,
    String reasoning,
    McpActionDto recommendedAction,
    String llmModel,
    Integer executionTimeMs
) {}
