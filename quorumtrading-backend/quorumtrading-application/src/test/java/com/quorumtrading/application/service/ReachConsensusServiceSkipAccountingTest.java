package com.quorumtrading.application.service;

import com.quorumtrading.application.event.ConsensusStartedEvent;
import com.quorumtrading.application.orchestration.StrategyParser;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.service.consensus.AgentInvoker;
import com.quorumtrading.application.service.consensus.ConsensusContextBuilder;
import com.quorumtrading.application.service.consensus.ConsensusOutcomeRecorder;
import com.quorumtrading.application.service.consensus.ConsensusTradeCoordinator;
import com.quorumtrading.application.service.consensus.DataGapCircuitBreaker;
import com.quorumtrading.application.usecase.budget.RecordLLMUsageUseCase;
import com.quorumtrading.application.usecase.command.ReachConsensusCommand;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the round-level success/failure accounting of {@link ReachConsensusService}.
 *
 * <p>The service used to call {@code circuitBreaker.recordSuccess} at the end of every round
 * that did not throw — including rounds where every ticker was skipped because the LLM engine
 * failed or timed out. Since recordSuccess resets the consecutive-skip counter, an engine that
 * answered nothing for hours kept {@code skippedEvaluationCount} at zero: the list page's
 * "stuck" warning never fired, the circuit breaker never paused the run, and the only evidence
 * was in container logs. These tests hold the branch open: engine-failure rounds must be
 * recorded as skips, benign rounds (cooldown) must still be recorded as successes.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReachConsensusServiceSkipAccountingTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private StrategyParser strategyParser;
    @Mock private RecordLLMUsageUseCase recordLLMUsageUseCase;
    @Mock private EventPublisher<ConsensusStartedEvent> consensusStartedEventPublisher;
    @Mock private AgentInvoker agentInvoker;
    @Mock private ConsensusContextBuilder contextBuilder;
    @Mock private ConsensusOutcomeRecorder outcomeRecorder;
    @Mock private ConsensusTradeCoordinator tradeCoordinator;
    @Mock private DataGapCircuitBreaker circuitBreaker;

    private ExecutorService executorService;
    private Simulation simulation;
    private Instant analysisTimestamp;
    private ReachConsensusCommand command;

    @BeforeEach
    void setUp() {
        executorService = Executors.newCachedThreadPool();

        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        simulation = new Simulation(
            simulationId,
            "Skip Accounting Sim",
            new StrategyId(UUID.randomUUID()),
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("100000")));
        simulation.start(); // startDate in the past → SIMULATING

        analysisTimestamp = Instant.parse("2024-03-01T15:00:00Z");
        command = new ReachConsensusCommand(simulationId, analysisTimestamp);

        Strategy strategy = new Strategy(
            new StrategyId(UUID.randomUUID()), "Test Strategy", "content",
            new UserId(UUID.randomUUID()));

        StrategyConfig config = new StrategyConfig();
        config.setConsensusMethod(ConsensusMethod.MAJORITY);
        config.setStockTickers(Set.of(new Symbol("AAPL", InvestmentType.STOCK)));
        config.setCryptoTickers(Set.of());

        Portfolio portfolio = new Portfolio(
            simulationId, Map.of(), Money.usd(new BigDecimal("100000")));

        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(any())).thenReturn(Optional.of(strategy));
        when(strategyParser.parse("content")).thenReturn(config);
        when(contextBuilder.validateMarketDataAvailability(any(), any(), any())).thenReturn(true);
        when(contextBuilder.getLatestPortfolio(any(), any())).thenReturn(portfolio);
        when(contextBuilder.fetchMultiTimeframeMarketDataForTickers(any(), any())).thenReturn(Map.of());
        when(contextBuilder.calculateIndicators(any(), any())).thenReturn(null);
        when(contextBuilder.resolveModelSelection(any(), any()))
            .thenReturn(new ConsensusContextBuilder.ModelSelection(null, null, null, Map.of()));
        when(contextBuilder.buildAgentContext(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock(AgentContext.class));
        when(contextBuilder.checkPositionCooldown(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(contextBuilder.checkMarketClosed(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(agentInvoker.buildParticipationNote(any(), any(), any())).thenReturn("");
        when(tradeCoordinator.currentPortfolio(any())).thenReturn(portfolio);
        when(outcomeRecorder.recordConsensusOutcome(any(), any(), any(), any(), any(), any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    private ReachConsensusService service(int pipelineTimeoutSeconds) {
        return new ReachConsensusService(
            simulationRepository, strategyRepository, strategyParser, recordLLMUsageUseCase,
            consensusStartedEventPublisher, agentInvoker, contextBuilder, outcomeRecorder,
            tradeCoordinator, circuitBreaker, executorService, pipelineTimeoutSeconds);
    }

    @Test
    @DisplayName("every agent failed → the round is recorded as an LLM-failure skip, not a success")
    void allAgentsFailedRecordsLlmFailureSkip() {
        // Every agent timed out before answering: the invoker returns no decisions at all
        when(agentInvoker.queryAgentsInParallel(any())).thenReturn(List.of());

        service(30).execute(command);

        verify(circuitBreaker).recordLlmFailureSkip(
            eq(simulation), eq(analysisTimestamp), anyString(), anyString());
        verify(circuitBreaker, never()).recordSuccess(any());
        verify(outcomeRecorder).createSkipConsensusForTicker(
            eq("AAPL"), eq(simulation), any(), any(), eq(analysisTimestamp), any());
    }

    @Test
    @DisplayName("a produced consensus (HOLD counts) → the round is recorded as a success")
    void producedConsensusRecordsSuccess() {
        LLMDecision hold = mock(LLMDecision.class);
        when(hold.getRecommendation()).thenReturn(RecommendationType.HOLD);
        when(hold.getConfidenceScore()).thenReturn(null);
        when(hold.getRecommendedAction()).thenReturn("{\"action\": \"HOLD\", \"reasoning\": \"steady\"}");
        when(hold.getTickerQuantity()).thenReturn(null);
        when(agentInvoker.queryAgentsInParallel(any())).thenReturn(List.of(hold));

        service(30).execute(command);

        verify(circuitBreaker).recordSuccess(simulation);
        verify(circuitBreaker, never()).recordLlmFailureSkip(any(), any(), any(), any());
    }

    @Test
    @DisplayName("pipeline slower than the timeout → LLM-failure skip with an audit row")
    void pipelineTimeoutRecordsLlmFailureSkip() {
        when(agentInvoker.queryAgentsInParallel(any())).thenAnswer(invocation -> {
            Thread.sleep(2500);
            return List.of();
        });

        service(1).execute(command);

        verify(outcomeRecorder).createSkippedConsensusDecision(
            eq(simulation), eq(analysisTimestamp), anyString());
        verify(circuitBreaker).recordLlmFailureSkip(
            eq(simulation), eq(analysisTimestamp), anyString(), anyString());
        verify(circuitBreaker, never()).recordSuccess(any());
    }

    @Test
    @DisplayName("benign round (cooldown only) → still recorded as a success, breaker untouched")
    void cooldownOnlyRoundStillRecordsSuccess() {
        when(contextBuilder.checkPositionCooldown(any(), any(), any(), any()))
            .thenReturn(Optional.of(new ConsensusContextBuilder.CooldownSkip(
                "cooling down", Duration.ofHours(2), Duration.ofDays(1))));

        service(30).execute(command);

        verify(circuitBreaker).recordSuccess(simulation);
        verify(circuitBreaker, never()).recordLlmFailureSkip(any(), any(), any(), any());
        verify(agentInvoker, never()).queryAgentsInParallel(any());
    }
}
