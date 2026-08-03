package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationCreatedEvent;
import com.quorumtrading.application.exception.AccountAccessDeniedException;
import com.quorumtrading.application.exception.EntityNotFoundException;
import com.quorumtrading.application.exception.InsufficientFundsException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.brokeraccount.CreateBrokerAccountUseCase;
import com.quorumtrading.application.usecase.command.CreateBrokerAccountCommand;
import com.quorumtrading.application.usecase.command.CreateBenchmarkCommand;
import com.quorumtrading.application.usecase.dto.CreateBenchmarkResponse;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.CostPreset;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateBenchmarkService following TDD approach.
 * These tests define the expected behavior BEFORE implementation.
 */
class CreateBenchmarkServiceTest {

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private EventPublisher<SimulationCreatedEvent> simulationCreatedEventPublisher;

    @Mock
    private CreateBrokerAccountUseCase createBrokerAccountUseCase;

    @Mock
    private BrokerAccountRepository brokerAccountRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Captor
    private ArgumentCaptor<Benchmark> benchmarkCaptor;

    @Captor
    private ArgumentCaptor<BrokerAccount> brokerAccountCaptor;

    @Captor
    private ArgumentCaptor<Simulation> simulationCaptor;

    @Captor
    private ArgumentCaptor<SimulationCreatedEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<CreateBrokerAccountCommand> brokerAccountCommandCaptor;

    private CreateBenchmarkService useCase;

    // Test user ID for mock account creation
    private static final UserId TEST_USER_ID = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        useCase = new CreateBenchmarkService(
            strategyRepository,
            benchmarkRepository,
            simulationRepository,
            simulationCreatedEventPublisher,
            createBrokerAccountUseCase,
            brokerAccountRepository,
            authorizationService
        );
    }

    @Test
    void shouldCreateBenchmarkWithCustomName() {
        // Given: Command with custom name, 2 valid strategy IDs, backtest dates (legacy mode, no mockConfig)
        UUID strategyId1 = UUID.randomUUID();
        UUID strategyId2 = UUID.randomUUID();
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-30T23:59:59Z");
        String customName = "My Custom Backtest";

        CreateBenchmarkCommand command = createLegacyCommand(
            List.of(strategyId1, strategyId2),
            startDate,
            endDate,
            customName
        );

        Strategy strategy1 = createStrategy(strategyId1, "Strategy 1");
        Strategy strategy2 = createStrategy(strategyId2, "Strategy 2");

        when(strategyRepository.findById(new StrategyId(strategyId1)))
            .thenReturn(Optional.of(strategy1));
        when(strategyRepository.findById(new StrategyId(strategyId2)))
            .thenReturn(Optional.of(strategy2));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        CreateBenchmarkResponse response = useCase.execute(command);

        // Then: Benchmark saved with custom name
        verify(benchmarkRepository).save(benchmarkCaptor.capture());
        Benchmark savedBenchmark = benchmarkCaptor.getValue();
        assertThat(savedBenchmark.getName()).isEqualTo(customName);
        assertThat(savedBenchmark.getStartDate()).isEqualTo(startDate);
        assertThat(savedBenchmark.getEndDate()).isEqualTo(endDate);
        assertThat(savedBenchmark.isBacktest()).isTrue();
        assertThat(savedBenchmark.isLive()).isFalse();

        // Then: 2 simulations created and saved
        verify(simulationRepository, times(2)).save(simulationCaptor.capture());
        List<Simulation> savedSimulations = simulationCaptor.getAllValues();
        assertThat(savedSimulations).hasSize(2);

        // Verify first simulation
        assertThat(savedSimulations.get(0).getStrategyId().value()).isEqualTo(strategyId1);
        assertThat(savedSimulations.get(0).getStartDate()).isEqualTo(startDate);
        assertThat(savedSimulations.get(0).getEndDate()).isEqualTo(endDate);
        assertThat(savedSimulations.get(0).getBenchmarkId()).isNotNull();
        assertThat(savedSimulations.get(0).getBenchmarkId().value())
            .isEqualTo(savedBenchmark.getBenchmarkId().value());

        // Verify second simulation
        assertThat(savedSimulations.get(1).getStrategyId().value()).isEqualTo(strategyId2);
        assertThat(savedSimulations.get(1).getBenchmarkId())
            .isEqualTo(savedSimulations.get(0).getBenchmarkId());

        // Then: 2 SimulationCreatedEvents published
        verify(simulationCreatedEventPublisher, times(2)).publish(eventCaptor.capture());
        List<SimulationCreatedEvent> publishedEvents = eventCaptor.getAllValues();
        assertThat(publishedEvents).hasSize(2);

        // Then: Response contains correct IDs and name
        assertThat(response.benchmarkId()).isNotNull();
        assertThat(response.simulationIds()).hasSize(2);
        assertThat(response.benchmarkName()).isEqualTo(customName);
    }

    @Test
    void shouldGenerateNameWhenNotProvided() {
        // Given: Command with null name, 2 strategies (multi-strategy triggers benchmark), backtest dates
        UUID strategyId1 = UUID.randomUUID();
        UUID strategyId2 = UUID.randomUUID();
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-30T23:59:59Z");

        CreateBenchmarkCommand command = createLegacyCommand(
            List.of(strategyId1, strategyId2),
            startDate,
            endDate,
            null  // No custom name
        );

        when(strategyRepository.findById(new StrategyId(strategyId1)))
            .thenReturn(Optional.of(createStrategy(strategyId1, "Strategy A")));
        when(strategyRepository.findById(new StrategyId(strategyId2)))
            .thenReturn(Optional.of(createStrategy(strategyId2, "Strategy B")));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        CreateBenchmarkResponse response = useCase.execute(command);

        // Then: Benchmark saved with auto-generated name
        verify(benchmarkRepository).save(benchmarkCaptor.capture());
        Benchmark savedBenchmark = benchmarkCaptor.getValue();

        // Expected format: "Backtest: Nov 1, 2025 - Nov 30, 2025"
        String expectedName = Benchmark.generateName(startDate, endDate);
        assertThat(savedBenchmark.getName()).isEqualTo(expectedName);
        assertThat(savedBenchmark.getName()).startsWith("Backtest:");
        assertThat(savedBenchmark.getName()).contains("Nov 1, 2025");
        assertThat(savedBenchmark.getName()).contains("Nov 30, 2025");

        // Then: Response contains generated name
        assertThat(response.benchmarkName()).isEqualTo(expectedName);
    }

    @Test
    void shouldGenerateLiveModeName() {
        // Given: Command with null name, null endDate (live mode), 2 strategies
        UUID strategyId1 = UUID.randomUUID();
        UUID strategyId2 = UUID.randomUUID();
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");

        CreateBenchmarkCommand command = createLegacyCommand(
            List.of(strategyId1, strategyId2),
            startDate,
            null,  // Live mode (no end date)
            null   // No custom name
        );

        when(strategyRepository.findById(new StrategyId(strategyId1)))
            .thenReturn(Optional.of(createStrategy(strategyId1, "Live Strategy A")));
        when(strategyRepository.findById(new StrategyId(strategyId2)))
            .thenReturn(Optional.of(createStrategy(strategyId2, "Live Strategy B")));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        CreateBenchmarkResponse response = useCase.execute(command);

        // Then: Benchmark saved with live name
        verify(benchmarkRepository).save(benchmarkCaptor.capture());
        Benchmark savedBenchmark = benchmarkCaptor.getValue();

        // Expected format: "Live: Started Nov 1, 2025"
        String expectedName = Benchmark.generateName(startDate, null);
        assertThat(savedBenchmark.getName()).isEqualTo(expectedName);
        assertThat(savedBenchmark.getName()).startsWith("Live: Started");
        assertThat(savedBenchmark.getName()).contains("Nov 1, 2025");
        assertThat(savedBenchmark.isLive()).isTrue();
        assertThat(savedBenchmark.isBacktest()).isFalse();

        // Then: Response contains generated live name
        assertThat(response.benchmarkName()).isEqualTo(expectedName);
    }

    @Test
    void shouldCreateStandaloneSimulationForSingleStrategy() {
        // Given: Single strategy — should NOT create a benchmark entity
        UUID strategyId = UUID.randomUUID();
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-30T23:59:59Z");

        CreateBenchmarkCommand command = createLegacyCommand(
            List.of(strategyId),
            startDate,
            endDate,
            null
        );

        Strategy strategy = createStrategy(strategyId, "Solo Strategy");
        when(strategyRepository.findById(any(StrategyId.class)))
            .thenReturn(Optional.of(strategy));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        CreateBenchmarkResponse response = useCase.execute(command);

        // Then: No benchmark saved
        verify(benchmarkRepository, never()).save(any(Benchmark.class));

        // Then: One simulation saved without benchmarkId
        verify(simulationRepository).save(simulationCaptor.capture());
        Simulation savedSimulation = simulationCaptor.getValue();
        assertThat(savedSimulation.getBenchmarkId()).isNull();

        // Then: Response has null benchmarkId
        assertThat(response.benchmarkId()).isNull();
        assertThat(response.simulationIds()).hasSize(1);
    }

    @Test
    void shouldCreateMultipleSimulations() {
        // Given: Command with 5 strategy IDs
        List<UUID> strategyIds = List.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-30T23:59:59Z");

        CreateBenchmarkCommand command = createLegacyCommand(
            strategyIds,
            startDate,
            endDate,
            "Multi-Strategy Backtest"
        );

        // Mock strategy existence for all 5 strategies
        for (UUID strategyId : strategyIds) {
            Strategy strategy = createStrategy(strategyId, "Strategy " + strategyId);
            when(strategyRepository.findById(new StrategyId(strategyId)))
                .thenReturn(Optional.of(strategy));
        }
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        CreateBenchmarkResponse response = useCase.execute(command);

        // Then: 5 simulations created and saved
        verify(simulationRepository, times(5)).save(any(Simulation.class));

        // Then: 5 SimulationCreatedEvents published
        verify(simulationCreatedEventPublisher, times(5)).publish(any(SimulationCreatedEvent.class));

        // Then: Response contains 5 simulation IDs
        assertThat(response.simulationIds()).hasSize(5);

        // Verify all simulations reference different strategies
        verify(simulationRepository, times(5)).save(simulationCaptor.capture());
        List<Simulation> savedSimulations = simulationCaptor.getAllValues();
        Set<UUID> uniqueStrategyIds = new HashSet<>();
        for (Simulation sim : savedSimulations) {
            uniqueStrategyIds.add(sim.getStrategyId().value());
        }
        assertThat(uniqueStrategyIds).hasSize(5);
        assertThat(uniqueStrategyIds).containsExactlyInAnyOrderElementsOf(strategyIds);
    }

    @Test
    void shouldThrowExceptionWhenStrategyNotFound() {
        // Given: Command with non-existent strategy ID
        UUID nonExistentStrategyId = UUID.randomUUID();
        CreateBenchmarkCommand command = createLegacyCommand(
            List.of(nonExistentStrategyId),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Test"
        );

        when(strategyRepository.findById(new StrategyId(nonExistentStrategyId)))
            .thenReturn(Optional.empty());

        // When & Then: Throws IllegalArgumentException with message
        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Strategy not found")
            .hasMessageContaining(nonExistentStrategyId.toString());

        // Verify no saves or events were published
        verify(benchmarkRepository, never()).save(any(Benchmark.class));
        verify(simulationRepository, never()).save(any(Simulation.class));
        verify(simulationCreatedEventPublisher, never()).publish(any(SimulationCreatedEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenAnyStrategyNotFound() {
        // Given: Command with mix of valid and invalid strategy IDs
        UUID validStrategyId = UUID.randomUUID();
        UUID invalidStrategyId = UUID.randomUUID();
        CreateBenchmarkCommand command = createLegacyCommand(
            List.of(validStrategyId, invalidStrategyId),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Test"
        );

        Strategy validStrategy = createStrategy(validStrategyId, "Valid Strategy");
        when(strategyRepository.findById(new StrategyId(validStrategyId)))
            .thenReturn(Optional.of(validStrategy));
        when(strategyRepository.findById(new StrategyId(invalidStrategyId)))
            .thenReturn(Optional.empty());

        // When & Then: Should fail on first invalid strategy
        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Strategy not found");
    }

    @Test
    void shouldLinkSimulationsToBenchmark() {
        // Given: Valid command with 2 strategies
        UUID strategyId1 = UUID.randomUUID();
        UUID strategyId2 = UUID.randomUUID();
        CreateBenchmarkCommand command = createLegacyCommand(
            List.of(strategyId1, strategyId2),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Linked Test"
        );

        Strategy strategy1 = createStrategy(strategyId1, "Strategy 1");
        Strategy strategy2 = createStrategy(strategyId2, "Strategy 2");
        when(strategyRepository.findById(new StrategyId(strategyId1)))
            .thenReturn(Optional.of(strategy1));
        when(strategyRepository.findById(new StrategyId(strategyId2)))
            .thenReturn(Optional.of(strategy2));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        useCase.execute(command);

        // Then: Each simulation has benchmarkId set correctly
        verify(benchmarkRepository).save(benchmarkCaptor.capture());
        Benchmark savedBenchmark = benchmarkCaptor.getValue();
        BenchmarkId benchmarkId = savedBenchmark.getBenchmarkId();

        verify(simulationRepository, times(2)).save(simulationCaptor.capture());
        List<Simulation> savedSimulations = simulationCaptor.getAllValues();

        for (Simulation simulation : savedSimulations) {
            assertThat(simulation.getBenchmarkId())
                .isNotNull()
                .isEqualTo(benchmarkId);
        }
    }

    @Test
    void shouldUseCommandDatesForSimulations() {
        // Given: Command with specific start/end dates
        UUID strategyId = UUID.randomUUID();
        Instant commandStartDate = Instant.parse("2025-06-15T12:30:00Z");
        Instant commandEndDate = Instant.parse("2025-12-25T23:45:59Z");

        CreateBenchmarkCommand command = createLegacyCommand(
            List.of(strategyId),
            commandStartDate,
            commandEndDate,
            "Date Test"
        );

        Strategy strategy = createStrategy(strategyId, "Test Strategy");
        when(strategyRepository.findById(any(StrategyId.class)))
            .thenReturn(Optional.of(strategy));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        useCase.execute(command);

        // Then: Simulations have exact same dates as command
        verify(simulationRepository).save(simulationCaptor.capture());
        Simulation savedSimulation = simulationCaptor.getValue();

        assertThat(savedSimulation.getStartDate()).isEqualTo(commandStartDate);
        assertThat(savedSimulation.getEndDate()).isEqualTo(commandEndDate);
    }

    @Test
    void shouldSetSimulationNamesWithStrategyPrefix() {
        // Given: Command with strategy
        UUID strategyId = UUID.randomUUID();
        String strategyName = "HODL Forever";
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-30T23:59:59Z");

        CreateBenchmarkCommand command = createLegacyCommand(
            List.of(strategyId),
            startDate,
            endDate,
            null
        );

        Strategy strategy = createStrategy(strategyId, strategyName);
        when(strategyRepository.findById(any(StrategyId.class)))
            .thenReturn(Optional.of(strategy));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        useCase.execute(command);

        // Then: Simulation name should contain strategy name
        verify(simulationRepository).save(simulationCaptor.capture());
        Simulation savedSimulation = simulationCaptor.getValue();

        assertThat(savedSimulation.getName())
            .isNotNull()
            .contains(strategyName);
    }

    @Test
    void shouldGenerateUniqueIdsForEachEntity() {
        // Given: Command with 3 strategies
        List<UUID> strategyIds = List.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );

        CreateBenchmarkCommand command = createLegacyCommand(
            strategyIds,
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "ID Test"
        );

        for (UUID strategyId : strategyIds) {
            Strategy strategy = createStrategy(strategyId, "Strategy");
            when(strategyRepository.findById(new StrategyId(strategyId)))
                .thenReturn(Optional.of(strategy));
        }
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        CreateBenchmarkResponse response = useCase.execute(command);

        // Then: All IDs are unique
        assertThat(response.benchmarkId()).isNotNull();
        assertThat(response.simulationIds()).hasSize(3);

        Set<UUID> allIds = new HashSet<>(response.simulationIds());
        allIds.add(response.benchmarkId());

        // Ensure all IDs are unique (set size should equal total IDs)
        assertThat(allIds).hasSize(4); // 1 benchmark + 3 simulations
    }

    // ============================================
    // Mock Broker Account Cloning Tests
    // ============================================

    @Test
    void shouldCreateMockBrokerAccountPerStrategyWhenMockConfigProvided() {
        // Given: Command with mockConfig and 3 strategies
        UUID strategyId1 = UUID.randomUUID();
        UUID strategyId2 = UUID.randomUUID();
        UUID strategyId3 = UUID.randomUUID();
        List<UUID> strategyIds = List.of(strategyId1, strategyId2, strategyId3);
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-30T23:59:59Z");

        MockBrokerConfig mockConfig = MockBrokerConfig.fromPreset(
            new Money(new BigDecimal("100000.00"), Money.Currency.USD),
            CostPreset.ZERO_FEES
        );

        CreateBenchmarkCommand command = new CreateBenchmarkCommand(
            strategyIds,
            startDate,
            endDate,
            "Mock Account Test",
            mockConfig,
            TEST_USER_ID
        );

        Strategy strategy1 = createStrategy(strategyId1, "Strategy 1");
        Strategy strategy2 = createStrategy(strategyId2, "Strategy 2");
        Strategy strategy3 = createStrategy(strategyId3, "Strategy 3");

        when(strategyRepository.findById(new StrategyId(strategyId1))).thenReturn(Optional.of(strategy1));
        when(strategyRepository.findById(new StrategyId(strategyId2))).thenReturn(Optional.of(strategy2));
        when(strategyRepository.findById(new StrategyId(strategyId3))).thenReturn(Optional.of(strategy3));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Mock broker account creation - return different accounts for each call
        when(createBrokerAccountUseCase.execute(any(CreateBrokerAccountCommand.class)))
            .thenAnswer(invocation -> createMockBrokerAccount());

        // When: execute(command)
        CreateBenchmarkResponse response = useCase.execute(command);

        // Then: 3 mock broker accounts created (one per strategy)
        verify(createBrokerAccountUseCase, times(3)).execute(brokerAccountCommandCaptor.capture());
        List<CreateBrokerAccountCommand> accountCommands = brokerAccountCommandCaptor.getAllValues();

        assertThat(accountCommands).hasSize(3);

        // Verify each account command has correct user and mock config
        for (CreateBrokerAccountCommand accountCommand : accountCommands) {
            assertThat(accountCommand.userId()).isEqualTo(TEST_USER_ID);
            assertThat(accountCommand.mockConfig()).isNotNull();
            assertThat(accountCommand.mockConfig().initialCapital().amount())
                .isEqualByComparingTo(new BigDecimal("100000.00"));
        }

        // Verify account names contain strategy names
        assertThat(accountCommands.get(0).name()).contains("Strategy 1");
        assertThat(accountCommands.get(1).name()).contains("Strategy 2");
        assertThat(accountCommands.get(2).name()).contains("Strategy 3");

        // Then: Response has correct simulation count
        assertThat(response.simulationIds()).hasSize(3);
    }

    @Test
    void shouldNotCreateBrokerAccountsWhenMockConfigNotProvided() {
        // Given: Command WITHOUT mockConfig (legacy mode)
        UUID strategyId = UUID.randomUUID();
        CreateBenchmarkCommand command = createLegacyCommand(
            List.of(strategyId),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Legacy Test"
        );

        Strategy strategy = createStrategy(strategyId, "Test Strategy");
        when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        useCase.execute(command);

        // Then: No broker accounts created
        verify(createBrokerAccountUseCase, never()).execute(any(CreateBrokerAccountCommand.class));
    }

    @Test
    void shouldLinkEachSimulationToItsMockBrokerAccount() {
        // Given: Command with mockConfig and 2 strategies
        UUID strategyId1 = UUID.randomUUID();
        UUID strategyId2 = UUID.randomUUID();
        Instant startDate = Instant.parse("2025-11-01T00:00:00Z");
        Instant endDate = Instant.parse("2025-11-30T23:59:59Z");

        MockBrokerConfig mockConfig = MockBrokerConfig.fromPreset(
            new Money(new BigDecimal("50000.00"), Money.Currency.USD),
            CostPreset.TYPICAL_RETAIL
        );

        CreateBenchmarkCommand command = new CreateBenchmarkCommand(
            List.of(strategyId1, strategyId2),
            startDate,
            endDate,
            "Link Test",
            mockConfig,
            TEST_USER_ID
        );

        Strategy strategy1 = createStrategy(strategyId1, "Strategy A");
        Strategy strategy2 = createStrategy(strategyId2, "Strategy B");

        when(strategyRepository.findById(new StrategyId(strategyId1))).thenReturn(Optional.of(strategy1));
        when(strategyRepository.findById(new StrategyId(strategyId2))).thenReturn(Optional.of(strategy2));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Create unique broker accounts for each strategy
        BrokerAccount account1 = createMockBrokerAccount();
        BrokerAccount account2 = createMockBrokerAccount();
        when(createBrokerAccountUseCase.execute(any(CreateBrokerAccountCommand.class)))
            .thenReturn(account1, account2);

        // When: execute(command)
        useCase.execute(command);

        // Then: Each simulation is linked to a different broker account
        verify(simulationRepository, times(2)).save(simulationCaptor.capture());
        List<Simulation> savedSimulations = simulationCaptor.getAllValues();

        assertThat(savedSimulations.get(0).getBrokerAccountId())
            .isNotNull()
            .isEqualTo(account1.getId());
        assertThat(savedSimulations.get(1).getBrokerAccountId())
            .isNotNull()
            .isEqualTo(account2.getId());

        // Verify simulations have different broker accounts
        assertThat(savedSimulations.get(0).getBrokerAccountId())
            .isNotEqualTo(savedSimulations.get(1).getBrokerAccountId());
    }

    @Test
    void shouldSetInitialCashFromMockConfig() {
        // Given: Command with mockConfig specifying $75,000 initial capital
        UUID strategyId = UUID.randomUUID();
        BigDecimal initialCapital = new BigDecimal("75000.00");
        MockBrokerConfig mockConfig = MockBrokerConfig.fromPreset(
            new Money(initialCapital, Money.Currency.USD),
            CostPreset.HIGH_FRICTION
        );

        CreateBenchmarkCommand command = new CreateBenchmarkCommand(
            List.of(strategyId),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Capital Test",
            mockConfig,
            TEST_USER_ID
        );

        Strategy strategy = createStrategy(strategyId, "Test Strategy");
        when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(createBrokerAccountUseCase.execute(any(CreateBrokerAccountCommand.class)))
            .thenReturn(createMockBrokerAccount());

        // When: execute(command)
        useCase.execute(command);

        // Then: Simulation initial cash matches mockConfig
        verify(simulationRepository).save(simulationCaptor.capture());
        Simulation savedSimulation = simulationCaptor.getValue();

        assertThat(savedSimulation.getInitialCash().amount())
            .isEqualByComparingTo(initialCapital);
    }

    @Test
    void shouldUseMockBrokerAccountNameFormat() {
        // Given: Command with specific names
        UUID strategyId = UUID.randomUUID();
        String benchmarkName = "Q4 2025 Backtest";
        String strategyName = "Momentum Alpha";

        MockBrokerConfig mockConfig = MockBrokerConfig.fromPreset(
            new Money(new BigDecimal("100000.00"), Money.Currency.USD),
            CostPreset.ZERO_FEES
        );

        CreateBenchmarkCommand command = new CreateBenchmarkCommand(
            List.of(strategyId),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            benchmarkName,
            mockConfig,
            TEST_USER_ID
        );

        Strategy strategy = createStrategy(strategyId, strategyName);
        when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(createBrokerAccountUseCase.execute(any(CreateBrokerAccountCommand.class)))
            .thenReturn(createMockBrokerAccount());

        // When: execute(command)
        useCase.execute(command);

        // Then: Broker account name follows format "[Benchmark name] - [Strategy name]"
        verify(createBrokerAccountUseCase).execute(brokerAccountCommandCaptor.capture());
        CreateBrokerAccountCommand accountCommand = brokerAccountCommandCaptor.getValue();

        String expectedAccountName = benchmarkName + " - " + strategyName;
        assertThat(accountCommand.name()).isEqualTo(expectedAccountName);
    }

    // ============================================
    // Paper/Real Broker Account Tests (Task 3)
    // ============================================

    @Test
    void shouldThrowNotFoundExceptionWhenBrokerAccountDoesNotExist() {
        // Given: Command with non-existent broker account ID
        UUID strategyId = UUID.randomUUID();
        BrokerAccountId nonExistentAccountId = BrokerAccountId.generate();
        Money capitalPerStrategy = new Money(new BigDecimal("50000.00"), Money.Currency.USD);

        CreateBenchmarkCommand command = new CreateBenchmarkCommand(
            List.of(strategyId),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Paper Trading Test",
            null,  // No mockConfig
            TEST_USER_ID,
            nonExistentAccountId,
            capitalPerStrategy
        );

        Strategy strategy = createStrategy(strategyId, "Test Strategy");
        when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
        when(brokerAccountRepository.findById(nonExistentAccountId)).thenReturn(Optional.empty());

        // When & Then: Throws EntityNotFoundException
        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Broker account not found");

        // Verify no simulations or benchmarks were created
        verify(benchmarkRepository, never()).save(any(Benchmark.class));
        verify(simulationRepository, never()).save(any(Simulation.class));
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenAccountNotOwnedByUser() {
        // Given: Command with broker account owned by different user
        UUID strategyId = UUID.randomUUID();
        BrokerAccountId accountId = BrokerAccountId.generate();
        UserId otherUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        Money capitalPerStrategy = new Money(new BigDecimal("50000.00"), Money.Currency.USD);

        CreateBenchmarkCommand command = new CreateBenchmarkCommand(
            List.of(strategyId),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Paper Trading Test",
            null,  // No mockConfig
            TEST_USER_ID,
            accountId,
            capitalPerStrategy
        );

        Strategy strategy = createStrategy(strategyId, "Test Strategy");
        BrokerAccount accountOwnedByOther = createPaperBrokerAccount(accountId, otherUserId,
            new Money(new BigDecimal("100000.00"), Money.Currency.USD));

        when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
        when(brokerAccountRepository.findById(accountId)).thenReturn(Optional.of(accountOwnedByOther));

        // When & Then: Throws AccountAccessDeniedException
        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(AccountAccessDeniedException.class)
            .hasMessageContaining("does not have access");

        // Verify no simulations or benchmarks were created
        verify(benchmarkRepository, never()).save(any(Benchmark.class));
        verify(simulationRepository, never()).save(any(Simulation.class));
    }

    @Test
    void shouldThrowInsufficientFundsExceptionWhenNotEnoughCapital() {
        // Given: Account has $100k, but requesting 3 strategies x $50k = $150k required
        UUID strategyId1 = UUID.randomUUID();
        UUID strategyId2 = UUID.randomUUID();
        UUID strategyId3 = UUID.randomUUID();
        BrokerAccountId accountId = BrokerAccountId.generate();
        Money capitalPerStrategy = new Money(new BigDecimal("50000.00"), Money.Currency.USD);

        CreateBenchmarkCommand command = new CreateBenchmarkCommand(
            List.of(strategyId1, strategyId2, strategyId3),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Paper Trading Test",
            null,  // No mockConfig
            TEST_USER_ID,
            accountId,
            capitalPerStrategy
        );

        Strategy strategy1 = createStrategy(strategyId1, "Strategy 1");
        Strategy strategy2 = createStrategy(strategyId2, "Strategy 2");
        Strategy strategy3 = createStrategy(strategyId3, "Strategy 3");
        BrokerAccount accountWithLimitedCapital = createPaperBrokerAccount(accountId, TEST_USER_ID,
            new Money(new BigDecimal("100000.00"), Money.Currency.USD));

        when(strategyRepository.findById(new StrategyId(strategyId1))).thenReturn(Optional.of(strategy1));
        when(strategyRepository.findById(new StrategyId(strategyId2))).thenReturn(Optional.of(strategy2));
        when(strategyRepository.findById(new StrategyId(strategyId3))).thenReturn(Optional.of(strategy3));
        when(brokerAccountRepository.findById(accountId)).thenReturn(Optional.of(accountWithLimitedCapital));

        // When & Then: Throws InsufficientFundsException
        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(InsufficientFundsException.class)
            .hasMessageContaining("Insufficient funds")
            .hasMessageContaining("150000")  // Required
            .hasMessageContaining("100000"); // Available

        // Verify no simulations or benchmarks were created
        verify(benchmarkRepository, never()).save(any(Benchmark.class));
        verify(simulationRepository, never()).save(any(Simulation.class));
    }

    @Test
    void shouldCreateSimulationsWithSharedBrokerAccountWhenSufficientCapital() {
        // Given: Account has $100k, requesting 2 strategies x $40k = $80k required (sufficient)
        UUID strategyId1 = UUID.randomUUID();
        UUID strategyId2 = UUID.randomUUID();
        BrokerAccountId accountId = BrokerAccountId.generate();
        Money capitalPerStrategy = new Money(new BigDecimal("40000.00"), Money.Currency.USD);

        CreateBenchmarkCommand command = new CreateBenchmarkCommand(
            List.of(strategyId1, strategyId2),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Paper Trading Test",
            null,  // No mockConfig
            TEST_USER_ID,
            accountId,
            capitalPerStrategy
        );

        Strategy strategy1 = createStrategy(strategyId1, "Strategy 1");
        Strategy strategy2 = createStrategy(strategyId2, "Strategy 2");
        BrokerAccount accountWithCapital = createPaperBrokerAccount(accountId, TEST_USER_ID,
            new Money(new BigDecimal("100000.00"), Money.Currency.USD));

        when(strategyRepository.findById(new StrategyId(strategyId1))).thenReturn(Optional.of(strategy1));
        when(strategyRepository.findById(new StrategyId(strategyId2))).thenReturn(Optional.of(strategy2));
        when(brokerAccountRepository.findById(accountId)).thenReturn(Optional.of(accountWithCapital));
        when(brokerAccountRepository.save(any(BrokerAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        CreateBenchmarkResponse response = useCase.execute(command);

        // Then: 2 simulations created
        assertThat(response.simulationIds()).hasSize(2);

        // Then: Both simulations share the same broker account
        verify(simulationRepository, times(2)).save(simulationCaptor.capture());
        List<Simulation> savedSimulations = simulationCaptor.getAllValues();

        assertThat(savedSimulations.get(0).getBrokerAccountId()).isEqualTo(accountId);
        assertThat(savedSimulations.get(1).getBrokerAccountId()).isEqualTo(accountId);

        // Then: Each simulation has tradingLimit = capitalPerStrategy
        assertThat(savedSimulations.get(0).getTradingLimit().amount())
            .isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(savedSimulations.get(1).getTradingLimit().amount())
            .isEqualByComparingTo(new BigDecimal("40000.00"));

        // Then: Capital was reserved on the account
        verify(brokerAccountRepository).save(brokerAccountCaptor.capture());
        BrokerAccount savedAccount = brokerAccountCaptor.getValue();
        assertThat(savedAccount.getReservedBalance().amount())
            .isEqualByComparingTo(new BigDecimal("80000.00"));  // 2 x 40k

        // Then: No mock broker accounts were created
        verify(createBrokerAccountUseCase, never()).execute(any(CreateBrokerAccountCommand.class));
    }

    @Test
    void shouldReserveCapitalOnBrokerAccountBeforeCreatingSimulations() {
        // Given: Single strategy with paper account
        UUID strategyId = UUID.randomUUID();
        BrokerAccountId accountId = BrokerAccountId.generate();
        Money capitalPerStrategy = new Money(new BigDecimal("25000.00"), Money.Currency.USD);

        CreateBenchmarkCommand command = new CreateBenchmarkCommand(
            List.of(strategyId),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Single Paper Trading Test",
            null,
            TEST_USER_ID,
            accountId,
            capitalPerStrategy
        );

        Strategy strategy = createStrategy(strategyId, "Test Strategy");
        BrokerAccount paperAccount = createPaperBrokerAccount(accountId, TEST_USER_ID,
            new Money(new BigDecimal("50000.00"), Money.Currency.USD));

        when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
        when(brokerAccountRepository.findById(accountId)).thenReturn(Optional.of(paperAccount));
        when(brokerAccountRepository.save(any(BrokerAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        useCase.execute(command);

        // Then: Account save called with reserved capital
        verify(brokerAccountRepository).save(brokerAccountCaptor.capture());
        BrokerAccount accountAfterReservation = brokerAccountCaptor.getValue();
        assertThat(accountAfterReservation.getReservedBalance().amount())
            .isEqualByComparingTo(new BigDecimal("25000.00"));
        assertThat(accountAfterReservation.getAvailableBalance().amount())
            .isEqualByComparingTo(new BigDecimal("25000.00"));
    }

    @Test
    void shouldSetInitialCashFromCapitalPerStrategy() {
        // Given: Paper account with capitalPerStrategy
        UUID strategyId = UUID.randomUUID();
        BrokerAccountId accountId = BrokerAccountId.generate();
        Money capitalPerStrategy = new Money(new BigDecimal("35000.00"), Money.Currency.USD);

        CreateBenchmarkCommand command = new CreateBenchmarkCommand(
            List.of(strategyId),
            Instant.parse("2025-11-01T00:00:00Z"),
            Instant.parse("2025-11-30T23:59:59Z"),
            "Initial Cash Test",
            null,
            TEST_USER_ID,
            accountId,
            capitalPerStrategy
        );

        Strategy strategy = createStrategy(strategyId, "Test Strategy");
        BrokerAccount paperAccount = createPaperBrokerAccount(accountId, TEST_USER_ID,
            new Money(new BigDecimal("100000.00"), Money.Currency.USD));

        when(strategyRepository.findById(any(StrategyId.class))).thenReturn(Optional.of(strategy));
        when(brokerAccountRepository.findById(accountId)).thenReturn(Optional.of(paperAccount));
        when(brokerAccountRepository.save(any(BrokerAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(benchmarkRepository.save(any(Benchmark.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRepository.save(any(Simulation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: execute(command)
        useCase.execute(command);

        // Then: Simulation's initialCash = capitalPerStrategy
        verify(simulationRepository).save(simulationCaptor.capture());
        Simulation savedSimulation = simulationCaptor.getValue();
        assertThat(savedSimulation.getInitialCash().amount())
            .isEqualByComparingTo(new BigDecimal("35000.00"));
    }

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Creates a legacy command without mockConfig (for backward compatibility tests).
     */
    private CreateBenchmarkCommand createLegacyCommand(
            List<UUID> strategyIds,
            Instant startDate,
            Instant endDate,
            String name) {
        return new CreateBenchmarkCommand(
            strategyIds,
            startDate,
            endDate,
            name,
            null,  // No mockConfig
            null   // No userId needed when mockConfig is null
        );
    }

    /**
     * Creates a mock BrokerAccount for testing.
     */
    private BrokerAccount createMockBrokerAccount() {
        return BrokerAccount.createMock(
            TEST_USER_ID,
            "Test Mock Account",
            MockBrokerConfig.fromPreset(
                new Money(new BigDecimal("100000.00"), Money.Currency.USD),
                CostPreset.ZERO_FEES
            )
        );
    }

    /**
     * Creates a paper trading BrokerAccount for testing.
     */
    private BrokerAccount createPaperBrokerAccount(BrokerAccountId accountId, UserId userId, Money balance) {
        return BrokerAccount.createReal(
            userId,
            "Test Paper Account",
            BrokerType.ALPACA_PAPER,
            new ApiKeyCredentials("test-api-key", "test-api-secret"),
            balance,
            null
        );
    }

    /**
     * Creates a test Strategy.
     */
    private Strategy createStrategy(UUID strategyId, String name) {
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        return new Strategy(
            new StrategyId(strategyId),
            name,
            null,  // description
            1,  // version
            "---\nversion: v1.0\n---\n# " + name,  // content
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
}
