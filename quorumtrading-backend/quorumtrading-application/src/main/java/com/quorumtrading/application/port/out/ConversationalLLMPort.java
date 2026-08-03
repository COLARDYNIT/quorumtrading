package com.quorumtrading.application.port.out;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.model.valueobject.ChatCompletionResult;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ToolCall;
import com.quorumtrading.model.valueobject.ToolDefinition;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Output port for multi-turn conversational LLM interactions with tool/function calling.
 * <p>
 * Unlike {@link LLMPort} which handles single-prompt queries, this port supports:
 * <ul>
 *   <li>Multi-turn message history (system, user, assistant, tool messages)</li>
 *   <li>Tool definitions in OpenAI function-calling format</li>
 *   <li>Responses that may contain text, tool calls, or both</li>
 * </ul>
 */
public interface ConversationalLLMPort {

    /**
     * Callback handler for streaming chat completion responses.
     * Called incrementally as tokens arrive from the LLM.
     */
    interface ChatCompletionStreamHandler {
        /** Called for each text token chunk as it arrives. */
        void onTextDelta(String delta);

        /** Called once when all tool calls have been fully assembled from the stream. */
        void onToolCalls(List<ToolCall> toolCalls);

        /** Called with final usage metrics from the last stream chunk. */
        void onUsage(LLMUsage usage);

        /** Called if an error occurs mid-stream. */
        void onError(String message);
    }

    /**
     * Send a chat completion request with message history and optional tool definitions.
     *
     * @param messages Ordered list of conversation messages
     * @param tools    Available tool definitions (may be empty for text-only conversations)
     * @param config   LLM configuration (model, temperature, tokens, userId)
     * @return Result containing text content, tool calls, usage metrics, and actual model used
     */
    ChatCompletionResult chatCompletion(List<ChatMessage> messages, List<ToolDefinition> tools, LLMConfig config);

    /**
     * Whether this conversational provider is actually usable right now.
     *
     * <p>Callers must check this before choosing the tool-calling path. Only one adapter
     * implements this port, and its bean exists whether or not a key is configured — so on a
     * keyless install (the self-host default, where the user has selected a local engine) every
     * tool-calling agent reached it anyway and failed with HTTP 401. Falling back to the plain
     * text path keeps those agents working on whichever engine the user actually selected.</p>
     *
     * @return true when a request would be attempted with real credentials
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Async version of {@link #chatCompletion(List, List, LLMConfig)}.
     * Default implementation wraps the sync call in a CompletableFuture.
     */
    default CompletableFuture<ChatCompletionResult> chatCompletionAsync(
            List<ChatMessage> messages, List<ToolDefinition> tools, LLMConfig config) {
        return CompletableFuture.supplyAsync(() -> chatCompletion(messages, tools, config));
    }

    /**
     * Send a streaming chat completion request. Tokens are delivered incrementally via the handler.
     * <p>
     * Default implementation falls back to {@link #chatCompletion} and delivers the full response
     * as a single {@code onTextDelta} call, so existing implementations and test stubs work unchanged.
     *
     * @param messages Ordered list of conversation messages
     * @param tools    Available tool definitions (may be empty for text-only conversations)
     * @param config   LLM configuration (model, temperature, tokens, userId)
     * @param handler  Callback handler for incremental token delivery
     * @return Result containing the complete accumulated response (for session history storage)
     */
    default ChatCompletionResult chatCompletionStreaming(
            List<ChatMessage> messages, List<ToolDefinition> tools, LLMConfig config,
            ChatCompletionStreamHandler handler) {
        ChatCompletionResult result = chatCompletion(messages, tools, config);
        if (result.content() != null && !result.content().isEmpty()) {
            handler.onTextDelta(result.content());
        }
        if (result.hasToolCalls()) {
            handler.onToolCalls(result.toolCalls());
        }
        if (result.usage() != null) {
            handler.onUsage(result.usage());
        }
        return result;
    }
}
