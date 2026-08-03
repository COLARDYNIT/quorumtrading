package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationDataReadyEvent;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.RequestBackfillUseCase;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import com.quorumtrading.model.valueobject.UserId;
import com.quorumtrading.application.usecase.command.AnalyzeDataAvailabilityCommand;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackfillOrchestratorServiceTest {

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase;

    @Mock
    private RequestBackfillUseCase requestBackfillUseCase;

    @Mock
    private EventPublisher<SimulationDataReadyEvent> eventPublisher;

    @Mock
    private MarketEventRepository marketEventRepository;

    private BackfillOrchestratorService service;

    @BeforeEach
    void setUp() {
        service = new BackfillOrchestratorService(
            simulationRepository,
            strategyRepository,
            analyzeDataAvailabilityUseCase,
            requestBackfillUseCase,
            eventPublisher,
            marketEventRepository
        );

        // Default: no existing data (first-time backfill) — tests that need clamping override this
        lenient().when(marketEventRepository.findEarliestPriceDate(any()))
            .thenReturn(Optional.empty());
    }

    @Test
    void shouldFireDataReadyEventImmediately_WhenSimulationIsLiveMode() {
        // Arrange
        SimulationId simId = new SimulationId(UUID.randomUUID());
        Simulation liveSimulation = createLiveSimulation(simId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(liveSimulation));

        // Act
        service.handleSimulationCreated(simId);

        // Assert
        ArgumentCaptor<SimulationDataReadyEvent> eventCaptor =
            ArgumentCaptor.forClass(SimulationDataReadyEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().simulationId()).isEqualTo(simId);

        // Verify no data availability check for LIVE mode
        verifyNoInteractions(analyzeDataAvailabilityUseCase);
        verifyNoInteractions(requestBackfillUseCase);
    }

    @Test
    void shouldFireDataReadyEventImmediately_WhenBacktestHasNoGapsAndDataValidated() {
        // Arrange
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation backtestSimulation = createBacktestSimulation(simId, strategyId);
        Strategy strategy = createStrategyWithSymbols(strategyId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(backtestSimulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        // Mock no gaps found
        DataAvailability availability = mock(DataAvailability.class);
        when(availability.identifyGaps()).thenReturn(List.of());
        when(analyzeDataAvailabilityUseCase.execute(any())).thenReturn(availability);

        // Mock data validation passes (at least 1 price event exists for each ticker)
        when(marketEventRepository.countEventsInRange(any(), eq(EventType.PRICE), any(), any()))
            .thenReturn(10L);

        // Act
        service.handleSimulationCreated(simId);

        // Assert
        ArgumentCaptor<SimulationDataReadyEvent> eventCaptor =
            ArgumentCaptor.forClass(SimulationDataReadyEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().simulationId()).isEqualTo(simId);

        // Verify backfill was NOT triggered
        verifyNoInteractions(requestBackfillUseCase);
    }

    @Test
    void shouldTriggerForcedBackfill_WhenNoGapsButDataValidationFails() {
        // Arrange
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation backtestSimulation = createBacktestSimulation(simId, strategyId);
        Strategy strategy = createStrategyWithSymbols(strategyId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(backtestSimulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        // Mock no gaps found by analysis
        DataAvailability availability = mock(DataAvailability.class);
        when(availability.identifyGaps()).thenReturn(List.of());
        when(analyzeDataAvailabilityUseCase.execute(any())).thenReturn(availability);

        // Mock data validation FAILS (0 price events)
        when(marketEventRepository.countEventsInRange(any(), eq(EventType.PRICE), any(), any()))
            .thenReturn(0L);

        // Mock backfill triggered with a job ID
        UUID backfillJobId = UUID.randomUUID();
        when(requestBackfillUseCase.execute(any()))
            .thenReturn(RequestBackfillResult.backfillStarted(backfillJobId, Instant.now()));

        // Act
        service.handleSimulationCreated(simId);

        // Assert - should trigger forced backfill, transition to WAITING_FOR_DATA, not fire event
        verify(requestBackfillUseCase).execute(any());
        // The WAITING_FOR_DATA transition is a lifecycle write, so it goes through the narrow
        // compare-and-set rather than a whole-entity save().
        verify(simulationRepository).updateStatus(any(), any(),
            eq(SimulationStatus.WAITING_FOR_DATA), any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldTriggerBackfill_WhenBacktestHasGaps() {
        // Arrange
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation backtestSimulation = createBacktestSimulation(simId, strategyId);
        Strategy strategy = createStrategyWithSymbols(strategyId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(backtestSimulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        // Mock gaps found
        DataAvailability availability = mock(DataAvailability.class);
        List<MarketDataGap> gaps = List.of(
            new MarketDataGap(
                new Symbol("BTC", InvestmentType.CRYPTO),
                EventType.PRICE,
                TimeRange.of(LocalDate.now().minusDays(2), LocalDate.now().minusDays(1))
            )
        );
        when(availability.identifyGaps()).thenReturn(gaps);
        when(analyzeDataAvailabilityUseCase.execute(any())).thenReturn(availability);

        // Mock backfill triggered with a job ID
        UUID backfillJobId = UUID.randomUUID();
        when(requestBackfillUseCase.execute(any()))
            .thenReturn(RequestBackfillResult.backfillStarted(backfillJobId, Instant.now()));

        // Act
        service.handleSimulationCreated(simId);

        // Assert - backfill triggered, transition to WAITING_FOR_DATA, no immediate DataReadyEvent
        verify(requestBackfillUseCase).execute(any());
        // The WAITING_FOR_DATA transition is a lifecycle write, so it goes through the narrow
        // compare-and-set rather than a whole-entity save().
        verify(simulationRepository).updateStatus(any(), any(),
            eq(SimulationStatus.WAITING_FOR_DATA), any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldThrowException_WhenSimulationNotFound() {
        // Arrange
        SimulationId simId = new SimulationId(UUID.randomUUID());
        when(simulationRepository.findById(simId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.handleSimulationCreated(simId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Simulation not found");

        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(requestBackfillUseCase);
    }

    @Test
    void shouldFireDataReadyEvent_WhenStrategyHasNoSymbols() {
        // Arrange
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation backtestSimulation = createBacktestSimulation(simId, strategyId);
        Strategy strategyWithoutSymbols = createStrategyWithoutSymbols(strategyId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(backtestSimulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategyWithoutSymbols));

        // Act
        service.handleSimulationCreated(simId);

        // Assert - should fire event anyway (graceful degradation)
        verify(eventPublisher).publish(any(SimulationDataReadyEvent.class));
        verifyNoInteractions(analyzeDataAvailabilityUseCase);
        verifyNoInteractions(requestBackfillUseCase);
    }

    @Test
    void shouldBackfillWithIndicatorWarmupBuffer_WhenBacktestSimulation() {
        // Arrange
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation backtestSimulation = createBacktestSimulation(simId, strategyId);
        Strategy strategy = createStrategyWithSymbols(strategyId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(backtestSimulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        DataAvailability availability = mock(DataAvailability.class);
        when(availability.identifyGaps()).thenReturn(List.of());
        when(analyzeDataAvailabilityUseCase.execute(any())).thenReturn(availability);
        when(marketEventRepository.countEventsInRange(any(), eq(EventType.PRICE), any(), any()))
            .thenReturn(10L);

        // Act
        service.handleSimulationCreated(simId);

        // Assert - the availability command's TimeRange should start 70 days before the simulation start
        ArgumentCaptor<AnalyzeDataAvailabilityCommand> cmdCaptor =
            ArgumentCaptor.forClass(AnalyzeDataAvailabilityCommand.class);
        verify(analyzeDataAvailabilityUseCase).execute(cmdCaptor.capture());

        LocalDate simStartDate = backtestSimulation.getStartDate().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate expectedBackfillStart = simStartDate.minusDays(BackfillOrchestratorService.INDICATOR_WARMUP_CALENDAR_DAYS);

        TimeRange capturedRange = cmdCaptor.getValue().timeRange();
        assertThat(capturedRange.getStart()).isEqualTo(expectedBackfillStart);
    }

    @Test
    void shouldClampWarmupStartDate_WhenEarliestDataExistsAfterWarmupStart() {
        // Arrange - simulation starts 100 days ago, warmup extends to 170 days ago,
        // but earliest data is only 120 days ago. Should clamp to 120 days ago.
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());

        LocalDate simStart = LocalDate.now().minusDays(100);
        LocalDate simEnd = LocalDate.now().minusDays(1);
        Instant startInstant = simStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = simEnd.atStartOfDay(ZoneOffset.UTC).toInstant();

        Simulation backtestSimulation = new Simulation(
            simId, "Clamp Test", strategyId, startInstant, endInstant, Money.ZERO_USD
        );

        Strategy strategy = createStrategyWithSymbols(strategyId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(backtestSimulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        // Earliest data is 120 days ago (between warmup start of 170 days ago and sim start of 100 days ago)
        LocalDate earliestDataDate = LocalDate.now().minusDays(120);
        Instant earliestDataInstant = earliestDataDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        when(marketEventRepository.findEarliestPriceDate(any()))
            .thenReturn(Optional.of(earliestDataInstant));

        DataAvailability availability = mock(DataAvailability.class);
        when(availability.identifyGaps()).thenReturn(List.of());
        when(analyzeDataAvailabilityUseCase.execute(any())).thenReturn(availability);
        when(marketEventRepository.countEventsInRange(any(), eq(EventType.PRICE), any(), any()))
            .thenReturn(10L);

        // Act
        service.handleSimulationCreated(simId);

        // Assert - TimeRange should start at earliest data date, not at warmup start
        ArgumentCaptor<AnalyzeDataAvailabilityCommand> cmdCaptor =
            ArgumentCaptor.forClass(AnalyzeDataAvailabilityCommand.class);
        verify(analyzeDataAvailabilityUseCase).execute(cmdCaptor.capture());

        TimeRange capturedRange = cmdCaptor.getValue().timeRange();
        assertThat(capturedRange.getStart()).isEqualTo(earliestDataDate);
    }

    @Test
    void shouldNotClampWarmupStartDate_WhenNoDataExistsForTicker() {
        // Arrange - first-time backfill, no existing data
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation backtestSimulation = createBacktestSimulation(simId, strategyId);
        Strategy strategy = createStrategyWithSymbols(strategyId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(backtestSimulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        // No existing data — findEarliestPriceDate returns empty (default from setUp)

        DataAvailability availability = mock(DataAvailability.class);
        when(availability.identifyGaps()).thenReturn(List.of());
        when(analyzeDataAvailabilityUseCase.execute(any())).thenReturn(availability);
        when(marketEventRepository.countEventsInRange(any(), eq(EventType.PRICE), any(), any()))
            .thenReturn(10L);

        // Act
        service.handleSimulationCreated(simId);

        // Assert - TimeRange should use the full warmup period (no clamping)
        ArgumentCaptor<AnalyzeDataAvailabilityCommand> cmdCaptor =
            ArgumentCaptor.forClass(AnalyzeDataAvailabilityCommand.class);
        verify(analyzeDataAvailabilityUseCase).execute(cmdCaptor.capture());

        LocalDate simStartDate = backtestSimulation.getStartDate().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate expectedBackfillStart = simStartDate.minusDays(BackfillOrchestratorService.INDICATOR_WARMUP_CALENDAR_DAYS);

        TimeRange capturedRange = cmdCaptor.getValue().timeRange();
        assertThat(capturedRange.getStart()).isEqualTo(expectedBackfillStart);
    }

    // ===== Helper Methods =====

    /**
     * Creates a LIVE simulation (endDate in the future).
     * A LIVE simulation is one where isBacktest() returns false.
     */
    private Simulation createLiveSimulation(SimulationId simId) {
        // LIVE simulation has endDate == null (runs indefinitely).
        return new Simulation(
            simId,
            "Test LIVE Simulation",
            new StrategyId(UUID.randomUUID()),
            Instant.now(),
            null,
            Money.ZERO_USD
        );
    }

    /**
     * Creates a BACKTEST simulation (endDate in the past).
     * A BACKTEST simulation is one where isBacktest() returns true.
     */
    private Simulation createBacktestSimulation(SimulationId simId, StrategyId strategyId) {
        // BACKTEST simulation has endDate in the past
        Instant pastEndDate = Instant.now().minusSeconds(86400); // -1 day
        return new Simulation(
            simId,
            "Test BACKTEST Simulation",
            strategyId,
            Instant.now().minusSeconds(172800), // startDate: 2 days ago
            pastEndDate,
            Money.ZERO_USD
        );
    }

    /**
     * Creates a strategy with ticker symbols configured.
     * Uses the StrategyConfigParser-compatible markdown format with valid YAML frontmatter.
     */
    private Strategy createStrategyWithSymbols(StrategyId strategyId) {
        String strategyContent = """
            ---
            consensus_method: MAJORITY
            crypto_tickers:
              - BTC
            stock_tickers:
              - AAPL
              - MSFT
            live_schedule:
              timezone: America/New_York
              evaluation_times:
                - "10:00"
            ---
            # Test Strategy

            ## Configuration
            Buy low, sell high.
            """;

        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        return new Strategy(
            strategyId,
            "Test Strategy",
            strategyContent,
            systemUserId
        );
    }

    /**
     * Creates a strategy that cannot be parsed by StrategyConfigParser.
     * This triggers graceful degradation - empty symbols set and immediate DataReadyEvent.
     *
     * Note: The StrategyConfigParser requires valid YAML frontmatter (---\\n...\\n---).
     * Content without frontmatter will fail parsing and return empty symbols.
     */
    private Strategy createStrategyWithoutSymbols(StrategyId strategyId) {
        // Strategy content WITHOUT valid YAML frontmatter - will fail parsing
        // This triggers the catch block in extractSymbolsFromStrategy() which returns Set.of()
        String strategyContent = """
            # Test Strategy Without Frontmatter

            This strategy has no YAML frontmatter, so parsing will fail.
            The service should gracefully handle this and fire DataReadyEvent anyway.
            """;

        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        return new Strategy(
            strategyId,
            "Test Strategy Without Symbols",
            strategyContent,
            systemUserId
        );
    }
}
