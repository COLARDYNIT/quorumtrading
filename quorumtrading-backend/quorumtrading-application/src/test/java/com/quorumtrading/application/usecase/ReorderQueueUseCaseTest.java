package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.service.AuthorizationService;
import com.quorumtrading.application.usecase.command.ReorderQueueCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReorderQueueUseCaseTest {

    @Mock
    private SimulationRepository repository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private AuthorizationService authorizationService;

    private ReorderQueueUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReorderQueueUseCase(repository, strategyRepository, authorizationService);
    }

    @Test
    void shouldReorderQueueSuccessfully() {
        // Given: 3 PENDING simulations
        SimulationId id1 = SimulationId.generate();
        SimulationId id2 = SimulationId.generate();
        SimulationId id3 = SimulationId.generate();

        Simulation sim1 = createMockSimulation(id1, "Sim 1", SimulationStatus.PENDING);
        Simulation sim2 = createMockSimulation(id2, "Sim 2", SimulationStatus.PENDING);
        Simulation sim3 = createMockSimulation(id3, "Sim 3", SimulationStatus.PENDING);

        // First findById calls during validation
        when(repository.findById(id1)).thenReturn(Optional.of(sim1));
        when(repository.findById(id2)).thenReturn(Optional.of(sim2));
        when(repository.findById(id3)).thenReturn(Optional.of(sim3));

        // Order: id3, id1, id2
        List<SimulationId> newOrder = List.of(id3, id1, id2);
        ReorderQueueCommand command = new ReorderQueueCommand(newOrder);

        // When: Reorder
        List<Simulation> result = useCase.execute(command);

        // Then: Timestamps updated 3 times
        verify(repository, times(3)).updateCreatedAt(any(SimulationId.class), any(Instant.class));

        // Verify order preserved
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getSimulationId()).isEqualTo(id3);
        assertThat(result.get(1).getSimulationId()).isEqualTo(id1);
        assertThat(result.get(2).getSimulationId()).isEqualTo(id2);
    }

    @Test
    void shouldRejectNonPendingSimulations() {
        // Given: 1 SIMULATING simulation
        SimulationId id = SimulationId.generate();
        Simulation sim = createMockSimulation(id, "Running Sim", SimulationStatus.SIMULATING);

        when(repository.findById(id)).thenReturn(Optional.of(sim));

        ReorderQueueCommand command = new ReorderQueueCommand(List.of(id));

        // When/Then: Should throw
        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only PENDING simulations can be reordered");
    }

    @Test
    void shouldRejectNonExistentSimulations() {
        // Given: Invalid SimulationId
        SimulationId id = SimulationId.generate();
        when(repository.findById(id)).thenReturn(Optional.empty());

        ReorderQueueCommand command = new ReorderQueueCommand(List.of(id));

        // When/Then: Should throw
        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Simulation not found");
    }

    @Test
    void shouldHandleSingleSimulationReorder() {
        // Given: 1 PENDING simulation
        SimulationId id = SimulationId.generate();
        Simulation sim = createMockSimulation(id, "Single Sim", SimulationStatus.PENDING);

        when(repository.findById(id)).thenReturn(Optional.of(sim));

        ReorderQueueCommand command = new ReorderQueueCommand(List.of(id));

        // When: Reorder
        List<Simulation> result = useCase.execute(command);

        // Then: Timestamp updated once
        verify(repository, times(1)).updateCreatedAt(eq(id), any(Instant.class));

        // Verify result
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSimulationId()).isEqualTo(id);
    }

    private Simulation createMockSimulation(SimulationId id, String name, SimulationStatus status) {
        StrategyId strategyId = StrategyId.generate();
        Instant now = Instant.now();
        Instant startDate = now.minus(90, ChronoUnit.DAYS);
        Instant endDate = now.minus(30, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(id, name, strategyId, startDate, endDate, initialCash);

        // The strategy is resolved for every simulation now that the ownership check no longer
        // sits behind an `if (requestingUserId != null)` guard.
        lenient().when(strategyRepository.findById(strategyId))
            .thenReturn(Optional.of(mock(com.quorumtrading.model.entity.Strategy.class)));

        // Start the simulation if status is not PENDING
        if (status == SimulationStatus.SIMULATING || status == SimulationStatus.LIVE) {
            simulation.start();
        }

        return simulation;
    }
}
