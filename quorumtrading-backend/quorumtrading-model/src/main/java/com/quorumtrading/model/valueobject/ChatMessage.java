package com.quorumtrading.model.valueobject;

import java.util.List;

/**
 * Value object representing a single message in a multi-turn chat conversation.
 * Supports user, assistant, system, and tool result messages.
 *
 * @param role       Message role: "system", "user", "assistant", or "tool"
 * @param content    Text content (null for assistant messages with only tool calls)
 * @param toolCalls  Tool calls requested by the assistant (null for non-assistant messages)
 * @param toolCallId The tool call ID this message is a response to (only for role="tool")
 */
public record ChatMessage(
    String role,
    String content,
    List<ToolCall> toolCalls,
    String toolCallId
) {

    public ChatMessage {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Message role cannot be null or blank");
        }
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage assistantWithToolCalls(List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", null, toolCalls, null);
    }

    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage("tool", content, null, toolCallId);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
