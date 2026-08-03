package com.quorumtrading.application.orchestration;

import com.quorumtrading.application.service.consensus.DataGapCircuitBreaker;
import com.quorumtrading.application.usecase.ReachConsensusUseCase;
import com.quorumtrading.application.usecase.command.ReachConsensusCommand;
import com.quorumtrading.model.entity.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs one consensus evaluation under a hard ceiling, and makes an abandoned one visible.
 *
 * <p>The consensus pipeline has its own per-ticker timeout, but an evaluation that never returns
 * at all is the one failure a backtest loop cannot otherwise survive. Such a round:</p>
 * <ul>
 *   <li>writes no consensus row, so the skip circuit breaker is blind to it;</li>
 *   <li>stops renewing the execution lease, so the row starts to look abandoned;</li>
 *   <li>never returns, so the executor's in-JVM slot for that simulation is held for the life of
 *       the process and every later pickup in it is refused.</li>
 * </ul>
 *
 * <p>One documented run sat inside a single evaluation for eight minutes with three agents that
 * never answered, and nothing in the system could distinguish it from a run that had finished —
 * which is what let a wall-clock watchdog "reap" it and report a truncated backtest as a clean
 * 100% COMPLETED result.</p>
 *
 * <p>The ceiling is deliberately generous (twice the pipeline budget). It is a backstop for
 * "the engine is not answering", not a second copy of the pipeline's own timeout policy. On
 * expiry the round is recorded through the same skip path a <em>failing</em> engine takes, so a
 * hung engine and a failing one produce the same honest outcome: visible skips, correct counters,
 * and eventually an {@code LLM_FAILURE_DETECTED} pause.</p>
 *
 * <p>Plain POJO wired via {@code ApplicationConfig}.</p>
 */
public class BoundedConsensusEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(BoundedConsensusEvaluator.class);

    private final ReachConsensusUseCase reachConsensusUseCase;
    private final DataGapCircuitBreaker dataGapCircuitBreaker;
    private final ExecutorService worker;
    private final Duration ceiling;

    public BoundedConsensusEvaluator(ReachConsensusUseCase reachConsensusUseCase,
                                     DataGapCircuitBreaker dataGapCircuitBreaker,
                                     ExecutorService worker,
                                     Duration ceiling) {
        this.reachConsensusUseCase = reachConsensusUseCase;
        this.dataGapCircuitBreaker = dataGapCircuitBreaker;
        this.worker = worker;
        this.ceiling = ceiling;
    }

    /** How long a single evaluation may run before it is abandoned. */
    public Duration ceiling() {
        return ceiling;
    }

    /**
     * Run one evaluation, abandoning it if it exceeds the ceiling.
     *
     * <p>Whatever the pipeline throws is propagated unchanged — the caller's budget-depleted
     * handling depends on seeing the original exception, not a wrapper.</p>
     *
     * @return true when the evaluation completed (successfully, or with a failure the caller will
     *         handle); false when it was abandoned at the ceiling and recorded as a skip
     */
    public boolean evaluate(Simulation simulation, ReachConsensusCommand command,
                            String consensusRequestId, Instant evaluationTime) {
        // The task captures its own failure rather than letting it surface as an
        // ExecutionException. Unwrapping a wrapper to rethrow its cause loses the caller-side
        // stack for no gain; this way the exception that reaches the caller is the original one.
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Future<?> pending = worker.submit(() -> {
            try {
                reachConsensusUseCase.execute(command);
            } catch (RuntimeException | Error failure) {
                thrown.set(failure);
            }
        });

        try {
            pending.get(ceiling.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            // Interrupt the task if it is interruptible. Either way we stop waiting on it, which
            // is the property that matters: the loop keeps its lease alive and moves on.
            pending.cancel(true);
            logger.warn("Evaluation at {} for simulation {} exceeded the {} ceiling and was "
                    + "abandoned; recording it as an LLM-failure skip so the round is visible",
                evaluationTime, simulation.getSimulationId().value(), ceiling);
            recordAbandoned(simulation, evaluationTime, consensusRequestId);
            return false;
        } catch (ExecutionException executionFailure) {
            // Unreachable in practice — the task body captures RuntimeException and Error itself.
            // Wrapping (rather than swallowing) keeps a genuine surprise visible.
            throw new IllegalStateException(
                "Consensus evaluation failed for simulation " + simulation.getSimulationId().value(),
                executionFailure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            pending.cancel(true);
            throw new IllegalStateException(
                "Interrupted while awaiting the evaluation for simulation "
                    + simulation.getSimulationId().value(), interrupted);
        }

        // Rethrown outside the catch blocks on purpose: this is the pipeline's own exception,
        // carrying its own stack, and the caller (budget handling in particular) must see it
        // exactly as the pipeline threw it.
        Throwable failure = thrown.get();
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return true;
    }

    /**
     * Record an abandoned evaluation as an LLM-failure skip.
     *
     * <p>Best-effort by design: the skip row exists to make the round visible, and failing to
     * write it must not take down a loop that is otherwise healthy enough to continue.</p>
     */
    private void recordAbandoned(Simulation simulation, Instant evaluationTime,
                                 String consensusRequestId) {
        if (dataGapCircuitBreaker == null) {
            return;
        }
        try {
            dataGapCircuitBreaker.recordLlmFailureSkip(simulation, evaluationTime, consensusRequestId,
                "The engine did not answer within " + ceiling + "; the evaluation was abandoned.");
        } catch (Exception e) {
            logger.warn("Failed to record abandoned evaluation for simulation {}: {}",
                simulation.getSimulationId().value(), e.getMessage());
        }
    }
}
