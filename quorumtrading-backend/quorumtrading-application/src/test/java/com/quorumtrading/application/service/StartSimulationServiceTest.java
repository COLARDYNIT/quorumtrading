package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.SimulationExecutor;
import com.quorumtrading.application.port.out.AsyncExecutionPort;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.StartSimulationCommand;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartSimulationServiceTest {

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private SimulationExecutor simulationExecutor;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AsyncExecutionPort asyncExecutionPort;

    private StartSimulationService service;

    private static final String STRATEGY_CONTENT = """
        ---
        version: v1.0
        consensus_method: MAJORITY
        stock_tickers:
          - AAPL
        ---
        # Test Strategy
        Buy when RSI < 30.
        """;

    @BeforeEach
    void setUp() {
        // AsyncExecutionPort runs tasks synchronously in tests for deterministic behavior.
        // Lenient because tests that return early (idempotency) won't invoke submitAsync.
        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(asyncExecutionPort).submitAsync(any(Runnable.class));

        service = new StartSimulationService(
            simulationRepository,
            strategyRepository,
            portfolioRepository,
            simulationExecutor,
            authorizationService,
            asyncExecutionPort
        );
    }

    @Test
    void shouldCreateInitialSnapshotWhenStartingSimulation() {
        // Given
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Money initialCash = Money.usd(new BigDecimal("100000"));
        Instant startDate = Instant.now();

        Simulation simulation = new Simulation(
            simulationId,
            "Test Simulation",
            strategyId,
            startDate,
            startDate.plusSeconds(86400), // 1 day
            initialCash
        );

        Strategy strategy = new Strategy(strategyId, "Test Strategy", STRATEGY_CONTENT, ownerId);

        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(portfolioRepository.findLatestBySimulationId(simulationId)).thenReturn(Optional.empty());
        when(portfolioRepository.save(any(PortfolioSnapshot.class))).thenAnswer(i -> i.getArgument(0));

        StartSimulationCommand command = StartSimulationCommand.system(simulationId);

        // When
        service.execute(command);

        // Then - verify initial snapshot was created with correct values
        ArgumentCaptor<PortfolioSnapshot> snapshotCaptor = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(portfolioRepository).save(snapshotCaptor.capture());

        PortfolioSnapshot createdSnapshot = snapshotCaptor.getValue();
        assertThat(createdSnapshot.getSimulationId()).isEqualTo(simulationId);
        assertThat(createdSnapshot.getTimestamp()).isEqualTo(startDate);
        assertThat(createdSnapshot.getPositions()).isEmpty();
        assertThat(createdSnapshot.getCashAvailable()).isEqualTo(initialCash);
        assertThat(createdSnapshot.getTotalValue()).isEqualTo(initialCash);

        // Verify executor was called
        verify(simulationExecutor).startSimulation(any(Simulation.class), eq(STRATEGY_CONTENT));
    }

    @Test
    void shouldPersistUnpauseState_WhenStartingSimulation() {
        // Given - simulation created with paused=true (as in benchmark flow)
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Money initialCash = Money.usd(new BigDecimal("100000"));
        Instant startDate = Instant.now();

        Simulation simulation = new Simulation(
            simulationId,
            "Test Simulation",
            strategyId,
            startDate,
            startDate.plusSeconds(86400),
            initialCash
        );

        Strategy strategy = new Strategy(strategyId, "Test Strategy", STRATEGY_CONTENT, ownerId);

        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(portfolioRepository.findLatestBySimulationId(simulationId)).thenReturn(Optional.empty());
        when(portfolioRepository.save(any(PortfolioSnapshot.class))).thenAnswer(i -> i.getArgument(0));

        StartSimulationCommand command = StartSimulationCommand.system(simulationId);

        // When
        service.execute(command);

        // Then - verify updatePauseState is called to explicitly persist unpause
        verify(simulationRepository).updatePauseState(simulationId, false, false, null);
    }

    @Test
    void shouldSkipSnapshotCreationIfAlreadyExists() {
        // Given
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId ownerId = new UserId(UUID.randomUUID());
        Money initialCash = Money.usd(new BigDecimal("100000"));
        Instant startDate = Instant.now();

        Simulation simulation = new Simulation(
            simulationId,
            "Test Simulation",
            strategyId,
            startDate,
            startDate.plusSeconds(86400),
            initialCash
        );

        Strategy strategy = new Strategy(strategyId, "Test Strategy", STRATEGY_CONTENT, ownerId);

        // Existing snapshot
        PortfolioSnapshot existingSnapshot = new PortfolioSnapshot(
            simulationId,
            startDate,
            java.util.Map.of(),
            initialCash,
            initialCash
        );

        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(portfolioRepository.findLatestBySimulationId(simulationId)).thenReturn(Optional.of(existingSnapshot));

        StartSimulationCommand command = StartSimulationCommand.system(simulationId);

        // When
        service.execute(command);

        // Then - no new snapshot should be saved (idempotency)
        verify(portfolioRepository, never()).save(any(PortfolioSnapshot.class));

        // Executor should still be called
        verify(simulationExecutor).startSimulation(any(Simulation.class), eq(STRATEGY_CONTENT));
    }

    @Test
    void shouldSkipIfSimulationAlreadyStarted() {
        // Given
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Money initialCash = Money.usd(new BigDecimal("100000"));
        Instant startDate = Instant.now();

        Simulation simulation = new Simulation(
            simulationId,
            "Test Simulation",
            strategyId,
            startDate,
            startDate.plusSeconds(86400),
            initialCash
        );
        // Manually set to LIVE to simulate already started
        simulation.start();

        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));

        StartSimulationCommand command = StartSimulationCommand.system(simulationId);

        // When
        service.execute(command);

        // Then - nothing should happen (idempotency)
        verify(portfolioRepository, never()).findLatestBySimulationId(any());
        verify(portfolioRepository, never()).save(any());
        verify(simulationExecutor, never()).startSimulation(any(), any());
    }

    @Test
    void shouldSkipIfSimulationIsWaitingForData() {
        // Given - simulation is in WAITING_FOR_DATA state (backfill in progress)
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Money initialCash = Money.usd(new BigDecimal("100000"));
        Instant startDate = Instant.now();

        Simulation simulation = new Simulation(
            simulationId,
            "Test Simulation",
            strategyId,
            startDate,
            startDate.plusSeconds(86400),
            initialCash
        );
        // Transition to WAITING_FOR_DATA (simulating backfill in progress)
        simulation.waitForData(UUID.randomUUID());

        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));

        StartSimulationCommand command = StartSimulationCommand.system(simulationId);

        // When
        service.execute(command);

        // Then - nothing should happen; simulation must wait for backfill to complete
        verify(portfolioRepository, never()).findLatestBySimulationId(any());
        verify(portfolioRepository, never()).save(any());
        verify(simulationExecutor, never()).startSimulation(any(), any());
        verify(simulationRepository, never()).save(any());
    }
}
