package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for status change operations (start, pause, etc.).
 */
@RegisterForReflection
public record McpStatusResponse(String simulationId, String message) {}
