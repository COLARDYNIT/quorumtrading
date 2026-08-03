package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.usecase.TriggerScheduledConsensusUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link ConsensusScheduler} in-flight guarding and shutdown behavior.
 *
 * <p>These tests run without a Quarkus/Arc container: the submitted task fails on
 * {@code Arc.container()} in that environment, which is exactly what lets us verify the
 * in-flight flag is released even when a run blows up.</p>
 */
class ConsensusSchedulerTest {

    private ConsensusScheduler scheduler;
    private TriggerScheduledConsensusUseCase useCase;

    @BeforeEach
    void setUp() {
        scheduler = new ConsensusScheduler();
        useCase = mock(TriggerScheduledConsensusUseCase.class);
        scheduler.triggerScheduledConsensusUseCase = useCase;
        scheduler.schedulerEnabled = true;
    }

    @Test
    void doesNothingWhenDisabled() {
        scheduler.schedulerEnabled = false;

        scheduler.triggerConsensusDecisions();

        verifyNoInteractions(useCase);
        assertThat(inFlight().get()).isFalse();
    }

    @Test
    void skipsTickWhenPreviousRunStillInFlight() {
        inFlight().set(true);

        scheduler.triggerConsensusDecisions();

        // Nothing submitted: the use case is never touched and the flag stays owned by the
        // (simulated) in-flight run.
        verifyNoInteractions(useCase);
        assertThat(inFlight().get()).isTrue();
    }

    @Test
    void releasesInFlightFlagEvenWhenRunFails() throws Exception {
        // Outside a Quarkus container the submitted task fails immediately on Arc.container();
        // the finally block must still release the flag so the next tick can run.
        scheduler.triggerConsensusDecisions();

        long deadline = System.currentTimeMillis() + 5000;
        while (inFlight().get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(inFlight().get()).isFalse();
    }

    @Test
    void shutdownStopsExecutorAndSubsequentTicksReleaseFlag() {
        scheduler.shutdown();

        assertThat(executor().isShutdown()).isTrue();

        // A tick after shutdown must not leave the flag stuck (RejectedExecutionException path)
        scheduler.triggerConsensusDecisions();
        assertThat(inFlight().get()).isFalse();
    }

    private AtomicBoolean inFlight() {
        return readField("inFlight", AtomicBoolean.class);
    }

    private ExecutorService executor() {
        return readField("blockingExecutor", ExecutorService.class);
    }

    private <T> T readField(String name, Class<T> type) {
        try {
            Field field = ConsensusScheduler.class.getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(scheduler));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read field " + name, e);
        }
    }
}
