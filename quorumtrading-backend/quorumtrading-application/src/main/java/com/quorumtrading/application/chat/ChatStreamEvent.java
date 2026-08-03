package com.quorumtrading.application.chat;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.quorumtrading.model.valueobject.FollowUpSuggestion;
import com.quorumtrading.model.valueobject.LLMUsage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sealed interface for SSE events emitted during chat message processing.
 * The frontend consumes these to show real-time tool execution progress
 * and the final text response.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ChatStreamEvent.TextResponse.class, name = "TextResponse"),
    @JsonSubTypes.Type(value = ChatStreamEvent.ToolStart.class, name = "ToolStart"),
    @JsonSubTypes.Type(value = ChatStreamEvent.ToolResult.class, name = "ToolResult"),
    @JsonSubTypes.Type(value = ChatStreamEvent.Done.class, name = "Done"),
    @JsonSubTypes.Type(value = ChatStreamEvent.Error.class, name = "Error"),
})
public sealed interface ChatStreamEvent {

    /** Final text response from the LLM. */
    record TextResponse(String content) implements ChatStreamEvent {}

    /** Tool execution started. */
    record ToolStart(String callId, String toolName, String args) implements ChatStreamEvent {}

    /** Tool execution completed with result. */
    record ToolResult(String callId, String result) implements ChatStreamEvent {}

    /** All processing complete. Includes cumulative session usage, cost, follow-up suggestions, and session title. */
    record Done(LLMUsage usage, BigDecimal sessionCost, List<FollowUpSuggestion> suggestions, String title) implements ChatStreamEvent {}

    /** Error during processing. */
    record Error(String message) implements ChatStreamEvent {}
}
