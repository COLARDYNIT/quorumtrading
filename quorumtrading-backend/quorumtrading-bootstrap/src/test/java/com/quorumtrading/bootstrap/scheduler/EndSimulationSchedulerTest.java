package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.usecase.EndSimulationResult;
import com.quorumtrading.application.usecase.EndSimulationUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for EndSimulationScheduler.
 */
class EndSimulationSchedulerTest {

    @Mock
    private EndSimulationUseCase endSimulationUseCase;

    private EndSimulationScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        scheduler = new EndSimulationScheduler();
        scheduler.endSimulationUseCase = endSimulationUseCase;
        scheduler.schedulerEnabled = true;
    }

    @Test
    void shouldCallUseCaseWhenEnabled() {
        // Given: Use case will end 2 simulations
        EndSimulationResult result = new EndSimulationResult(2, 0, 0, Collections.emptyList());
        when(endSimulationUseCase.endExpiredSimulations()).thenReturn(result);

        // When: Scheduler runs
        scheduler.endExpiredSimulations();

        // Then: Use case should be called
        verify(endSimulationUseCase, times(1)).endExpiredSimulations();
    }

    @Test
    void shouldNotCallUseCaseWhenDisabled() {
        // Given: Scheduler is disabled
        scheduler.schedulerEnabled = false;

        // When: Scheduler runs
        scheduler.endExpiredSimulations();

        // Then: Use case should not be called
        verify(endSimulationUseCase, never()).endExpiredSimulations();
    }

    @Test
    void shouldHandleNoExpiredSimulations() {
        // Given: No simulations to end
        EndSimulationResult result = new EndSimulationResult(0, 0, 0, Collections.emptyList());
        when(endSimulationUseCase.endExpiredSimulations()).thenReturn(result);

        // When: Scheduler runs
        scheduler.endExpiredSimulations();

        // Then: Should complete without error
        verify(endSimulationUseCase, times(1)).endExpiredSimulations();
    }

    @Test
    void shouldHandleUseCaseError() {
        // Given: Use case throws exception
        when(endSimulationUseCase.endExpiredSimulations())
                .thenThrow(new RuntimeException("Database error"));

        // When: Scheduler runs
        scheduler.endExpiredSimulations();

        // Then: Should not throw exception (error is logged)
        verify(endSimulationUseCase, times(1)).endExpiredSimulations();
    }

    @Test
    void shouldHandlePartialSuccess() {
        // Given: Some simulations ended, some failed
        EndSimulationResult result = new EndSimulationResult(
                2,  // ended
                1,  // skipped
                1,  // failed
                List.of("Error ending simulation: Database error")
        );
        when(endSimulationUseCase.endExpiredSimulations()).thenReturn(result);

        // When: Scheduler runs
        scheduler.endExpiredSimulations();

        // Then: Should complete despite errors
        verify(endSimulationUseCase, times(1)).endExpiredSimulations();
    }
}
