package com.quorumtrading.model.valueobject;

/**
 * Value object representing a tool/function call requested by an LLM.
 *
 * @param id           Unique identifier for this tool call (assigned by the LLM)
 * @param functionName Name of the function to invoke
 * @param arguments    JSON string of function arguments
 */
public record ToolCall(String id, String functionName, String arguments) {

    public ToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Tool call id cannot be null or blank");
        }
        if (functionName == null || functionName.isBlank()) {
            throw new IllegalArgumentException("Function name cannot be null or blank");
        }
    }
}
