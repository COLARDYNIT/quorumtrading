package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpModelPreferenceDto(
    String context,
    String contextDescription,
    String selectedModel,
    String source,
    String systemDefault
) {}
