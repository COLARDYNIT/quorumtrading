package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationCompletedEvent;
import com.quorumtrading.application.orchestration.SimulationExecutor;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.EndSimulationResult;
import com.quorumtrading.application.usecase.EndSimulationUseCase;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.service.PerformanceCalculator;
import com.quorumtrading.model.valueobject.ExternalPauseReason;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Two jobs, deliberately kept apart.
 *
 * <p><b>1. Complete LIVE simulations whose period has elapsed.</b> A LIVE run past its endDate has
 * genuinely finished; completing it is honest and is this service's original purpose.</p>
 *
 * <p><b>2. Detect stalled backtests and pause them.</b> This replaces a watchdog that
 * force-completed any SIMULATING backtest older than {@code startedAt + 1h}, every five minutes,
 * forever, with no liveness check of any kind — no lease, no recent decisions, not even the paused
 * flags. It truncated real runs and reported them as clean 100% COMPLETED results, released broker
 * capital while the run kept trading, and published completion events for runs that had not
 * finished. See ADR-0004; the rules it imposes are:</p>
 * <ul>
 *   <li><b>Liveness is evidence, never age.</b> A stall is established from a dead execution lease
 *       plus no new consensus decision, both compared database-side against {@code NOW()} — never
 *       from how long the run has existed. Elapsed time measures existence, not life.</li>
 *   <li><b>The only permitted action is an honest, recoverable one:</b> a system pause with a
 *       reason. Never COMPLETED, never FAILED, never a progress change, never a capital release,
 *       never a completion event. A fabricated result flows into leaderboards, benchmarks and
 *       strategy statistics as though it were authoritative, which is worse than an error.</li>
 *   <li><b>It may not count time it was not awake for.</b> On a laptop that suspends mid-run every
 *       wall-clock signal ages together, and this repo's Quartz misfire policy then replays every
 *       missed firing back-to-back — so a naive predicate is wrong hundreds of times in a row, and
 *       any "N consecutive ticks" confirmation is satisfied in milliseconds. Hence the two guards
 *       below, measured in observed time.</li>
 * </ul>
 */
public class EndSimulationService implements EndSimulationUseCase {

    private static final Logger LOG = Logger.getLogger(EndSimulationService.class);

    private final SimulationRepository simulationRepository;
    private final PortfolioRepository portfolioRepository;
    private final EventPublisher<SimulationCompletedEvent> simulationCompletedEventPublisher;
    private final BrokerAccountRepository brokerAccountRepository;
    private final SimulationContributionRepository contributionRepository;

    /** How old the execution lease must be before it counts as dead. */
    private final Duration leaseStaleAfter;
    /** How long there must have been no consensus decision before a stall is called. */
    private final Duration stallGrace;
    /** A gap larger than this between two of our own ticks means we were not observing. */
    private final Duration observationGapReset;
    /** False disables the detector entirely; the LIVE arm is unaffected. */
    private final boolean stallDetectionEnabled;
    private final Clock clock;

    /** G1: when this process started. Covers boot, deploys and task replacement. */
    private final Instant processStartedAt;
    /** G2: start of the current uninterrupted observation window. */
    private volatile Instant observingSince;
    /** G2: when we last ran, to measure the gap since. */
    private volatile Instant lastTickAt;

    public EndSimulationService(
            SimulationRepository simulationRepository,
            PortfolioRepository portfolioRepository,
            EventPublisher<SimulationCompletedEvent> simulationCompletedEventPublisher,
            BrokerAccountRepository brokerAccountRepository,
            SimulationContributionRepository contributionRepository,
            Duration leaseStaleAfter,
            Duration stallGrace,
            Duration observationGapReset,
            String stallAction,
            Clock clock) {
        this.simulationRepository = simulationRepository;
        this.portfolioRepository = portfolioRepository;
        this.simulationCompletedEventPublisher = simulationCompletedEventPublisher;
        this.brokerAccountRepository = brokerAccountRepository;
        this.contributionRepository = contributionRepository;
        this.leaseStaleAfter = leaseStaleAfter;
        this.stallGrace = stallGrace;
        this.observationGapReset = observationGapReset;
        this.clock = clock;
        this.processStartedAt = clock.instant();
        this.observingSince = this.processStartedAt;

        this.stallDetectionEnabled = STALL_ACTION_PAUSE.equalsIgnoreCase(stallAction);
        if (!stallDetectionEnabled && !STALL_ACTION_OFF.equalsIgnoreCase(stallAction)) {
            LOG.warnf("Unknown stall-action '%s' — the stall detector is OFF. Valid values are "
                    + "'%s' and '%s'; there is deliberately no 'complete'.",
                    stallAction, STALL_ACTION_PAUSE, STALL_ACTION_OFF);
        }
        if (leaseStaleAfter.compareTo(SimulationExecutor.LOCK_TIMEOUT) <= 0) {
            LOG.warnf("lease-stale-after (%s) is not greater than the executor's lock timeout (%s):"
                    + " a healthy run whose lease is merely due for its next heartbeat can look "
                    + "abandoned. Raise lease-stale-after above %s.",
                    leaseStaleAfter, SimulationExecutor.LOCK_TIMEOUT, SimulationExecutor.LOCK_TIMEOUT);
        }
        LOG.infof("End-simulation stall detector %s (lease-stale-after=%s, stall-grace=%s, "
                + "observation-gap-reset=%s)",
                stallDetectionEnabled ? "enabled" : "disabled",
                leaseStaleAfter, stallGrace, observationGapReset);
    }

    /** Pause a stalled backtest with a reason (the only supported action). */
    public static final String STALL_ACTION_PAUSE = "pause";
    /** Detect nothing. There is deliberately no "complete". */
    public static final String STALL_ACTION_OFF = "off";

    @Override
    public EndSimulationResult endExpiredSimulations() {
        int ended = 0;
        int skipped = 0;
        int failed = 0;
        int paused = 0;
        List<String> errors = new ArrayList<>();

        Instant now = clock.instant();
        // Observe this tick before doing anything else, so the guards see every firing —
        // including the ones that end in an exception.
        boolean armed = observeTickAndCheckArmed(now);

        try {
            List<Simulation> expiredSimulations = simulationRepository.findExpiredSimulations(now);
            LOG.debugf("Found %d LIVE simulation(s) with endDate <= %s", expiredSimulations.size(), now);

            for (Simulation simulation : expiredSimulations) {
                try {
                    // Only a LIVE run is completed here. The query already restricts to LIVE; this
                    // is the defensive half of the same statement, and it is deliberately strict.
                    // A SIMULATING backtest must NEVER be completed from an expiry predicate: its
                    // endDate is in the past from the moment it is created, so "expired" says
                    // nothing whatsoever about whether it has finished.
                    if (simulation.getStatus() != SimulationStatus.LIVE) {
                        LOG.debugf("Simulation %s is %s, not LIVE — the expiry arm does not act on it",
                                simulation.getSimulationId().value(), simulation.getStatus());
                        skipped++;
                        continue;
                    }

                    LOG.infof("Ending simulation %s: its LIVE period has elapsed (endDate: %s)",
                            simulation.getSimulationId().value(), simulation.getEndDate());

                    simulation.complete();

                    // Compare-and-set, not save(). If another writer (a user force-end, another
                    // instance) transitioned the row first, we lose and must not go on to release
                    // its capital or publish a completion for it a second time.
                    boolean won = simulationRepository.updateStatus(
                            simulation.getSimulationId(), SimulationStatus.LIVE, SimulationStatus.COMPLETED,
                            simulation.getCompletedAt(), simulation.getFailureReason());
                    if (!won) {
                        LOG.infof("Simulation %s was already transitioned out of LIVE by another writer, skipping",
                                simulation.getSimulationId().value());
                        skipped++;
                        continue;
                    }
                    // complete() implies progress = 100, which is true here: the period elapsed.
                    simulationRepository.updateProgress(
                            simulation.getSimulationId(), simulation.getProgress());

                    // Release reserved trading capital back to broker account
                    releaseReservedCapital(simulation);

                    // Publish completion event with actual portfolio metrics
                    publishCompletionEvent(simulation);

                    ended++;
                    LOG.infof("Successfully ended simulation %s", simulation.getSimulationId().value());

                } catch (Exception e) {
                    failed++;
                    String errorMsg = String.format("Failed to end simulation %s: %s",
                            simulation.getSimulationId().value(), e.getMessage());
                    LOG.errorf(e, errorMsg);
                    errors.add(errorMsg);
                }
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to execute end simulation process");
            errors.add("Global error: " + e.getMessage());
            return new EndSimulationResult(ended, skipped, failed, paused, errors);
        }

        paused = pauseStalledBacktests(armed, errors);

        return new EndSimulationResult(ended, skipped, failed, paused, errors);
    }

    /**
     * Record that this tick happened and decide whether it is allowed to act.
     *
     * <p><b>G1 — process uptime.</b> Act only after {@code stallGrace} of our own uptime. Covers
     * boot, ECS task replacement and rolling deploys: a deploy buys every in-flight backtest a
     * fresh grace window instead of the instant death the old reaper handed it, because after a
     * deploy every run's {@code startedAt} is unchanged and already past any wall-clock budget.</p>
     *
     * <p><b>G2 — observation gap.</b> If more than {@code observationGapReset} elapsed since our
     * previous tick, this process was not observing — a suspended laptop, a long stop-the-world
     * pause, a Quartz catch-up burst, or simply another instance in the cluster having served the
     * ticks in between. Every wall-clock signal in the database aged during that gap while the run
     * itself is healthy and about to be resumed by pickup, so the honest response is to re-arm and
     * act on nothing.</p>
     *
     * <p>Note what this is <em>not</em>: a count of consecutive ticks. With
     * {@code misfire-policy…=ignore-misfire-policy}, waking from a two-day suspend replays every
     * missed firing back-to-back, so hundreds of ticks elapse in milliseconds and a tick counter
     * confirms instantly on precisely the case it was meant to protect. Confirmation is measured
     * in observed time or it is not confirmation. The window is also measured from
     * {@code max(processStartedAt, observingSince)} rather than from {@code startedAt}: that field
     * is the one that caused the original bug, it does not reset on resume, and re-stamping it
     * would corrupt an audit value.</p>
     *
     * @return true when the detector may act on what it finds
     */
    private boolean observeTickAndCheckArmed(Instant now) {
        Instant previousTick = lastTickAt;
        lastTickAt = now;

        if (previousTick != null) {
            Duration gap = Duration.between(previousTick, now);
            if (gap.compareTo(observationGapReset) > 0) {
                observingSince = now;
                LOG.warnf("Stall detector re-arming: %s since the previous tick (limit %s). This "
                        + "process was not observing — every age in the database grew while we were "
                        + "not watching, so nothing may be judged stalled on it. Re-arming for %s.",
                        gap, observationGapReset, stallGrace);
                return false;
            }
        }

        Instant armedSince = observingSince.isAfter(processStartedAt) ? observingSince : processStartedAt;
        Duration observed = Duration.between(armedSince, now);
        if (observed.compareTo(stallGrace) < 0) {
            LOG.debugf("Stall detector not armed yet: %s of continuous observation, %s required",
                    observed, stallGrace);
            return false;
        }
        return true;
    }

    /**
     * Pause backtests with evidence of a stall — and do nothing else whatsoever.
     *
     * <p>No {@code complete()}, no status change, no {@code completedAt}, no {@code progress}, no
     * capital release, no completion event. Each of those was a documented side effect of the
     * predecessor, and each was wrong: the run has not finished, so releasing its ring-fenced
     * capital while it may still place orders corrupts the accounting, and a completion event
     * feeds a truncated result to the results analyser and the Redis fan-out as though it were a
     * real one.</p>
     *
     * <p>{@code updatePauseState} is also the only write path that persists the pause columns at
     * all — {@code save()} skips them by design — which is why the predecessor's one attempt to
     * leave a marker ({@code setPaused(true)}) never reached the database and its victims showed
     * {@code paused = f} with no reason anywhere.</p>
     */
    private int pauseStalledBacktests(boolean armed, List<String> errors) {
        if (!stallDetectionEnabled || !armed) {
            return 0;
        }

        List<Simulation> stalled;
        try {
            stalled = simulationRepository.findStalledBacktests(leaseStaleAfter, stallGrace);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to query stalled backtests");
            errors.add("Stall detection error: " + e.getMessage());
            return 0;
        }

        int paused = 0;
        for (Simulation simulation : stalled) {
            try {
                simulationRepository.updatePauseState(simulation.getSimulationId(),
                        false, true, ExternalPauseReason.EXECUTION_STALLED);
                paused++;
                LOG.warnf("Simulation %s paused as %s: no execution lease newer than %s and no "
                        + "consensus decision for at least %s. Nothing was completed, no capital "
                        + "released and no completion event published — the run keeps its progress "
                        + "and one resume continues it from its last decision.",
                        simulation.getSimulationId().value(),
                        ExternalPauseReason.EXECUTION_STALLED, leaseStaleAfter, stallGrace);
            } catch (Exception e) {
                String errorMsg = String.format("Failed to pause stalled simulation %s: %s",
                        simulation.getSimulationId().value(), e.getMessage());
                LOG.errorf(e, errorMsg);
                errors.add(errorMsg);
            }
        }
        return paused;
    }

    private void publishCompletionEvent(Simulation simulation) {
        BigDecimal finalValue = BigDecimal.ZERO;
        BigDecimal totalReturn = BigDecimal.ZERO;

        try {
            var latestSnapshot = portfolioRepository.findLatestBySimulationId(simulation.getSimulationId());
            if (latestSnapshot.isPresent()) {
                PortfolioSnapshot snapshot = latestSnapshot.get();
                finalValue = snapshot.getTotalValue().amount();
                // Return basis is total invested (seed + Σ contributions), so a DCA
                // top-up counts as added capital, not profit. With no contributions
                // this equals initialCash, so non-DCA returns are unchanged.
                List<Contribution> contributions =
                    contributionRepository.findBySimulationId(simulation.getSimulationId());
                BigDecimal invested = PerformanceCalculator
                    .totalInvested(simulation.getInitialCash(), contributions).amount();
                if (invested.compareTo(BigDecimal.ZERO) > 0) {
                    totalReturn = finalValue.subtract(invested)
                        .divide(invested, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                }
            } else {
                LOG.warnf("No portfolio snapshot found for completed simulation %s, publishing with zero values",
                    simulation.getSimulationId().value());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to calculate portfolio metrics for simulation %s, publishing with zero values",
                simulation.getSimulationId().value());
        }

        SimulationCompletedEvent event = new SimulationCompletedEvent(
            simulation.getSimulationId(),
            simulation.getStrategyId(),
            finalValue,
            totalReturn,
            simulation.getCompletedAt()
        );
        simulationCompletedEventPublisher.publish(event);
    }

    private void releaseReservedCapital(Simulation simulation) {
        if (simulation.getBrokerAccountId() == null || simulation.getTradingLimit() == null) {
            return;
        }
        try {
            BrokerAccount account = brokerAccountRepository.findById(simulation.getBrokerAccountId())
                .orElse(null);
            if (account == null) {
                LOG.warnf("Broker account %s not found when releasing capital for simulation %s",
                    simulation.getBrokerAccountId().value(), simulation.getSimulationId().value());
                return;
            }
            account.release(simulation.getTradingLimit());
            brokerAccountRepository.save(account);
            LOG.infof("Released %s from broker account %s for completed simulation %s",
                simulation.getTradingLimit(), simulation.getBrokerAccountId().value(),
                simulation.getSimulationId().value());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to release capital for simulation %s from broker account %s",
                simulation.getSimulationId().value(), simulation.getBrokerAccountId().value());
        }
    }
}
