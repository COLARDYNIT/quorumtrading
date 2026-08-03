package com.quorumtrading.application.chat;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.model.valueobject.ChatCompletionResult;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;

/**
 * Generates short session titles from the first user+assistant exchange.
 * Uses qwen-turbo for speed and minimal cost.
 */
public class ChatTitleGenerationService {

    private static final Logger LOG = Logger.getLogger(ChatTitleGenerationService.class);
    private static final String TITLE_MODEL = "qwen/qwen-turbo";
    private static final int MAX_TOKENS = 32;
    private static final double TEMPERATURE = 0.3;

    private static final String SYSTEM_PROMPT = """
        Generate a very short title (3-6 words) summarizing the user's intent.
        Output ONLY the title text, no quotes, no punctuation at the end.
        Examples:
        - "BTC Backtest Strategy Review"
        - "Create RSI Mean Reversion"
        - "Portfolio Performance Check"
        - "AAPL Technical Indicators"
        - "New Market Research Setup"
        """;

    private final ConversationalLLMPort llmPort;

    public ChatTitleGenerationService(ConversationalLLMPort llmPort) {
        this.llmPort = llmPort;
    }

    /**
     * Generate a title from a single user+assistant exchange (first message).
     *
     * @return A short title, or null if generation fails
     */
    public String generate(String userMessage, String assistantResponse, UserId userId) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }

        String truncatedUser = truncateText(userMessage, 300);
        String truncatedAssistant = truncateText(assistantResponse, 300);

        String userPrompt = "User: " + truncatedUser;
        if (truncatedAssistant != null && !truncatedAssistant.isBlank()) {
            userPrompt += "\nAssistant: " + truncatedAssistant;
        }

        return generateFromPrompt(userPrompt, userId);
    }

    /**
     * Refine a title using recent conversation history for better accuracy.
     * Called after several exchanges when the topic is clearer.
     *
     * @param recentMessages Recent user+assistant messages (typically last 6)
     * @return A short title, or null if generation fails
     */
    public String refine(List<ChatMessage> recentMessages, UserId userId) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return null;
        }

        var sb = new StringBuilder();
        for (ChatMessage msg : recentMessages) {
            if ("user".equals(msg.role()) || "assistant".equals(msg.role())) {
                String label = "user".equals(msg.role()) ? "User" : "Assistant";
                sb.append(label).append(": ").append(truncateText(msg.content(), 200)).append("\n");
            }
        }

        return generateFromPrompt(sb.toString().trim(), userId);
    }

    private String generateFromPrompt(String conversationSummary, UserId userId) {
        try {
            String userPrompt = conversationSummary + "\n\nGenerate a short title:";

            List<ChatMessage> messages = List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(userPrompt)
            );

            LLMConfig config = new LLMConfig(TITLE_MODEL, TEMPERATURE, MAX_TOKENS, null, userId.value(), false);
            ChatCompletionResult result = llmPort.chatCompletion(messages, Collections.emptyList(), config);

            if (result.content() == null || result.content().isBlank()) {
                LOG.warn("LLM returned empty title");
                return null;
            }

            return cleanTitle(result.content());
        } catch (Exception e) {
            LOG.warnf("Failed to generate chat title: %s", e.getMessage());
            return null;
        }
    }

    private static String truncateText(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private String cleanTitle(String raw) {
        String title = raw.trim();
        // Strip surrounding quotes
        if ((title.startsWith("\"") && title.endsWith("\"")) ||
            (title.startsWith("'") && title.endsWith("'"))) {
            title = title.substring(1, title.length() - 1).trim();
        }
        // Strip trailing punctuation
        if (title.endsWith(".") || title.endsWith("!") || title.endsWith(",")) {
            title = title.substring(0, title.length() - 1).trim();
        }
        // Limit length
        if (title.length() > 60) {
            title = title.substring(0, 57) + "...";
        }
        return title.isEmpty() ? null : title;
    }
}
