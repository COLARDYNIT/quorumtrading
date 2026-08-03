package com.quorumtrading.application.chat;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.port.out.ChatSessionPort;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.application.port.out.FreeModelRateLimitPort;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.service.FreeModelPolicy;
import com.quorumtrading.model.valueobject.*;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import org.jboss.logging.Logger;

import java.util.*;

/**
 * Core agentic loop use case: sends a user message, calls the LLM,
 * executes any tool calls, and returns a stream of events for SSE.
 */
public class SendChatMessageUseCase {

    private static final Logger LOG = Logger.getLogger(SendChatMessageUseCase.class);
    private static final int MAX_TOOL_ITERATIONS = 30;
    private static final int RECENT_MESSAGES_FOR_SUGGESTIONS = 4;
    private static final int TITLE_REFINE_AT_MESSAGE = 3;
    private static final int RECENT_MESSAGES_FOR_TITLE = 6;

    private final ChatSessionPort chatSessionPort;
    private final ConversationalLLMPort conversationalLLMPort;
    private final ChatToolExecutor chatToolExecutor;
    private final RecordLLMUsageUseCase recordLLMUsageUseCase;
    private final FollowUpSuggestionService followUpSuggestionService;
    private final ChatTitleGenerationService chatTitleGenerationService;
    private final FreeModelPolicy freeModelPolicy;
    private final FreeModelRateLimitPort freeModelRateLimitPort;

    public SendChatMessageUseCase(ChatSessionPort chatSessionPort,
                                   ConversationalLLMPort conversationalLLMPort,
                                   ChatToolExecutor chatToolExecutor,
                                   RecordLLMUsageUseCase recordLLMUsageUseCase,
                                   FollowUpSuggestionService followUpSuggestionService,
                                   ChatTitleGenerationService chatTitleGenerationService,
                                   FreeModelPolicy freeModelPolicy,
                                   FreeModelRateLimitPort freeModelRateLimitPort) {
        this.chatSessionPort = chatSessionPort;
        this.conversationalLLMPort = conversationalLLMPort;
        this.chatToolExecutor = chatToolExecutor;
        this.recordLLMUsageUseCase = recordLLMUsageUseCase;
        this.followUpSuggestionService = followUpSuggestionService;
        this.chatTitleGenerationService = chatTitleGenerationService;
        this.freeModelPolicy = freeModelPolicy;
        this.freeModelRateLimitPort = freeModelRateLimitPort;
    }

    /**
     * Process a user message and return a stream of events.
     *
     * @param sessionId The chat session ID
     * @param userId    The current user's ID
     * @param content   The user's message text
     * @return Multi stream of ChatStreamEvent for SSE
     */
    public Multi<ChatStreamEvent> execute(UUID sessionId, UserId userId, String content) {
        return Multi.createFrom().<ChatStreamEvent>emitter(emitter -> {
            try {
                ChatSession session = chatSessionPort.findById(sessionId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Chat session not found: " + sessionId));

                // Free model rate limit check
                String sessionModel = session.getModel();
                if (freeModelPolicy.isFreeForUser(sessionModel)) {
                    if (!freeModelRateLimitPort.isAllowed(userId, freeModelPolicy.getDailyLimit())) {
                        emitter.emit(new ChatStreamEvent.Error(
                            "FREE_MODEL_LIMIT:Free model daily limit of " + freeModelPolicy.getDailyLimit()
                                + " calls reached. Try again tomorrow or switch to a paid model."));
                        emitter.complete();
                        return;
                    }
                } else {
                    // Budget + daily limit check (only for paid models)
                    try {
                        recordLLMUsageUseCase.requireAvailableBudget(userId);
                    } catch (com.quorumtrading.application.exception.DailySpendLimitExceededException dailyEx) {
                        emitter.emit(new ChatStreamEvent.Error(
                            "DAILY_LIMIT:Your daily spend limit of $" + dailyEx.getDailyLimit()
                                + " has been reached. AI operations will resume tomorrow, "
                                + "or increase your limit at /settings/budget."));
                        emitter.complete();
                        return;
                    } catch (com.quorumtrading.application.exception.BudgetDepletedException budgetEx) {
                        emitter.emit(new ChatStreamEvent.Error(
                            "BUDGET_DEPLETED:Your AI budget has been depleted. "
                                + "Please top up your budget at /settings/budget to continue."));
                        emitter.complete();
                        return;
                    }
                }

                // Add user message
                session.addMessage(ChatMessage.user(content));

                LLMConfig config = new LLMConfig(appendOnlineSuffix(session.getModel()), 0.7, 4096, null, userId.value(), false);
                List<ToolDefinition> tools = ChatToolRegistry.allTools();
                Map<String, String> toolResultsMap = new LinkedHashMap<>();

                // Agentic loop
                for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                    List<ChatMessage> messages = session.getMessagesForLLM();

                    LOG.infof("Chat LLM call: sessionId=%s, iteration=%d, messageCount=%d",
                              sessionId, iteration, messages.size());

                    ChatCompletionResult result = conversationalLLMPort.chatCompletionStreaming(
                        messages, tools, config,
                        new ConversationalLLMPort.ChatCompletionStreamHandler() {
                            @Override
                            public void onTextDelta(String delta) {
                                emitter.emit(new ChatStreamEvent.TextResponse(delta));
                            }
                            @Override
                            public void onToolCalls(List<ToolCall> toolCalls) { /* handled via result */ }
                            @Override
                            public void onUsage(LLMUsage usage) { /* handled via result */ }
                            @Override
                            public void onError(String message) { /* caught by outer try/catch */ }
                        });

                    // Record usage
                    session.recordUsage(result.usage());
                    if (freeModelPolicy.isFreeForUser(sessionModel)) {
                        freeModelRateLimitPort.recordUsage(userId);
                    } else {
                        recordLLMUsageUseCase.recordUsage(userId, result.usage().billedCost(),
                            result.usage().rawCost(), result.usage().totalTokens(), UsageSource.CHAT);
                    }

                    if (result.hasToolCalls()) {
                        // Add assistant message with tool calls to history
                        session.addMessage(ChatMessage.assistantWithToolCalls(result.toolCalls()));

                        // Execute each tool call
                        for (ToolCall toolCall : result.toolCalls()) {
                            emitter.emit(new ChatStreamEvent.ToolStart(
                                toolCall.id(), toolCall.functionName(), toolCall.arguments()));

                            String toolResult = chatToolExecutor.execute(
                                toolCall.functionName(), toolCall.arguments(), userId);

                            // Track tool results for suggestion generation
                            toolResultsMap.put(toolCall.functionName(), toolResult);

                            emitter.emit(new ChatStreamEvent.ToolResult(toolCall.id(), toolResult));

                            // Add tool result to conversation history
                            session.addMessage(ChatMessage.toolResult(toolCall.id(), toolResult));
                        }
                        // Continue loop — LLM needs to process tool results
                    } else {
                        // Text response — we're done (content already streamed via onTextDelta)
                        String textContent = result.content() != null ? result.content() : "";
                        session.addMessage(ChatMessage.assistant(textContent));

                        // Generate title on first user exchange (session has no title yet)
                        generateTitleIfNeeded(session, content, textContent, userId);

                        // Generate context-aware follow-up suggestions
                        List<FollowUpSuggestion> suggestions = followUpSuggestionService.generate(
                            session.getRecentMessages(RECENT_MESSAGES_FOR_SUGGESTIONS),
                            textContent,
                            toolResultsMap.keySet(),
                            toolResultsMap,
                            userId);

                        emitter.emit(new ChatStreamEvent.Done(
                            session.getCumulativeUsage(),
                            session.getCumulativeUsage().billedCost(),
                            suggestions,
                            session.getTitle()));

                        chatSessionPort.save(session);
                        emitter.complete();
                        return;
                    }
                }

                // Max iterations reached — force a text response
                LOG.warnf("Chat max tool iterations reached: sessionId=%s", sessionId);
                String maxIterationResponse = "I've gathered the information above. Let me know if you need anything else.";
                session.addMessage(ChatMessage.assistant(maxIterationResponse));
                emitter.emit(new ChatStreamEvent.TextResponse(maxIterationResponse));

                // Generate title on first user exchange
                generateTitleIfNeeded(session, content, maxIterationResponse, userId);

                // Generate context-aware follow-up suggestions
                List<FollowUpSuggestion> suggestions = followUpSuggestionService.generate(
                    session.getRecentMessages(RECENT_MESSAGES_FOR_SUGGESTIONS),
                    maxIterationResponse,
                    toolResultsMap.keySet(),
                    toolResultsMap,
                    userId);

                emitter.emit(new ChatStreamEvent.Done(
                    session.getCumulativeUsage(),
                    session.getCumulativeUsage().billedCost(),
                    suggestions,
                    session.getTitle()));

                chatSessionPort.save(session);
                emitter.complete();

            } catch (Exception e) {
                LOG.errorf("Chat message processing failed: sessionId=%s, error=%s", sessionId, e.getMessage());
                emitter.emit(new ChatStreamEvent.Error("An error occurred: " + e.getMessage()));
                emitter.complete();
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /**
     * Generate or refine the session title based on conversation progress.
     * - First user message: generate initial title from the single exchange
     * - Third user message: refine title using broader conversation context
     */
    private void generateTitleIfNeeded(ChatSession session, String userMessage, String assistantResponse, UserId userId) {
        long userMessageCount = session.getMessages().stream()
            .filter(m -> "user".equals(m.role()))
            .count();

        try {
            if (session.getTitle() == null) {
                // First exchange — generate initial title
                String title = chatTitleGenerationService.generate(userMessage, assistantResponse, userId);
                if (title != null) {
                    session.setTitle(title);
                }
            } else if (userMessageCount == TITLE_REFINE_AT_MESSAGE) {
                // Third exchange — refine with more context
                String refined = chatTitleGenerationService.refine(
                    session.getRecentMessages(RECENT_MESSAGES_FOR_TITLE), userId);
                if (refined != null) {
                    session.setTitle(refined);
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to generate session title: sessionId=%s, error=%s", session.getSessionId(), e.getMessage());
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
}
