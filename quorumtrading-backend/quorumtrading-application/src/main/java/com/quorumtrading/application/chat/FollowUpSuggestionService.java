package com.quorumtrading.application.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.model.valueobject.ChatCompletionResult;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.FollowUpSuggestion;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM-based service that generates context-aware follow-up suggestions.
 * <p>
 * Uses a fast, cheap model (qwen-turbo) to analyze the conversation context
 * and generate relevant next-action suggestions shown as clickable chips.
 */
public class FollowUpSuggestionService {

    private static final Logger LOG = Logger.getLogger(FollowUpSuggestionService.class);
    private static final String SUGGESTION_MODEL = "qwen/qwen-turbo";
    private static final int MAX_TOKENS = 256;
    private static final double TEMPERATURE = 0.3; // Lower for more consistent output
    private static final int MAX_SUGGESTIONS = 3;

    // Public for prompt engineering tests (ChatPromptEngineeringTest)
    public static final String SYSTEM_PROMPT = """
        You are a suggestion generator for QuorumTrading, a multi-LLM trading consensus platform.
        Based on the conversation context, generate exactly 3 follow-up action suggestions the user might want to click next.

        Rules:
        - Each suggestion has a "label" and a "message"
        - **message**: The full text shown on the button AND sent as the user's prompt. Keep it SHORT (max 60 characters) but clear.
          - WRONG: "Would you like me to backtest?" (assistant voice)
          - WRONG: "Run the backtest simulation for the BTC Mean Reversion strategy using 90 days of historical data" (too long)
          - CORRECT: "Run backtest for BTC Mean Reversion" (short, specific, user voice)
        - **label**: A 2-3 word summary of the message (max 20 chars). Used as fallback only.
        - If the assistant offered something, the suggestion should be the user's affirmative response
        - Be specific (mention strategy names, tickers when known)

        Output ONLY a valid JSON array, no explanation:
        [
          {"label": "View Strategy", "message": "Show me the BTC Mean Reversion strategy"},
          {"label": "Run Backtest", "message": "Run backtest for BTC Mean Reversion"},
          {"label": "Check BTC", "message": "Show latest BTC indicators"}
        ]
        """;

    private final ConversationalLLMPort llmPort;
    private final ObjectMapper objectMapper;

    public FollowUpSuggestionService(ConversationalLLMPort llmPort, ObjectMapper objectMapper) {
        this.llmPort = llmPort;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate follow-up suggestions based on the conversation context.
     *
     * @param recentMessages   The last few messages from the conversation
     * @param assistantResponse The assistant's final text response
     * @param toolsUsed        Set of tool names that were called
     * @param toolResults      Map of tool name to result JSON (may be truncated for large results)
     * @param userId           The current user's ID
     * @return List of up to 3 follow-up suggestions, or empty list on error
     */
    public List<FollowUpSuggestion> generate(
            List<ChatMessage> recentMessages,
            String assistantResponse,
            Set<String> toolsUsed,
            Map<String, String> toolResults,
            UserId userId) {

        if (recentMessages == null || recentMessages.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String userPrompt = buildUserPrompt(recentMessages, assistantResponse, toolsUsed, toolResults);
            List<ChatMessage> messages = List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(userPrompt)
            );

            LLMConfig config = new LLMConfig(SUGGESTION_MODEL, TEMPERATURE, MAX_TOKENS, null, userId.value(), false);

            ChatCompletionResult result = llmPort.chatCompletion(messages, Collections.emptyList(), config);

            if (result.content() == null || result.content().isBlank()) {
                LOG.warn("LLM returned empty content for follow-up suggestions");
                return Collections.emptyList();
            }

            return parseSuggestions(result.content());

        } catch (Exception e) {
            LOG.warnf("Failed to generate LLM follow-up suggestions: %s", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildUserPrompt(
            List<ChatMessage> recentMessages,
            String assistantResponse,
            Set<String> toolsUsed,
            Map<String, String> toolResults) {

        StringBuilder sb = new StringBuilder();

        // Summarize recent conversation (excluding the latest assistant message to avoid duplication)
        sb.append("## Recent Conversation\n");
        for (ChatMessage msg : recentMessages) {
            String role = msg.role().toString().toLowerCase();
            String content = msg.content();
            // Skip assistant messages - we'll show the final response separately with emphasis
            if ("assistant".equals(role)) {
                continue;
            }
            if (content != null && !content.isBlank()) {
                // Truncate long messages
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                sb.append(role).append(": ").append(content).append("\n\n");
            }
        }

        // Include tools used
        if (toolsUsed != null && !toolsUsed.isEmpty()) {
            sb.append("## Tools Called\n");
            for (String tool : toolsUsed) {
                sb.append("- ").append(tool).append("\n");
            }
            sb.append("\n");
        }

        // Include summarized tool results
        if (toolResults != null && !toolResults.isEmpty()) {
            sb.append("## Tool Results (summarized)\n");
            for (Map.Entry<String, String> entry : toolResults.entrySet()) {
                String result = entry.getValue();
                // Truncate large results
                if (result.length() > 300) {
                    result = result.substring(0, 300) + "...";
                }
                sb.append("### ").append(entry.getKey()).append("\n");
                sb.append(result).append("\n\n");
            }
        }

        // Include assistant's final response LAST for emphasis - this is what suggestions should respond to
        sb.append("## Assistant's Final Response (IMPORTANT: base suggestions on what was offered here)\n");
        if (assistantResponse != null && !assistantResponse.isBlank()) {
            String truncated = assistantResponse.length() > 800
                ? assistantResponse.substring(0, 800) + "..."
                : assistantResponse;
            sb.append(truncated).append("\n\n");
        }

        sb.append("Generate 3 relevant follow-up suggestions as JSON.");
        return sb.toString();
    }

    private List<FollowUpSuggestion> parseSuggestions(String content) {
        try {
            // Extract JSON from the response (in case there's any surrounding text)
            String json = extractJson(content);

            List<Map<String, String>> parsed = objectMapper.readValue(
                json,
                new TypeReference<List<Map<String, String>>>() {}
            );

            return parsed.stream()
                .filter(m -> m.containsKey("label") && m.containsKey("message"))
                .map(m -> new FollowUpSuggestion(m.get("label"), m.get("message")))
                .limit(MAX_SUGGESTIONS)
                .toList();

        } catch (Exception e) {
            LOG.warnf("Failed to parse suggestion JSON: %s", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extract JSON array from response content, handling potential markdown code blocks.
     */
    private String extractJson(String content) {
        String trimmed = content.trim();

        // Handle markdown code blocks
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        trimmed = trimmed.trim();

        // Find JSON array boundaries
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }
}
