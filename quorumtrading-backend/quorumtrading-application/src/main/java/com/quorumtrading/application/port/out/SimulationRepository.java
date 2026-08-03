package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for Simulation entities.
 * Implemented by persistence adapters to provide data access.
 */
public interface SimulationRepository {
    /**
     * Persist the mutable, non-lifecycle fields of a simulation.
     *
     * <p><b>This method does not write lifecycle state.</b> {@code status}, {@code startedAt},
     * {@code completedAt} and {@code failureReason} are written only through
     * {@link #updateStatus} / {@link #markStarted}, exactly as the pause columns, evaluation
     * counters, benchmark id and challenge id already were. The reason is the same in every case
     * and is worth stating once: an executor holds a single {@code Simulation} instance for the
     * whole run — hours — so copying lifecycle fields off it silently reverts anything written
     * out of band in the meantime. That is not theoretical; it produced a documented loop of nine
     * force-completions undone by nine progress saves, leaking a capital release and a completion
     * event each time (ADR-0004).</p>
     */
    Simulation save(Simulation simulation);
    Optional<Simulation> findById(SimulationId id);
    List<Simulation> findByStrategyId(StrategyId strategyId);
    List<Simulation> findAll();
    boolean deleteById(SimulationId id);

    /**
     * Find LIVE simulations whose period has elapsed ({@code endDate <= cutoffTime}).
     *
     * <p>Deliberately restricted to {@code status = 'LIVE'}. A LIVE run past its end date has
     * genuinely finished its period, and completing it is this query's whole job. SIMULATING
     * backtests used to come back here too, which was meaningless — a backtest's endDate is in
     * the past by definition, so every one of them qualified from the moment it started, and the
     * caller was left to invent a wall-clock rule to tell the finished ones from the running
     * ones. Stalled backtests are found by {@link #findStalledBacktests} on evidence instead.</p>
     *
     * @param cutoffTime The time to compare against endDate (typically Instant.now())
     * @return List of LIVE simulations that should be completed
     */
    List<Simulation> findExpiredSimulations(Instant cutoffTime);

    /**
     * Find backtests whose execution has stalled: no live lease and no forward progress.
     *
     * <p>Every freshness comparison happens <b>database-side against {@code NOW()}</b>. The
     * {@code locked_at} column is {@code timestamp without time zone}, so a Java-side comparison
     * would silently inherit the JVM's zone (ADR-0003) — correct on a UTC container, wrong on a
     * developer's laptop. Passing the two windows as durations keeps that property.</p>
     *
     * <p>A row is returned only when all of the following hold:</p>
     * <ol>
     *   <li>{@code status = 'SIMULATING'} and {@code end_date IS NOT NULL} (i.e. a backtest);</li>
     *   <li>neither {@code paused} nor {@code paused_for_external_reason} — a user-paused run and
     *       a circuit-breaker-paused run both already have an owner, and a watchdog that touches
     *       them turns "I paused this to think" into a lost run;</li>
     *   <li>no live execution lease: {@code locked_by IS NULL} (the stale-lock cleaner nulls it)
     *       or {@code locked_at} older than {@code leaseStaleAfter};</li>
     *   <li>no forward progress: the newest {@code consensus_decisions.created_at} for the run
     *       — falling back to {@code started_at} when it has none — is older than
     *       {@code stallGrace}. {@code created_at} is wall-clock; {@code decision_timestamp} is
     *       simulation time and must never be used here, since a backtest's simulation clock is
     *       years in the past.</li>
     * </ol>
     *
     * <p>The two signals are both needed and cover each other: a run that is alive but skipping
     * every round keeps writing consensus rows (so this never fires, and the circuit breaker owns
     * it), while a run hung inside one evaluation writes no row at all (so the breaker cannot
     * fire, and this is the only thing that can).</p>
     *
     * @param leaseStaleAfter how old the execution lease must be to count as dead; must exceed
     *                        the executor's lock timeout or a healthy long round looks abandoned
     * @param stallGrace      how long there must have been no new decision
     * @return backtests with evidence of a stall — never a run that is merely old
     */
    List<Simulation> findStalledBacktests(Duration leaseStaleAfter, Duration stallGrace);

    /**
     * Find simulations in {@code status} that are runnable right now — neither user-paused nor
     * system-paused.
     *
     * <p>Exists so the three schedulers that offer work ask the database the question instead of
     * materialising every simulation ever created and filtering in Java. It is also the reason a
     * system pause is worth anything: a paused run that the schedulers keep re-offering costs the
     * fleet exactly as much as a running one, and pausing it would relieve nothing.</p>
     *
     * @param status the status to offer work for
     * @return simulations in that status with both pause flags clear
     */
    List<Simulation> findRunnable(SimulationStatus status);

    /**
     * Find simulations with optional filters.
     *
     * @param status     optional filter by simulation status
     * @param strategyId optional filter by strategy ID
     * @param since      optional filter by startedAt >= since
     * @param limit      optional limit on number of results
     * @return List of simulations matching the filters
     */
    List<Simulation> findWithFilters(
        Optional<SimulationStatus> status,
        Optional<StrategyId> strategyId,
        Optional<Instant> since,
        Optional<Integer> limit
    );

    /**
     * Find all simulations belonging to a specific benchmark.
     *
     * @param benchmarkId the benchmark ID to filter by
     * @return List of simulations in the benchmark
     */
    List<Simulation> findByBenchmarkId(BenchmarkId benchmarkId);

    /**
     * Find all paused simulations (simulations with paused flag set to true).
     *
     * @return List of paused simulations
     */
    List<Simulation> findPausedSimulations();

    /**
     * Update the createdAt timestamp of a simulation (used for queue reordering).
     *
     * @param id           the simulation ID
     * @param newCreatedAt the new createdAt timestamp
     */
    void updateCreatedAt(SimulationId id, Instant newCreatedAt);

    /**
     * Find all simulations owned by a user.
     * Ownership is determined through the simulation's strategy.
     *
     * @param ownerId the user's ID
     * @return list of simulations owned by the user
     */
    List<Simulation> findByOwnerId(UserId ownerId);

    /**
     * Find all running simulations (SIMULATING or LIVE) owned by a user.
     * Ownership is determined through the simulation's strategy.
     *
     * @param ownerId the user's ID
     * @return list of running simulations owned by the user
     */
    List<Simulation> findRunningByOwnerId(UserId ownerId);

    /**
     * Find all simulations owned by a user that are paused for external reasons.
     * Ownership is determined through the simulation's strategy.
     *
     * @param ownerId the user's ID
     * @return list of simulations paused for external reasons
     */
    List<Simulation> findPausedForExternalReasonByOwnerId(UserId ownerId);

    /**
     * Find all non-terminal simulations linked to a specific broker account.
     * Used to recalculate reserved capital during balance sync.
     *
     * @param brokerAccountId the broker account ID
     * @return list of active (non-terminal) simulations using this broker account
     */
    List<Simulation> findActiveByBrokerAccountId(BrokerAccountId brokerAccountId);

    /**
     * Count the total number of simulations.
     *
     * @return total number of simulations
     */
    long count();

    /**
     * Count simulations grouped by status.
     *
     * @return map of status name to count
     */
    Map<String, Long> countByStatus();

    /**
     * Find all simulations belonging to a specific challenge.
     *
     * @param challengeId the challenge ID to filter by
     * @return List of simulations in the challenge
     */
    List<Simulation> findByChallengeId(ChallengeId challengeId);

    /**
     * Find a user's simulation entry in a specific challenge.
     * Ownership is determined through the simulation's strategy.
     *
     * @param ownerId the user's ID
     * @param challengeId the challenge ID
     * @return the user's simulation in the challenge, if any
     */
    Optional<Simulation> findByOwnerIdAndChallengeId(UserId ownerId, ChallengeId challengeId);

    /**
     * Update the challenge_id of a simulation directly via SQL.
     * This bypasses the in-memory entity to avoid stale overwrites.
     *
     * @param id          the simulation ID
     * @param challengeId the challenge ID to assign (null to unassign)
     */
    void updateChallengeId(SimulationId id, ChallengeId challengeId);

    /**
     * Update the benchmark_id of a simulation directly via SQL.
     * This bypasses the in-memory entity to avoid stale overwrites when
     * a running simulation is added to a benchmark mid-execution.
     *
     * @param id          the simulation ID
     * @param benchmarkId the benchmark ID to assign (null to unassign)
     */
    void updateBenchmarkId(SimulationId id, BenchmarkId benchmarkId);

    /**
     * Update the pause state of a simulation directly via SQL.
     * This bypasses the in-memory entity to avoid stale overwrites from
     * long-running simulation loops that hold entities in memory.
     *
     * @param id                       the simulation ID
     * @param paused                   user-initiated pause flag
     * @param pausedForExternalReason  system-initiated pause flag
     * @param externalPauseReason      reason for system pause (null to clear)
     */
    /**
     * Persist the evaluation counters with a narrow update.
     * <p>
     * Kept out of {@code save()} on purpose: a run holds a single Simulation instance for its whole
     * duration, so saving that instance would write back a stale counter and undo increments made
     * in between — the same hazard the pause fields avoid.
     *
     * @param id                         the simulation to update
     * @param totalEvaluationCount       evaluations attempted, successful or skipped
     * @param skippedEvaluationCount     consecutive skips, reset on success
     * @param totalSkippedEvaluationCount skips over the whole run
     */
    void updateEvaluationCounters(SimulationId id, int totalEvaluationCount,
                                  int skippedEvaluationCount, int totalSkippedEvaluationCount);

    void updatePauseState(SimulationId id, boolean paused, boolean pausedForExternalReason, String externalPauseReason);

    /**
     * Persist execution progress with a narrow single-column update.
     *
     * <p>The executor writes progress after every evaluation, for hours. Doing that through
     * {@code save()} meant every progress tick also rewrote the whole row from an in-memory
     * entity that had been loaded before any of those hours passed — which is precisely how an
     * out-of-band completion got reverted fifteen seconds after it was written.</p>
     *
     * @param id       the simulation to update
     * @param progress percentage complete, 0-100
     */
    void updateProgress(SimulationId id, int progress);

    /**
     * Compare-and-set lifecycle transition: move a simulation from {@code expected} to
     * {@code next}, writing {@code completedAt} and {@code failureReason} verbatim (null clears).
     *
     * <p>The {@code WHERE status = :expected} clause is the point. Every transition becomes a
     * compare-and-set, so a writer working from a stale view <em>loses</em> instead of winning by
     * being last — and the caller learns which it was. {@code @Version} does not help here: a
     * whole-entity save re-reads the managed row inside its own transaction and copies onto it,
     * so the version check is trivially satisfied and optimistic locking is defeated.</p>
     *
     * @param id            the simulation to transition
     * @param expected      the status the row must currently be in
     * @param next          the status to move it to
     * @param completedAt   value for {@code completed_at} (null clears it, e.g. on restart)
     * @param failureReason value for {@code failure_reason} (null clears it)
     * @return true when this caller won the transition; false when the row was not in
     *         {@code expected} (someone else transitioned it first, or it no longer exists)
     */
    boolean updateStatus(SimulationId id, SimulationStatus expected, SimulationStatus next,
                         Instant completedAt, String failureReason);

    /**
     * Compare-and-set the PENDING → SIMULATING/LIVE transition, stamping {@code started_at}.
     *
     * <p>Separate from {@link #updateStatus} because it is the only transition that writes
     * {@code started_at}, and because {@code started_at} is an audit value written exactly once:
     * folding it into the general transition method would invite a second writer, and a
     * re-stamped {@code started_at} is what made the old wall-clock reaper's arithmetic
     * meaningless across a resume.</p>
     *
     * @param id        the simulation to start
     * @param next      SIMULATING for a backtest, LIVE for a real-time run
     * @param startedAt the moment execution first started
     * @return true when the row was PENDING and is now started
     */
    boolean markStarted(SimulationId id, SimulationStatus next, Instant startedAt);
}
