package com.quorumtrading.application.orchestration;

import com.quorumtrading.application.event.ConsensusStartedEvent;
import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.exception.DailySpendLimitExceededException;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationLockPort;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.service.BrokerRouter;
import com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase;
import com.quorumtrading.application.usecase.command.DispatchNotificationCommand;
import com.quorumtrading.application.usecase.command.ReachConsensusCommand;
import com.quorumtrading.application.usecase.notification.DispatchNotificationUseCase;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.ExternalPauseReason;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;

import com.quorumtrading.model.enums.TradingMode;
import com.quorumtrading.model.valueobject.MarketHours;

/**
 * Simulation Executor - Backtesting engine that runs simulations through historical time.
 * <p>
 * Uses BoundedConsensusEvaluator (over ReachConsensusUseCase) so a single evaluation can never
 * hang the whole run; decisions stay consistent with manual consensus.
 * Integrates real market data, news, and properly persists decisions to database.
 * <p>
 * Supports resumable execution: Can continue from last checkpoint after server restart.
 */
public class SimulationExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SimulationExecutor.class);
    /**
     * How long an execution lease survives without a heartbeat before another instance may take
     * it over.
     *
     * <p>Public because the stall detector's {@code lease-stale-after} window must sit strictly
     * above it — a detector that calls a lease dead sooner than the executor is allowed to renew
     * it would pause healthy runs mid-round. Two constants that must not disagree should not live
     * in two files with no link between them.</p>
     */
    public static final Duration LOCK_TIMEOUT = Duration.ofMinutes(15);
    /** Weekdays in a full trading week, used to size progress estimates. */
    private static final int WEEKDAYS_PER_WEEK = 5;

    // Unique instance ID for distributed locking (hostname or random UUID)
    private static final String INSTANCE_ID = resolveInstanceId();

    /**
     * Simulations currently executing in THIS JVM (the bean is @ApplicationScoped). First line
     * of the two-level idempotency guard: this set serializes same-JVM submissions cheaply and
     * without touching the database; the DB lease in {@code simulationLockPort} arbitrates
     * across instances only. Both are needed — the lease alone cannot tell "I am already
     * running it" from "someone else is running it".
     */
    private final java.util.Set<UUID> activeSimulations = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final BoundedConsensusEvaluator boundedConsensusEvaluator;
    private final UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase;
    private final StrategyParser strategyParser;
    private final StrategyRepository strategyRepository;
    private final MarketEventRepository marketEventRepository;
    private final PortfolioRepository portfolioRepository;
    private final EventPublisher<ConsensusStartedEvent> consensusStartedEventPublisher;
    private final BrokerAccountRepository brokerAccountRepository;
    private final SimulationPortfolioRepository simulationPortfolioRepository;
    private final BrokerRouter brokerRouter;
    private final SimulationRepository simulationRepository;
    private final SimulationLockPort simulationLockPort;
    private final DispatchNotificationUseCase dispatchNotificationUseCase;
    private final ConsensusDecisionRepository consensusDecisionRepository;

    public SimulationExecutor(
            BoundedConsensusEvaluator boundedConsensusEvaluator,
            UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase,
            StrategyParser strategyParser,
            StrategyRepository strategyRepository,
            MarketEventRepository marketEventRepository,
            PortfolioRepository portfolioRepository,
            EventPublisher<ConsensusStartedEvent> consensusStartedEventPublisher,
            BrokerAccountRepository brokerAccountRepository,
            SimulationPortfolioRepository simulationPortfolioRepository,
            BrokerRouter brokerRouter,
            SimulationRepository simulationRepository,
            SimulationLockPort simulationLockPort,
            DispatchNotificationUseCase dispatchNotificationUseCase,
            ConsensusDecisionRepository consensusDecisionRepository) {
        this.boundedConsensusEvaluator = boundedConsensusEvaluator;
        this.updatePortfolioWithMarketValueUseCase = updatePortfolioWithMarketValueUseCase;
        this.strategyParser = strategyParser;
        this.strategyRepository = strategyRepository;
        this.marketEventRepository = marketEventRepository;
        this.portfolioRepository = portfolioRepository;
        this.consensusStartedEventPublisher = consensusStartedEventPublisher;
        this.brokerAccountRepository = brokerAccountRepository;
        this.simulationPortfolioRepository = simulationPortfolioRepository;
        this.brokerRouter = brokerRouter;
        this.simulationRepository = simulationRepository;
        this.simulationLockPort = simulationLockPort;
        this.dispatchNotificationUseCase = dispatchNotificationUseCase;
        this.consensusDecisionRepository = consensusDecisionRepository;
    }

    /**
     * Best-effort dispatch of a SIMULATION_PAUSED_BUDGET notification after a pause
     * landed in persistence. The pause is the source of truth — a notification failure
     * here must never disturb the loop state.
     */
    private void dispatchBudgetPauseNotification(Simulation simulation, Exception budgetEx) {
        if (dispatchNotificationUseCase == null || simulation.getOwnerUserId() == null) {
            return;
        }
        try {
            String pauseReason = budgetEx instanceof DailySpendLimitExceededException
                    ? "DAILY_LIMIT_EXCEEDED"
                    : "BUDGET_DEPLETED";
            Map<String, Object> payload = new HashMap<>();
            payload.put("simulationId", simulation.getSimulationId().value().toString());
            payload.put("pauseReason", pauseReason);
            String title = "Simulation paused: "
                    + (pauseReason.equals("DAILY_LIMIT_EXCEEDED")
                        ? "daily LLM spend cap hit"
                        : "LLM budget exhausted");
            dispatchNotificationUseCase.execute(new DispatchNotificationCommand(
                    simulation.getOwnerUserId(),
                    NotificationType.SIMULATION_PAUSED_BUDGET,
                    title,
                    budgetEx.getMessage(),
                    payload,
                    simulation.getSimulationId().value()
            ));
        } catch (Exception notifyEx) {
            logger.warn("Failed to dispatch SIMULATION_PAUSED_BUDGET notification for simulation {}: {}",
                    simulation.getSimulationId().value(), notifyEx.getMessage());
        }
    }

    private static String resolveInstanceId() {
        // Use ECS_TASK_ID if available, otherwise hostname, otherwise random UUID
        String ecsTaskId = System.getenv("ECS_TASK_ID");
        if (ecsTaskId != null && !ecsTaskId.isBlank()) {
            return ecsTaskId;
        }
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * Start or resume a backtesting simulation.
     * Uses ReachConsensusUseCase for each evaluation point to ensure consistency.
     * Automatically detects if resuming from checkpoint or starting fresh.
     * Idempotent: Returns immediately if already running.
     *
     * NOTE: Expects simulation to already be in SIMULATING or LIVE state.
     * The caller (StartSimulationService) is responsible for calling simulation.start().
     *
     * @return {@code true} when this call held the execution lease and ran the simulation
     *         to a quiescent state (completed, paused, transitioned, or nothing left to do);
     *         {@code false} when it did not run — already in-flight in this JVM, lease held
     *         elsewhere, or lease lost to a takeover mid-run. On {@code false} the in-memory
     *         entity was not (fully) executed by us, so callers must not persist it.
     */
    public boolean startSimulation(Simulation simulation, String strategyMarkdown) {
        String simIdShort = simulation.getSimulationId().value().toString().substring(0, 8);

        // Same-JVM guard: the pickup scheduler re-offers every SIMULATING simulation on each
        // tick, so a multi-hour backtest lands here twice a minute for its whole run. The DB
        // lease below is deliberately not re-entrant call-to-call, which made every re-offer
        // log a lock failure blaming "another instance" that did not exist.
        if (!activeSimulations.add(simulation.getSimulationId().value())) {
            logger.debug("Simulation {} is already executing in this instance — re-submit ignored", simIdShort);
            return false;
        }
        try {
            return runHoldingLease(simulation, strategyMarkdown, simIdShort);
        } finally {
            // Always freed — including when the lease was lost to another instance, and
            // including when an evaluation was abandoned at its ceiling. This slot is what makes
            // pickup a no-op for a run already in flight here, so a thread that never returns
            // holds it forever and blinds every later pickup in this process. The ceiling in
            // evaluateWithinCeiling() exists so that "never returns" cannot happen.
            activeSimulations.remove(simulation.getSimulationId().value());
        }
    }

    private boolean runHoldingLease(Simulation simulation, String strategyMarkdown, String simIdShort) {
        logger.info("Starting/resuming simulation {}: {}", simIdShort, simulation.getName());

        // Cross-instance guard: the DB lease. With the in-JVM guard above, a refusal here
        // genuinely means the lease lives elsewhere — another instance, or a previous run of
        // this instance that died without unlocking and whose lease has not yet gone stale.
        if (!simulationLockPort.tryLock(simulation.getSimulationId(), INSTANCE_ID, LOCK_TIMEOUT)) {
            logger.info("Simulation {} execution lease held elsewhere (another instance, or a "
                + "not-yet-stale lease from a dead run) — skipping this attempt", simIdShort);
            return false;
        }

        BrokerAccount brokerAccount = null;
        Money tradingLimit = null;

        try {
            // Verify simulation is in a valid state to execute
            // (Should be SIMULATING or LIVE, not PENDING)
            if (simulation.getStatus() == com.quorumtrading.model.enums.SimulationStatus.PENDING) {
                throw new IllegalStateException(
                    "Cannot execute simulation in PENDING state. Call simulation.start() first."
                );
            }

            // ========== BROKER ACCOUNT INTEGRATION ==========
            // If simulation has a linked BrokerAccount, set up per-account routing
            BrokerAccountId brokerAccountId = simulation.getBrokerAccountId();
            if (brokerAccountId != null) {
                brokerAccount = initializeBrokerAccountForSimulation(simulation, brokerAccountId);
                tradingLimit = simulation.getTradingLimit();
            } else {
                logger.info("Simulation {} using legacy global broker routing (no BrokerAccountId)",
                    simulation.getSimulationId().value().toString().substring(0, 8));
            }

            // Get strategy
            Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                    .orElseThrow(() -> new IllegalStateException("Strategy not found: " + simulation.getStrategyId()));

            // Parse strategy config
            StrategyConfig strategyConfig = strategyParser.parse(strategyMarkdown);
            if (strategyConfig.getTradingMode() == TradingMode.STOCK && strategyConfig.getBacktestSchedule() != null) {
                logger.info("Parsed STOCK mode strategy — backtest schedule: {}",
                        strategyConfig.getBacktestSchedule().evaluationTimes());
            } else {
                logger.info("Parsed CRYPTO mode strategy — backtest interval: {} seconds",
                        strategyConfig.getBacktestIntervalSeconds());
            }

            // FIND RESUME POINT: Last checkpoint or start date
            ResumeContext resumeContext = findResumePoint(simulation, strategyConfig);

            // Check if already complete
            if (resumeContext.isComplete()) {
                logger.info("Simulation {} already complete, marking as COMPLETED",
                    simulation.getSimulationId().value().toString().substring(0, 8));

                // Mark simulation as completed if not already in terminal state
                if (!simulation.isTerminal()) {
                    SimulationStatus before = simulation.getStatus();
                    simulation.complete();
                    persistCompletion(simulation, before);
                }

                // Release reserved capital if broker account was set up
                if (brokerAccount != null && tradingLimit != null) {
                    releaseBrokerAccountReservation(brokerAccount, tradingLimit);
                }
                return true;
            }

            // Initialize or resume portfolio
            if (!resumeContext.isResuming()) {
                logger.info("Starting simulation {} from beginning",
                    simulation.getSimulationId().value().toString().substring(0, 8));
                initializePortfolio(
                    simulation.getSimulationId(),
                    simulation.getStartDate(),
                    simulation.getInitialCash()
                );
            }

            // Always ensure SimulationPortfolio exists for broker-backed simulations.
            // This is idempotent (initializeSimulationPortfolio checks existsById first).
            // Critical: must run on resume too — older simulations created before capital
            // isolation was added would otherwise fall back to the full Alpaca account balance.
            //
            // Broker-less backtests deliberately have no aggregate: with no real account to
            // ring-fence there is nothing to isolate, and the mock broker's own state is the
            // single ledger behind portfolio snapshots. Creating a duplicate here would
            // reintroduce exactly the two-ledger problem this design avoids.
            if (brokerAccount != null && tradingLimit != null) {
                initializeSimulationPortfolio(simulation.getSimulationId(), brokerAccountId, tradingLimit, brokerAccount);
            }

            // Execute simulation from resume point
            Instant lastExecutedTimestamp = executeSimulation(
                    simulation,
                    strategyConfig,
                    resumeContext
            );

            // Check if simulation was paused (user or system) — don't transition state
            if (simulation.isPaused() || simulation.isPausedForExternalReason()) {
                logger.info("Simulation {} paused{}{}, not transitioning state",
                    simulation.getSimulationId().value().toString().substring(0, 8),
                    simulation.isPaused() ? " (user paused)" : "",
                    simulation.isPausedForExternalReason() ? " (reason: " + simulation.getExternalPauseReason() + ")" : "");
                simulationRepository.save(simulation);
            } else if (!simulation.isTerminal()
                    && simulation.getEndDate() != null
                    && (!lastExecutedTimestamp.isBefore(simulation.getEndDate())
                        || !simulation.getEndDate().isAfter(Instant.now()))) {
                // Simulation period has fully elapsed, mark as completed. Two ways to get here:
                // the last executed tick reached endDate in SIMULATION time, or endDate lies in
                // the past in WALL-CLOCK time — the loop can only have exited because the schedule
                // was exhausted (every scheduled tick strictly precedes endDate), never because it
                // caught up to the present, so LIVE would be the wrong terminal state.
                SimulationStatus before = simulation.getStatus();
                simulation.complete();
                persistCompletion(simulation, before);

                // Release reserved capital back to BrokerAccount
                if (brokerAccount != null && tradingLimit != null) {
                    releaseBrokerAccountReservation(brokerAccount, tradingLimit);
                    saveFinalSimulationPortfolio(simulation.getSimulationId());
                }

                logger.info("Simulation {} completed successfully (reached end date: {}, last executed: {})",
                        simulation.getSimulationId().value().toString().substring(0, 8),
                        simulation.getEndDate(),
                        lastExecutedTimestamp);
            } else if (simulation.getStatus() == SimulationStatus.SIMULATING) {
                // Simulation in SIMULATING state broke early due to time boundary, transition to LIVE mode
                simulation.transitionToLive();
                simulationRepository.updateStatus(simulation.getSimulationId(),
                        SimulationStatus.SIMULATING, SimulationStatus.LIVE,
                        simulation.getCompletedAt(), simulation.getFailureReason());
                logger.info("Simulation {} transitioned to LIVE mode (caught up to current time, end date: {}, last executed: {})",
                        simulation.getSimulationId().value().toString().substring(0, 8),
                        simulation.getEndDate(),
                        lastExecutedTimestamp);
            }

            return true;

        } catch (LockLeaseLostException lockLost) {
            // Another instance took over after our lease went stale. It is executing this
            // simulation now — do NOT touch simulation state (no COMPLETED/LIVE/FAILED), just
            // exit quietly. The takeover instance resumes from the last portfolio snapshot.
            // false tells callers the same thing: the entity in their hands is not ours to save.
            logger.warn("Simulation {} executor loop stopped: {}",
                simulation.getSimulationId().value().toString().substring(0, 8), lockLost.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Simulation {} failed: {}", simulation.getSimulationId(), e.getMessage(), e);
            SimulationStatus before = simulation.getStatus();
            simulation.fail("Execution error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            simulationRepository.updateStatus(simulation.getSimulationId(), before,
                    SimulationStatus.FAILED, simulation.getCompletedAt(), simulation.getFailureReason());

            // Release reserved capital on failure
            // Reload from DB to get fresh state — the in-memory object may be stale
            // after a long-running simulation execution
            if (brokerAccount != null && tradingLimit != null) {
                try {
                    BrokerAccount freshAccount = brokerAccountRepository.findById(brokerAccount.getId())
                        .orElse(null);
                    if (freshAccount != null) {
                        releaseBrokerAccountReservation(freshAccount, tradingLimit);
                    } else {
                        logger.warn("Broker account {} not found during failure cleanup for simulation {}",
                            brokerAccount.getId().value().toString().substring(0, 8),
                            simulation.getSimulationId().value().toString().substring(0, 8));
                    }
                } catch (Exception releaseEx) {
                    logger.warn("Failed to release broker account reservation on simulation failure for account {}: {}",
                        brokerAccount.getId().value().toString().substring(0, 8), releaseEx.getMessage());
                }
            }

            throw new RuntimeException("Simulation failed: " + e.getMessage(), e);
        } finally {
            // ALWAYS release the distributed lock to allow future resume attempts
            simulationLockPort.unlock(simulation.getSimulationId(), INSTANCE_ID);
        }
    }

    /**
     * Persist a completion this executor just made in memory: the status transition first, and
     * the {@code progress = 100} that {@code complete()} implies only if we won it.
     *
     * <p>Order and the compare-and-set both matter. Writing progress first would stamp a full
     * period onto a run another writer had already ended early; writing it after a lost
     * transition would do the same. "100%" is a claim that the whole period was simulated, and
     * only the writer that actually ended the run gets to make it.</p>
     */
    private void persistCompletion(Simulation simulation, SimulationStatus expected) {
        boolean won = simulationRepository.updateStatus(simulation.getSimulationId(), expected,
                simulation.getStatus(), simulation.getCompletedAt(), simulation.getFailureReason());
        if (won) {
            simulationRepository.updateProgress(simulation.getSimulationId(), simulation.getProgress());
        } else {
            logger.info("Simulation {} was already transitioned out of {} by another writer — "
                    + "leaving its persisted status and progress alone",
                simulation.getSimulationId().value().toString().substring(0, 8), expected);
        }
    }

    /** Thrown inside an executor loop when the distributed lock lease was lost to another instance. */
    private static final class LockLeaseLostException extends RuntimeException {
        LockLeaseLostException(String message) {
            super(message);
        }
    }

    /**
     * Heartbeat: renew the distributed lock lease before each evaluation. Slow LLM engines
     * (local models, CLI shims) can stretch a backtest past LOCK_TIMEOUT; once the lease
     * lapses, a scheduler resume acquires the lock and a second executor loop runs the same
     * simulation — every decision is then computed twice (the duplicate row is discarded by
     * the dedupe check, but its LLM calls are still spent) and progress writes ping-pong.
     * If the lease is already lost, abort this loop and let the takeover instance continue.
     */
    private void renewLockLeaseOrAbort(Simulation simulation, int evaluationCount) {
        if (!simulationLockPort.refresh(simulation.getSimulationId(), INSTANCE_ID)) {
            throw new LockLeaseLostException(String.format(
                "lock lease lost before evaluation #%d (taken over by another instance) — aborting to avoid duplicate execution",
                evaluationCount));
        }
    }

    private Portfolio initializePortfolio(SimulationId simulationId, Instant startDate, Money initialCash) {
        // Create initial portfolio with starting cash
        Portfolio portfolio = new Portfolio(simulationId, new HashMap<>(), initialCash);

        // Idempotent: the opening snapshot is normally written before the executor ever runs
        // (CreateSimulationService / StartSimulationService), so writing unconditionally would
        // leave two snapshots at the same instant. This path only became reachable once the
        // resume point stopped treating that opening snapshot as evaluation progress.
        if (portfolioRepository.findLatestBySimulationId(simulationId).isPresent()) {
            logger.debug("Opening snapshot already exists for simulation {}, not writing another",
                simulationId.value().toString().substring(0, 8));
            return portfolio;
        }

        // Save initial portfolio snapshot so we have a baseline for the chart
        PortfolioSnapshot initialSnapshot = new PortfolioSnapshot(
            simulationId,
            startDate,
            Map.of(), // Empty positions - no trades yet
            initialCash, // totalValue = initialCash
            initialCash  // cashAvailable = initialCash
        );
        portfolioRepository.save(initialSnapshot);
        logger.info("Created initial portfolio snapshot for simulation {} with {} at {}",
            simulationId.value().toString().substring(0, 8),
            initialCash,
            startDate);

        return portfolio;
    }

    /**
     * Whether this simulation has produced any consensus decision yet.
     *
     * <p>This, not the presence of a portfolio snapshot, is what distinguishes a run that has
     * made progress from one that has only been seeded with an opening balance.</p>
     *
     * <p>Fails safe toward resuming: if the lookup errors we do not want to replay evaluations
     * that already happened and already sent orders to a broker.</p>
     */
    private Instant lastEvaluationTimestamp(Simulation simulation) {
        if (consensusDecisionRepository == null) {
            return simulation.getStartDate();
        }
        try {
            return consensusDecisionRepository
                .findLatestBySimulationId(simulation.getSimulationId())
                .map(ConsensusDecision::getDecisionTimestamp)
                .orElse(null);
        } catch (Exception e) {
            logger.warn("Could not determine evaluation progress for simulation {}: {}. "
                    + "Assuming it has run to avoid replaying decisions.",
                simulation.getSimulationId().value().toString().substring(0, 8), e.getMessage());
            return simulation.getStartDate();
        }
    }

    /**
     * Find the resume point for a simulation.
     * Returns either:
     * - Last portfolio snapshot timestamp + interval (resuming)
     * - Simulation start date (starting fresh)
     * - Already complete indicator
     */
    private ResumeContext findResumePoint(Simulation simulation, StrategyConfig config) {
        // Query for last portfolio snapshot
        List<PortfolioSnapshot> snapshots = portfolioRepository.findBySimulationId(
            simulation.getSimulationId(),
            null,  // from start
            Instant.now(),  // up to now
            1  // get most recent
        );

        if (snapshots.isEmpty()) {
            // No checkpoints yet - start from beginning
            logger.info("No checkpoints found for simulation {}, starting from beginning",
                simulation.getSimulationId().value().toString().substring(0, 8));
            return ResumeContext.startFresh(simulation.getStartDate());
        }

        // A snapshot is NOT evidence of evaluation progress. Every simulation is seeded with an
        // opening snapshot at its start date before anything runs (CreateSimulationService,
        // StartSimulationService, initializePortfolio), so this list is never empty on a fresh
        // run — the branch above was dead for every backtest ever started. The seed was then
        // treated as a checkpoint and the run resumed at startDate + interval, silently skipping
        // the first scheduled evaluation: 0 of 40 simulations in the database had a decision at
        // their own start date.
        //
        // Consensus decisions are the real record of what has been evaluated. No decisions means
        // nothing has run yet, whatever snapshots exist.
        Instant lastEvaluatedAt = lastEvaluationTimestamp(simulation);
        if (lastEvaluatedAt == null) {
            logger.info("Simulation {} has no consensus decisions yet, starting from its start date {}",
                simulation.getSimulationId().value().toString().substring(0, 8),
                simulation.getStartDate());
            return ResumeContext.startFresh(simulation.getStartDate());
        }

        PortfolioSnapshot lastCheckpoint = snapshots.get(0);

        // Resume one interval after the last EVALUATION, not after the last snapshot. Snapshots
        // are written on their own cadence (six-hourly by default) which has nothing to do with
        // the evaluation schedule, so advancing from a snapshot timestamp lands at an arbitrary
        // offset. After restart_simulation_from_decision — which deletes the target decision so
        // it can be re-run — the last snapshot could sit hours before the cutoff, so
        // snapshot + interval skipped straight past the decision the user asked to replay and
        // left a permanent hole in the series.
        Instant nextEvaluationPoint = lastEvaluatedAt
            .plus(config.getBacktestIntervalSeconds(), ChronoUnit.SECONDS);

        // If next point is beyond end date, we're actually complete
        if (simulation.getEndDate() != null &&
            !nextEvaluationPoint.isBefore(simulation.getEndDate())) {
            logger.info("Simulation {} already complete (last evaluation: {})",
                simulation.getSimulationId().value().toString().substring(0, 8),
                lastEvaluatedAt);
            return ResumeContext.alreadyComplete(lastEvaluatedAt);
        }

        // Convert snapshot to Portfolio for resume - positions already deserialized by PortfolioMapper
        Portfolio resumePortfolio = new Portfolio(
            simulation.getSimulationId(),
            lastCheckpoint.getPositions(),
            lastCheckpoint.getCashAvailable()
        );

        logger.info("Resuming simulation {} from checkpoint at {} (next evaluation: {})",
            simulation.getSimulationId().value().toString().substring(0, 8),
            lastCheckpoint.getTimestamp(),
            nextEvaluationPoint);

        return ResumeContext.resumeFrom(nextEvaluationPoint, resumePortfolio);
    }

    /**
     * Get the latest portfolio state from snapshots, or initialize a new one if none exist.
     */
    private Portfolio getLatestPortfolio(SimulationId simulationId, Instant beforeTime) {
        // Query for the most recent portfolio snapshot before the given time
        List<PortfolioSnapshot> snapshots = portfolioRepository.findBySimulationId(
                simulationId,
                null,  // from: get all from beginning
                beforeTime,  // to: up to this time
                1  // limit: just get the most recent one
        );

        if (!snapshots.isEmpty()) {
            PortfolioSnapshot snapshot = snapshots.get(0);
            logger.debug("Found portfolio snapshot at {} for simulation {}",
                    snapshot.getTimestamp(), simulationId.value().toString().substring(0, 8));

            // Convert PortfolioSnapshot to Portfolio - positions already deserialized by PortfolioMapper
            return new Portfolio(simulationId, snapshot.getPositions(), snapshot.getCashAvailable());
        }

        // No snapshots exist yet - fetch simulation to get start date and initial cash
        logger.debug("No portfolio snapshots found, initializing portfolio for simulation {}",
                simulationId.value().toString().substring(0, 8));
        Simulation simulation = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalStateException("Simulation not found: " + simulationId));
        return initializePortfolio(simulationId, simulation.getStartDate(), simulation.getInitialCash());
    }


    /**
     * Execute simulation from a specific timestamp with resume context.
     * Supports resuming from last checkpoint or starting fresh.
     *
     * Handles two trading modes:
     * - STOCK: Uses scheduled evaluation times from live_schedule config (e.g., 10:00, 14:00 NY time)
     * - CRYPTO: Uses fixed interval between evaluations (live_schedule.interval)
     *
     * @return The last timestamp successfully executed in simulation time
     */
    private Instant executeSimulation(
            Simulation simulation,
            StrategyConfig strategyConfig,
            ResumeContext resumeContext) {

        Instant startTime = resumeContext.getResumeFrom();
        Instant endTime = simulation.getEndDate();
        Duration portfolioIntervalDuration = strategyConfig.getEffectivePortfolioSnapshotInterval();

        // Track the last successfully executed timestamp in simulation time
        Instant lastExecutedTimestamp = startTime;

        // Check trading mode to determine execution strategy (use BACKTEST schedule for simulations)
        TradingMode tradingMode = strategyConfig.getTradingMode();
        MarketHours backtestSchedule = strategyConfig.getBacktestSchedule();

        if (tradingMode == TradingMode.STOCK && backtestSchedule != null) {
            // STOCK MODE: Use backtest scheduled evaluation times
            logger.info("STOCK mode backtest - using backtest schedule: {}",
                backtestSchedule.evaluationTimes());
            return executeWithScheduledTimes(simulation, strategyConfig, startTime, endTime, portfolioIntervalDuration);
        } else {
            // CRYPTO MODE: Use backtest interval
            Duration consensusIntervalDuration = Duration.ofSeconds(strategyConfig.getBacktestIntervalSeconds());

            // SAFETY CHECK: Validate evaluation interval is reasonable (at least 1 second)
            if (consensusIntervalDuration.toSeconds() < 1) {
                logger.error("Invalid evaluation interval: {} seconds. Must be at least 1 second. Failing simulation.",
                    consensusIntervalDuration.toSeconds());
                throw new IllegalStateException("Evaluation interval must be at least 1 second, got: " +
                    consensusIntervalDuration.toSeconds() + " seconds");
            }

            logger.info("CRYPTO mode simulation - using {} second interval", consensusIntervalDuration.toSeconds());
            return executeWithInterval(simulation, strategyConfig, startTime, endTime, consensusIntervalDuration, portfolioIntervalDuration);
        }
    }

    /**
     * Execute simulation using scheduled evaluation times (STOCK mode).
     * Iterates through specific times of day (e.g., 10:00, 14:00) on trading days.
     */
    private Instant executeWithScheduledTimes(
            Simulation simulation,
            StrategyConfig strategyConfig,
            Instant startTime,
            Instant endTime,
            Duration portfolioIntervalDuration) {

        // Use backtest schedule for simulations (separate from live schedule)
        MarketHours marketHours = strategyConfig.getBacktestSchedule();
        ZonedDateTime currentZonedTime = ZonedDateTime.ofInstant(startTime, marketHours.timezone());
        Instant lastExecutedTimestamp = startTime;

        // Calculate approximate total evaluations for progress tracking
        int evaluationCount = 0;
        int evalTimesPerDay = marketHours.evaluationTimes().size();
        int totalEvaluations;
        if (endTime != null) {
            long totalDays = Duration.between(startTime, endTime).toDays();
            // Count only the weekdays this schedule actually evaluates on. Assuming a full five-day
            // week understates progress for a narrowed backtest_schedule — a [MONDAY] schedule
            // reported 11% when it was 18 of 29 sessions in, which reads as a stalled run.
            int tradingDaysPerWeek = marketHours.tradingDays() == null || marketHours.tradingDays().isEmpty()
                ? WEEKDAYS_PER_WEEK
                : Math.min(marketHours.tradingDays().size(), WEEKDAYS_PER_WEEK);
            totalEvaluations = (int) (totalDays * tradingDaysPerWeek / 7 * evalTimesPerDay) + 1;
            logger.info("Simulation will run approximately {} evaluations ({} eval times on each of {} trading days per week)",
                totalEvaluations, evalTimesPerDay, tradingDaysPerWeek);
        } else {
            // LIVE simulation with no end date - progress tracking is indefinite
            totalEvaluations = Integer.MAX_VALUE;
            logger.info("LIVE simulation with no end date - running indefinitely ({} eval times per trading day)",
                evalTimesPerDay);
        }

        // Find the first evaluation time on or after the start time
        ZonedDateTime nextEvalTime = marketHours.calculateNextEvaluationTime(currentZonedTime);
        if (nextEvalTime == null) {
            logger.error("No valid evaluation times found after start date");
            throw new IllegalStateException("No valid evaluation times found after start date");
        }

        // If nextEvalTime is before startTime (same day, earlier time already passed), skip to actual next
        if (nextEvalTime.toInstant().isBefore(startTime)) {
            nextEvalTime = marketHours.calculateNextEvaluationTime(nextEvalTime.plusMinutes(1));
        }

        // For LIVE simulations (endTime == null), run until current time boundary
        Instant effectiveEndTime = endTime != null ? endTime : Instant.MAX;
        while (nextEvalTime != null && nextEvalTime.toInstant().isBefore(effectiveEndTime)) {
            Instant currentTime = nextEvalTime.toInstant();

            // Time boundary check: Stop processing if we've reached present time
            if (currentTime.isAfter(Instant.now())) {
                logger.info("Reached current time boundary at evaluation #{}, stopping simulation (will switch to LIVE mode)",
                    evaluationCount);
                break;
            }

            // CRITICAL: Check if simulation was marked as FAILED, paused by user, or paused by system
            Simulation freshSimulation = simulationRepository.findById(simulation.getSimulationId()).orElse(null);
            if (freshSimulation != null && (freshSimulation.isTerminal() || freshSimulation.isPaused() || freshSimulation.isPausedForExternalReason())) {
                logger.info("Simulation {} was marked as {}{}{}, stopping executor loop",
                    simulation.getSimulationId().value().toString().substring(0, 8),
                    freshSimulation.getStatus(),
                    freshSimulation.isPaused() ? " (user paused)" : "",
                    freshSimulation.isPausedForExternalReason() ? " (reason: " + freshSimulation.getExternalPauseReason() + ")" : "");
                if (freshSimulation.isPausedForExternalReason()) {
                    simulation.pauseForExternalReason(freshSimulation.getExternalPauseReason());
                } else if (freshSimulation.isPaused()) {
                    simulation.pause();
                } else {
                    // Fresh copy is terminal (COMPLETED/FAILED) - adopt persisted state as-is.
                    // No transition validation: a force-completed LIVE simulation may have
                    // endDate null or in the future, which setStatus() would reject.
                    simulation.adoptTerminalStatus(freshSimulation.getStatus());
                }
                break;
            }

            renewLockLeaseOrAbort(simulation, evaluationCount);

            logger.info("Evaluation #{} at {} ({})", evaluationCount, currentTime, nextEvalTime.toLocalTime());

            try {
                String consensusRequestId = UUID.randomUUID().toString();

                consensusStartedEventPublisher.publish(new ConsensusStartedEvent(
                        simulation.getSimulationId(),
                        consensusRequestId,
                        currentTime
                ));

                ReachConsensusCommand command = new ReachConsensusCommand(
                        simulation.getSimulationId(),
                        currentTime
                );

                // Bounded: an evaluation that never returns is the one failure the loop cannot
                // otherwise survive. On expiry the round is recorded as a skip and we move on,
                // deliberately NOT advancing lastExecutedTimestamp — nothing was evaluated.
                if (boundedConsensusEvaluator.evaluate(simulation, command, consensusRequestId, currentTime)) {
                    lastExecutedTimestamp = currentTime;
                }
            } catch (BudgetDepletedException | DailySpendLimitExceededException budgetEx) {
                logger.warn("Budget exhausted at evaluation #{} for simulation {}, pausing: {}",
                    evaluationCount,
                    simulation.getSimulationId().value().toString().substring(0, 8),
                    budgetEx.getMessage());
                simulation.pauseForExternalReason(ExternalPauseReason.BUDGET_DEPLETED); // in-memory flag for post-loop guard
                simulationRepository.updatePauseState(simulation.getSimulationId(), false, true,
                    ExternalPauseReason.BUDGET_DEPLETED);
                dispatchBudgetPauseNotification(simulation, budgetEx);
                break;
            } catch (Exception e) {
                logger.warn("Evaluation #{} failed, continuing: {}", evaluationCount, e.getMessage(), e);
            }

            // Update portfolio values between evaluations
            // Wrapped in try-catch because portfolio updates may fail if consensus was skipped
            // (broker adapter not initialized, or no market data for valuation)
            try {
                Instant portfolioTime = lastExecutedTimestamp.plus(portfolioIntervalDuration);
                ZonedDateTime nextNextEval = marketHours.calculateNextEvaluationTime(nextEvalTime.plusMinutes(1));
                // Use effectiveEndTime (handles null endTime for LIVE simulations)
                Instant nextEvalInstant = nextNextEval != null ? nextNextEval.toInstant() : effectiveEndTime;

                while (portfolioTime.isBefore(nextEvalInstant)
                       && portfolioTime.isBefore(Instant.now())
                       && portfolioTime.isBefore(effectiveEndTime)) {
                    updatePortfolioWithMarketValueUseCase.updatePortfolioWithMarketValue(simulation.getSimulationId(), portfolioTime);
                    portfolioTime = portfolioTime.plus(portfolioIntervalDuration);
                }
            } catch (Exception e) {
                logger.debug("Portfolio valuation skipped for evaluation #{}: {}", evaluationCount, e.getMessage());
            }

            evaluationCount++;

            // Update progress
            if (totalEvaluations > 0) {
                int progressPercent = Math.min(99, (evaluationCount * 100) / totalEvaluations);
                simulation.updateProgress(progressPercent);
                // Narrow single-column write. A whole-entity save() here would also rewrite
                // status/completedAt from an instance loaded hours ago, silently undoing any
                // transition made in the meantime (ADR-0004).
                simulationRepository.updateProgress(simulation.getSimulationId(), progressPercent);
                logger.debug("Simulation progress: {}% ({}/{})", progressPercent, evaluationCount, totalEvaluations);
            }

            // Get next evaluation time
            nextEvalTime = marketHours.calculateNextEvaluationTime(nextEvalTime.plusMinutes(1));
        }

        // Final progress update — only for a run that actually reached its end. The loop also
        // breaks when the simulation was force-ended or paused from outside, and writing 100 here
        // overwrote that: a run stopped at 23% was persisted as a complete 100% run, making a
        // force-end indistinguishable from natural completion on every surface.
        if (!simulation.isTerminal() && !simulation.isPaused() && !simulation.isPausedForExternalReason()) {
            simulation.updateProgress(100);
            simulationRepository.updateProgress(simulation.getSimulationId(), 100);
        } else {
            logger.info("Simulation {} stopped externally at {}% — leaving progress as-is",
                simulation.getSimulationId().value().toString().substring(0, 8),
                simulation.getProgress());
        }

        logger.info("Simulation completed {} evaluations (last executed: {})", evaluationCount, lastExecutedTimestamp);
        return lastExecutedTimestamp;
    }

    /**
     * Execute simulation using fixed interval (CRYPTO mode).
     * Original interval-based execution logic.
     */
    private Instant executeWithInterval(
            Simulation simulation,
            StrategyConfig strategyConfig,
            Instant startTime,
            Instant endTime,
            Duration consensusIntervalDuration,
            Duration portfolioIntervalDuration) {

        Instant currentTime = startTime;
        Instant lastExecutedTimestamp = currentTime;

        // Calculate total evaluations for progress tracking
        int evaluationCount = 0;
        int totalEvaluations = 0;
        if (endTime != null && consensusIntervalDuration.toSeconds() > 0) {
            long totalSeconds = Duration.between(currentTime, endTime).toSeconds();
            totalEvaluations = (int) (totalSeconds / consensusIntervalDuration.toSeconds()) + 1;
            logger.info("Simulation will run approximately {} evaluations", totalEvaluations);
        }

        do {
            // Time boundary check: Stop processing if we've reached present time
            if (currentTime.isAfter(Instant.now())) {
                logger.info("Reached current time boundary at evaluation #{}, stopping simulation (will switch to LIVE mode)",
                    evaluationCount);
                break;
            }

            // CRITICAL: Check if simulation was marked as FAILED, paused by user, or paused by system
            Simulation freshSimulation = simulationRepository.findById(simulation.getSimulationId()).orElse(null);
            if (freshSimulation != null && (freshSimulation.isTerminal() || freshSimulation.isPaused() || freshSimulation.isPausedForExternalReason())) {
                logger.info("Simulation {} was marked as {}{}{}, stopping executor loop",
                    simulation.getSimulationId().value().toString().substring(0, 8),
                    freshSimulation.getStatus(),
                    freshSimulation.isPaused() ? " (user paused)" : "",
                    freshSimulation.isPausedForExternalReason() ? " (reason: " + freshSimulation.getExternalPauseReason() + ")" : "");
                if (freshSimulation.isPausedForExternalReason()) {
                    simulation.pauseForExternalReason(freshSimulation.getExternalPauseReason());
                } else if (freshSimulation.isPaused()) {
                    simulation.pause();
                } else {
                    // Fresh copy is terminal (COMPLETED/FAILED) - adopt persisted state as-is.
                    // No transition validation: a force-completed LIVE simulation may have
                    // endDate null or in the future, which setStatus() would reject.
                    simulation.adoptTerminalStatus(freshSimulation.getStatus());
                }
                break;
            }

            renewLockLeaseOrAbort(simulation, evaluationCount);

            logger.info("Evaluation #{} at {}", evaluationCount, currentTime);

            try {
                String consensusRequestId = UUID.randomUUID().toString();

                consensusStartedEventPublisher.publish(new ConsensusStartedEvent(
                        simulation.getSimulationId(),
                        consensusRequestId,
                        currentTime
                ));

                ReachConsensusCommand command = new ReachConsensusCommand(
                        simulation.getSimulationId(),
                        currentTime
                );

                // Bounded: an evaluation that never returns is the one failure the loop cannot
                // otherwise survive. On expiry the round is recorded as a skip and we move on,
                // deliberately NOT advancing lastExecutedTimestamp — nothing was evaluated.
                if (boundedConsensusEvaluator.evaluate(simulation, command, consensusRequestId, currentTime)) {
                    lastExecutedTimestamp = currentTime;
                }
            } catch (BudgetDepletedException | DailySpendLimitExceededException budgetEx) {
                logger.warn("Budget exhausted at evaluation #{} for simulation {}, pausing: {}",
                    evaluationCount,
                    simulation.getSimulationId().value().toString().substring(0, 8),
                    budgetEx.getMessage());
                simulation.pauseForExternalReason(ExternalPauseReason.BUDGET_DEPLETED); // in-memory flag for post-loop guard
                simulationRepository.updatePauseState(simulation.getSimulationId(), false, true,
                    ExternalPauseReason.BUDGET_DEPLETED);
                dispatchBudgetPauseNotification(simulation, budgetEx);
                break;
            } catch (Exception e) {
                logger.warn("Evaluation #{} failed, continuing: {}", evaluationCount, e.getMessage(), e);
            }

            // Update portfolio values - wrapped in try-catch because portfolio updates may fail
            // if consensus was skipped (broker adapter not initialized, or no market data)
            Instant previousTime = currentTime;
            currentTime = currentTime.plus(consensusIntervalDuration);
            try {
                Instant currentPortfolioTime = previousTime.plus(portfolioIntervalDuration);
                while (currentPortfolioTime.isBefore(currentTime)
                       && currentPortfolioTime.isBefore(Instant.now())
                       && (endTime != null && currentPortfolioTime.isBefore(endTime))){
                    updatePortfolioWithMarketValueUseCase.updatePortfolioWithMarketValue(simulation.getSimulationId(), currentPortfolioTime);
                    currentPortfolioTime = currentPortfolioTime.plus(portfolioIntervalDuration);
                }
            } catch (Exception e) {
                logger.debug("Portfolio valuation skipped for evaluation #{}: {}", evaluationCount, e.getMessage());
            }

            evaluationCount++;

            // Update progress and persist
            if (totalEvaluations > 0) {
                int progressPercent = Math.min(99, (evaluationCount * 100) / totalEvaluations);
                simulation.updateProgress(progressPercent);
                // Narrow single-column write. A whole-entity save() here would also rewrite
                // status/completedAt from an instance loaded hours ago, silently undoing any
                // transition made in the meantime (ADR-0004).
                simulationRepository.updateProgress(simulation.getSimulationId(), progressPercent);
                logger.debug("Simulation progress: {}% ({}/{})", progressPercent, evaluationCount, totalEvaluations);
            }
        } while ((endTime != null && currentTime.isBefore(endTime)));

        // Final progress update — only for a run that actually reached its end. The loop also
        // breaks when the simulation was force-ended or paused from outside, and writing 100 here
        // overwrote that: a run stopped at 23% was persisted as a complete 100% run, making a
        // force-end indistinguishable from natural completion on every surface.
        if (!simulation.isTerminal() && !simulation.isPaused() && !simulation.isPausedForExternalReason()) {
            simulation.updateProgress(100);
            simulationRepository.updateProgress(simulation.getSimulationId(), 100);
        } else {
            logger.info("Simulation {} stopped externally at {}% — leaving progress as-is",
                simulation.getSimulationId().value().toString().substring(0, 8),
                simulation.getProgress());
        }

        logger.info("Simulation completed {} evaluations (last executed: {})", evaluationCount, lastExecutedTimestamp);
        return lastExecutedTimestamp;
    }

    /**
     * Calculates the Sharpe ratio from portfolio value history.
     * <p>
     * Sharpe Ratio = (Average Return - Risk-Free Rate) / Standard Deviation of Returns
     */
    private double calculateSharpeRatio(List<BigDecimal> portfolioHistory) {
        // Calculate returns between each evaluation
        List<Double> returns = new ArrayList<>();

        for (int i = 1; i < portfolioHistory.size(); i++) {
            BigDecimal previousValue = portfolioHistory.get(i - 1);
            BigDecimal currentValue = portfolioHistory.get(i);

            if (previousValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal returnAmount = currentValue.subtract(previousValue);
                double returnPct = returnAmount.divide(previousValue, 6, RoundingMode.HALF_UP).doubleValue();
                returns.add(returnPct);
            }
        }

        if (returns.isEmpty()) {
            return 0.0;
        }

        // Calculate average return
        double avgReturn = returns.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // Calculate standard deviation
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - avgReturn, 2))
                .average()
                .orElse(0.0);

        double stdDeviation = Math.sqrt(variance);

        // Avoid division by zero
        if (stdDeviation == 0.0) {
            return 0.0;
        }

        // Risk-free rate (assume 0 for simplicity)
        double riskFreeRate = 0.0;

        return (avgReturn - riskFreeRate) / stdDeviation;
    }

    // ========== BROKER ACCOUNT HELPER METHODS ==========

    /**
     * Initialize broker account for simulation.
     * Configures broker adapter, links simulation to account, and reserves trading capital.
     *
     * @param simulation the simulation being started
     * @param brokerAccountId the broker account to use
     * @return the loaded BrokerAccount entity
     * @throws IllegalStateException if account not found or insufficient balance
     */
    private BrokerAccount initializeBrokerAccountForSimulation(Simulation simulation, BrokerAccountId brokerAccountId) {
        logger.info("Initializing broker account {} for simulation {}",
            brokerAccountId.value().toString().substring(0, 8),
            simulation.getSimulationId().value().toString().substring(0, 8));

        // Load broker account
        BrokerAccount account = brokerAccountRepository.findById(brokerAccountId)
            .orElseThrow(() -> new IllegalStateException(
                "Broker account not found: " + brokerAccountId.value()));

        // Get trading limit from simulation
        Money tradingLimit = simulation.getTradingLimit();
        if (tradingLimit == null) {
            throw new IllegalStateException(
                "Simulation has BrokerAccountId but no trading limit configured");
        }

        // Configure broker adapter with account config via BrokerRouter
        brokerRouter.getBrokerForAccount(brokerAccountId);

        // Link simulation to account via BrokerRouter
        brokerRouter.linkSimulationToAccount(simulation.getSimulationId(), brokerAccountId);

        // Reserve trading capital from BrokerAccount (idempotent for retries)
        Money available = account.getAvailableBalance();
        if (available.compareTo(tradingLimit) >= 0) {
            // Normal case: sufficient balance, reserve capital
            account.reserve(tradingLimit);
            brokerAccountRepository.save(account);

            logger.info("Reserved {} from broker account {} for simulation {} (available: {})",
                tradingLimit,
                brokerAccountId.value().toString().substring(0, 8),
                simulation.getSimulationId().value().toString().substring(0, 8),
                account.getAvailableBalance());
        } else if (account.getReservedBalance().compareTo(tradingLimit) >= 0) {
            // Retry scenario: capital already reserved from a previous attempt that failed
            // without releasing. Skip re-reservation to avoid double-booking.
            logger.info("Broker account {} already has sufficient reserved capital ({} >= {}), " +
                "skipping re-reservation (retry scenario for simulation {})",
                brokerAccountId.value().toString().substring(0, 8),
                account.getReservedBalance(),
                tradingLimit,
                simulation.getSimulationId().value().toString().substring(0, 8));
        } else {
            throw new IllegalStateException(
                String.format("Insufficient available balance. Requested: %s, Available: %s",
                    tradingLimit.format(), available.format()));
        }

        return account;
    }

    /**
     * Initialize SimulationPortfolio for tracking per-simulation positions and P&L.
     *
     * <p>The portfolio is created in the <em>account's</em> settlement currency
     * ({@link BrokerAccount#getSettlementCurrency()}), not a hard-coded USD. For a
     * USD (Alpaca) account this is byte-identical to the prior behaviour. For a EUR
     * (Belgian/Saxo) account it guarantees the portfolio's cash — and therefore every
     * downstream consensus / contribution {@code Money} operation — settles in EUR, so
     * {@code Money}'s same-currency guard is never tripped. The trading limit's amount
     * is preserved; only its currency is re-stamped from the account (a no-op when they
     * already agree, which they do once the create path validated the limit against the
     * account balance).</p>
     *
     * @param simulationId the simulation ID
     * @param brokerAccountId the linked broker account ID
     * @param tradingLimit the trading limit (becomes initial cash available)
     * @param account the linked broker account, source of the settlement currency
     */
    private void initializeSimulationPortfolio(
            SimulationId simulationId,
            BrokerAccountId brokerAccountId,
            Money tradingLimit,
            BrokerAccount account) {
        // Check if portfolio already exists (resume scenario)
        if (simulationPortfolioRepository.existsById(simulationId)) {
            logger.debug("SimulationPortfolio already exists for simulation {}, skipping initialization",
                simulationId.value().toString().substring(0, 8));
            return;
        }

        Money.Currency settlementCurrency = account.getSettlementCurrency();
        Money seedCash = tradingLimit;
        if (tradingLimit.currency() != settlementCurrency) {
            // The trading limit was minted in a different currency than the account
            // settles in. The account currency is authoritative — re-stamp the limit so
            // the portfolio's cash/positions arithmetic stays single-currency. This is
            // defensive: the create path validates the limit against the account balance
            // (same-currency compare), so a mismatch here should not normally occur.
            logger.warn("Trading limit currency {} differs from broker account {} settlement currency {} " +
                    "for simulation {}; minting portfolio in the account currency",
                tradingLimit.currency(),
                brokerAccountId.value().toString().substring(0, 8),
                settlementCurrency,
                simulationId.value().toString().substring(0, 8));
            seedCash = Money.of(tradingLimit.amount(), settlementCurrency);
        }

        SimulationPortfolio portfolio = SimulationPortfolio.initialize(
            simulationId,
            brokerAccountId,
            seedCash
        );

        simulationPortfolioRepository.save(portfolio);

        logger.info("Initialized SimulationPortfolio for simulation {} with trading limit {} ({})",
            simulationId.value().toString().substring(0, 8),
            seedCash,
            settlementCurrency);
    }

    /**
     * Release reserved capital back to BrokerAccount when simulation ends.
     *
     * @param brokerAccount the broker account to release to
     * @param tradingLimit the amount to release
     */
    private void releaseBrokerAccountReservation(BrokerAccount brokerAccount, Money tradingLimit) {
        logger.info("Releasing {} back to broker account {}",
            tradingLimit,
            brokerAccount.getId().value().toString().substring(0, 8));

        brokerAccount.release(tradingLimit);
        brokerAccountRepository.save(brokerAccount);

        logger.info("Released trading capital, broker account available balance: {}",
            brokerAccount.getAvailableBalance());
    }

    /**
     * Save final SimulationPortfolio state when simulation completes.
     *
     * @param simulationId the simulation ID
     */
    private void saveFinalSimulationPortfolio(SimulationId simulationId) {
        simulationPortfolioRepository.findById(simulationId).ifPresent(portfolio -> {
            // The portfolio is already being updated during trade execution
            // This is a final save to ensure consistency
            simulationPortfolioRepository.save(portfolio);
            logger.info("Saved final SimulationPortfolio state for simulation {} (realized P&L: {})",
                simulationId.value().toString().substring(0, 8),
                portfolio.getRealizedPnL());
        });
    }
}
