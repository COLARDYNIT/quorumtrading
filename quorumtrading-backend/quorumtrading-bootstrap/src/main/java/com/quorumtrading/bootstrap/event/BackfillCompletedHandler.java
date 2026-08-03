package com.quorumtrading.bootstrap.event;

import com.quorumtrading.application.event.BackfillStatusChangedEvent;
import com.quorumtrading.application.event.SimulationDataReadyEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.MarketEventRepository;
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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * CDI event handler that observes backfill completion and notifies linked simulations.
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * Bootstrap layer adapter that bridges CDI events. When a backfill completes
 * successfully, if it has a linked simulation, fires SimulationDataReadyEvent
 * to start the simulation.
 *
 * <p><b>Flow:</b></p>
 * <pre>
 * BackfillStatusChangedEvent (COMPLETED) -> BackfillCompletedHandler
 *                                            |
 *                                            v
 *                             [Has linkedSimulationId?]
 *                                            |
 *                            Yes             |            No
 *                             |              v             |
 *                             v         (no action)       v
 *                   SimulationDataReadyEvent
 *                             |
 *                             v
 *                   SimulationDataReadyEventHandler -> StartSimulation
 * </pre>
 *
 * <p><b>Design Note:</b></p>
 * Uses synchronous @Observes (not @ObservesAsync) because we want to ensure
 * the event is processed within the same transaction context as the backfill
 * completion if applicable. The SimulationDataReadyEvent is fired async.
 */
@ApplicationScoped
public class BackfillCompletedHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BackfillCompletedHandler.class);
    private static final int MINIMUM_PRICE_EVENTS_PER_TICKER = 1;

    @Inject
    BackfillJobRepository backfillJobRepository;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    MarketEventRepository marketEventRepository;

    @Inject
    Event<SimulationDataReadyEvent> dataReadyEvent;

    private final StrategyConfigParser strategyConfigParser = new StrategyConfigParser();

    /**
     * Observes BackfillStatusChangedEvent and fires SimulationDataReadyEvent for linked simulations.
     * Validates that market data actually exists before firing the event.
     *
     * @param event the backfill status changed event
     */
    void onBackfillCompleted(@Observes BackfillStatusChangedEvent event) {
        if (event.newStatus() != BackfillStatus.COMPLETED) {
            return;
        }

        LOG.debug("Backfill completed: {}", event.jobId());

        backfillJobRepository.findById(event.jobId())
            .flatMap(BackfillJob::getLinkedSimulationId)
            .ifPresent(simId -> {
                // Guard: simulation may have been deleted while backfill was running
                if (simulationRepository.findById(simId).isEmpty()) {
                    LOG.info("Linked simulation {} no longer exists (deleted during backfill), skipping", simId);
                    return;
                }

                // Validate that market data exists before starting simulation
                if (validateMarketDataAvailability(simId)) {
                    // Transition simulation from WAITING_FOR_DATA back to PENDING
                    Simulation simulation = simulationRepository.findById(simId)
                        .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simId));

                    if (simulation.isWaitingForData()) {
                        simulation.dataReady();  // WAITING_FOR_DATA → PENDING, clears backfillJobId
                        simulationRepository.updateStatus(simId,
                            SimulationStatus.WAITING_FOR_DATA, SimulationStatus.PENDING,
                            simulation.getCompletedAt(), simulation.getFailureReason());
                        LOG.info("Simulation {} transitioned from WAITING_FOR_DATA to PENDING", simId);
                    }

                    LOG.info("Market data validated - firing SimulationDataReadyEvent for simulation: {}", simId);
                    dataReadyEvent.fireAsync(new SimulationDataReadyEvent(simId));
                } else {
                    LOG.error("Backfill completed but insufficient market data exists for simulation: {}. " +
                        "Simulation will not start. Check backfill logs for skipped gaps.", simId);

                    // Fail the simulation to prevent it from being orphaned in WAITING_FOR_DATA
                    simulationRepository.findById(simId).ifPresent(sim -> {
                        if (!sim.isTerminal()) {
                            SimulationStatus before = sim.getStatus();
                            sim.fail("Backfill finished but not enough market data exists for this "
                                + "simulation period. Check get_price_coverage for the tickers and widen "
                                + "the date range or back-fill again.");
                            simulationRepository.updateStatus(simId, before, SimulationStatus.FAILED,
                                sim.getCompletedAt(), sim.getFailureReason());
                            LOG.warn("Simulation {} failed due to insufficient market data after backfill", simId);
                        }
                    });
                }
            });
    }

    /**
     * Validates that sufficient market data exists for a simulation.
     * Checks that at least MINIMUM_PRICE_EVENTS_PER_TICKER price events exist for each ticker.
     *
     * @param simulationId The simulation to validate
     * @return true if sufficient data exists, false otherwise
     */
    private boolean validateMarketDataAvailability(com.quorumtrading.model.valueobject.SimulationId simulationId) {
        try {
            // Load simulation and strategy
            Simulation simulation = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));

            Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + simulation.getStrategyId()));

            // Extract symbols from strategy
            Set<Symbol> symbols = extractSymbolsFromStrategy(strategy);
            if (symbols.isEmpty()) {
                LOG.error("No symbols found in strategy {} for simulation {} - cannot validate market data",
                    strategy.getId(), simulationId.value());
                return false;
            }

            // Check each ticker has minimum price data
            // Cap end date to now — for live/future-dated simulations, data beyond now doesn't exist yet
            Instant now = Instant.now();
            Instant effectiveEnd = simulation.getEndDate().isAfter(now) ? now : simulation.getEndDate();

            for (Symbol symbol : symbols) {
                long priceEventCount = marketEventRepository.countEventsInRange(
                    symbol,
                    EventType.PRICE,
                    simulation.getStartDate(),
                    effectiveEnd
                );

                if (priceEventCount < MINIMUM_PRICE_EVENTS_PER_TICKER) {
                    LOG.error("Insufficient price data for ticker {}: found {} events, need at least {}",
                        symbol.ticker(), priceEventCount, MINIMUM_PRICE_EVENTS_PER_TICKER);
                    return false;
                }

                LOG.debug("Validated ticker {}: {} price events available", symbol.ticker(), priceEventCount);
            }

            LOG.info("Market data validation passed for simulation {}: {} tickers checked",
                simulationId, symbols.size());
            return true;

        } catch (Exception e) {
            LOG.error("Failed to validate market data for simulation {}: {}", simulationId, e.getMessage(), e);
            return false; // Fail safe: don't start simulation if validation fails
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
