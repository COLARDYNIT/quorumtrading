package com.quorumtrading.application.service;

import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static com.quorumtrading.application.service.TestConstants.TEST_USER_ID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MarketResearchOrchestrator.
 *
 * New agent flow:
 * 1. News Analyst + Viability Assessor run in PARALLEL
 * 2. If viability >= 70 → Strategy Designer runs
 * 3. If viability >= 70 → Coordinator decides CREATE_STRATEGY or NO_OPPORTUNITY
 * 4. If viability < 70 → short-circuit to NO_OPPORTUNITY (no strategy design)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarketResearchOrchestrator Tests")
class MarketResearchOrchestratorTest {

    @Mock
    private LLMPort llmPort;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private ResearchExecutionRepository executionRepository;

    @Mock
    private EventPublisher<ResearchExecutionCompleted> eventPublisher;

    @Captor
    private ArgumentCaptor<ResearchExecution> executionCaptor;

    @Captor
    private ArgumentCaptor<ResearchExecutionCompleted> eventCaptor;

    private MarketResearchOrchestrator orchestrator;

    private MarketResearch testResearch;

    // Valid strategy markdown with YAML frontmatter (used by strategy designer)
    private static final String VALID_STRATEGY_MARKDOWN = """
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

        # BTC Momentum Strategy

        ## Entry Rules
        - RSI-14 drops below 30
        - Price is above 200-day SMA

        ## Exit Rules
        - Take profit: +8% from entry
        - Stop loss: -5% from entry
        """;

    private static final String HIGH_VIABILITY = """
        {
          "viabilityScore": 85,
          "recommendation": "APPROVE",
          "currentMarketConditions": "BTC trading at $44,500, moderate volatility",
          "assetAccessibility": "BTC fully tradable on Alpaca and Coinbase",
          "opportunityQuality": "Strong bullish signal from ETF approval catalyst",
          "riskAssessment": "Moderate risk, acceptable for trading",
          "timingAnalysis": "Good time to execute",
          "recommendedTickers": "BTC",
          "majorConcerns": "None",
          "detailedReasoning": "Strong market opportunity"
        }
        """;

    private static final String LOW_VIABILITY = """
        {
          "viabilityScore": 45,
          "recommendation": "REJECT",
          "currentMarketConditions": "Market conditions unclear",
          "assetAccessibility": "Assets tradable",
          "opportunityQuality": "No clear catalyst",
          "riskAssessment": "Risk level too high",
          "timingAnalysis": "Poor timing",
          "recommendedTickers": "None",
          "majorConcerns": "No clear opportunity identified",
          "detailedReasoning": "Market conditions do not support a trading strategy"
        }
        """;

    @BeforeEach
    void setUp() {
        orchestrator = new MarketResearchOrchestrator(
            llmPort,
            strategyRepository,
            executionRepository,
            eventPublisher,
            null, // agentStartedPublisher
            null, // agentCompletedPublisher
            "openai/gpt-4",
            "openai/gpt-4:online",
            0.7,
            2000,
            null,  // templateRegistry
            false, // indicatorsEnabled
            60,    // templateConfidenceThreshold
            null,  // createStrategyUseCase
            null,  // conversationalLLMPort
            null,  // chatToolExecutor
            null,  // agentExecutor
            null   // marketEventRepository
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
    @DisplayName("Should create strategy when viability is high and coordinator approves")
    void shouldCreateStrategyWhenViableAndCoordinatorApproves() {
        // Given: news + viability (parallel), then strategy designer, then coordinator
        String newsAnalysis = "Bullish news for BTC";
        String coordinatorDecision = """
            {
              "decision": "CREATE_STRATEGY",
              "reasoning": "All agents agree. Strong opportunity.",
              "viabilityScore": 85
            }
            """;

        // News Analyst and Viability Assessor run in parallel, then Strategy Designer, then Coordinator
        when(llmPort.queryWithUsage(contains("News & Sentiment Analyst"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(newsAnalysis, "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Market Viability Assessor"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(HIGH_VIABILITY, "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Strategy Designer"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(VALID_STRATEGY_MARKDOWN, "openai/gpt-4"));
        when(llmPort.queryWithUsage(contains("Market Research Coordinator"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(coordinatorDecision, "openai/gpt-4"));

        Strategy savedStrategy = mock(Strategy.class);
        when(savedStrategy.getId()).thenReturn(new StrategyId(UUID.randomUUID()));
        when(strategyRepository.save(any(Strategy.class))).thenReturn(savedStrategy);

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResearchExecution result = orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then
        verify(executionRepository).save(executionCaptor.capture());
        ResearchExecution captured = executionCaptor.getValue();
        assertThat(captured.getOutcome()).isEqualTo(ResearchOutcome.STRATEGY_CREATED);
        assertThat(captured.getViabilityScore().value())
            .isEqualByComparingTo(java.math.BigDecimal.valueOf(85));
        assertThat(captured.getStrategyId()).isNotNull();

        // Strategy was created
        verify(strategyRepository).save(any(Strategy.class));
        // Event published
        verify(eventPublisher).publish(any(ResearchExecutionCompleted.class));
    }

    @Test
    @DisplayName("Should short-circuit to NO_OPPORTUNITY when viability is low — no strategy design")
    void shouldShortCircuitWhenViabilityIsLow() {
        // Given: news + viability run, viability is low (45)
        String newsAnalysis = "Market conditions unclear";

        when(llmPort.queryWithUsage(contains("News & Sentiment Analyst"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(newsAnalysis, "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Market Viability Assessor"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(LOW_VIABILITY, "openai/gpt-4:online"));

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResearchExecution result = orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then: NO_OPPORTUNITY, no strategy designer or coordinator called
        verify(executionRepository).save(executionCaptor.capture());
        ResearchExecution captured = executionCaptor.getValue();
        assertThat(captured.getOutcome()).isEqualTo(ResearchOutcome.NO_OPPORTUNITY);
        assertThat(captured.getViabilityScore().value())
            .isEqualByComparingTo(java.math.BigDecimal.valueOf(45));
        assertThat(captured.getStrategyId()).isNull();
        assertThat(captured.getResearcherConclusion()).contains("below the threshold");

        // Only 2 agent decisions (news + viability), NOT 3
        assertThat(captured.getAgentDecisions()).hasSize(2);
        assertThat(captured.getAgentDecisions())
            .extracting(d -> d.getAgentType())
            .containsExactlyInAnyOrder(
                ResearchAgentType.NEWS_SENTIMENT_ANALYST,
                ResearchAgentType.VIABILITY_ASSESSOR
            );

        // Strategy designer and coordinator were NOT called
        verify(llmPort, never()).queryWithUsage(contains("Strategy Designer"), any(LLMConfig.class));
        verify(llmPort, never()).queryWithUsage(contains("Market Research Coordinator"), any(LLMConfig.class));
        verify(strategyRepository, never()).save(any(Strategy.class));
    }

    @Test
    @DisplayName("Should save all three agent decisions when viability is high")
    void shouldSaveAllAgentDecisionsWhenViable() {
        // Given
        String newsAnalysis = "News analysis result";
        String coordinatorDecision = """
            {
              "decision": "NO_OPPORTUNITY",
              "reasoning": "Strategy not well-formed",
              "viabilityScore": 85
            }
            """;

        when(llmPort.queryWithUsage(contains("News & Sentiment Analyst"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(newsAnalysis, "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Market Viability Assessor"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(HIGH_VIABILITY, "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Strategy Designer"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(VALID_STRATEGY_MARKDOWN, "openai/gpt-4"));
        when(llmPort.queryWithUsage(contains("Market Research Coordinator"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(coordinatorDecision, "openai/gpt-4"));

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then: all 3 agent decisions saved
        verify(executionRepository).save(executionCaptor.capture());
        ResearchExecution captured = executionCaptor.getValue();
        assertThat(captured.getAgentDecisions()).hasSize(3);
        assertThat(captured.getAgentDecisions())
            .extracting(d -> d.getAgentType())
            .containsExactlyInAnyOrder(
                ResearchAgentType.NEWS_SENTIMENT_ANALYST,
                ResearchAgentType.STRATEGY_DESIGNER,
                ResearchAgentType.VIABILITY_ASSESSOR
            );
    }

    @Test
    @DisplayName("Should persist agent decisions on failed execution")
    void shouldPersistAgentDecisionsOnFailedExecution() {
        // Given: news + viability succeed, strategy fails, coordinator returns bad JSON
        String newsAnalysis = "News analysis from analyst";
        String coordinatorDecision = "This is not JSON at all";

        when(llmPort.queryWithUsage(contains("News & Sentiment Analyst"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(newsAnalysis, "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Market Viability Assessor"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(HIGH_VIABILITY, "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Strategy Designer"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(VALID_STRATEGY_MARKDOWN, "openai/gpt-4"));
        when(llmPort.queryWithUsage(contains("Market Research Coordinator"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(coordinatorDecision, "openai/gpt-4"));

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResearchExecution result = orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then: FAILED but all 3 agent decisions preserved
        verify(executionRepository).save(executionCaptor.capture());
        ResearchExecution captured = executionCaptor.getValue();
        assertThat(captured.getOutcome()).isEqualTo(ResearchOutcome.FAILED);
        assertThat(captured.getAgentDecisions()).hasSize(3);
    }

    @Test
    @DisplayName("Should handle LLM failure on first call gracefully — degrades to NO_OPPORTUNITY")
    void shouldHandleFirstAgentFailureGracefully() {
        // Given: LLM throws on first call (both news and viability will fail in parallel)
        // With per-agent error handling, both futures use .exceptionally() fallbacks:
        //   - news returns "News analysis unavailable due to agent failure."
        //   - viability returns score=0 / REJECT
        // The viability gate then rejects (score < 70) → NO_OPPORTUNITY
        when(llmPort.queryWithUsage(anyString(), any(LLMConfig.class)))
            .thenThrow(new RuntimeException("LLM service unavailable"));

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResearchExecution result = orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then: graceful degradation — not FAILED, but NO_OPPORTUNITY
        verify(executionRepository).save(executionCaptor.capture());
        ResearchExecution captured = executionCaptor.getValue();
        assertThat(captured.getOutcome()).isEqualTo(ResearchOutcome.NO_OPPORTUNITY);
        verify(eventPublisher).publish(any(ResearchExecutionCompleted.class));
    }

    @Test
    @DisplayName("Should publish event with auto-backtest flag from research configuration")
    void shouldPublishEventWithAutoBacktestFlag() {
        // Given: low viability for simple short-circuit test
        String newsAnalysis = "News";

        when(llmPort.queryWithUsage(contains("News & Sentiment Analyst"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(newsAnalysis, "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Market Viability Assessor"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(LOW_VIABILITY, "openai/gpt-4:online"));

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then
        verify(eventPublisher).publish(eventCaptor.capture());
        ResearchExecutionCompleted event = eventCaptor.getValue();
        assertThat(event.autoBacktestEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should accept strategy with live_schedule format")
    void shouldAcceptStrategyWithLiveSchedule() {
        // Given: strategy uses live_schedule (tested via the VALID_STRATEGY_MARKDOWN constant)
        String newsAnalysis = "Bullish news";
        String coordinatorDecision = """
            {
              "decision": "CREATE_STRATEGY",
              "reasoning": "Good opportunity",
              "viabilityScore": 85
            }
            """;

        when(llmPort.queryWithUsage(contains("News & Sentiment Analyst"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(newsAnalysis, "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Market Viability Assessor"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(HIGH_VIABILITY, "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Strategy Designer"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(VALID_STRATEGY_MARKDOWN, "openai/gpt-4"));
        when(llmPort.queryWithUsage(contains("Market Research Coordinator"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(coordinatorDecision, "openai/gpt-4"));

        Strategy savedStrategy = mock(Strategy.class);
        when(savedStrategy.getId()).thenReturn(new StrategyId(UUID.randomUUID()));
        when(strategyRepository.save(any(Strategy.class))).thenReturn(savedStrategy);

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ResearchExecution result = orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then: STRATEGY_CREATED — live_schedule passes validation
        verify(executionRepository).save(executionCaptor.capture());
        assertThat(executionCaptor.getValue().getOutcome()).isEqualTo(ResearchOutcome.STRATEGY_CREATED);
    }

    @Test
    @DisplayName("Should use web search for both News Analyst and Viability Assessor")
    void shouldUseWebSearchForParallelAgents() {
        // Given
        when(llmPort.queryWithUsage(contains("News & Sentiment Analyst"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage("News", "openai/gpt-4:online"));
        when(llmPort.queryWithUsage(contains("Market Viability Assessor"), any(LLMConfig.class)))
            .thenReturn(LLMQueryResult.withoutUsage(LOW_VIABILITY, "openai/gpt-4:online"));

        when(executionRepository.save(any(ResearchExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<LLMConfig> configCaptor = ArgumentCaptor.forClass(LLMConfig.class);

        // When
        orchestrator.executeResearch(testResearch, UUID.randomUUID());

        // Then: both parallel agents should use :online suffix
        verify(llmPort, atLeast(2)).queryWithUsage(anyString(), configCaptor.capture());
        // All calls in this scenario should be :online (news + viability)
        configCaptor.getAllValues().forEach(config ->
            assertThat(config.model()).endsWith(":online")
        );
    }

    // ---------- Strategy naming convention ----------

    @Test
    void shouldEnrichGenericTechniqueNameWithLeadTickers() {
        String markdown = """
            ---
            version: v1.0
            consensus_method: MAJORITY_VOTE
            stock_tickers:
              - BAESY
              - GD
              - LMT
            ---

            # Moving Average Crossover Strategy

            ## Introduction
            Defense names on a moving-average crossover.
            """;

        String name = orchestrator.extractStrategyName(markdown);

        // Generic H1 must be made distinctive by prefixing the lead tickers.
        assertThat(name).startsWith("BAESY/GD/LMT Moving Average Crossover Strategy");
    }

    @Test
    void shouldNotEnrichDistinctiveH1Name() {
        String markdown = """
            ---
            version: v1.0
            stock_tickers:
              - LMT
              - NOC
            ---

            # Defense Primes — Budget Surge Momentum

            ## Introduction
            Distinctive thesis name; leave it alone.
            """;

        String name = orchestrator.extractStrategyName(markdown);

        assertThat(name).startsWith("Defense Primes — Budget Surge Momentum");
        assertThat(name).doesNotContain("LMT/NOC");
    }

    @Test
    void shouldNeverOverrideExplicitFrontmatterName() {
        // An explicit frontmatter name: wins even when it is generic — author intent is respected.
        String markdown = """
            ---
            version: v1.0
            name: Moving Average Crossover Strategy
            stock_tickers:
              - AAPL
              - MSFT
            ---

            # Moving Average Crossover Strategy

            ## Introduction
            Has an explicit frontmatter name.
            """;

        String name = orchestrator.extractStrategyName(markdown);

        assertThat(name).startsWith("Moving Average Crossover Strategy");
        assertThat(name).doesNotContain("AAPL/MSFT");
    }

    @Test
    void shouldEnrichGenericNameFromInlineTickerArray() {
        String markdown = """
            ---
            version: v1.0
            crypto_tickers: [BTC, ETH]
            ---

            # RSI Divergence

            ## Introduction
            Crypto RSI divergence.
            """;

        String name = orchestrator.extractStrategyName(markdown);

        assertThat(name).startsWith("BTC/ETH RSI Divergence");
    }
}
