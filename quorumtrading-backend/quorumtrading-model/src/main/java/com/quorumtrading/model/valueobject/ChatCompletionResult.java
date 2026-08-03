package com.quorumtrading.model.valueobject;

import java.util.List;

/**
 * Value object representing the result of a chat completion API call.
 * Contains either text content, tool calls, or both.
 *
 * @param content   Text content from the assistant (may be null if only tool calls)
 * @param toolCalls Tool calls requested by the assistant (may be null/empty for text-only responses)
 * @param usage     Token usage and cost metrics
 * @param model     The actual model that served the request
 */
public record ChatCompletionResult(
    String content,
    List<ToolCall> toolCalls,
    LLMUsage usage,
    String model
) {

    public ChatCompletionResult {
        if (usage == null) {
            usage = LLMUsage.ZERO;
        }
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static ChatCompletionResult textOnly(String content, LLMUsage usage, String model) {
        return new ChatCompletionResult(content, null, usage, model);
    }

    public static ChatCompletionResult withToolCalls(List<ToolCall> toolCalls, LLMUsage usage, String model) {
        return new ChatCompletionResult(null, toolCalls, usage, model);
    }

    public static ChatCompletionResult withContentAndToolCalls(String content, List<ToolCall> toolCalls, LLMUsage usage, String model) {
        return new ChatCompletionResult(content, toolCalls, usage, model);
    }
}
