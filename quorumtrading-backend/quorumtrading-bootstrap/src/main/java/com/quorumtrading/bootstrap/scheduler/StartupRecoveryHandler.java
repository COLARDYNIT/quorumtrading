package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.event.SimulationDataReadyEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.SimulationLockPort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.Symbol;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Handles recovery tasks on application startup.
 *
 * <p>After a server restart, the previous instance may have left behind stale locks
 * and orphaned simulations. This handler runs immediately on startup to:</p>
 * <ol>
 *   <li>Clean stale simulation locks (instead of waiting up to 5m for {@link StaleLockCleanupScheduler})</li>
 *   <li>Log orphaned SIMULATING simulations (picked up by {@link SimulationPickupScheduler} within 30s)</li>
 *   <li>Log pending backfill jobs (picked up by {@link BackfillScheduler} within 10s)</li>
 *   <li>Recover WAITING_FOR_DATA simulations whose data is already available</li>
 * </ol>
 */
@ApplicationScoped
public class StartupRecoveryHandler {

    private static final Logger LOG = Logger.getLogger(StartupRecoveryHandler.class);
    private static final Duration STALE_LOCK_DURATION = Duration.ofMinutes(15);

    @Inject
    SimulationLockPort simulationLockPort;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    BackfillJobRepository backfillJobRepository;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    MarketEventRepository marketEventRepository;

    @Inject
    Event<SimulationDataReadyEvent> dataReadyEvent;

    private final StrategyConfigParser strategyConfigParser = new StrategyConfigParser();

    void onStartup(@Observes StartupEvent event) {
        LOG.info("Running startup recovery checks...");

        cleanStaleLocks();
        logOrphanedSimulations();
        logPendingBackfillJobs();
        recoverWaitingForDataSimulations();

        LOG.info("Startup recovery checks complete");
    }

    private void cleanStaleLocks() {
        try {
            int cleaned = simulationLockPort.forceUnlockStale(STALE_LOCK_DURATION);
            if (cleaned > 0) {
                LOG.infof("Startup recovery: cleaned %d stale simulation locks from previous instance", cleaned);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Startup recovery: failed to clean stale locks");
        }
    }

    private void logOrphanedSimulations() {
        try {
            List<Simulation> orphaned = simulationRepository.findWithFilters(
                Optional.of(SimulationStatus.SIMULATING),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            );
            if (!orphaned.isEmpty()) {
                LOG.infof("Startup recovery: found %d orphaned SIMULATING simulations — " +
                    "SimulationPickupScheduler will resume them within 30s", orphaned.size());
                for (Simulation sim : orphaned) {
                    LOG.infof("  Orphaned simulation: %s (paused=%s)",
                        sim.getSimulationId().value().toString().substring(0, 8), sim.isPaused());
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Startup recovery: failed to check orphaned simulations");
        }
    }

    private void logPendingBackfillJobs() {
        try {
            List<BackfillJob> pendingJobs = backfillJobRepository.findPendingJobs();
            if (!pendingJobs.isEmpty()) {
                LOG.infof("Startup recovery: found %d pending backfill jobs — " +
                    "BackfillScheduler will process them within 10s", pendingJobs.size());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Startup recovery: failed to check pending backfill jobs");
        }
    }

    /**
     * Recovers simulations stuck in WAITING_FOR_DATA state.
     *
     * <p>A simulation can get stuck if the backfill completed but the completion event
     * failed to transition the simulation (e.g. due to a race condition or crash).
     * This method checks each stuck simulation:</p>
     * <ul>
     *   <li>If the linked backfill job is still in progress → skip (BackfillScheduler handles it)</li>
     *   <li>If all market data is available → transition to PENDING and fire SimulationDataReadyEvent</li>
     *   <li>If data is missing and no backfill is running → fail the simulation</li>
     * </ul>
     */
    private void recoverWaitingForDataSimulations() {
        try {
            List<Simulation> stuck = simulationRepository.findWithFilters(
                Optional.of(SimulationStatus.WAITING_FOR_DATA),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            );
            if (stuck.isEmpty()) {
                return;
            }

            LOG.infof("Startup recovery: found %d WAITING_FOR_DATA simulations, checking data availability...",
                stuck.size());

            for (Simulation sim : stuck) {
                recoverSimulation(sim);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Startup recovery: failed to recover WAITING_FOR_DATA simulations");
        }
    }

    private void recoverSimulation(Simulation sim) {
        String simIdShort = sim.getSimulationId().value().toString().substring(0, 8);
        try {
            // If a backfill job is still linked and in progress, leave it alone
            if (sim.getBackfillJobId() != null) {
                Optional<BackfillJob> job = backfillJobRepository.findById(sim.getBackfillJobId());
                if (job.isPresent()
                        && job.get().getStatus() != BackfillStatus.COMPLETED
                        && job.get().getStatus() != BackfillStatus.FAILED) {
                    LOG.infof("Startup recovery: simulation %s has active backfill job %s (%s), skipping",
                        simIdShort, sim.getBackfillJobId().toString().substring(0, 8), job.get().getStatus());
                    return;
                }
            }

            // Check if market data is available
            if (validateMarketData(sim)) {
                sim.dataReady();
                simulationRepository.updateStatus(sim.getSimulationId(),
                    SimulationStatus.WAITING_FOR_DATA, SimulationStatus.PENDING,
                    sim.getCompletedAt(), sim.getFailureReason());
                LOG.infof("Startup recovery: simulation %s recovered WAITING_FOR_DATA → PENDING (data available)",
                    simIdShort);
                dataReadyEvent.fireAsync(new SimulationDataReadyEvent(sim.getSimulationId()));
            } else {
                sim.fail("Stuck in WAITING_FOR_DATA at startup: no market data stored for this range and no active backfill job. Back-fill the tickers for the simulation period, then create it again.");
                simulationRepository.updateStatus(sim.getSimulationId(),
                    SimulationStatus.WAITING_FOR_DATA, SimulationStatus.FAILED,
                    sim.getCompletedAt(), sim.getFailureReason());
                LOG.warnf("Startup recovery: simulation %s failed — stuck in WAITING_FOR_DATA with no data and no active backfill",
                    simIdShort);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Startup recovery: failed to recover simulation %s", simIdShort);
        }
    }

    private boolean validateMarketData(Simulation sim) {
        Strategy strategy = strategyRepository.findById(sim.getStrategyId()).orElse(null);
        if (strategy == null) {
            LOG.warnf("Startup recovery: strategy %s not found for simulation %s",
                sim.getStrategyId(), sim.getSimulationId());
            return false;
        }

        Set<Symbol> symbols = extractSymbols(strategy);
        if (symbols.isEmpty()) {
            return true; // No symbols configured — allow simulation to proceed
        }

        for (Symbol symbol : symbols) {
            long count = marketEventRepository.countEventsInRange(
                symbol, EventType.PRICE, sim.getStartDate(), sim.getEndDate());
            if (count < 1) {
                LOG.infof("Startup recovery: simulation %s missing data for %s (0 price events)",
                    sim.getSimulationId().value().toString().substring(0, 8), symbol.ticker());
                return false;
            }
        }
        return true;
    }

    private Set<Symbol> extractSymbols(Strategy strategy) {
        try {
            StrategyConfig config = strategyConfigParser.parse(strategy.getContent());
            Set<Symbol> all = new HashSet<>();
            all.addAll(config.getStockTickers());
            all.addAll(config.getCryptoTickers());
            return all;
        } catch (Exception e) {
            LOG.warnf("Startup recovery: failed to parse strategy %s for symbols: %s",
                strategy.getId(), e.getMessage());
            return Set.of();
        }
    }
}
