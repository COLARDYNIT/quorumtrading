package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.DataCollectionResult;
import com.quorumtrading.application.usecase.TriggerDataCollectionUseCase;
import com.quorumtrading.application.usecase.UpdatePortfolioWithMarketValueUseCase;
import com.quorumtrading.application.usecase.command.TriggerDataCollectionCommand;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled job to automatically collect market data from enabled providers
 * and update portfolio snapshots for LIVE simulations.
 * Runs once on startup and then every 5 minutes.
 */
@ApplicationScoped
public class MarketDataCollectionJob {

    private static final Logger LOG = Logger.getLogger(MarketDataCollectionJob.class);

    @Inject
    TriggerDataCollectionUseCase triggerDataCollectionUseCase;

    @Inject
    UpdatePortfolioWithMarketValueUseCase updatePortfolioWithMarketValueUseCase;

    @Inject
    SimulationRepository simulationRepository;

    @ConfigProperty(name = "quorumtrading.marketdata.scheduled-collection.enabled", defaultValue = "true")
    boolean scheduledCollectionEnabled;

    @ConfigProperty(name = "quorumtrading.portfolio.periodic-snapshots.enabled", defaultValue = "true")
    boolean periodicSnapshotsEnabled;

//    /**
//     * Run market data collection once on application startup for faster testing.
//     */
//    void onStart(@Observes StartupEvent ev) {
//        if (!scheduledCollectionEnabled) {
//            LOG.debug("Scheduled market data collection is disabled, skipping startup collection");
//            return;
//        }
//        LOG.info("Running initial market data collection on startup");
//        collectMarketData();
//    }

    /**
     * Trigger market data collection from all enabled providers
     * and update portfolio snapshots for LIVE simulations.
     * Runs every 5 minutes.
     */
    @Scheduled(every = "5m", concurrentExecution = ConcurrentExecution.SKIP)
    public void collectMarketData() {
        if (!scheduledCollectionEnabled) {
            LOG.debug("Scheduled market data collection is disabled");
            return;
        }
        LOG.info("Starting scheduled market data collection");

        try {
            // 1. Collect market data from all enabled providers
            TriggerDataCollectionCommand command = new TriggerDataCollectionCommand(null, null, null);
            DataCollectionResult result = triggerDataCollectionUseCase.execute(command);

            if (result.errors().isEmpty()) {
                LOG.infof("Market data collection completed successfully - Provider: %s, Events: %d",
                        result.providerName(), result.eventsCollected());
            } else {
                LOG.warnf("Market data collection completed with errors - Provider: %s, Events: %d, Errors: %s",
                        result.providerName(), result.eventsCollected(), String.join(", ", result.errors()));
            }

            // 2. Update portfolio snapshots for LIVE simulations with fresh market data
            updateLivePortfolios();

        } catch (Exception e) {
            LOG.errorf(e, "Failed to collect market data");
        }
    }

    /**
     * Update portfolio snapshots for all LIVE simulations with fresh market prices.
     * Runs after market data collection to ensure latest prices are available.
     * <p>
     * This method creates portfolio snapshots every 5 minutes using the same logic
     * as backtesting (SimulationExecutor.java:394-402), ensuring real-time portfolio
     * tracking independent of consensus evaluations.
     */
    private void updateLivePortfolios() {
        if (!periodicSnapshotsEnabled) {
            LOG.debug("Periodic portfolio snapshots disabled");
            return;
        }

        try {
            // Find all LIVE simulations
            List<Simulation> liveSimulations = simulationRepository.findWithFilters(
                Optional.of(SimulationStatus.LIVE),  // status filter
                Optional.empty(),                     // no strategy filter
                Optional.empty(),                     // no since filter
                Optional.empty()                      // no limit
            );

            if (liveSimulations.isEmpty()) {
                LOG.debug("No LIVE simulations found, skipping portfolio updates");
                return;
            }

            LOG.infof("Updating portfolio snapshots for %d LIVE simulations", liveSimulations.size());
            Instant now = Instant.now();

            int successCount = 0;
            int failCount = 0;

            for (Simulation simulation : liveSimulations) {
                try {
                    // Update portfolio with fresh market prices (reuses same logic as backtesting)
                    // Returns null if market prices are unavailable (outside trading hours, missing data)
                    PortfolioSnapshot snapshot = updatePortfolioWithMarketValueUseCase
                        .updatePortfolioWithMarketValue(simulation.getSimulationId(), now);

                    if (snapshot != null) {
                        successCount++;
                        LOG.debugf("Updated portfolio for simulation %s: value=%s",
                            simulation.getSimulationId().value().toString().substring(0, 8),
                            snapshot.getTotalValue());
                    } else {
                        // Null means skipped due to missing market data - not a failure
                        LOG.debugf("Skipped portfolio update for simulation %s (missing market prices)",
                            simulation.getSimulationId().value().toString().substring(0, 8));
                    }
                } catch (Exception e) {
                    failCount++;
                    LOG.warnf(e, "Failed to update portfolio for simulation %s",
                        simulation.getSimulationId().value().toString().substring(0, 8));
                }
            }

            LOG.infof("Portfolio updates completed: %d succeeded, %d failed", successCount, failCount);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to update LIVE portfolios");
        }
    }
}
