package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * MCP DTO for a consensus decision with its agent decision breakdown.
 */
@RegisterForReflection
public record McpConsensusWithAgentsDto(
    McpConsensusDecisionDto consensus,
    List<McpAgentDecisionDto> agentDecisions,
    List<McpParseFailureDto> parseFailures
) {}
