package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpAvailableModelDto(
    String id,
    String name,
    String provider,
    int contextLength,
    String promptCostPerMillion,
    String completionCostPerMillion,
    boolean supportsWebSearch,
    boolean isFree,
    Integer parameterSizeB,
    String tier,
    String tierReason
) {}
