package com.quorumtrading.application.orchestration;

import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.service.consensus.DataGapCircuitBreaker;
import com.quorumtrading.application.usecase.ReachConsensusUseCase;
import com.quorumtrading.application.usecase.command.ReachConsensusCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins the backstop for the one failure a backtest loop cannot otherwise survive: an evaluation
 * that never returns.
 *
 * <p>Such a round writes no consensus row (so the skip circuit breaker is blind to it), stops
 * renewing the execution lease (so the row starts to look abandoned), and never returns (so the
 * executor's in-JVM slot is held for the life of the process). It is invisible to every defence
 * the platform has — which is exactly why a wall-clock watchdog was able to "reap" such a run and
 * report a truncated backtest as a clean 100% COMPLETED result.</p>
 */
@ExtendWith(MockitoExtension.class)
class BoundedConsensusEvaluatorTest {

    @Mock private ReachConsensusUseCase reachConsensusUseCase;
    @Mock private DataGapCircuitBreaker dataGapCircuitBreaker;

    private ExecutorService worker;
    private Simulation simulation;
    private ReachConsensusCommand command;
    private final Instant evaluationTime = Instant.parse("2025-06-02T14:00:00Z");

    @BeforeEach
    void setUp() {
        worker = Executors.newSingleThreadExecutor();
        simulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Bounded Sim",
            new StrategyId(UUID.randomUUID()),
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-12-31T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));
        simulation.setOwnerUserId(UserId.generate());
        command = new ReachConsensusCommand(simulation.getSimulationId(), evaluationTime);
    }

    @AfterEach
    void tearDown() {
        worker.shutdownNow();
    }

    private BoundedConsensusEvaluator evaluator(Duration ceiling) {
        return new BoundedConsensusEvaluator(
            reachConsensusUseCase, dataGapCircuitBreaker, worker, ceiling);
    }

    @Test
    @DisplayName("an evaluation that returns in time is not interfered with")
    void completedEvaluationIsReportedAsCompleted() {
        BoundedConsensusEvaluator evaluator = evaluator(Duration.ofSeconds(10));

        boolean completed = evaluator.evaluate(simulation, command, "req-1", evaluationTime);

        assertThat(completed).isTrue();
        verify(reachConsensusUseCase).execute(command);
        verify(dataGapCircuitBreaker, never())
            .recordLlmFailureSkip(any(), any(), any(), any());
    }

    @Test
    @Timeout(20)
    @DisplayName("an evaluation that never returns is abandoned and recorded as an LLM-failure skip")
    void abandonedEvaluationIsRecordedAsASkip() {
        doAnswer(invocation -> {
            Thread.sleep(30_000);
            return null;
        }).when(reachConsensusUseCase).execute(any(ReachConsensusCommand.class));

        BoundedConsensusEvaluator evaluator = evaluator(Duration.ofMillis(150));

        boolean completed = evaluator.evaluate(simulation, command, "req-2", evaluationTime);

        assertThat(completed)
            .as("the caller must learn the round did not happen, so it does not advance its "
                + "last-executed timestamp over a round that produced nothing")
            .isFalse();
        // Routed through the same path a *failing* engine takes, so a hung engine and a failing
        // one produce the same honest outcome: a visible skip, correct counters, and eventually
        // an LLM_FAILURE_DETECTED pause.
        verify(dataGapCircuitBreaker).recordLlmFailureSkip(
            eq(simulation), eq(evaluationTime), eq("req-2"), contains("did not answer"));
    }

    @Test
    @Timeout(20)
    @DisplayName("a failure to record the skip does not take down an otherwise healthy loop")
    void skipRecordingFailureIsSwallowed() {
        doAnswer(invocation -> {
            Thread.sleep(30_000);
            return null;
        }).when(reachConsensusUseCase).execute(any(ReachConsensusCommand.class));
        doThrow(new IllegalStateException("counter write failed"))
            .when(dataGapCircuitBreaker).recordLlmFailureSkip(any(), any(), any(), any());

        BoundedConsensusEvaluator evaluator = evaluator(Duration.ofMillis(150));

        assertThat(evaluator.evaluate(simulation, command, "req-3", evaluationTime)).isFalse();
    }

    @Test
    @DisplayName("the pipeline's own exception reaches the caller unchanged")
    void pipelineFailurePropagatesUnwrapped() {
        // Load-bearing: the executor loop distinguishes BudgetDepletedException from any other
        // failure and pauses with BUDGET_DEPLETED instead of skipping. Wrapping it here would
        // silently turn "out of budget" into "an evaluation failed, continuing" — and the run
        // would keep spending against a depleted budget.
        UserId owner = UserId.generate();
        doThrow(new BudgetDepletedException(owner))
            .when(reachConsensusUseCase).execute(any(ReachConsensusCommand.class));

        BoundedConsensusEvaluator evaluator = evaluator(Duration.ofSeconds(10));

        assertThatThrownBy(() -> evaluator.evaluate(simulation, command, "req-4", evaluationTime))
            .isInstanceOf(BudgetDepletedException.class);
        verify(dataGapCircuitBreaker, never())
            .recordLlmFailureSkip(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an Error from the pipeline is propagated, not converted into a skip")
    void errorPropagates() {
        doThrow(new StackOverflowError("boom"))
            .when(reachConsensusUseCase).execute(any(ReachConsensusCommand.class));

        BoundedConsensusEvaluator evaluator = evaluator(Duration.ofSeconds(10));

        assertThatThrownBy(() -> evaluator.evaluate(simulation, command, "req-5", evaluationTime))
            .isInstanceOf(StackOverflowError.class);
    }

    @Test
    @DisplayName("the configured ceiling is readable, so a caller can log what it enforced")
    void ceilingIsExposed() {
        assertThat(evaluator(Duration.ofSeconds(90)).ceiling()).isEqualTo(Duration.ofSeconds(90));
    }
}
