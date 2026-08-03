package com.quorumtrading.bootstrap.event;

import com.quorumtrading.application.event.DataGapDetectedEvent;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.RequestBackfillUseCase;
import com.quorumtrading.application.usecase.command.RequestBackfillCommand;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CDI event handler that observes DataGapDetectedEvent and triggers re-backfill with delay.
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * Bootstrap layer adapter that bridges CDI events to use cases. When a circuit breaker
 * detects too many skipped evaluations due to missing data, this handler:
 * 1. Waits for configured delay (respects provider rate limits)
 * 2. Triggers backfill for the remaining simulation time range
 * 3. Links the backfill to the simulation for auto-resume on completion
 *
 * <p><b>Flow:</b></p>
 * <pre>
 * DataGapDetectedEvent -> DataGapHandler
 *                          |
 *                          v
 *                   [Wait rebackfill-delay-seconds]
 *                          |
 *                          v
 *                   RequestBackfillUseCase
 *                          |
 *                          v
 *                   Simulation.waitForData(backfillJobId)
 *                          |
 *                          v
 *                   [On backfill complete]
 *                          |
 *                          v
 *                   BackfillCompletedHandler -> resume simulation
 * </pre>
 *
 * <p><b>Design Note:</b></p>
 * Uses @ObservesAsync to avoid blocking the consensus thread. The delay is configurable
 * to respect provider rate limits (e.g., Alpaca's rate limiting).
 */
@ApplicationScoped
public class DataGapHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DataGapHandler.class);

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    RequestBackfillUseCase requestBackfillUseCase;

    @Inject
    AnalyzeDataAvailabilityUseCase analyzeDataAvailabilityUseCase;

    @ConfigProperty(name = "quorumtrading.backfill.rebackfill-delay-seconds", defaultValue = "60")
    int rebackfillDelaySeconds;

    private final StrategyConfigParser strategyConfigParser = new StrategyConfigParser();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                LOG.warn("DataGapHandler scheduler did not terminate within 10s - forcing shutdown");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Observes DataGapDetectedEvent and schedules re-backfill after configured delay.
     *
     * @param event the data gap detected event from circuit breaker
     */
    void onDataGapDetected(@ObservesAsync DataGapDetectedEvent event) {
        LOG.info("Data gap detected for simulation {} - {} consecutive skips, {}% total. " +
            "Scheduling re-backfill in {} seconds to respect rate limits.",
            event.simulationId().value().toString().substring(0, 8),
            event.consecutiveSkips(),
            event.totalSkipPercentage(),
            rebackfillDelaySeconds);

        // Schedule re-backfill with delay to respect provider rate limits
        scheduler.schedule(() -> triggerRebackfill(event), rebackfillDelaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Triggers backfill for the remaining time range of the simulation.
     * Called after the configured delay has elapsed.
     *
     * @param event the original data gap detected event
     */
    private void triggerRebackfill(DataGapDetectedEvent event) {
        try {
            LOG.info("Starting re-backfill for simulation {} after {} second delay",
                event.simulationId().value().toString().substring(0, 8),
                rebackfillDelaySeconds);

            // Load simulation
            Simulation simulation = simulationRepository.findById(event.simulationId())
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + event.simulationId()));

            // Skip if simulation is no longer paused (user may have resumed/cancelled)
            if (!simulation.isPaused()) {
                LOG.info("Simulation {} is no longer paused, skipping re-backfill",
                    event.simulationId().value().toString().substring(0, 8));
                return;
            }

            // Load strategy to get tickers
            Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + simulation.getStrategyId()));

            Set<Symbol> symbols = extractSymbolsFromStrategy(strategy);
            if (symbols.isEmpty()) {
                LOG.warn("No symbols found in strategy {} - cannot trigger re-backfill", strategy.getId());
                return;
            }

            // Use the event's tickers if available (more specific to the gap)
            Set<Symbol> tickersToBackfill = event.missingSymbols() != null && !event.missingSymbols().isEmpty()
                ? event.missingSymbols()
                : symbols;

            // Build time range from current gap to simulation end
            // The gap started at currentTimestamp and extends to remainingEndTimestamp
            // For LIVE simulations, remainingEndTimestamp is null — use current time as end
            LocalDate gapStart = event.currentTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate gapEnd = event.remainingEndTimestamp() != null
                ? event.remainingEndTimestamp().atZone(ZoneOffset.UTC).toLocalDate()
                : Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
            TimeRange timeRange = TimeRange.of(gapStart, gapEnd);

            LOG.info("Triggering re-backfill for simulation {} - {} tickers, time range: {} to {}",
                event.simulationId().value().toString().substring(0, 8),
                tickersToBackfill.size(),
                gapStart,
                gapEnd);

            // Trigger backfill with forceBackfill=true to ensure data is fetched
            RequestBackfillResult result = requestBackfillUseCase.execute(new RequestBackfillCommand(
                tickersToBackfill,
                Set.of(EventType.PRICE),  // Only price data needed
                timeRange,
                true,  // forceBackfill - override gap analysis
                event.simulationId()  // linkedSimulationId - triggers auto-resume on complete
            ));

            if (result.backfillTriggered() && result.jobId() != null) {
                // Transition simulation to WAITING_FOR_DATA
                SimulationStatus before = simulation.getStatus();
                simulation.waitForData(result.jobId());
                simulationRepository.updateStatus(simulation.getSimulationId(), before,
                    simulation.getStatus(), simulation.getCompletedAt(), simulation.getFailureReason());

                LOG.info("Re-backfill triggered for simulation {} - backfill job: {}. " +
                    "Simulation will auto-resume when backfill completes.",
                    event.simulationId().value().toString().substring(0, 8),
                    result.jobId());
            } else {
                LOG.warn("Re-backfill not triggered for simulation {} - result: {}",
                    event.simulationId().value().toString().substring(0, 8),
                    result);
            }

        } catch (Exception e) {
            LOG.error("Failed to trigger re-backfill for simulation {}: {}",
                event.simulationId().value().toString().substring(0, 8),
                e.getMessage(), e);
        }
    }

    /**
     * Extract all ticker symbols from a strategy using StrategyConfigParser.
     *
     * @param strategy The strategy to extract symbols from
     * @return Set of Symbol objects, or empty set if parsing fails
     */
    private Set<Symbol> extractSymbolsFromStrategy(Strategy strategy) {
        try {
            StrategyConfig config = strategyConfigParser.parse(strategy.getContent());
            Set<Symbol> allTickers = new HashSet<>();
            allTickers.addAll(config.getStockTickers());
            allTickers.addAll(config.getCryptoTickers());
            return allTickers;
        } catch (Exception e) {
            LOG.warn("Failed to parse strategy {} for symbols: {}", strategy.getId(), e.getMessage());
            return Set.of();
        }
    }
}
