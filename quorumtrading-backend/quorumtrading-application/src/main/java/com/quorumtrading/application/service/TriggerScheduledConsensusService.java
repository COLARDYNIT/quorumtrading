package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.StrategyParser;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.ReachConsensusUseCase;
import com.quorumtrading.application.usecase.ScheduledConsensusResult;
import com.quorumtrading.application.usecase.SimulationConsensusResult;
import com.quorumtrading.application.usecase.TriggerScheduledConsensusUseCase;
import com.quorumtrading.application.usecase.command.ReachConsensusCommand;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.enums.TradingMode;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.MarketHours;
import com.quorumtrading.model.valueobject.SimulationId;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service implementation for triggering scheduled consensus decisions.
 * Orchestrates data retrieval and calls ReachConsensusUseCase for active simulations.
 * Only triggers consensus if the strategy's evaluation interval has elapsed since the last consensus.
 *
 * IMPORTANT: Simulations are processed in PARALLEL using a dedicated thread pool to prevent
 * blocking the Quarkus scheduler thread. This allows multiple simulations to reach consensus
 * concurrently without causing scheduler thread timeouts (60s limit).
 */
public class TriggerScheduledConsensusService implements TriggerScheduledConsensusUseCase {

    private static final Logger LOG = Logger.getLogger(TriggerScheduledConsensusService.class);
    private static final int DEFAULT_THREAD_POOL_SIZE = 5; // Reduced from 10 to limit resource contention
    private static final long EXECUTOR_AWAIT_TIMEOUT_SECONDS = 300; // 5 minutes max wait per batch

    private final ReachConsensusUseCase reachConsensusUseCase;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final PortfolioRepository portfolioRepository;
    private final MarketEventRepository marketEventRepository;
    private final ConsensusDecisionRepository consensusDecisionRepository;
    private final StrategyParser strategyParser;
    private final ExecutorService executorService;

    public TriggerScheduledConsensusService(
            ReachConsensusUseCase reachConsensusUseCase,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            PortfolioRepository portfolioRepository,
            MarketEventRepository marketEventRepository,
            ConsensusDecisionRepository consensusDecisionRepository,
            StrategyParser strategyParser,
            ExecutorService executorService) {
        this.reachConsensusUseCase = reachConsensusUseCase;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.portfolioRepository = portfolioRepository;
        this.marketEventRepository = marketEventRepository;
        this.consensusDecisionRepository = consensusDecisionRepository;
        this.strategyParser = strategyParser;
        this.executorService = executorService;
    }

    @Override
    public ScheduledConsensusResult executeForAllActive() {
        List<String> errors = new ArrayList<>();

        try {
            // Query all simulations and filter for LIVE status (not SIMULATING) and not paused
            // SIMULATING simulations are still catching up with historical data and should not be interrupted
            // Runnable rows only — the pause filter now covers system pauses too, so a run the
            // circuit breaker or the stall detector paused stops being offered consensus rounds.
            // Asking the database also replaces a findAll() that materialised every simulation
            // ever created once a minute to keep the few that are LIVE.
            // The Java filter is belt-and-braces on top of the SQL predicate, not redundancy:
            // it keeps the guarantee locally checkable, and it survives someone later swapping
            // the query for a broader one.
            List<Simulation> liveNonPausedSimulations =
                    simulationRepository.findRunnable(SimulationStatus.LIVE).stream()
                    .filter(sim -> !sim.isPaused() && !sim.isPausedForExternalReason())
                    .toList();

            // Filter out backtests - they should only be started via StartSimulationUseCase (triggered by event handler)
            // Backtests have endDate <= now and are processed immediately on creation, not by scheduler
            List<Simulation> activeSimulations = liveNonPausedSimulations.stream()
                    .filter(sim -> !sim.isBacktest())
                    .toList();

            int backtestCount = liveNonPausedSimulations.size() - activeSimulations.size();
            if (backtestCount > 0) {
                LOG.infof("Found %d LIVE non-paused simulation(s), excluding %d backtest(s) from scheduled processing",
                        liveNonPausedSimulations.size(), backtestCount);
            }

            if (activeSimulations.isEmpty()) {
                LOG.debug("No active live simulations found (LIVE status, not paused, and not backtest)");
                return new ScheduledConsensusResult(0, 0, 0, List.of());
            }

            LOG.infof("Found %d active live simulation(s) to process in parallel", activeSimulations.size());

            // Submit all simulations as parallel tasks (non-blocking for scheduler thread)
            List<CompletableFuture<SimulationConsensusResult>> futures = new ArrayList<>();
            for (Simulation simulation : activeSimulations) {
                CompletableFuture<SimulationConsensusResult> future = CompletableFuture.supplyAsync(
                        () -> executeForSimulation(simulation.getSimulationId()),
                        executorService
                );
                futures.add(future);
            }

            // Wait for all futures to complete with timeout to prevent indefinite blocking
            // Note: Timeout applies to the wait, not individual simulations
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allFutures.get(EXECUTOR_AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Collect results from all completed futures
            int processed = 0;
            int skipped = 0;
            int failed = 0;

            for (CompletableFuture<SimulationConsensusResult> future : futures) {
                try {
                    SimulationConsensusResult result = future.getNow(null);
                    if (result == null) {
                        failed++;
                        errors.add("Simulation processing returned null result");
                        continue;
                    }

                    if (result.success()) {
                        processed++;
                    } else if (result.skipped()) {
                        skipped++;
                        if (result.errorMessage() != null) {
                            errors.add(result.errorMessage());
                        }
                    } else {
                        failed++;
                        if (result.errorMessage() != null) {
                            errors.add(result.errorMessage());
                        }
                    }
                } catch (Exception e) {
                    failed++;
                    errors.add("Error retrieving simulation result: " + e.getMessage());
                }
            }

            return new ScheduledConsensusResult(processed, skipped, failed, errors);

        } catch (java.util.concurrent.TimeoutException e) {
            LOG.warnf(e, "Consensus scheduler timed out after %d seconds. Some simulations may still be processing in background.",
                    EXECUTOR_AWAIT_TIMEOUT_SECONDS);
            errors.add(String.format("Timeout after %d seconds waiting for all simulations to complete",
                    EXECUTOR_AWAIT_TIMEOUT_SECONDS));
            // Return partial results - simulations continue processing in background
            return new ScheduledConsensusResult(0, 0, 0, errors);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to execute scheduled consensus for all active simulations");
            errors.add("Global error: " + e.getMessage());
            return new ScheduledConsensusResult(0, 0, 0, errors);
        }
    }

    @Override
    public SimulationConsensusResult executeForSimulation(SimulationId simulationId) {
        try {
            LOG.debugf("Processing simulation ID: %s", simulationId.value());

            // Get the simulation
            Optional<Simulation> simulationOpt = simulationRepository.findById(simulationId);
            if (simulationOpt.isEmpty()) {
                String msg = String.format("Simulation not found: %s", simulationId.value());
                LOG.warnf(msg);
                return SimulationConsensusResult.skipped(simulationId, msg);
            }
            Simulation simulation = simulationOpt.get();

            // Verify simulation is in LIVE status (not SIMULATING) and not paused
            if (simulation.getStatus() != SimulationStatus.LIVE || simulation.isPaused()) {
                String msg = String.format("Simulation %s is not in LIVE status or is paused (status: %s, paused: %s)",
                        simulationId.value(), simulation.getStatus(), simulation.isPaused());
                LOG.debugf(msg);
                return SimulationConsensusResult.skipped(simulationId, msg);
            }

            // Verify simulation is not a backtest (backtests should only be started via StartSimulationUseCase)
            if (simulation.isBacktest()) {
                String msg = String.format("Simulation %s is a backtest (endDate <= now) and should not be processed by scheduler",
                        simulationId.value());
                LOG.debugf(msg);
                return SimulationConsensusResult.skipped(simulationId, msg);
            }

            // Get the strategy for this simulation
            Optional<Strategy> strategyOpt = strategyRepository.findById(simulation.getStrategyId());
            if (strategyOpt.isEmpty()) {
                String msg = String.format("Strategy not found for simulation %s", simulationId.value());
                LOG.warnf(msg);
                return SimulationConsensusResult.skipped(simulationId, msg);
            }
            Strategy strategy = strategyOpt.get();

            // Parse strategy configuration to get evaluation interval
            StrategyConfig strategyConfig;
            try {
                strategyConfig = strategyParser.parse(strategy.getContent());
            } catch (Exception e) {
                String msg = String.format("Failed to parse strategy for simulation %s: %s",
                        simulationId.value(), e.getMessage());
                LOG.warnf(e, msg);
                return SimulationConsensusResult.skipped(simulationId, msg);
            }

            // STOCK mode: Check if current time is within market hours (use live schedule)
            if (strategyConfig.getTradingMode() == TradingMode.STOCK) {
                MarketHours marketHours = strategyConfig.getLiveSchedule();
                if (marketHours == null) {
                    String msg = String.format(
                            "Simulation %s: STOCK mode requires live_schedule configuration but none found",
                            simulationId.value());
                    LOG.warnf(msg);
                    return SimulationConsensusResult.skipped(simulationId, msg);
                }

                ZonedDateTime now = ZonedDateTime.now(marketHours.timezone());

                // Check if we're within market hours
                if (!marketHours.isWithinMarketHours(now)) {
                    String msg = String.format(
                            "Simulation %s: Outside market hours (%s). Current time: %s %s. Skipping consensus.",
                            simulationId.value(),
                            marketHours.getDisplaySchedule(),
                            now.toLocalTime(),
                            now.getDayOfWeek());
                    LOG.debugf(msg);
                    return SimulationConsensusResult.skipped(simulationId, msg);
                }

                // Check if we're near a scheduled evaluation time
                if (!marketHours.isNearEvaluationTime(now)) {
                    String msg = String.format(
                            "Simulation %s: Not near any scheduled evaluation time. Current time: %s. Scheduled times: %s. Skipping consensus.",
                            simulationId.value(),
                            now.toLocalTime(),
                            marketHours.evaluationTimes());
                    LOG.debugf(msg);
                    return SimulationConsensusResult.skipped(simulationId, msg);
                }

                LOG.infof("Simulation %s: Within market hours and near evaluation time. Current time: %s. Proceeding with consensus.",
                        simulationId.value(), now.toLocalTime());

                // STOCK mode: Also check that we haven't already evaluated at this scheduled time today
                // This prevents multiple evaluations within the same scheduled time window
                Optional<ConsensusDecision> lastConsensusOpt = consensusDecisionRepository.findLatestBySimulationId(simulationId);
                if (lastConsensusOpt.isPresent()) {
                    ConsensusDecision lastConsensus = lastConsensusOpt.get();
                    Instant lastConsensusTime = lastConsensus.getDecisionTimestamp();
                    // If we evaluated within the last 30 minutes, skip (prevents duplicate triggers within same window)
                    long minutesSinceLastConsensus = ChronoUnit.MINUTES.between(lastConsensusTime, Instant.now());
                    if (minutesSinceLastConsensus < 30) {
                        String msg = String.format(
                                "Simulation %s: Already evaluated %d minutes ago (within 30-minute window). Skipping duplicate.",
                                simulationId.value(), minutesSinceLastConsensus);
                        LOG.debugf(msg);
                        return SimulationConsensusResult.skipped(simulationId, msg);
                    }
                }
            } else {
                // CRYPTO mode: Check if enough time has passed since last consensus (use live interval)
                int evaluationIntervalSeconds = strategyConfig.getLiveIntervalSeconds();
                Optional<ConsensusDecision> lastConsensusOpt = consensusDecisionRepository.findLatestBySimulationId(simulationId);

                if (lastConsensusOpt.isPresent()) {
                    ConsensusDecision lastConsensus = lastConsensusOpt.get();
                    Instant lastConsensusTime = lastConsensus.getDecisionTimestamp();
                    Instant now = Instant.now();
                    long secondsSinceLastConsensus = ChronoUnit.SECONDS.between(lastConsensusTime, now);

                    if (secondsSinceLastConsensus < evaluationIntervalSeconds) {
                        String msg = String.format(
                                "Simulation %s: Only %d seconds since last consensus (need %d seconds). Skipping.",
                                simulationId.value(), secondsSinceLastConsensus, evaluationIntervalSeconds);
                        LOG.debugf(msg);
                        return SimulationConsensusResult.skipped(simulationId, msg);
                    }

                    LOG.infof("Simulation %s: %d seconds since last consensus (threshold: %d seconds). Triggering consensus.",
                            simulationId.value(), secondsSinceLastConsensus, evaluationIntervalSeconds);
                } else {
                    LOG.infof("Simulation %s: No previous consensus found. Triggering first consensus.",
                            simulationId.value());
                }
            }

            // Get the latest portfolio snapshot
            Optional<PortfolioSnapshot> snapshotOpt = portfolioRepository.findLatestBySimulationId(simulationId);
            if (snapshotOpt.isEmpty()) {
                String msg = String.format("No portfolio snapshot found for simulation %s", simulationId.value());
                LOG.warnf(msg);
                return SimulationConsensusResult.skipped(simulationId, msg);
            }
            PortfolioSnapshot snapshot = snapshotOpt.get();

            // Create minimal consensus command — ReachConsensusService fetches full portfolio with positions
            Instant now = Instant.now();
            ReachConsensusCommand command = new ReachConsensusCommand(
                    simulationId,
                    now
            );

            reachConsensusUseCase.execute(command);

            LOG.infof("Successfully triggered consensus for simulation %s", simulationId.value());
            return SimulationConsensusResult.success(simulationId);

        } catch (Exception e) {
            String msg = String.format("Failed to process simulation %s: %s",
                    simulationId.value(), e.getMessage());
            LOG.errorf(e, msg);
            return SimulationConsensusResult.failed(simulationId, msg);
        }
    }
}
