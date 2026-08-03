package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.RemoveSimulationFromBenchmarkCommand;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RemoveSimulationFromBenchmarkServiceTest {

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private AuthorizationService authorizationService;

    private RemoveSimulationFromBenchmarkService service;

    private static final UserId USER_ID = TestConstants.TEST_USER_ID;
    private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2025-06-30T23:59:59Z");
    private static final Money CAPITAL = Money.usd(new BigDecimal("100000"));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RemoveSimulationFromBenchmarkService(
            simulationRepository, strategyRepository, authorizationService);
    }

    @Test
    void shouldRemoveSimulationFromBenchmark() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId stratId = new StrategyId(UUID.randomUUID());

        Simulation sim = createSimulation(simId, stratId);
        sim.assignToBenchmark(benchmarkId);
        Strategy strat = createStrategy(stratId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(sim));
        when(strategyRepository.findById(stratId)).thenReturn(Optional.of(strat));
        // When
        service.execute(new RemoveSimulationFromBenchmarkCommand(simId, USER_ID));

        // Then
        assertThat(sim.getBenchmarkId()).isNull();
        verify(simulationRepository).updateBenchmarkId(simId, null);
    }

    @Test
    void shouldThrowWhenSimulationNotInAnyBenchmark() {
        // Given
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId stratId = new StrategyId(UUID.randomUUID());

        Simulation sim = createSimulation(simId, stratId); // No benchmark assigned
        Strategy strat = createStrategy(stratId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(sim));
        when(strategyRepository.findById(stratId)).thenReturn(Optional.of(strat));

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new RemoveSimulationFromBenchmarkCommand(simId, USER_ID)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not assigned to any benchmark");

        verify(simulationRepository, never()).updateBenchmarkId(any(), any());
    }

    @Test
    void shouldThrowWhenSimulationNotFound() {
        // Given
        SimulationId simId = new SimulationId(UUID.randomUUID());
        when(simulationRepository.findById(simId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new RemoveSimulationFromBenchmarkCommand(simId, USER_ID)))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Simulation not found");
    }

    @Test
    void shouldThrowWhenUserLacksPermission() {
        // Given
        BenchmarkId benchmarkId = new BenchmarkId(UUID.randomUUID());
        SimulationId simId = new SimulationId(UUID.randomUUID());
        StrategyId stratId = new StrategyId(UUID.randomUUID());

        Simulation sim = createSimulation(simId, stratId);
        sim.assignToBenchmark(benchmarkId);
        Strategy strat = createStrategy(stratId);

        when(simulationRepository.findById(simId)).thenReturn(Optional.of(sim));
        when(strategyRepository.findById(stratId)).thenReturn(Optional.of(strat));
        doThrow(new AccessDeniedException(USER_ID, "Simulation", simId.value().toString()))
            .when(authorizationService).requireEdit(eq(USER_ID), eq(sim), eq(strat));

        // When / Then
        assertThatThrownBy(() -> service.execute(
                new RemoveSimulationFromBenchmarkCommand(simId, USER_ID)))
            .isInstanceOf(AccessDeniedException.class);

        verify(simulationRepository, never()).updateBenchmarkId(any(), any());
    }

    // === Helper methods ===

    private Simulation createSimulation(SimulationId simulationId, StrategyId strategyId) {
        return new Simulation(simulationId, "Test Sim", strategyId, START, END, CAPITAL);
    }

    private Strategy createStrategy(StrategyId strategyId) {
        return new Strategy(strategyId, "Test Strategy", "content", USER_ID);
    }
}
