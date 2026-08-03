package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.event.SimulationDataReadyEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.SimulationLockPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StartupRecoveryHandlerTest {

    @Mock
    SimulationLockPort simulationLockPort;

    @Mock
    SimulationRepository simulationRepository;

    @Mock
    BackfillJobRepository backfillJobRepository;

    @Mock
    StrategyRepository strategyRepository;

    @Mock
    MarketEventRepository marketEventRepository;

    @Mock
    Event<SimulationDataReadyEvent> dataReadyEvent;

    private StartupRecoveryHandler handler;

    private static final String STRATEGY_CONTENT = """
            ---
            crypto_tickers:
              - BTC
              - ETH
            consensus_method: ORCHESTRATOR_OVERRIDE
            backtest_schedule:
              interval: 1d
            live_schedule:
              interval: 4h
            agents:
              - type: TECHNICAL
                weight: 1.0
            ---
            Buy low sell high.
            """;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new StartupRecoveryHandler();
        handler.simulationLockPort = simulationLockPort;
        handler.simulationRepository = simulationRepository;
        handler.backfillJobRepository = backfillJobRepository;
        handler.strategyRepository = strategyRepository;
        handler.marketEventRepository = marketEventRepository;
        handler.dataReadyEvent = dataReadyEvent;

        // Default stubs for non-recovery methods
        when(simulationLockPort.forceUnlockStale(any(Duration.class))).thenReturn(0);
        when(backfillJobRepository.findPendingJobs()).thenReturn(List.of());
    }

    private void stubNoWaitingForData() {
        when(simulationRepository.findWithFilters(any(), any(), any(), any())).thenReturn(List.of());
    }

    private Simulation createWaitingForDataSimulation(StrategyId strategyId) {
        Simulation sim = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Test Sim",
            strategyId,
            Instant.now().minus(Duration.ofDays(30)),
            Instant.now(),
            Money.usd(new BigDecimal("100000"))
        );
        sim.waitForData(UUID.randomUUID());
        return sim;
    }

    private Strategy createStrategy(StrategyId strategyId) {
        return new Strategy(
            strategyId,
            "Test Strategy",
            STRATEGY_CONTENT,
            new UserId(UUID.randomUUID())
        );
    }

    @Test
    void shouldCleanStaleLocks() {
        when(simulationLockPort.forceUnlockStale(any(Duration.class))).thenReturn(3);
        stubNoWaitingForData();

        handler.onStartup(new StartupEvent());

        verify(simulationLockPort).forceUnlockStale(Duration.ofMinutes(15));
    }

    @Test
    void shouldLogOrphanedSimulations() {
        Simulation orphaned = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Orphaned Sim",
            new StrategyId(UUID.randomUUID()),
            Instant.now().minus(Duration.ofDays(1)),
            Instant.now(),
            Money.usd(new BigDecimal("10000"))
        );
        when(simulationRepository.findWithFilters(
            eq(Optional.of(SimulationStatus.SIMULATING)),
            eq(Optional.empty()),
            eq(Optional.empty()),
            eq(Optional.empty())
        )).thenReturn(List.of(orphaned));
        when(simulationRepository.findWithFilters(
            eq(Optional.of(SimulationStatus.WAITING_FOR_DATA)),
            eq(Optional.empty()),
            eq(Optional.empty()),
            eq(Optional.empty())
        )).thenReturn(List.of());

        handler.onStartup(new StartupEvent());

        verify(simulationRepository).findWithFilters(
            eq(Optional.of(SimulationStatus.SIMULATING)),
            eq(Optional.empty()),
            eq(Optional.empty()),
            eq(Optional.empty())
        );
    }

    @Test
    void shouldLogPendingBackfillJobs() {
        stubNoWaitingForData();
        when(backfillJobRepository.findPendingJobs()).thenReturn(List.of(mock(BackfillJob.class)));

        handler.onStartup(new StartupEvent());

        verify(backfillJobRepository).findPendingJobs();
    }

    @Test
    void shouldHandleExceptionsGracefully() {
        when(simulationLockPort.forceUnlockStale(any(Duration.class)))
            .thenThrow(new RuntimeException("DB connection failed"));
        when(simulationRepository.findWithFilters(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("DB connection failed"));
        when(backfillJobRepository.findPendingJobs())
            .thenThrow(new RuntimeException("DB connection failed"));

        handler.onStartup(new StartupEvent());

        verify(simulationLockPort).forceUnlockStale(any(Duration.class));
    }

    @Test
    void shouldRecoverWaitingForDataSimulationWhenDataAvailable() {
        // Given — simulation stuck in WAITING_FOR_DATA, but data exists
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation sim = createWaitingForDataSimulation(strategyId);
        Strategy strategy = createStrategy(strategyId);

        // Backfill job is already completed
        BackfillJob completedJob = mock(BackfillJob.class);
        when(completedJob.getStatus()).thenReturn(BackfillStatus.COMPLETED);
        when(backfillJobRepository.findById(sim.getBackfillJobId())).thenReturn(Optional.of(completedJob));

        when(simulationRepository.findWithFilters(
            eq(Optional.of(SimulationStatus.SIMULATING)), any(), any(), any()
        )).thenReturn(List.of());
        when(simulationRepository.findWithFilters(
            eq(Optional.of(SimulationStatus.WAITING_FOR_DATA)), any(), any(), any()
        )).thenReturn(List.of(sim));

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(100L);

        // When
        handler.onStartup(new StartupEvent());

        // Then — the WAITING_FOR_DATA -> PENDING transition is written through the narrow
        // compare-and-set; save() no longer carries lifecycle state.
        verify(simulationRepository).updateStatus(sim.getSimulationId(),
            SimulationStatus.WAITING_FOR_DATA, SimulationStatus.PENDING, null, null);
        verify(simulationRepository, never()).save(sim);
        assertEquals(SimulationStatus.PENDING, sim.getStatus());

        ArgumentCaptor<SimulationDataReadyEvent> captor = ArgumentCaptor.forClass(SimulationDataReadyEvent.class);
        verify(dataReadyEvent).fireAsync(captor.capture());
        assertEquals(sim.getSimulationId(), captor.getValue().simulationId());
    }

    @Test
    void shouldFailWaitingForDataSimulationWhenNoDataAndNoActiveBackfill() {
        // Given — simulation stuck, backfill job gone, no data
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation sim = createWaitingForDataSimulation(strategyId);
        Strategy strategy = createStrategy(strategyId);

        // Backfill job not found (already cleaned up)
        when(backfillJobRepository.findById(sim.getBackfillJobId())).thenReturn(Optional.empty());

        when(simulationRepository.findWithFilters(
            eq(Optional.of(SimulationStatus.SIMULATING)), any(), any(), any()
        )).thenReturn(List.of());
        when(simulationRepository.findWithFilters(
            eq(Optional.of(SimulationStatus.WAITING_FOR_DATA)), any(), any(), any()
        )).thenReturn(List.of(sim));

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(0L);

        // When
        handler.onStartup(new StartupEvent());

        // Then — simulation should be failed, through the narrow compare-and-set
        verify(simulationRepository).updateStatus(eq(sim.getSimulationId()),
            eq(SimulationStatus.WAITING_FOR_DATA), eq(SimulationStatus.FAILED), any(), any());
        verify(simulationRepository, never()).save(sim);
        assertEquals(SimulationStatus.FAILED, sim.getStatus());
        verify(dataReadyEvent, never()).fireAsync(any());
    }

    @Test
    void shouldSkipWaitingForDataSimulationWhenBackfillStillInProgress() {
        // Given — simulation waiting, backfill still running
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation sim = createWaitingForDataSimulation(strategyId);

        BackfillJob inProgressJob = mock(BackfillJob.class);
        when(inProgressJob.getStatus()).thenReturn(BackfillStatus.IN_PROGRESS);
        when(backfillJobRepository.findById(sim.getBackfillJobId())).thenReturn(Optional.of(inProgressJob));

        when(simulationRepository.findWithFilters(
            eq(Optional.of(SimulationStatus.SIMULATING)), any(), any(), any()
        )).thenReturn(List.of());
        when(simulationRepository.findWithFilters(
            eq(Optional.of(SimulationStatus.WAITING_FOR_DATA)), any(), any(), any()
        )).thenReturn(List.of(sim));

        // When
        handler.onStartup(new StartupEvent());

        // Then — simulation should NOT be modified
        verify(simulationRepository, never()).save(any());
        verify(dataReadyEvent, never()).fireAsync(any());
        assertEquals(SimulationStatus.WAITING_FOR_DATA, sim.getStatus());
    }

    @Test
    void shouldRecoverWhenBackfillJobIdIsNull() {
        // Given — simulation stuck with no backfillJobId at all, but data exists
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Simulation sim = createWaitingForDataSimulation(strategyId);
        // Simulate the case where backfillJobId was cleared but status wasn't updated
        sim.setBackfillJobId(null);

        Strategy strategy = createStrategy(strategyId);

        when(simulationRepository.findWithFilters(
            eq(Optional.of(SimulationStatus.SIMULATING)), any(), any(), any()
        )).thenReturn(List.of());
        when(simulationRepository.findWithFilters(
            eq(Optional.of(SimulationStatus.WAITING_FOR_DATA)), any(), any(), any()
        )).thenReturn(List.of(sim));

        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(marketEventRepository.countEventsInRange(any(Symbol.class), eq(EventType.PRICE), any(), any()))
            .thenReturn(500L);

        // When
        handler.onStartup(new StartupEvent());

        // Then — should recover since data is available
        verify(simulationRepository).updateStatus(sim.getSimulationId(),
            SimulationStatus.WAITING_FOR_DATA, SimulationStatus.PENDING, null, null);
        verify(simulationRepository, never()).save(sim);
        assertEquals(SimulationStatus.PENDING, sim.getStatus());
        verify(dataReadyEvent).fireAsync(any(SimulationDataReadyEvent.class));
    }
}
