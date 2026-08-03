package com.quorumtrading.application.chat;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.port.out.ChatSessionPort;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.application.usecase.UpdateModelPreferencesUseCase;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.model.valueobject.ChatCompletionResult;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.FollowUpSuggestion;
import com.quorumtrading.model.valueobject.ToolDefinition;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateChatSessionUseCaseTest {

    @Mock
    private ChatSessionPort chatSessionPort;

    @Mock
    private SystemPromptBuilder systemPromptBuilder;

    @Mock
    private FollowUpSuggestionService followUpSuggestionService;

    @Mock
    private UpdateModelPreferencesUseCase updateModelPreferencesUseCase;

    @Mock
    private RecordLLMUsageUseCase recordLLMUsageUseCase;

    private CreateChatSessionUseCase useCase;

    private static final String DEFAULT_MODEL = "google/gemini-2.0-flash-001";
    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    private StubConversationalLLMPort stubLLMPort;

    @BeforeEach
    void setUp() {
        stubLLMPort = new StubConversationalLLMPort();
        when(updateModelPreferencesUseCase.getPreferences(any())).thenReturn(ModelPreferences.EMPTY);
        lenient().when(followUpSuggestionService.generate(any(), any(), any(), any(), any()))
            .thenReturn(List.of(new FollowUpSuggestion("Test", "Test message")));
        when(recordLLMUsageUseCase.hasBudgetAvailable(any())).thenReturn(true);
        useCase = new CreateChatSessionUseCase(
            chatSessionPort, stubLLMPort, systemPromptBuilder, followUpSuggestionService,
            DEFAULT_MODEL, updateModelPreferencesUseCase, recordLLMUsageUseCase);
    }

    @Test
    void shouldCreateSessionWithGeneratedGreeting() {
        when(systemPromptBuilder.build(USER_ID)).thenReturn("System prompt\n## User Context\nStrategies: 2");
        stubLLMPort.result = ChatCompletionResult.textOnly(
            "Welcome back. You have 2 active simulations.", LLMUsage.ZERO, DEFAULT_MODEL);

        CreateChatSessionUseCase.Result result = useCase.execute(USER_ID);

        assertThat(result.sessionId()).isNotNull();
        assertThat(result.greeting()).isEqualTo("Welcome back. You have 2 active simulations.");
        assertThat(result.model()).isEqualTo(DEFAULT_MODEL);
        assertThat(result.suggestions()).hasSize(1);
        assertThat(result.suggestions().get(0).label()).isEqualTo("Test");

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionPort).save(sessionCaptor.capture());

        ChatSession saved = sessionCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getModel()).isEqualTo(DEFAULT_MODEL);
        assertThat(saved.getMessages()).hasSize(1);
        assertThat(saved.getMessages().get(0).content()).isEqualTo("Welcome back. You have 2 active simulations.");
    }

    @Test
    void shouldUseFallbackGreetingWhenLLMFails() {
        when(systemPromptBuilder.build(USER_ID)).thenReturn("System prompt");
        stubLLMPort.shouldThrow = new RuntimeException("LLM unavailable");

        CreateChatSessionUseCase.Result result = useCase.execute(USER_ID);

        assertThat(result.sessionId()).isNotNull();
        assertThat(result.greeting()).isEqualTo("Welcome to QuorumTrading. How can I help?");
        assertThat(result.model()).isEqualTo(DEFAULT_MODEL);

        verify(chatSessionPort).save(any(ChatSession.class));
    }

    @Test
    void shouldUseFallbackGreetingWhenLLMReturnsNull() {
        when(systemPromptBuilder.build(USER_ID)).thenReturn("System prompt");
        stubLLMPort.result = ChatCompletionResult.textOnly(null, LLMUsage.ZERO, DEFAULT_MODEL);

        CreateChatSessionUseCase.Result result = useCase.execute(USER_ID);

        assertThat(result.greeting()).isEqualTo("Welcome to QuorumTrading. How can I help?");
    }

    @Test
    void shouldUseUserChatPreferenceWhenSet() {
        String preferredModel = "anthropic/claude-sonnet-4";
        when(systemPromptBuilder.build(USER_ID)).thenReturn("System prompt");
        when(updateModelPreferencesUseCase.getPreferences(USER_ID))
            .thenReturn(ModelPreferences.ofContextMap(Map.of(TradingContext.CHAT, ModelId.of(preferredModel))));
        stubLLMPort.result = ChatCompletionResult.textOnly(
            "Welcome back.", LLMUsage.ZERO, preferredModel);

        CreateChatSessionUseCase.Result result = useCase.execute(USER_ID);

        assertThat(result.model()).isEqualTo(preferredModel);

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionPort).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getModel()).isEqualTo(preferredModel);
    }

    @Test
    void shouldFallBackToDefaultModelWhenNoPreferenceSet() {
        when(systemPromptBuilder.build(USER_ID)).thenReturn("System prompt");
        when(updateModelPreferencesUseCase.getPreferences(USER_ID))
            .thenReturn(ModelPreferences.EMPTY);
        stubLLMPort.result = ChatCompletionResult.textOnly(
            "Welcome.", LLMUsage.ZERO, DEFAULT_MODEL);

        CreateChatSessionUseCase.Result result = useCase.execute(USER_ID);

        assertThat(result.model()).isEqualTo(DEFAULT_MODEL);
    }

    @Test
    void shouldFallBackToDefaultModelWhenPreferenceLookupFails() {
        when(systemPromptBuilder.build(USER_ID)).thenReturn("System prompt");
        when(updateModelPreferencesUseCase.getPreferences(USER_ID))
            .thenThrow(new RuntimeException("DB unavailable"));
        stubLLMPort.result = ChatCompletionResult.textOnly(
            "Welcome.", LLMUsage.ZERO, DEFAULT_MODEL);

        CreateChatSessionUseCase.Result result = useCase.execute(USER_ID);

        assertThat(result.model()).isEqualTo(DEFAULT_MODEL);
    }

    @Test
    void shouldUseStaticGreetingAndSuggestionsForNewUser() {
        when(systemPromptBuilder.build(USER_ID)).thenReturn("System prompt");
        when(systemPromptBuilder.hasNoUserData(USER_ID)).thenReturn(true);

        CreateChatSessionUseCase.Result result = useCase.execute(USER_ID);

        assertThat(result.greeting()).startsWith("Welcome to QuorumTrading. You have no strategies yet.");
        assertThat(result.suggestions()).hasSize(3);
        assertThat(result.suggestions().get(0).label()).isEqualTo("Explore Indicators");
        // No LLM calls for new users
        assertThat(stubLLMPort.callCount).isZero();
        verify(followUpSuggestionService, never()).generate(any(), any(), any(), any(), any());
        verify(chatSessionPort).save(any(ChatSession.class));
    }

    @Test
    void shouldUseStaticGreetingWhenBudgetDepleted() {
        when(systemPromptBuilder.build(USER_ID)).thenReturn("System prompt");
        when(recordLLMUsageUseCase.hasBudgetAvailable(USER_ID)).thenReturn(false);

        CreateChatSessionUseCase.Result result = useCase.execute(USER_ID);

        assertThat(result.greeting()).isEqualTo("Welcome to QuorumTrading. How can I help?");
        assertThat(result.suggestions()).isEmpty();
        // LLM should NOT be called when budget is depleted
        assertThat(stubLLMPort.callCount).isZero();
        verify(chatSessionPort).save(any(ChatSession.class));
    }

    /**
     * Manual stub for ConversationalLLMPort — Mockito inline mock maker
     * can't mock this interface on GraalVM JDK 25.
     */
    private static class StubConversationalLLMPort implements ConversationalLLMPort {
        ChatCompletionResult result;
        RuntimeException shouldThrow;
        int callCount;

        @Override
        public ChatCompletionResult chatCompletion(List<ChatMessage> messages, List<ToolDefinition> tools, LLMConfig config) {
            callCount++;
            if (shouldThrow != null) {
                throw shouldThrow;
            }
            return result;
        }
    }
}
