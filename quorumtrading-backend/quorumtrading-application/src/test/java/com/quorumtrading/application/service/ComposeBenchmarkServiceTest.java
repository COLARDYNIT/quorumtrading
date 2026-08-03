package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.ComposeBenchmarkCommand;
import com.quorumtrading.application.usecase.dto.ComposeBenchmarkResponse;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ComposeBenchmarkServiceTest {

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private AuthorizationService authorizationService;

    private ComposeBenchmarkService service;

    private static final UserId USER_ID = TestConstants.TEST_USER_ID;
    private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2025-06-30T23:59:59Z");
    private static final Money CAPITAL = Money.usd(new BigDecimal("100000"));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ComposeBenchmarkService(
            benchmarkRepository, simulationRepository, strategyRepository, authorizationService);
    }

    @Test
    void shouldCreateNewBenchmarkFromSimulations() {
        // Given
        SimulationId simId1 = new SimulationId(UUID.randomUUID());
        SimulationId simId2 = new SimulationId(UUID.randomUUID());
        StrategyId stratId1 = new StrategyId(UUID.randomUUID());
        StrategyId stratId2 = new StrategyId(UUID.randomUUID());

        Simulation sim1 = createSimulation(simId1, stratId1, START, END);
        Simulation sim2 = createSimulation(simId2, stratId2,
            Instant.parse("2025-02-01T00:00:00Z"), Instant.parse("2025-05-31T23:59:59Z"));
        Strategy strat1 = createStrategy(stratId1);
        Strategy strat2 = createStrategy(stratId2);

        when(simulationRepository.findById(simId1)).thenReturn(Optional.of(sim1));
        when(simulationRepository.findById(simId2)).thenReturn(Optional.of(sim2));
        when(strategyRepository.findById(stratId1)).thenReturn(Optional.of(strat1));
        when(strategyRepository.findById(stratId2)).thenReturn(Optional.of(strat2));
        // Return 2 sims when querying by benchmark
        when(simulationRepository.findByBenchmarkId(any(BenchmarkId.class))).thenReturn(List.of(sim1, sim2));

        // When
        ComposeBenchmarkResponse response = service.execute(
            new ComposeBenchmarkCommand(List.of(simId1, simId2), null, "My Benchmark", USER_ID));

        // Then
        assertThat(response.benchmarkId()).isNotNull();
        assertThat(response.name()).isEqualTo("My Benchmark");
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.addedCount()).isEqualTo(2);

        verify(benchmarkRepository).save(any(Benchmark.class));
        verify(simulationRepository, times(2)).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldAddSimulationsToExistingBenchmark() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId stratId = new StrategyId(UUID.randomUUID());

        Benchmark benchmark = createBenchmark(benchmarkId);
        Simulation sim = createSimulation(simId, stratId, START, END);
        Strategy strat = createStrategy(stratId);

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findById(simId)).thenReturn(Optional.of(sim));
        when(strategyRepository.findById(stratId)).thenReturn(Optional.of(strat));
        when(simulationRepository.findByBenchmarkId(benchmarkId)).thenReturn(List.of(sim));

        // When
        ComposeBenchmarkResponse response = service.execute(
            new ComposeBenchmarkCommand(List.of(simId), benchmarkId, null, USER_ID));

        // Then
        assertThat(response.benchmarkId()).isEqualTo(benchmarkId.value());
        assertThat(response.name()).isEqualTo("Test Benchmark");
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.addedCount()).isEqualTo(1);

        verify(benchmarkRepository, never()).save(any()); // Existing benchmark, no save
        verify(simulationRepository).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldSkipAlreadyAssignedSimulations() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId stratId = new StrategyId(UUID.randomUUID());

        Benchmark benchmark = createBenchmark(benchmarkId);
        Simulation sim = createSimulation(simId, stratId, START, END);
        sim.assignToBenchmark(benchmarkId); // Already assigned to this benchmark
        Strategy strat = createStrategy(stratId);

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findById(simId)).thenReturn(Optional.of(sim));
        when(strategyRepository.findById(stratId)).thenReturn(Optional.of(strat));
        when(simulationRepository.findByBenchmarkId(benchmarkId)).thenReturn(List.of(sim));

        // When
        ComposeBenchmarkResponse response = service.execute(
            new ComposeBenchmarkCommand(List.of(simId), benchmarkId, null, USER_ID));

        // Then
        assertThat(response.addedCount()).isEqualTo(0);
        assertThat(response.totalCount()).isEqualTo(1);

        verify(simulationRepository, never()).updateBenchmarkId(any(), any()); // Skipped
    }

    @Test
    void shouldThrowWhenSimulationNotFound() {
        // Given
        SimulationId simId = new SimulationId(UUID.randomUUID());
        when(simulationRepository.findById(simId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new ComposeBenchmarkCommand(List.of(simId), null, null, USER_ID)))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Simulation not found");
    }

    @Test
    void shouldThrowWhenBenchmarkNotFound() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId stratId = new StrategyId(UUID.randomUUID());

        Simulation sim = createSimulation(simId, stratId, START, END);
        Strategy strat = createStrategy(stratId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(sim));
        when(strategyRepository.findById(stratId)).thenReturn(Optional.of(strat));
        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new ComposeBenchmarkCommand(List.of(simId), benchmarkId, null, USER_ID)))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Benchmark not found");
    }

    @Test
    void shouldThrowWhenUserLacksEditPermission() {
        // Given
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId stratId = new StrategyId(UUID.randomUUID());

        Simulation sim = createSimulation(simId, stratId, START, END);
        Strategy strat = createStrategy(stratId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(sim));
        when(strategyRepository.findById(stratId)).thenReturn(Optional.of(strat));
        doThrow(new AccessDeniedException(USER_ID, "Simulation", simId.value().toString()))
            .when(authorizationService).requireEdit(eq(USER_ID), eq(sim), eq(strat));

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new ComposeBenchmarkCommand(List.of(simId), null, null, USER_ID)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldThrowWhenUserLacksDeletePermissionOnBenchmark() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId stratId = new StrategyId(UUID.randomUUID());

        Benchmark benchmark = createBenchmark(benchmarkId);
        Simulation sim = createSimulation(simId, stratId, START, END);
        Strategy strat = createStrategy(stratId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(sim));
        when(strategyRepository.findById(stratId)).thenReturn(Optional.of(strat));
        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        doThrow(new AccessDeniedException(USER_ID, "Benchmark", benchmarkId.value().toString()))
            .when(authorizationService).requireDelete(eq(USER_ID), eq(benchmark));

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new ComposeBenchmarkCommand(List.of(simId), benchmarkId, null, USER_ID)))
            .isInstanceOf(AccessDeniedException.class);

        verify(simulationRepository, never()).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldAutoGenerateNameWhenNullForNewBenchmark() {
        // Given
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId stratId = new StrategyId(UUID.randomUUID());

        Simulation sim = createSimulation(simId, stratId, START, END);
        Strategy strat = createStrategy(stratId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(sim));
        when(strategyRepository.findById(stratId)).thenReturn(Optional.of(strat));
        when(simulationRepository.findByBenchmarkId(any(BenchmarkId.class))).thenReturn(List.of(sim));

        ArgumentCaptor<Benchmark> benchmarkCaptor = ArgumentCaptor.forClass(Benchmark.class);

        // When
        ComposeBenchmarkResponse response = service.execute(
            new ComposeBenchmarkCommand(List.of(simId), null, null, USER_ID));

        // Then
        verify(benchmarkRepository).save(benchmarkCaptor.capture());
        Benchmark saved = benchmarkCaptor.getValue();
        assertThat(saved.getName()).isNotNull();
        assertThat(saved.getName()).isNotBlank();
        assertThat(response.name()).isEqualTo(saved.getName());
    }

    // === Helper methods ===

    private Benchmark createBenchmark(BenchmarkId benchmarkId) {
        return new Benchmark(benchmarkId, "Test Benchmark", START, END, Instant.now(), USER_ID);
    }

    private Simulation createSimulation(SimulationId simulationId, StrategyId strategyId,
                                         Instant startDate, Instant endDate) {
        return new Simulation(simulationId, "Test Sim", strategyId, startDate, endDate, CAPITAL);
    }

    private Strategy createStrategy(StrategyId strategyId) {
        return new Strategy(strategyId, "Test Strategy", "content", USER_ID);
    }
}
