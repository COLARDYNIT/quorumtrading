package com.quorumtrading.bootstrap.event;

import com.quorumtrading.application.event.BackfillStatusChangedEvent;
import com.quorumtrading.application.event.SimulationDataReadyEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BackfillCompletedHandler.
 * Tests the bridge between backfill completion and simulation lifecycle.
 */
class BackfillCompletedHandlerTest {

    @Mock
    private BackfillJobRepository backfillJobRepository;

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private MarketEventRepository marketEventRepository;

    @Mock
    private Event<SimulationDataReadyEvent> dataReadyEvent;

    private BackfillCompletedHandler handler;

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final SimulationId SIM_ID = new SimulationId(UUID.randomUUID());
    private static final StrategyId STRATEGY_ID = new StrategyId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new BackfillCompletedHandler();
        handler.backfillJobRepository = backfillJobRepository;
        handler.simulationRepository = simulationRepository;
        handler.strategyRepository = strategyRepository;
        handler.marketEventRepository = marketEventRepository;
        handler.dataReadyEvent = dataReadyEvent;
    }

    @Test
    void shouldIgnoreNonCompletedEvents() {
        var event = new BackfillStatusChangedEvent(JOB_ID, BackfillStatus.PENDING, BackfillStatus.IN_PROGRESS, null, null);

        handler.onBackfillCompleted(event);

        verifyNoInteractions(backfillJobRepository, simulationRepository);
    }

    @Test
    void shouldFailSimulationWhenBackfillCompletesWithInsufficientData() {
        // Given: backfill completed but no market data exists
        var backfillRequest = createBackfillJobWithLinkedSimulation();
        var simulation = createWaitingForDataSimulation();
        var strategy = createStrategyWithStockTickers();

        when(backfillJobRepository.findById(JOB_ID)).thenReturn(Optional.of(backfillRequest));
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(0L); // No price data

        // When
        handler.onBackfillCompleted(new BackfillStatusChangedEvent(JOB_ID, BackfillStatus.IN_PROGRESS, BackfillStatus.COMPLETED, null, null));

        // Then: simulation should be failed, not started
        verify(dataReadyEvent, never()).fireAsync(any());
        // The failure is written through the narrow compare-and-set, not save(): lifecycle
        // columns have one writer so a stale in-memory entity cannot revert them.
        verify(simulationRepository).updateStatus(any(), eq(SimulationStatus.WAITING_FOR_DATA),
            eq(SimulationStatus.FAILED), any(), any());
        verify(simulationRepository, never()).save(any());
    }

    @Test
    void shouldStartSimulationWhenBackfillCompletesWithSufficientData() {
        // Given: backfill completed and market data exists
        var backfillRequest = createBackfillJobWithLinkedSimulation();
        var simulation = createWaitingForDataSimulation();
        var strategy = createStrategyWithStockTickers();

        when(backfillJobRepository.findById(JOB_ID)).thenReturn(Optional.of(backfillRequest));
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(10L); // Sufficient price data

        // When
        handler.onBackfillCompleted(new BackfillStatusChangedEvent(JOB_ID, BackfillStatus.IN_PROGRESS, BackfillStatus.COMPLETED, null, null));

        // Then: simulation should transition to PENDING and data ready event fires
        verify(dataReadyEvent).fireAsync(any(SimulationDataReadyEvent.class));
    }

    @Test
    void shouldSkipDeletedSimulation() {
        // Given: backfill completed but simulation was deleted
        var backfillRequest = createBackfillJobWithLinkedSimulation();

        when(backfillJobRepository.findById(JOB_ID)).thenReturn(Optional.of(backfillRequest));
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.empty());

        // When
        handler.onBackfillCompleted(new BackfillStatusChangedEvent(JOB_ID, BackfillStatus.IN_PROGRESS, BackfillStatus.COMPLETED, null, null));

        // Then: nothing happens
        verify(dataReadyEvent, never()).fireAsync(any());
        verify(simulationRepository, never()).save(any());
    }

    @Test
    void shouldCapEndDateToNow_WhenSimulationEndDateIsInFuture() {
        // Given: backfill completed for a simulation with a future end date
        var backfillRequest = createBackfillJobWithLinkedSimulation();
        Instant startDate = Instant.now().minus(60, ChronoUnit.DAYS);
        Instant futureEndDate = Instant.now().plus(30, ChronoUnit.DAYS);
        var simulation = Simulation.reconstitute(
            SIM_ID, "Future End Simulation", STRATEGY_ID,
            startDate, futureEndDate,
            Money.usd(100_000), null, null, null
        );
        simulation.waitForData(JOB_ID);
        var strategy = createStrategyWithStockTickers();

        when(backfillJobRepository.findById(JOB_ID)).thenReturn(Optional.of(backfillRequest));
        when(simulationRepository.findById(SIM_ID)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(10L);

        // When
        handler.onBackfillCompleted(new BackfillStatusChangedEvent(JOB_ID, BackfillStatus.IN_PROGRESS, BackfillStatus.COMPLETED, null, null));

        // Then: validation should pass and use now instead of the future end date
        verify(dataReadyEvent).fireAsync(any(SimulationDataReadyEvent.class));

        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(marketEventRepository).countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), endCaptor.capture());
        Instant capturedEnd = endCaptor.getValue();
        assertTrue(capturedEnd.isBefore(futureEndDate), "End date should be capped to now, not the future end date");
        assertTrue(capturedEnd.isAfter(Instant.now().minus(10, ChronoUnit.SECONDS)), "Capped end date should be approximately now");
    }

    // ── Helpers ──

    private BackfillJob createBackfillJobWithLinkedSimulation() {
        var gap = new MarketDataGap(
            new Symbol("AAPL", InvestmentType.STOCK),
            EventType.PRICE,
            new TimeRange(Instant.now().minus(30, ChronoUnit.DAYS), Instant.now())
        );
        var request = new BackfillJob(List.of(gap), SIM_ID);
        // Use reflection to set the ID since the constructor generates a random one
        try {
            var idField = BackfillJob.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(request, JOB_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return request;
    }

    private Simulation createWaitingForDataSimulation() {
        Instant startDate = Instant.now().minus(60, ChronoUnit.DAYS);
        Instant endDate = Instant.now().minus(30, ChronoUnit.DAYS);
        var simulation = Simulation.reconstitute(
            SIM_ID, "Test Simulation", STRATEGY_ID,
            startDate, endDate,
            Money.usd(100_000), null, null, null
        );
        simulation.waitForData(JOB_ID);
        return simulation;
    }

    private Strategy createStrategyWithStockTickers() {
        String content = """
            ---
            consensus_method: MAJORITY
            stock_tickers:
              - AAPL
            live_schedule:
              timezone: America/New_York
              evaluation_times:
                - "10:00"
            ---
            # Test Strategy

            Buy AAPL on dips.
            """;
        return new Strategy(STRATEGY_ID, "Test Strategy", content, new UserId(UUID.randomUUID()));
    }
}
