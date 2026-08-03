package com.quorumtrading.application.chat;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.port.out.ChatSessionPort;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.application.usecase.UpdateModelPreferencesUseCase;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.valueobject.ChatCompletionResult;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.FollowUpSuggestion;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UsageSource;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Creates a new chat session with a system prompt and LLM-generated greeting.
 */
public class CreateChatSessionUseCase {

    private static final Logger LOG = Logger.getLogger(CreateChatSessionUseCase.class);

    private static final String STATIC_GREETING = "Welcome to QuorumTrading. How can I help?";
    private static final String NEW_USER_GREETING = "Welcome to QuorumTrading. You have no strategies yet. Explore indicators or set up market research to begin.";
    private static final List<FollowUpSuggestion> NEW_USER_SUGGESTIONS = List.of(
        new FollowUpSuggestion("Explore Indicators", "Show me technical indicators for BTC"),
        new FollowUpSuggestion("New Research", "Set up a market research for BTC to discover promising trading strategies"),
        new FollowUpSuggestion("Create Strategy", "Help me create my first trading strategy")
    );

    private final ChatSessionPort chatSessionPort;
    private final ConversationalLLMPort conversationalLLMPort;
    private final SystemPromptBuilder systemPromptBuilder;
    private final FollowUpSuggestionService followUpSuggestionService;
    private final String defaultModel;
    private final UpdateModelPreferencesUseCase updateModelPreferencesUseCase;
    private final RecordLLMUsageUseCase recordLLMUsageUseCase;

    public CreateChatSessionUseCase(ChatSessionPort chatSessionPort,
                                     ConversationalLLMPort conversationalLLMPort,
                                     SystemPromptBuilder systemPromptBuilder,
                                     FollowUpSuggestionService followUpSuggestionService,
                                     String defaultModel,
                                     UpdateModelPreferencesUseCase updateModelPreferencesUseCase,
                                     RecordLLMUsageUseCase recordLLMUsageUseCase) {
        this.chatSessionPort = chatSessionPort;
        this.conversationalLLMPort = conversationalLLMPort;
        this.systemPromptBuilder = systemPromptBuilder;
        this.followUpSuggestionService = followUpSuggestionService;
        this.defaultModel = defaultModel;
        this.updateModelPreferencesUseCase = updateModelPreferencesUseCase;
        this.recordLLMUsageUseCase = recordLLMUsageUseCase;
    }

    /**
     * Create a new chat session and generate a personalized greeting.
     *
     * @param userId The user creating the session
     * @return Result with session ID, greeting, and model
     */
    public Result execute(UserId userId) {
        UUID sessionId = UUID.randomUUID();
        String systemPrompt = systemPromptBuilder.build(userId);

        String resolvedModel = resolveModel(userId);

        ChatSession session = new ChatSession(sessionId, userId, resolvedModel, systemPrompt);

        // Generate greeting — skip LLM for new users (no data) and when budget is depleted
        String greeting;
        List<FollowUpSuggestion> suggestions;
        boolean budgetAvailable = recordLLMUsageUseCase.hasBudgetAvailable(userId);
        boolean isNewUser = systemPromptBuilder.hasNoUserData(userId);

        if (isNewUser) {
            // New users get a static greeting — no LLM cost on first visit
            greeting = NEW_USER_GREETING;
            suggestions = NEW_USER_SUGGESTIONS;
        } else if (!budgetAvailable) {
            greeting = STATIC_GREETING;
            suggestions = Collections.emptyList();
        } else {
            try {
                LLMConfig config = new LLMConfig(appendOnlineSuffix(resolvedModel), 0.7, 128, null, userId.value(), false);
                List<ChatMessage> greetingMessages = List.of(
                    ChatMessage.system(systemPrompt),
                    ChatMessage.user("""
                        Generate a 1-2 sentence welcome message based on the User Context section. Rules:
                        - If user has active simulations, mention their count/name and offer to check status
                        - Else if user has active market research, mention it and offer to run or review results
                        - Else if user has strategies but no simulations, suggest backtesting one
                        - Else if user has nothing, welcome them and suggest exploring indicators or setting up research
                        - Max 35 words. No emojis. Terminal style. Do not list capabilities.""")
                );
                ChatCompletionResult result = conversationalLLMPort.chatCompletion(
                    greetingMessages, List.of(), config);
                greeting = result.content() != null ? result.content() : STATIC_GREETING;
                session.recordUsage(result.usage());
            } catch (Exception e) {
                LOG.warnf("Failed to generate chat greeting: %s", e.getMessage());
                greeting = STATIC_GREETING;
            }
            suggestions = generateInitialSuggestions(greeting, systemPrompt, userId);
        }

        session.addMessage(ChatMessage.assistant(greeting));

        try {
            chatSessionPort.save(session);
        } catch (Exception e) {
            LOG.errorf("Failed to persist chat session to Redis: sessionId=%s, error=%s",
                sessionId, e.getMessage());
            throw new RuntimeException("Failed to create chat session: storage unavailable", e);
        }

        // Record accumulated LLM usage against budget
        LLMUsage cumulativeUsage = session.getCumulativeUsage();
        if (cumulativeUsage.totalTokens() > 0) {
            recordLLMUsageUseCase.recordUsage(
                userId, cumulativeUsage.billedCost(), cumulativeUsage.rawCost(),
                cumulativeUsage.totalTokens(), UsageSource.CHAT);
        }

        LOG.infof("Created chat session: sessionId=%s, model=%s", sessionId, resolvedModel);
        return new Result(sessionId, greeting, resolvedModel, suggestions);
    }

    /**
     * Generate initial follow-up suggestions based on the greeting.
     * Uses the system prompt context to understand what the user has.
     */
    private List<FollowUpSuggestion> generateInitialSuggestions(String greeting, String systemPrompt, UserId userId) {
        try {
            // Extract the User Context section from the system prompt to pass as context
            String userContext = extractUserContext(systemPrompt);

            // Create a synthetic "recent messages" list with the system context
            List<ChatMessage> contextMessages = List.of(
                ChatMessage.system(userContext)
            );

            return followUpSuggestionService.generate(
                contextMessages,
                greeting,
                Set.of(),      // No tools called for initial greeting
                Map.of(),      // No tool results
                userId
            );
        } catch (Exception e) {
            LOG.warnf("Failed to generate initial suggestions: %s", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extract the User Context section from the system prompt.
     */
    private String extractUserContext(String systemPrompt) {
        int start = systemPrompt.indexOf("## User Context");
        if (start < 0) {
            return systemPrompt; // Fallback to full prompt
        }
        // Find the next major section or end of prompt
        int end = systemPrompt.indexOf("\n## ", start + 1);
        if (end < 0) {
            end = systemPrompt.length();
        }
        return systemPrompt.substring(start, end);
    }

    private String resolveModel(UserId userId) {
        try {
            ModelPreferences preferences = updateModelPreferencesUseCase.getPreferences(userId);
            return preferences.getFor(TradingContext.CHAT)
                .map(modelId -> modelId.value())
                .orElse(defaultModel);
        } catch (Exception e) {
            LOG.warnf("Failed to load user model preferences, using default: %s", e.getMessage());
            return defaultModel;
        }
    }

    /**
     * Append :online suffix to model ID for web search capability.
     * OpenRouter models with :online can query the web when relevant.
     */
    private static String appendOnlineSuffix(String model) {
        if (model == null || model.isBlank()) {
            return model;
        }
        if (model.toLowerCase().endsWith(":online")) {
            return model;
        }
        return model + ":online";
    }

    public record Result(UUID sessionId, String greeting, String model, List<FollowUpSuggestion> suggestions) {}
}
