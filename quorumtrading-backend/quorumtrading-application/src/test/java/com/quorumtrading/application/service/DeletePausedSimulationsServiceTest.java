package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.SimulationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeletePausedSimulationsServiceTest {

    @Mock
    private SimulationRepository repository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private AuthorizationService authorizationService;

    private DeletePausedSimulationsService service;

    @BeforeEach
    void setUp() {
        service = new DeletePausedSimulationsService(repository, strategyRepository, authorizationService);
        // Previously a null requesting user skipped the per-simulation ownership check entirely,
        // so these tests deleted everything without stubbing either of these. The check now runs
        // on every call, so the strategy must resolve and the policy must actually allow it.
        lenient().when(strategyRepository.findById(any()))
            .thenReturn(java.util.Optional.of(mock(com.quorumtrading.model.entity.Strategy.class)));
        lenient().when(authorizationService.canDelete(any(), any(com.quorumtrading.model.entity.Simulation.class), any()))
            .thenReturn(true);
    }

    @Test
    void shouldDeleteAllPausedSimulations() {
        // Given
        Simulation sim1 = mock(Simulation.class);
        Simulation sim2 = mock(Simulation.class);
        when(sim1.getSimulationId()).thenReturn(SimulationId.generate());
        when(sim2.getSimulationId()).thenReturn(SimulationId.generate());
        when(repository.findPausedSimulations()).thenReturn(List.of(sim1, sim2));
        when(repository.deleteById(any())).thenReturn(true);

        // When
        int count = service.execute(com.quorumtrading.model.valueobject.UserId.SYSTEM);

        // Then
        assertThat(count).isEqualTo(2);
        verify(repository).findPausedSimulations();
        verify(repository, times(2)).deleteById(any());
    }

    @Test
    void shouldReturnZeroWhenNoPausedSimulations() {
        // Given
        when(repository.findPausedSimulations()).thenReturn(List.of());

        // When
        int count = service.execute(com.quorumtrading.model.valueobject.UserId.SYSTEM);

        // Then
        assertThat(count).isZero();
        verify(repository).findPausedSimulations();
        verify(repository, never()).deleteById(any());
    }

    @Test
    void shouldHandlePartialDeletionFailures() {
        // Given
        Simulation sim1 = mock(Simulation.class);
        Simulation sim2 = mock(Simulation.class);
        when(sim1.getSimulationId()).thenReturn(SimulationId.generate());
        when(sim2.getSimulationId()).thenReturn(SimulationId.generate());
        when(repository.findPausedSimulations()).thenReturn(List.of(sim1, sim2));
        when(repository.deleteById(any()))
            .thenReturn(true)  // First succeeds
            .thenReturn(false); // Second fails

        // When
        int count = service.execute(com.quorumtrading.model.valueobject.UserId.SYSTEM);

        // Then
        assertThat(count).isEqualTo(1); // Only one deletion succeeded
        verify(repository, times(2)).deleteById(any());
    }
}
