package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.CompareSimulationsCommand;
import com.quorumtrading.application.usecase.dto.CompareSimulationsResponse;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompareSimulationsServiceTest {

    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private BenchmarkRepository benchmarkRepository;
    @Mock private AuthorizationService authorizationService;

    private CompareSimulationsService service;
    private static final UserId USER_ID = new UserId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new CompareSimulationsService(
            simulationRepository, strategyRepository, benchmarkRepository, authorizationService);
    }

    @Test
    void shouldCreateBenchmarkWithIdenticalSimulations() {
        // Given
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test Strategy", "# content", USER_ID);
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-01T00:00:00Z");

        SimulationId simId1 = new SimulationId(UUID.randomUUID());
        Simulation sim1 = new Simulation(simId1, "Sim 1", strategyId, start, end, Money.usd(new BigDecimal("10000")));

        SimulationId simId2 = new SimulationId(UUID.randomUUID());
        Simulation sim2 = new Simulation(simId2, "Sim 2", strategyId, start, end, Money.usd(new BigDecimal("10000")));

        when(simulationRepository.findById(simId1)).thenReturn(Optional.of(sim1));
        when(simulationRepository.findById(simId2)).thenReturn(Optional.of(sim2));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        CompareSimulationsCommand cmd = new CompareSimulationsCommand(
            List.of(simId1.value(), simId2.value()), "My Benchmark", USER_ID);

        // When
        CompareSimulationsResponse response = service.execute(cmd);

        // Then
        assertThat(response.benchmarkId()).isNotNull();
        assertThat(response.benchmarkName()).isEqualTo("My Benchmark");
        assertThat(response.simulationIds()).containsExactly(simId1.value(), simId2.value());
        assertThat(response.warnings()).isEmpty();

        verify(benchmarkRepository).save(any());
        verify(simulationRepository, times(2)).save(any());
    }

    @Test
    void shouldWarnAboutDifferentStartDates() {
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", "# content", USER_ID);

        SimulationId simId1 = new SimulationId(UUID.randomUUID());
        Simulation sim1 = new Simulation(simId1, "Sim 1", strategyId,
            Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));

        SimulationId simId2 = new SimulationId(UUID.randomUUID());
        Simulation sim2 = new Simulation(simId2, "Sim 2", strategyId,
            Instant.parse("2025-02-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));

        when(simulationRepository.findById(simId1)).thenReturn(Optional.of(sim1));
        when(simulationRepository.findById(simId2)).thenReturn(Optional.of(sim2));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        CompareSimulationsCommand cmd = new CompareSimulationsCommand(
            List.of(simId1.value(), simId2.value()), null, USER_ID);

        CompareSimulationsResponse response = service.execute(cmd);

        assertThat(response.warnings()).contains("Simulations have different start dates");
    }

    @Test
    void shouldWarnAboutDifferentInitialCapital() {
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", "# content", USER_ID);
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-01T00:00:00Z");

        SimulationId simId1 = new SimulationId(UUID.randomUUID());
        Simulation sim1 = new Simulation(simId1, "Sim 1", strategyId, start, end,
            Money.usd(new BigDecimal("10000")));

        SimulationId simId2 = new SimulationId(UUID.randomUUID());
        Simulation sim2 = new Simulation(simId2, "Sim 2", strategyId, start, end,
            Money.usd(new BigDecimal("50000")));

        when(simulationRepository.findById(simId1)).thenReturn(Optional.of(sim1));
        when(simulationRepository.findById(simId2)).thenReturn(Optional.of(sim2));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        CompareSimulationsCommand cmd = new CompareSimulationsCommand(
            List.of(simId1.value(), simId2.value()), null, USER_ID);

        CompareSimulationsResponse response = service.execute(cmd);

        assertThat(response.warnings()).contains("Simulations have different initial capital amounts");
    }

    @Test
    void shouldWarnAboutDifferentBrokerTypes() {
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", "# content", USER_ID);
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-01T00:00:00Z");
        Money cash = Money.usd(new BigDecimal("10000"));

        SimulationId simId1 = new SimulationId(UUID.randomUUID());
        Simulation sim1 = new Simulation(simId1, "Sim 1", strategyId, start, end, cash);
        // sim1 has no broker account (mock)

        SimulationId simId2 = new SimulationId(UUID.randomUUID());
        Simulation sim2 = new Simulation(simId2, "Sim 2", strategyId, start, end, cash);
        sim2.setBrokerAccountId(new BrokerAccountId(UUID.randomUUID()));

        when(simulationRepository.findById(simId1)).thenReturn(Optional.of(sim1));
        when(simulationRepository.findById(simId2)).thenReturn(Optional.of(sim2));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        CompareSimulationsCommand cmd = new CompareSimulationsCommand(
            List.of(simId1.value(), simId2.value()), null, USER_ID);

        CompareSimulationsResponse response = service.execute(cmd);

        assertThat(response.warnings()).contains("Simulations use different broker types (some mock, some real)");
    }

    @Test
    void shouldFailWhenSimulationNotFound() {
        UUID missingId = UUID.randomUUID();
        when(simulationRepository.findById(new SimulationId(missingId))).thenReturn(Optional.empty());

        CompareSimulationsCommand cmd = new CompareSimulationsCommand(
            List.of(missingId, UUID.randomUUID()), null, USER_ID);

        assertThatThrownBy(() -> service.execute(cmd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Simulation not found");
    }

    @Test
    void shouldFailWhenNoSimulations() {
        assertThatThrownBy(() -> new CompareSimulationsCommand(
            List.of(), null, USER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least 1");
    }

    @Test
    void shouldFailWhenSimulationAlreadyInDifferentBenchmark() {
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", "# content", USER_ID);
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-01T00:00:00Z");

        SimulationId simId1 = new SimulationId(UUID.randomUUID());
        Simulation sim1 = new Simulation(simId1, "Sim 1", strategyId, start, end,
            Money.usd(new BigDecimal("10000")), BenchmarkId.generate());

        SimulationId simId2 = new SimulationId(UUID.randomUUID());
        Simulation sim2 = new Simulation(simId2, "Sim 2", strategyId, start, end,
            Money.usd(new BigDecimal("10000")));

        when(simulationRepository.findById(simId1)).thenReturn(Optional.of(sim1));
        when(simulationRepository.findById(simId2)).thenReturn(Optional.of(sim2));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        when(benchmarkRepository.save(any())).thenReturn(null);

        CompareSimulationsCommand cmd = new CompareSimulationsCommand(
            List.of(simId1.value(), simId2.value()), null, USER_ID);

        assertThatThrownBy(() -> service.execute(cmd))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already assigned to benchmark");
    }

    @Test
    void shouldFailWhenUserDoesNotOwnSimulation() {
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        UserId otherUser = new UserId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Other User Strategy", "# content", otherUser);

        SimulationId simId1 = new SimulationId(UUID.randomUUID());
        Simulation sim1 = new Simulation(simId1, "Sim 1", strategyId,
            Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));

        when(simulationRepository.findById(simId1)).thenReturn(Optional.of(sim1));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));
        doThrow(new AccessDeniedException(USER_ID, "Simulation", simId1.value().toString()))
            .when(authorizationService).requireView(eq(USER_ID), any(Simulation.class), any(Strategy.class));

        CompareSimulationsCommand cmd = new CompareSimulationsCommand(
            List.of(simId1.value(), UUID.randomUUID()), null, USER_ID);

        assertThatThrownBy(() -> service.execute(cmd))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldUseDateRangeFromAllSimulations() {
        StrategyId strategyId = new StrategyId(UUID.randomUUID());
        Strategy strategy = new Strategy(strategyId, "Test", "# content", USER_ID);

        SimulationId simId1 = new SimulationId(UUID.randomUUID());
        Simulation sim1 = new Simulation(simId1, "Sim 1", strategyId,
            Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-03-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));

        SimulationId simId2 = new SimulationId(UUID.randomUUID());
        Simulation sim2 = new Simulation(simId2, "Sim 2", strategyId,
            Instant.parse("2025-02-01T00:00:00Z"), Instant.parse("2025-06-01T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));

        when(simulationRepository.findById(simId1)).thenReturn(Optional.of(sim1));
        when(simulationRepository.findById(simId2)).thenReturn(Optional.of(sim2));
        when(strategyRepository.findById(strategyId)).thenReturn(Optional.of(strategy));

        CompareSimulationsCommand cmd = new CompareSimulationsCommand(
            List.of(simId1.value(), simId2.value()), null, USER_ID);

        CompareSimulationsResponse response = service.execute(cmd);

        // Benchmark should span earliest start to latest end
        assertThat(response.benchmarkId()).isNotNull();
        assertThat(response.warnings()).isNotEmpty(); // Different dates
    }
}
