package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for entity creation operations.
 */
@RegisterForReflection
public record McpCreatedResponse(
    String id,
    String message,
    /**
     * Web-app page where the caller can watch the entity this call touched. Null — and omitted
     * from the JSON — when the entity no longer exists (deletes) or the instance is headless.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String uiUrl
) {
    public McpCreatedResponse(String id, String message) {
        this(id, message, null);
    }
}
