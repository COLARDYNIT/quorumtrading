package com.quorumtrading.application.service;

import com.quorumtrading.application.config.AutoBacktestConfig;
import com.quorumtrading.application.event.ResearchExecutionCompleted;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.RequestBackfillUseCase;
import com.quorumtrading.application.usecase.StartSimulationUseCase;
import com.quorumtrading.application.usecase.command.AnalyzeDataAvailabilityCommand;
import com.quorumtrading.application.usecase.command.RequestBackfillCommand;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.marketdata.DataCoverageMetrics;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.TimeRange;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AutoBacktestService with data availability integration.
 * <p>
 * Tests the complete flow of auto-backtest creation including:
 * - Data availability checking
 * - Automatic backfill requests
 * - Waiting for backfill completion
 * - Simulation creation and execution
 */
@ExtendWith(MockitoExtension.class)
class AutoBacktestServiceTest {

    @Mock
    private CreateSimulationUseCase createSimulationUseCase;

    @Mock
    private StartSimulationUseCase startSimulationUseCase;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private AutoBacktestConfig autoBacktestConfig;

    @Mock
    private AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase;

    @Mock
    private RequestBackfillUseCase requestBackfillUseCase;

    @Mock
    private BackfillJobRepository backfillJobRepository;

    private AutoBacktestService autoBacktestService;

    @BeforeEach
    void setUp() {
        autoBacktestService = new AutoBacktestService(
            createSimulationUseCase,
            startSimulationUseCase,
            strategyRepository,
            autoBacktestConfig,
            analyzeDataAvailabilityUseCase,
            requestBackfillUseCase,
            backfillJobRepository
        );

        // Default config - use lenient to avoid unnecessary stubbing errors
        lenient().when(autoBacktestConfig.periodWeeks()).thenReturn(1);
        lenient().when(autoBacktestConfig.initialCash()).thenReturn(new BigDecimal("100000"));
        lenient().when(autoBacktestConfig.checkDataAvailability()).thenReturn(true);
        lenient().when(autoBacktestConfig.autoBackfill()).thenReturn(true);
        lenient().when(autoBacktestConfig.waitForBackfill()).thenReturn(false);
        lenient().when(autoBacktestConfig.maxWaitSeconds()).thenReturn(300);
        lenient().when(autoBacktestConfig.coverageThreshold()).thenReturn(new BigDecimal("80"));
    }

    @Test
    void shouldNotTriggerBacktestWhenEventShouldNotTrigger() {
        // Given
        ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            false
        );

        // When
        autoBacktestService.onExecutionCompleted(event);

        // Then
        verifyNoInteractions(strategyRepository);
        verifyNoInteractions(createSimulationUseCase);
        verifyNoInteractions(startSimulationUseCase);
    }

    @Test
    void shouldCreateBacktestWithoutDataCheckWhenDisabled() {
        // Given
        when(autoBacktestConfig.checkDataAvailability()).thenReturn(false);

        UUID strategyId = UUID.randomUUID();
        Strategy strategy = createStrategy(strategyId, "Test Strategy", "BTC");

        ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            strategyId,
            true
        );

        when(strategyRepository.findById(new StrategyId(strategyId)))
            .thenReturn(Optional.of(strategy));
        when(createSimulationUseCase.execute(any()))
            .thenReturn(new SimulationId(UUID.randomUUID()));

        // When
        autoBacktestService.onExecutionCompleted(event);

        // Then
        verify(analyzeDataAvailabilityUseCase, never()).execute(any());
        verify(createSimulationUseCase).execute(any());
        verify(startSimulationUseCase).execute(any());
    }

    @Test
    void shouldCreateBacktestWhenDataCoverageIsSufficient() {
        // Given
        UUID strategyId = UUID.randomUUID();
        Strategy strategy = createStrategy(strategyId, "Test Strategy", "BTC");

        ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            strategyId,
            true
        );

        when(strategyRepository.findById(new StrategyId(strategyId)))
            .thenReturn(Optional.of(strategy));

        // Mock sufficient data availability (90% coverage)
        DataAvailability availability = createDataAvailability(new BigDecimal("90"));
        when(analyzeDataAvailabilityUseCase.execute(any(AnalyzeDataAvailabilityCommand.class)))
            .thenReturn(availability);

        when(createSimulationUseCase.execute(any()))
            .thenReturn(new SimulationId(UUID.randomUUID()));

        // When
        autoBacktestService.onExecutionCompleted(event);

        // Then
        verify(analyzeDataAvailabilityUseCase).execute(any(AnalyzeDataAvailabilityCommand.class));
        verify(requestBackfillUseCase, never()).execute(any()); // No backfill needed
        verify(createSimulationUseCase).execute(any());
        verify(startSimulationUseCase).execute(any());
    }

    @Test
    void shouldRequestBackfillWhenDataCoverageInsufficient() {
        // Given
        UUID strategyId = UUID.randomUUID();
        Strategy strategy = createStrategy(strategyId, "Test Strategy", "BTC");

        ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            strategyId,
            true
        );

        when(strategyRepository.findById(new StrategyId(strategyId)))
            .thenReturn(Optional.of(strategy));

        // Mock insufficient data availability (50% coverage)
        DataAvailability availability = createDataAvailability(new BigDecimal("50"));
        when(analyzeDataAvailabilityUseCase.execute(any(AnalyzeDataAvailabilityCommand.class)))
            .thenReturn(availability);

        UUID jobId = UUID.randomUUID();
        RequestBackfillResult backfillResult = RequestBackfillResult.backfillStarted(jobId, Instant.now());
        when(requestBackfillUseCase.execute(any(RequestBackfillCommand.class)))
            .thenReturn(backfillResult);

        // When
        autoBacktestService.onExecutionCompleted(event);

        // Then
        verify(analyzeDataAvailabilityUseCase).execute(any(AnalyzeDataAvailabilityCommand.class));
        verify(requestBackfillUseCase).execute(any(RequestBackfillCommand.class));
        verify(createSimulationUseCase, never()).execute(any()); // Should not create simulation (wait disabled)
        verify(startSimulationUseCase, never()).execute(any());
    }

    @Test
    void shouldNotRequestBackfillWhenAutoBackfillDisabled() {
        // Given
        when(autoBacktestConfig.autoBackfill()).thenReturn(false);

        UUID strategyId = UUID.randomUUID();
        Strategy strategy = createStrategy(strategyId, "Test Strategy", "BTC");

        ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            strategyId,
            true
        );

        when(strategyRepository.findById(new StrategyId(strategyId)))
            .thenReturn(Optional.of(strategy));

        // Mock insufficient data availability (50% coverage)
        DataAvailability availability = createDataAvailability(new BigDecimal("50"));
        when(analyzeDataAvailabilityUseCase.execute(any(AnalyzeDataAvailabilityCommand.class)))
            .thenReturn(availability);

        // When
        autoBacktestService.onExecutionCompleted(event);

        // Then
        verify(analyzeDataAvailabilityUseCase).execute(any(AnalyzeDataAvailabilityCommand.class));
        verify(requestBackfillUseCase, never()).execute(any()); // Auto-backfill disabled
        verify(createSimulationUseCase, never()).execute(any());
        verify(startSimulationUseCase, never()).execute(any());
    }

    @Test
    void shouldWaitForBackfillCompletionWhenEnabled() throws InterruptedException {
        // Given
        when(autoBacktestConfig.waitForBackfill()).thenReturn(true);
        when(autoBacktestConfig.maxWaitSeconds()).thenReturn(5);

        UUID strategyId = UUID.randomUUID();
        Strategy strategy = createStrategy(strategyId, "Test Strategy", "BTC");

        ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            strategyId,
            true
        );

        when(strategyRepository.findById(new StrategyId(strategyId)))
            .thenReturn(Optional.of(strategy));

        // Mock insufficient data availability (50% coverage)
        DataAvailability availability = createDataAvailability(new BigDecimal("50"));
        when(analyzeDataAvailabilityUseCase.execute(any(AnalyzeDataAvailabilityCommand.class)))
            .thenReturn(availability);

        UUID jobId = UUID.randomUUID();
        RequestBackfillResult backfillResult = RequestBackfillResult.backfillStarted(jobId, Instant.now());
        when(requestBackfillUseCase.execute(any(RequestBackfillCommand.class)))
            .thenReturn(backfillResult);

        // Mock backfill job that completes immediately
        BackfillJob completedRequest = mock(BackfillJob.class);
        when(completedRequest.getStatus()).thenReturn(BackfillStatus.COMPLETED);
        when(completedRequest.getProgressPercentage()).thenReturn(100);
        when(backfillJobRepository.findById(jobId)).thenReturn(Optional.of(completedRequest));

        when(createSimulationUseCase.execute(any()))
            .thenReturn(new SimulationId(UUID.randomUUID()));

        // When
        autoBacktestService.onExecutionCompleted(event);

        // Then
        verify(analyzeDataAvailabilityUseCase).execute(any(AnalyzeDataAvailabilityCommand.class));
        verify(requestBackfillUseCase).execute(any(RequestBackfillCommand.class));
        verify(backfillJobRepository, atLeastOnce()).findById(jobId); // Polled for status
        verify(createSimulationUseCase).execute(any()); // Should create simulation after backfill completes
        verify(startSimulationUseCase).execute(any());
    }

    @Test
    void shouldHandleBackfillFailure() {
        // Given
        when(autoBacktestConfig.waitForBackfill()).thenReturn(true);
        when(autoBacktestConfig.maxWaitSeconds()).thenReturn(5);

        UUID strategyId = UUID.randomUUID();
        Strategy strategy = createStrategy(strategyId, "Test Strategy", "BTC");

        ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            strategyId,
            true
        );

        when(strategyRepository.findById(new StrategyId(strategyId)))
            .thenReturn(Optional.of(strategy));

        // Mock insufficient data availability
        DataAvailability availability = createDataAvailability(new BigDecimal("50"));
        when(analyzeDataAvailabilityUseCase.execute(any(AnalyzeDataAvailabilityCommand.class)))
            .thenReturn(availability);

        UUID jobId = UUID.randomUUID();
        RequestBackfillResult backfillResult = RequestBackfillResult.backfillStarted(jobId, Instant.now());
        when(requestBackfillUseCase.execute(any(RequestBackfillCommand.class)))
            .thenReturn(backfillResult);

        // Mock failed backfill job
        BackfillJob failedRequest = mock(BackfillJob.class);
        when(failedRequest.getStatus()).thenReturn(BackfillStatus.FAILED);
        when(failedRequest.getErrorMessage()).thenReturn("API error");
        when(backfillJobRepository.findById(jobId)).thenReturn(Optional.of(failedRequest));

        // When
        autoBacktestService.onExecutionCompleted(event);

        // Then
        verify(analyzeDataAvailabilityUseCase).execute(any(AnalyzeDataAvailabilityCommand.class));
        verify(requestBackfillUseCase).execute(any(RequestBackfillCommand.class));
        verify(backfillJobRepository).findById(jobId);
        verify(createSimulationUseCase, never()).execute(any()); // Should NOT create simulation
        verify(startSimulationUseCase, never()).execute(any());
    }

    @Test
    void shouldPassCorrectDataTypesToAvailabilityCheck() {
        // Given
        UUID strategyId = UUID.randomUUID();
        Strategy strategy = createStrategy(strategyId, "Test Strategy", "BTC");

        ResearchExecutionCompleted event = ResearchExecutionCompleted.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            strategyId,
            true
        );

        when(strategyRepository.findById(new StrategyId(strategyId)))
            .thenReturn(Optional.of(strategy));

        DataAvailability availability = createDataAvailability(new BigDecimal("90"));
        when(analyzeDataAvailabilityUseCase.execute(any(AnalyzeDataAvailabilityCommand.class)))
            .thenReturn(availability);

        when(createSimulationUseCase.execute(any()))
            .thenReturn(new SimulationId(UUID.randomUUID()));

        // When
        autoBacktestService.onExecutionCompleted(event);

        // Then
        ArgumentCaptor<AnalyzeDataAvailabilityCommand> commandCaptor =
            ArgumentCaptor.forClass(AnalyzeDataAvailabilityCommand.class);
        verify(analyzeDataAvailabilityUseCase).execute(commandCaptor.capture());

        AnalyzeDataAvailabilityCommand command = commandCaptor.getValue();
        assertThat(command.dataTypes()).containsExactlyInAnyOrder(
            EventType.PRICE,
            EventType.NEWS,
            EventType.SOCIAL
        );
        assertThat(command.symbols()).contains(new Symbol("BTC", com.quorumtrading.model.enums.InvestmentType.CRYPTO));
    }

    // Helper methods

    private Strategy createStrategy(UUID id, String name, String ticker) {
        // Uses YAML frontmatter format required by StrategyConfigParser
        String content = String.format("""
            ---
            version: v1.0
            consensus_method: ORCHESTRATOR_OVERRIDE
            crypto_tickers:
              - %s
            ---
            # Strategy: %s

            ## Description
            Test strategy for auto-backtest
            """, ticker, name);

        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        return new Strategy(
            new StrategyId(id),
            name,
            null,  // description
            1,  // version
            content,
            Strategy.StrategyStatus.ACTIVE,
            Instant.now(),
            Instant.now(),
            null,  // parentStrategyId
            0,     // generation
            null,  // lastTestedAt
            null,  // testFrequency
            true,  // enabled
            systemUserId,
            false  // publicVisible
        );
    }

    private DataAvailability createDataAvailability(BigDecimal coveragePercentage) {
        // Create a simple data availability with the specified coverage
        LocalDate today = LocalDate.now();
        Symbol btcSymbol = new Symbol("BTC", com.quorumtrading.model.enums.InvestmentType.CRYPTO);
        Map<LocalDate, DataCoverageMetrics> dailyCoverage = Map.of(
            today,
            new DataCoverageMetrics(
                coveragePercentage,
                Map.of(
                    EventType.PRICE, coveragePercentage,
                    EventType.NEWS, coveragePercentage,
                    EventType.SOCIAL, coveragePercentage
                ),
                Map.of(btcSymbol, coveragePercentage)
            )
        );

        LocalDate startDate = today.minusDays(7);
        TimeRange timeRange = TimeRange.of(startDate, today);

        return DataAvailability.of(
            timeRange,
            Set.of(btcSymbol),
            Set.of(EventType.PRICE, EventType.NEWS, EventType.SOCIAL),
            dailyCoverage
        );
    }
}
