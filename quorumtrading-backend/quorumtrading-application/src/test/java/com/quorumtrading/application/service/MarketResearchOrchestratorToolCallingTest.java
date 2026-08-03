package com.quorumtrading.application.service;

import com.quorumtrading.application.chat.ChatToolExecutor;
import com.quorumtrading.application.chat.ResearchToolRegistry;
import com.quorumtrading.application.event.ResearchAgentCompletedEvent;
import com.quorumtrading.application.event.ResearchAgentStartedEvent;
import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.ConversationalLLMPort;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CreateStrategyUseCase;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.ChatMessage;
import com.quorumtrading.model.valueobject.ResearchOutcome;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.quorumtrading.application.service.TestConstants.TEST_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MarketResearchOrchestrator Tool-Calling Tests")
class MarketResearchOrchestratorToolCallingTest {

    private LLMPort llmPort;
    private ConversationalLLMPort conversationalLLMPort;
    private ChatToolExecutor chatToolExecutor;
    private StrategyRepository strategyRepository;
    private ResearchExecutionRepository executionRepository;
    private EventPublisher<ResearchExecutionCompleted> eventPublisher;
    private MarketResearchOrchestrator orchestrator;
    private MarketResearch testResearch;

    @BeforeEach
    void setUp() {
        llmPort = mock(LLMPort.class);
        conversationalLLMPort = mock(ConversationalLLMPort.class);
        // Tool calling is only taken when the provider is actually usable — a mock reports false
        // by default, which is the keyless-install case covered by the fallback tests below.
        when(conversationalLLMPort.isAvailable()).thenReturn(true);
        chatToolExecutor = mock(ChatToolExecutor.class);
        strategyRepository = mock(StrategyRepository.class);
        executionRepository = mock(ResearchExecutionRepository.class);
        eventPublisher = mock(EventPublisher.class);

        orchestrator = new MarketResearchOrchestrator(
            llmPort,
            strategyRepository,
            executionRepository,
            eventPublisher,
            (EventPublisher<ResearchAgentStartedEvent>) null,
            (EventPublisher<ResearchAgentCompletedEvent>) null,
            "openai/gpt-4",
            "openai/gpt-4:online",
            0.7,
            2000,
            (StrategyTemplateRegistry) null,
            false, // indicatorsEnabled
            60,    // templateConfidenceThreshold
            (CreateStrategyUseCase) null,
            conversationalLLMPort,
            chatToolExecutor,
            Runnable::run, // synchronous executor for tests
            null // marketEventRepository
        );

        testResearch = new MarketResearch(
            "Daily BTC Research",
            "# Research Instructions\nFind BTC trading opportunities",
            Set.of(new Symbol("BTC", InvestmentType.CRYPTO)),
            new ResearchInterval(1, ResearchInterval.Unit.DAYS),
            true,
            new BacktestConfiguration(
                Duration.ofDays(30),
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-31T00:00:00Z")
            ),
            TEST_USER_ID
        );
    }

    @Test
    @DisplayName("Strategy Designer should use tool calling when conversationalLLMPort is available")
    void strategyDesignerShouldUseToolCalling() {
        // Given: News analyst uses plain LLM (queryAgentWithUsage → llmPort)
        String newsAnalysis = "BTC shows bullish momentum with strong ETF inflows";
        when(llmPort.queryWithUsage(contains("News & Sentiment Analyst"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(newsAnalysis, "openai/gpt-4:online"));

        // Strategy Designer tool call and text responses
        ChatCompletionResult toolCallResult = ChatCompletionResult.withToolCalls(
            List.of(new ToolCall("call_1", "get_indicators", "{\"ticker\":\"BTC\",\"investmentType\":\"CRYPTO\"}")),
            LLMUsage.ZERO, "openai/gpt-4"
        );

        String strategyDesign = """
            ---
            version: v1.0
            consensus_method: MAJORITY_VOTE
            consensus_threshold: 0.66
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            backtest_schedule:
              interval: 1w
            position_limits:
              BTC: 0.8
            reserve_cash_percentage: 0.2
            ---
            # BTC Momentum Breakout
            ## Entry Rules
            - RSI below 40 with MACD bullish crossover
            ## Exit Rules
            - Take profit +10%, Stop loss -5%
            ## Risk Management
            - Max loss 5%
            """;
        ChatCompletionResult strategyTextResult = ChatCompletionResult.textOnly(
            strategyDesign, LLMUsage.ZERO, "openai/gpt-4"
        );

        // Viability assessor returns high score
        String viabilityAssessment = """
            {
              "viabilityScore": 80,
              "recommendation": "APPROVE",
              "currentMarketConditions": "BTC at $43,200, RSI 35.2 (oversold zone)",
              "assetAccessibility": "BTC tradable on major exchanges",
              "opportunityQuality": "Strong bullish momentum",
              "riskAssessment": "Acceptable risk with proper sizing",
              "timingAnalysis": "Good timing, RSI suggests oversold bounce",
              "recommendedTickers": "BTC",
              "majorConcerns": "None",
              "detailedReasoning": "Strong viability based on actual indicator data"
            }
            """;
        ChatCompletionResult viabilityTextResult = ChatCompletionResult.textOnly(
            viabilityAssessment, LLMUsage.ZERO, "openai/gpt-4:online"
        );

        // Tool executor returns indicator data
        when(chatToolExecutor.execute(eq("get_indicators"), anyString(), any(UserId.class)))
            .thenReturn("{\"RSI_14\":35.2,\"SMA_50\":42500,\"SMA_200\":38000,\"latestClose\":43200}");

        // New flow: Viability + News in parallel, then Strategy.
        // Dispatch based on system prompt content to handle non-deterministic parallel ordering.
        AtomicInteger strategyCallCount = new AtomicInteger(0);
        when(conversationalLLMPort.chatCompletion(anyList(), anyList(), any(LLMConfig.class)))
            .thenAnswer(invocation -> {
                List<ChatMessage> messages = invocation.getArgument(0);
                String systemPrompt = messages.get(0).content();
                if (systemPrompt.contains("Market Viability Assessor") || systemPrompt.contains("viability assessor")) {
                    return viabilityTextResult;
                } else {
                    // Strategy designer: first call returns tool call, second returns text
                    int count = strategyCallCount.getAndIncrement();
                    if (count == 0) return toolCallResult;
                    return strategyTextResult;
                }
            });

        // Coordinator uses plain LLM (no tool calling)
        String coordinatorDecision = """
            {
              "decision": "NO_OPPORTUNITY",
              "reasoning": "Testing tool calling integration",
              "viabilityScore": 80
            }
            """;
        when(llmPort.queryWithUsage(contains("Market Research Coordinator"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(coordinatorDecision, "openai/gpt-4"));

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResearchExecution result = orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then
        assertThat(result).isNotNull();

        // Verify ConversationalLLMPort was called (for viability + strategy)
        verify(conversationalLLMPort, atLeast(2)).chatCompletion(anyList(), anyList(), any(LLMConfig.class));

        // Verify tool was executed (strategy designer called get_indicators)
        verify(chatToolExecutor).execute(eq("get_indicators"), anyString(), eq(TEST_USER_ID));

        // Verify plain LLM was used for news analyst and coordinator
        verify(llmPort, times(2)).queryWithUsage(anyString(), any(LLMConfig.class));
    }

    @Test
    @DisplayName("Should fall back to plain text when tool calling hits max iterations")
    void shouldFallBackWhenMaxIterationsReached() {
        // Given: News analyst uses plain LLM
        when(llmPort.queryWithUsage(contains("News & Sentiment Analyst"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage("News analysis", "openai/gpt-4:online"));

        // Viability must pass (score ≥70) for strategy designer to be invoked
        String viabilityAssessment = """
            {
              "viabilityScore": 80,
              "recommendation": "APPROVE",
              "currentMarketConditions": "Test",
              "assetAccessibility": "Tradable",
              "opportunityQuality": "Good",
              "riskAssessment": "Test",
              "timingAnalysis": "Test",
              "recommendedTickers": "BTC",
              "majorConcerns": "None",
              "detailedReasoning": "Test"
            }
            """;
        ChatCompletionResult viabilityResult = ChatCompletionResult.textOnly(
            viabilityAssessment, LLMUsage.ZERO, "openai/gpt-4:online"
        );

        // Strategy Designer: keeps requesting tools forever (simulates runaway)
        ChatCompletionResult toolCallResult = ChatCompletionResult.withToolCalls(
            List.of(new ToolCall("call_runaway", "get_indicators", "{\"ticker\":\"BTC\",\"investmentType\":\"CRYPTO\"}")),
            LLMUsage.ZERO, "openai/gpt-4"
        );

        String finalResponse = """
            ---
            version: v1.0
            consensus_method: MAJORITY_VOTE
            consensus_threshold: 0.66
            crypto_tickers:
              - BTC
            live_schedule:
              interval: 1h
            backtest_schedule:
              interval: 1w
            position_limits:
              BTC: 0.8
            reserve_cash_percentage: 0.2
            ---
            # BTC Strategy
            ## Entry Rules
            - RSI < 30
            ## Exit Rules
            - Take profit +10%
            ## Risk Management
            - Max loss 5%
            """;
        ChatCompletionResult forcedTextResult = ChatCompletionResult.textOnly(
            finalResponse, LLMUsage.ZERO, "openai/gpt-4"
        );

        // Dispatch based on prompt content to handle parallel call ordering
        AtomicInteger strategyCallCount = new AtomicInteger(0);
        when(conversationalLLMPort.chatCompletion(anyList(), anyList(), any(LLMConfig.class)))
            .thenAnswer(invocation -> {
                List<ChatMessage> messages = invocation.getArgument(0);
                String systemPrompt = messages.get(0).content();
                if (systemPrompt.contains("Market Viability Assessor") || systemPrompt.contains("viability assessor")) {
                    return viabilityResult;
                } else {
                    // Strategy designer: 10 tool call iterations then forced text
                    int count = strategyCallCount.getAndIncrement();
                    if (count < 10) return toolCallResult;
                    return forcedTextResult;
                }
            });

        when(chatToolExecutor.execute(anyString(), anyString(), any(UserId.class)))
            .thenReturn("{\"RSI_14\":42}");

        String coordinatorDecision = """
            {
              "decision": "NO_OPPORTUNITY",
              "reasoning": "Test",
              "viabilityScore": 80
            }
            """;
        when(llmPort.queryWithUsage(contains("Market Research Coordinator"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(coordinatorDecision, "openai/gpt-4"));

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResearchExecution result = orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then
        assertThat(result).isNotNull();

        // Verify tool executor was called 10 times (max iterations) for strategy designer
        verify(chatToolExecutor, atLeast(10)).execute(anyString(), anyString(), any(UserId.class));
    }

    @Test
    @DisplayName("Should gracefully handle viability assessor returning prose instead of JSON")
    void shouldHandleViabilityProseGracefully() {
        // Given: News analyst returns analysis
        when(llmPort.queryWithUsage(contains("News & Sentiment Analyst"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage("BTC news analysis", "openai/gpt-4:online"));

        // Viability assessor returns PROSE (not JSON) — tools returned no data and LLM apologized
        String viabilityProse = "I apologize, but I'm unable to retrieve the indicator data for BTC. " +
            "This is likely because the data hasn't been backfilled.";
        ChatCompletionResult viabilityProseResult = ChatCompletionResult.textOnly(
            viabilityProse, LLMUsage.ZERO, "openai/gpt-4:online"
        );

        // Viability is the only agent calling conversationalLLMPort (prose → score 0 → short-circuit)
        when(conversationalLLMPort.chatCompletion(anyList(), anyList(), any(LLMConfig.class)))
            .thenReturn(viabilityProseResult);

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResearchExecution result = orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then: viability prose → score 0 → short-circuit to NO_OPPORTUNITY (no strategy/coordinator)
        assertThat(result).isNotNull();
        assertThat(result.getOutcome()).isEqualTo(ResearchOutcome.NO_OPPORTUNITY);

        // Verify strategy designer and coordinator were never called
        verify(llmPort, never()).queryWithUsage(contains("Market Research Coordinator"), any(LLMConfig.class));
    }

    @Test
    @DisplayName("Should use the selected engine, not tool calling, when the conversational provider has no credentials")
    void shouldFallBackToPlainTextWhenConversationalProviderIsUnavailable() {
        // The conversational adapter is a CDI bean whether or not a key is configured. On a
        // keyless self-host install — the documented default, where the user picked a local
        // engine — every research agent still went down the tool-calling path and got HTTP 401,
        // which the caller recorded as viability score 0 / REJECT. Research could therefore never
        // produce a strategy, and the failure was indistinguishable from a genuine market verdict.
        when(conversationalLLMPort.isAvailable()).thenReturn(false);

        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(
                "{\"viabilityScore\": 85, \"recommendation\": \"PROCEED\", \"reasoning\": \"ok\"}",
                "local/claude"));
        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ResearchExecution result = orchestrator.executeResearch(testResearch, UUID.randomUUID());

        assertThat(result).isNotNull();
        verify(conversationalLLMPort, never()).chatCompletion(anyList(), anyList(), any(LLMConfig.class));
        verify(llmPort, atLeastOnce()).queryWithUsage(anyString(), any(LLMConfig.class));
    }

    @Test
    @DisplayName("ResearchToolRegistry should return curated subset of tools")
    void researchToolRegistryShouldReturnCuratedTools() {
        List<ToolDefinition> tools = ResearchToolRegistry.researchTools();

        assertThat(tools).isNotEmpty();

        List<String> toolNames = tools.stream()
            .map(ToolDefinition::name)
            .toList();

        // Should include data-reading tools
        assertThat(toolNames).contains(
            "get_indicators",
            "get_ticker_performance",
            "query_market_events",
            "find_ticker",
            "list_historical_events",
            "get_strategy_performance",
            "list_strategies"
        );

        // Should NOT include mutating tools
        assertThat(toolNames).doesNotContain(
            "create_strategy",
            "create_simulation",
            "delete_strategy",
            "delete_simulation",
            "create_market_research",
            "sell_position",
            "liquidate_all_positions"
        );
    }
}
