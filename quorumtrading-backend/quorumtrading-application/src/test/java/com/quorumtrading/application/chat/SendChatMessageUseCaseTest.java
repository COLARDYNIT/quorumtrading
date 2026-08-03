package com.quorumtrading.application.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.port.out.ChatSessionPort;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.application.port.out.FreeModelRateLimitPort;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.service.FreeModelPolicy;
import com.quorumtrading.model.valueobject.ChatCompletionResult;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ToolCall;
import com.quorumtrading.model.valueobject.ToolDefinition;
import com.quorumtrading.model.valueobject.UsageSource;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.quorumtrading.model.valueobject.FollowUpSuggestion;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.exception.DailySpendLimitExceededException;

@ExtendWith(MockitoExtension.class)
class SendChatMessageUseCaseTest {

    @Mock
    private ChatSessionPort chatSessionPort;

    @Mock
    private ChatToolExecutor chatToolExecutor;

    @Mock
    private RecordLLMUsageUseCase recordLLMUsageUseCase;

    /**
     * Manual stub for ConversationalLLMPort — Mockito inline mock maker
     * can't mock this interface on GraalVM JDK 25.
     */
    private StubConversationalLLMPort stubLLMPort;

    private SendChatMessageUseCase useCase;

    private static final UserId USER_ID = new UserId(UUID.randomUUID());
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String MODEL = "google/gemini-2.0-flash-001";

    @Mock
    private FreeModelRateLimitPort freeModelRateLimitPort;

    private StubFollowUpSuggestionService stubFollowUpSuggestionService;
    private final FreeModelPolicy freeModelPolicy = new FreeModelPolicy(List.of(), 50);

    private StubChatTitleGenerationService stubTitleService;

    @BeforeEach
    void setUp() {
        stubLLMPort = new StubConversationalLLMPort();
        stubFollowUpSuggestionService = new StubFollowUpSuggestionService();
        stubTitleService = new StubChatTitleGenerationService();
        useCase = new SendChatMessageUseCase(
            chatSessionPort, stubLLMPort, chatToolExecutor,
            recordLLMUsageUseCase, stubFollowUpSuggestionService, stubTitleService,
            freeModelPolicy, freeModelRateLimitPort);
    }

    private ChatSession createSession() {
        return new ChatSession(SESSION_ID, USER_ID, MODEL, "System prompt");
    }

    @Test
    void shouldReturnTextResponseForSimpleMessage() {
        ChatSession session = createSession();
        when(chatSessionPort.findById(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        stubLLMPort.enqueue(ChatCompletionResult.textOnly("Hello! How can I help?", LLMUsage.ZERO, MODEL));

        List<ChatStreamEvent> events = collectEvents();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(ChatStreamEvent.TextResponse.class);
        assertThat(((ChatStreamEvent.TextResponse) events.get(0)).content()).isEqualTo("Hello! How can I help?");
        assertThat(events.get(1)).isInstanceOf(ChatStreamEvent.Done.class);
        ChatStreamEvent.Done done = (ChatStreamEvent.Done) events.get(1);
        assertThat(done.suggestions()).isEmpty();

        verify(chatSessionPort).save(session);
        assertThat(stubLLMPort.callCount).isEqualTo(1);
    }

    @Test
    void shouldExecuteToolCallAndReturnResult() {
        ChatSession session = createSession();
        when(chatSessionPort.findById(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        ToolCall toolCall = new ToolCall("call-1", "list_strategies", "{}");
        stubLLMPort.enqueue(ChatCompletionResult.withToolCalls(List.of(toolCall), LLMUsage.ZERO, MODEL));
        stubLLMPort.enqueue(ChatCompletionResult.textOnly("You have 3 strategies.", LLMUsage.ZERO, MODEL));

        when(chatToolExecutor.execute("list_strategies", "{}", USER_ID))
            .thenReturn("[{\"name\":\"BTC Momentum\"}]");

        List<ChatStreamEvent> events = collectEvents();

        assertThat(events).hasSize(4);
        assertThat(events.get(0)).isInstanceOf(ChatStreamEvent.ToolStart.class);
        assertThat(((ChatStreamEvent.ToolStart) events.get(0)).toolName()).isEqualTo("list_strategies");
        assertThat(events.get(1)).isInstanceOf(ChatStreamEvent.ToolResult.class);
        assertThat(events.get(2)).isInstanceOf(ChatStreamEvent.TextResponse.class);
        assertThat(((ChatStreamEvent.TextResponse) events.get(2)).content()).isEqualTo("You have 3 strategies.");
        assertThat(events.get(3)).isInstanceOf(ChatStreamEvent.Done.class);
        ChatStreamEvent.Done done = (ChatStreamEvent.Done) events.get(3);
        // Stub always returns suggestions when tools were used
        assertThat(done.suggestions()).isNotEmpty();
        assertThat(done.suggestions()).extracting(FollowUpSuggestion::label)
            .contains("Next Step");

        assertThat(stubLLMPort.callCount).isEqualTo(2);
        verify(chatSessionPort).save(session);
    }

    @Test
    void shouldHandleMultipleToolCallsInSequence() {
        ChatSession session = createSession();
        when(chatSessionPort.findById(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        ToolCall call1 = new ToolCall("call-1", "list_strategies", "{}");
        ToolCall call2 = new ToolCall("call-2", "get_simulation", "{\"simulationId\":\"abc\"}");

        stubLLMPort.enqueue(ChatCompletionResult.withToolCalls(List.of(call1), LLMUsage.ZERO, MODEL));
        stubLLMPort.enqueue(ChatCompletionResult.withToolCalls(List.of(call2), LLMUsage.ZERO, MODEL));
        stubLLMPort.enqueue(ChatCompletionResult.textOnly("Analysis complete.", LLMUsage.ZERO, MODEL));

        when(chatToolExecutor.execute(eq("list_strategies"), any(), eq(USER_ID)))
            .thenReturn("[{\"name\":\"BTC\"}]");
        when(chatToolExecutor.execute(eq("get_simulation"), any(), eq(USER_ID)))
            .thenReturn("{\"status\":\"COMPLETED\"}");

        List<ChatStreamEvent> events = collectEvents();

        assertThat(events).hasSize(6);
        assertThat(events.get(0)).isInstanceOf(ChatStreamEvent.ToolStart.class);
        assertThat(events.get(1)).isInstanceOf(ChatStreamEvent.ToolResult.class);
        assertThat(events.get(2)).isInstanceOf(ChatStreamEvent.ToolStart.class);
        assertThat(events.get(3)).isInstanceOf(ChatStreamEvent.ToolResult.class);
        assertThat(events.get(4)).isInstanceOf(ChatStreamEvent.TextResponse.class);
        assertThat(events.get(5)).isInstanceOf(ChatStreamEvent.Done.class);

        assertThat(stubLLMPort.callCount).isEqualTo(3);
    }

    @Test
    void shouldStopAfterMaxToolIterations() {
        ChatSession session = createSession();
        when(chatSessionPort.findById(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        // LLM always returns tool calls — should stop after 30 iterations
        ToolCall toolCall = new ToolCall("call-x", "list_strategies", "{}");
        ChatCompletionResult toolResult = ChatCompletionResult.withToolCalls(List.of(toolCall), LLMUsage.ZERO, MODEL);
        for (int i = 0; i < 31; i++) {
            stubLLMPort.enqueue(toolResult);
        }
        when(chatToolExecutor.execute(any(), any(), any())).thenReturn("[]");

        List<ChatStreamEvent> events = collectEvents();

        long toolStarts = events.stream().filter(e -> e instanceof ChatStreamEvent.ToolStart).count();
        assertThat(toolStarts).isEqualTo(30);

        ChatStreamEvent lastText = events.stream()
            .filter(e -> e instanceof ChatStreamEvent.TextResponse).findFirst().orElse(null);
        assertThat(lastText).isNotNull();

        assertThat(events.get(events.size() - 1)).isInstanceOf(ChatStreamEvent.Done.class);
        assertThat(stubLLMPort.callCount).isEqualTo(30);
        verify(chatSessionPort).save(session);
    }

    @Test
    void shouldEmitErrorWhenBudgetDepleted() {
        ChatSession session = createSession();
        when(chatSessionPort.findById(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        doThrow(new BudgetDepletedException(USER_ID))
            .when(recordLLMUsageUseCase).requireAvailableBudget(USER_ID);

        List<ChatStreamEvent> events = collectEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ChatStreamEvent.Error.class);
        assertThat(((ChatStreamEvent.Error) events.get(0)).message()).startsWith("BUDGET_DEPLETED:");

        assertThat(stubLLMPort.callCount).isEqualTo(0);
        verify(chatSessionPort, never()).save(any());
    }

    @Test
    void shouldEmitErrorWhenDailyLimitExceeded() {
        ChatSession session = createSession();
        when(chatSessionPort.findById(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        doThrow(new DailySpendLimitExceededException(USER_ID, new BigDecimal("5.00"), new BigDecimal("5.25")))
            .when(recordLLMUsageUseCase).requireAvailableBudget(USER_ID);

        List<ChatStreamEvent> events = collectEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ChatStreamEvent.Error.class);
        assertThat(((ChatStreamEvent.Error) events.get(0)).message()).startsWith("DAILY_LIMIT:");

        assertThat(stubLLMPort.callCount).isEqualTo(0);
        verify(chatSessionPort, never()).save(any());
    }

    @Test
    void shouldEmitErrorWhenSessionNotFound() {
        when(chatSessionPort.findById(SESSION_ID, USER_ID)).thenReturn(Optional.empty());

        List<ChatStreamEvent> events = collectEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ChatStreamEvent.Error.class);
        assertThat(((ChatStreamEvent.Error) events.get(0)).message()).contains("Chat session not found");
    }

    @Test
    void shouldRecordUsageAfterEachLLMCall() {
        ChatSession session = createSession();
        when(chatSessionPort.findById(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        LLMUsage usage = new LLMUsage(MODEL, 100, 50, 150, new BigDecimal("0.001"));
        stubLLMPort.enqueue(ChatCompletionResult.textOnly("Response", usage, MODEL));

        collectEvents();

        verify(recordLLMUsageUseCase).recordUsage(
            eq(USER_ID), any(BigDecimal.class), any(BigDecimal.class), eq(150), eq(UsageSource.CHAT));
    }

    private List<ChatStreamEvent> collectEvents() {
        return useCase.execute(SESSION_ID, USER_ID, "Hello")
            .collect().asList()
            .await().atMost(java.time.Duration.ofSeconds(5));
    }

    /**
     * Manual stub for ConversationalLLMPort that returns pre-enqueued results.
     */
    private static class StubConversationalLLMPort implements ConversationalLLMPort {
        private final Queue<ChatCompletionResult> results = new LinkedList<>();
        int callCount = 0;

        void enqueue(ChatCompletionResult result) {
            results.add(result);
        }

        @Override
        public ChatCompletionResult chatCompletion(List<ChatMessage> messages, List<ToolDefinition> tools, LLMConfig config) {
            callCount++;
            ChatCompletionResult result = results.poll();
            if (result == null) {
                throw new IllegalStateException("No more enqueued results for StubConversationalLLMPort (call #" + callCount + ")");
            }
            return result;
        }
    }

    /**
     * Stub for ChatTitleGenerationService that returns a fixed title.
     */
    private static class StubChatTitleGenerationService extends ChatTitleGenerationService {
        StubChatTitleGenerationService() {
            super(null);
        }

        @Override
        public String generate(String userMessage, String assistantResponse, UserId userId) {
            return "Test Session Title";
        }
    }

    /**
     * Stub for FollowUpSuggestionService that returns predictable suggestions.
     * Returns suggestions only when tools were used.
     */
    private static class StubFollowUpSuggestionService extends FollowUpSuggestionService {
        StubFollowUpSuggestionService() {
            super(null, null); // Not used since we override generate()
        }

        @Override
        public List<FollowUpSuggestion> generate(
                List<ChatMessage> recentMessages,
                String assistantResponse,
                Set<String> toolsUsed,
                Map<String, String> toolResults,
                UserId userId) {
            if (toolsUsed == null || toolsUsed.isEmpty()) {
                return Collections.emptyList();
            }
            return List.of(
                new FollowUpSuggestion("Next Step", "What should I do next?"),
                new FollowUpSuggestion("More Details", "Give me more details")
            );
        }
    }
}
