package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * MCP DTO for an LLM parse failure. Excludes the prompt field to keep MCP responses compact.
 */
@RegisterForReflection
public record McpParseFailureDto(
    String decisionTimestamp,
    String ticker,
    String agentRole,
    String requestedModel,
    String actualModel,
    Double temperature,
    Integer executionTimeMs,
    String rawResponse,
    String errorMessage,
    String errorType
) {}
