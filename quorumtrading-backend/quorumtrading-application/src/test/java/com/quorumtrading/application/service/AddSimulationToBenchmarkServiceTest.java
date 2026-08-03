package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.AddSimulationToBenchmarkCommand;
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

class AddSimulationToBenchmarkServiceTest {

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private AuthorizationService authorizationService;

    private AddSimulationToBenchmarkService service;

    private static final UserId USER_ID = TestConstants.TEST_USER_ID;
    private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2025-06-30T23:59:59Z");
    private static final Money CAPITAL = Money.usd(new BigDecimal("100000"));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AddSimulationToBenchmarkService(
            benchmarkRepository, simulationRepository, strategyRepository, authorizationService);
    }

    @Test
    void shouldAddSimulationToEmptyBenchmark() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());

        Benchmark benchmark = createBenchmark(benchmarkId);
        Simulation simulation = createSimulation(simulationId, strategyId, CAPITAL, START, END);
        Strategy strategy = createStrategy(strategyId);

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByBenchmarkId(benchmarkId)).thenReturn(List.of());
        // updateBenchmarkId is void — no stubbing needed

        // When
        service.execute(new AddSimulationToBenchmarkCommand(simulationId, benchmarkId, USER_ID));

        // Then
        assertThat(simulation.getBenchmarkId()).isEqualTo(benchmarkId);
        verify(simulationRepository).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldAddSimulationWithMatchingParameters() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        SimulationId existingSimId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        StrategyId existingStrategyId = new StrategyId(UUID.randomUUID());

        Benchmark benchmark = createBenchmark(benchmarkId);
        Simulation simulation = createSimulation(simulationId, strategyId, CAPITAL, START, END);
        Simulation existingSim = createSimulation(existingSimId, existingStrategyId, CAPITAL, START, END);
        existingSim.assignToBenchmark(benchmarkId);
        Strategy strategy = createStrategy(strategyId);

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByBenchmarkId(benchmarkId)).thenReturn(List.of(existingSim));
        // updateBenchmarkId is void — no stubbing needed

        // When
        service.execute(new AddSimulationToBenchmarkCommand(simulationId, benchmarkId, USER_ID));

        // Then
        assertThat(simulation.getBenchmarkId()).isEqualTo(benchmarkId);
        verify(simulationRepository).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldRejectMismatchedInitialCash() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        SimulationId existingSimId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());

        Money differentCapital = Money.usd(new BigDecimal("50000"));

        Benchmark benchmark = createBenchmark(benchmarkId);
        Simulation simulation = createSimulation(simulationId, strategyId, differentCapital, START, END);
        Simulation existingSim = createSimulation(existingSimId, new StrategyId(UUID.randomUUID()), CAPITAL, START, END);
        existingSim.assignToBenchmark(benchmarkId);
        Strategy strategy = createStrategy(strategyId);

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByBenchmarkId(benchmarkId)).thenReturn(List.of(existingSim));

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new AddSimulationToBenchmarkCommand(simulationId, benchmarkId, USER_ID)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("initial capital")
            .hasMessageContaining("50000")
            .hasMessageContaining("100000");

        verify(simulationRepository, never()).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldRejectMismatchedStartDate() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        SimulationId existingSimId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());

        Instant differentStart = Instant.parse("2025-06-01T00:00:00Z");

        Benchmark benchmark = createBenchmark(benchmarkId);
        Simulation simulation = createSimulation(simulationId, strategyId, CAPITAL, differentStart, END);
        Simulation existingSim = createSimulation(existingSimId, new StrategyId(UUID.randomUUID()), CAPITAL, START, END);
        existingSim.assignToBenchmark(benchmarkId);
        Strategy strategy = createStrategy(strategyId);

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByBenchmarkId(benchmarkId)).thenReturn(List.of(existingSim));

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new AddSimulationToBenchmarkCommand(simulationId, benchmarkId, USER_ID)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("starts")
            .hasMessageContaining("2025-06-01")
            .hasMessageContaining("2025-01-01");

        verify(simulationRepository, never()).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldRejectMismatchedEndDate() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        SimulationId existingSimId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());

        Instant differentEnd = Instant.parse("2025-12-31T23:59:59Z");

        Benchmark benchmark = createBenchmark(benchmarkId);
        Simulation simulation = createSimulation(simulationId, strategyId, CAPITAL, START, differentEnd);
        Simulation existingSim = createSimulation(existingSimId, new StrategyId(UUID.randomUUID()), CAPITAL, START, END);
        existingSim.assignToBenchmark(benchmarkId);
        Strategy strategy = createStrategy(strategyId);

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByBenchmarkId(benchmarkId)).thenReturn(List.of(existingSim));

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new AddSimulationToBenchmarkCommand(simulationId, benchmarkId, USER_ID)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ends")
            .hasMessageContaining("2025-12-31")
            .hasMessageContaining("2025-06-30");

        verify(simulationRepository, never()).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldRejectWhenSimulationAlreadyInDifferentBenchmark() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        BenchmarkId otherBenchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());

        Benchmark benchmark = createBenchmark(benchmarkId);
        Simulation simulation = createSimulation(simulationId, strategyId, CAPITAL, START, END);
        simulation.assignToBenchmark(otherBenchmarkId); // Already assigned elsewhere
        Strategy strategy = createStrategy(strategyId);

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(simulationRepository.findByBenchmarkId(benchmarkId)).thenReturn(List.of());

        // When / Then — domain method rejects reassignment
        assertThatThrownBy(() -> service.execute(
                new AddSimulationToBenchmarkCommand(simulationId, benchmarkId, USER_ID)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already assigned");

        verify(simulationRepository, never()).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldThrowWhenBenchmarkNotFound() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new AddSimulationToBenchmarkCommand(simulationId, benchmarkId, USER_ID)))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Benchmark not found");

        verify(simulationRepository, never()).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldThrowWhenSimulationNotFound() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());

        Benchmark benchmark = createBenchmark(benchmarkId);

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new AddSimulationToBenchmarkCommand(simulationId, benchmarkId, USER_ID)))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Simulation not found");

        verify(simulationRepository, never()).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldThrowWhenUserDoesNotOwnSimulation() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        StrategyId strategyId = new StrategyId(UUID.randomUUID());

        Benchmark benchmark = createBenchmark(benchmarkId);
        Simulation simulation = createSimulation(simulationId, strategyId, CAPITAL, START, END);
        Strategy strategy = createStrategy(strategyId);

        when(benchmarkRepository.findById(benchmarkId)).thenReturn(Optional.of(benchmark));
        when(simulationRepository.findById(simulationId)).thenReturn(Optional.of(simulation));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        doThrow(new AccessDeniedException(USER_ID, "Simulation", simulationId.value().toString()))
            .when(authorizationService).requireEdit(eq(USER_ID), eq(simulation), eq(strategy));

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new AddSimulationToBenchmarkCommand(simulationId, benchmarkId, USER_ID)))
            .isInstanceOf(AccessDeniedException.class);

        verify(simulationRepository, never()).updateBenchmarkId(any(), any());
    }

    // === Helper methods ===

    private Benchmark createBenchmark(BenchmarkId benchmarkId) {
        return new Benchmark(benchmarkId, "Test Benchmark", START, END, Instant.now(), USER_ID);
    }

    private Simulation createSimulation(SimulationId simulationId, StrategyId strategyId,
                                         Money initialCash, Instant startDate, Instant endDate) {
        return new Simulation(simulationId, "Test Sim", strategyId, startDate, endDate, initialCash);
    }

    private Strategy createStrategy(StrategyId strategyId) {
        return new Strategy(strategyId, "Test Strategy", "content", USER_ID);
    }
}
