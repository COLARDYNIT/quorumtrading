package com.quorumtrading.model.valueobject;

import java.util.List;
import java.util.Map;

/**
 * Value object representing a tool definition for LLM function calling.
 * Matches the OpenAI function-calling schema format.
 *
 * @param name        Tool/function name
 * @param description Human-readable description of what the tool does
 * @param parameters  Map of parameter name to parameter definition
 * @param required    List of required parameter names
 */
public record ToolDefinition(
    String name,
    String description,
    Map<String, ToolParameter> parameters,
    List<String> required
) {

    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name cannot be null or blank");
        }
        if (parameters == null) {
            parameters = Map.of();
        }
        if (required == null) {
            required = List.of();
        }
    }

    /**
     * Describes a single parameter for a tool definition.
     *
     * @param type        JSON Schema type (string, integer, boolean, number)
     * @param description Human-readable description
     */
    public record ToolParameter(String type, String description) {

        public ToolParameter {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("Parameter type cannot be null or blank");
            }
        }
    }
}
