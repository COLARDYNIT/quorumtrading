package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationDataReadyEvent;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.RequestBackfillUseCase;
import com.quorumtrading.application.usecase.command.AnalyzeDataAvailabilityCommand;
import com.quorumtrading.application.usecase.command.RequestBackfillCommand;
import com.quorumtrading.application.usecase.result.RequestBackfillResult;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates the backfill gate for simulations.
 *
 * <p><b>DDD Classification:</b> Application Service</p>
 * Coordinates use cases for data availability checking and backfill triggering.
 *
 * <p><b>Flow:</b></p>
 * <ul>
 *   <li>For LIVE simulations: fires SimulationDataReadyEvent immediately (no backfill needed)</li>
 *   <li>For BACKTEST simulations: checks data availability, triggers backfill if needed</li>
 * </ul>
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * Pure application service - no framework dependencies.
 * Bootstrap layer handlers call this service with CDI events.
 */
public class BackfillOrchestratorService {

    private static final Logger LOG = LoggerFactory.getLogger(BackfillOrchestratorService.class);
    private static final int MINIMUM_PRICE_EVENTS_PER_TICKER = 1;

    /**
     * Extra calendar days to backfill before the simulation start date so that
     * technical indicators (SMA-50, MACD-26/9, RSI-14, Bollinger-20) have enough
     * historical data to produce meaningful values from day one.
     * 70 calendar days ≈ 50 trading days (× 7/5).
     */
    static final int INDICATOR_WARMUP_CALENDAR_DAYS = 70;

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AnalyzeDataAvailabilityUseCase availabilityUseCase;
    private final RequestBackfillUseCase backfillUseCase;
    private final EventPublisher<SimulationDataReadyEvent> eventPublisher;
    private final MarketEventRepository marketEventRepository;
    private final StrategyConfigParser strategyConfigParser;

    public BackfillOrchestratorService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AnalyzeDataAvailabilityUseCase availabilityUseCase,
            RequestBackfillUseCase backfillUseCase,
            EventPublisher<SimulationDataReadyEvent> eventPublisher,
            MarketEventRepository marketEventRepository) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.availabilityUseCase = availabilityUseCase;
        this.backfillUseCase = backfillUseCase;
        this.eventPublisher = eventPublisher;
        this.marketEventRepository = marketEventRepository;
        this.strategyConfigParser = new StrategyConfigParser();
    }

    /**
     * Handle a newly created simulation.
     *
     * <p><b>Mode-based behavior:</b></p>
     * <ul>
     *   <li>LIVE mode: fires SimulationDataReadyEvent immediately (streams real-time data)</li>
     *   <li>BACKTEST mode: checks data availability, fires event or triggers backfill</li>
     * </ul>
     *
     * @param simulationId The ID of the newly created simulation
     */
    public void handleSimulationCreated(SimulationId simulationId) {
        LOG.info("Orchestrating backfill check for simulation: {}", simulationId);

        Simulation simulation = simulationRepository.findById(simulationId)
            .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));

        // LIVE mode (no end date) doesn't need backfill — streams real-time data
        if (!simulation.isBacktest()) {
            LOG.debug("LIVE simulation - firing data ready event immediately: {}", simulationId);
            eventPublisher.publish(new SimulationDataReadyEvent(simulationId));
            return;
        }

        // BACKTEST mode - check data availability
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + simulation.getStrategyId()));

        // Extract symbols from strategy using StrategyConfigParser
        Set<Symbol> symbols = extractSymbolsFromStrategy(strategy);
        if (symbols.isEmpty()) {
            LOG.warn("No symbols found in strategy {} - firing data ready event anyway", strategy.getId());
            eventPublisher.publish(new SimulationDataReadyEvent(simulationId));
            return;
        }

        // Backfill PRICE data (required) and NEWS data (enriches sentiment/fundamental agents).
        // Alpaca provides historical news back to 2015, Finnhub provides company-specific news.
        Set<EventType> dataTypes = Set.of(EventType.PRICE, EventType.NEWS);

        // Build TimeRange from simulation dates
        // IMPORTANT: Must use TimeRange.of(LocalDate, LocalDate) factory method, NOT the
        // direct Instant constructor. The factory method correctly handles end-date inclusivity,
        // while direct Instant construction causes getEnd() to return 1 day before actual end.
        LocalDate startDate = simulation.getStartDate().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate backfillStartDate = startDate.minusDays(INDICATOR_WARMUP_CALENDAR_DAYS);
        LocalDate endDate = simulation.getEndDate().atZone(ZoneOffset.UTC).toLocalDate();

        // Clamp warmup start to earliest available data per ticker to avoid false-positive gaps.
        // If the warmup extends before any data exists, gap detection flags those pre-data dates
        // as gaps and triggers a redundant backfill.
        LocalDate effectiveBackfillStart = backfillStartDate;
        for (Symbol symbol : symbols) {
            Optional<Instant> earliestDate = marketEventRepository.findEarliestPriceDate(symbol);
            if (earliestDate.isPresent()) {
                LocalDate earliest = earliestDate.get().atZone(ZoneOffset.UTC).toLocalDate();
                if (earliest.isAfter(backfillStartDate) && earliest.isBefore(startDate)) {
                    LOG.debug("Clamping warmup start for {} from {} to earliest data {}",
                        symbol.ticker(), backfillStartDate, earliest);
                    if (earliest.isAfter(effectiveBackfillStart)) {
                        effectiveBackfillStart = earliest;
                    }
                }
            }
        }
        if (!effectiveBackfillStart.equals(backfillStartDate)) {
            LOG.info("Clamped warmup start from {} to {} based on earliest available data",
                backfillStartDate, effectiveBackfillStart);
        }

        TimeRange timeRange = TimeRange.of(effectiveBackfillStart, endDate);

        LOG.debug("Analyzing data availability for {} symbols over {}", symbols.size(), timeRange);

        DataAvailability availability = availabilityUseCase.execute(
            new AnalyzeDataAvailabilityCommand(
                timeRange,
                symbols,
                dataTypes
            )
        );

        List<MarketDataGap> gaps = availability.identifyGaps();

        if (gaps.isEmpty()) {
            // DEFENSE IN DEPTH: Even when analysis says no gaps, validate actual data exists
            // This catches edge cases where analysis incorrectly reports coverage
            if (validateMarketDataAvailability(symbols, simulation)) {
                LOG.info("No data gaps found and data validated - firing data ready event: {}", simulationId);
                eventPublisher.publish(new SimulationDataReadyEvent(simulationId));
            } else {
                // Data validation failed - trigger backfill with forceBackfill=true
                LOG.warn("No data gaps found but validation failed - triggering forced backfill for simulation: {}", simulationId);
                RequestBackfillResult result = backfillUseCase.execute(new RequestBackfillCommand(
                    symbols,
                    dataTypes,
                    timeRange,
                    true,  // forceBackfill - override gap analysis
                    simulationId  // linkedSimulationId - fires event when complete
                ));
                transitionToWaitingForData(simulation, result);
            }
        } else {
            LOG.info("Found {} data gaps - triggering backfill for simulation: {}", gaps.size(), simulationId);
            RequestBackfillResult result = backfillUseCase.execute(new RequestBackfillCommand(
                symbols,
                dataTypes,
                timeRange,
                false,  // forceBackfill
                simulationId  // linkedSimulationId - fires event when complete
            ));
            transitionToWaitingForData(simulation, result);
        }
    }

    /**
     * Transition simulation to WAITING_FOR_DATA state when backfill is triggered.
     * Links the simulation to the backfill job for tracking.
     *
     * @param simulation The simulation being transitioned
     * @param backfillResult The result from triggering backfill
     */
    private void transitionToWaitingForData(Simulation simulation, RequestBackfillResult backfillResult) {
        if (backfillResult.backfillTriggered() && backfillResult.jobId() != null) {
            com.quorumtrading.model.enums.SimulationStatus before = simulation.getStatus();
            simulation.waitForData(backfillResult.jobId());
            simulationRepository.updateStatus(simulation.getSimulationId(), before,
                simulation.getStatus(), simulation.getCompletedAt(), simulation.getFailureReason());
            LOG.info("Simulation {} transitioned to WAITING_FOR_DATA, linked to backfill job {}",
                simulation.getSimulationId(), backfillResult.jobId());
        } else {
            // Edge case: backfill wasn't triggered (data was already complete)
            // This shouldn't happen in normal flow, but fire data ready event just in case
            LOG.warn("Backfill not triggered for simulation {} - firing data ready event",
                simulation.getSimulationId());
            eventPublisher.publish(new SimulationDataReadyEvent(simulation.getSimulationId()));
        }
    }

    /**
     * Validates that market data actually exists for all tickers in the simulation's time range.
     *
     * <p>This is a DEFENSE IN DEPTH check to ensure simulations don't start without data,
     * even if the gap analysis incorrectly reports no gaps.</p>
     *
     * @param symbols The symbols to validate
     * @param simulation The simulation being validated
     * @return true if at least MINIMUM_PRICE_EVENTS_PER_TICKER exist for all tickers, false otherwise
     */
    private boolean validateMarketDataAvailability(Set<Symbol> symbols, Simulation simulation) {
        try {
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
                    LOG.warn("Insufficient price data for ticker {}: found {} events, need at least {}",
                        symbol.ticker(), priceEventCount, MINIMUM_PRICE_EVENTS_PER_TICKER);
                    return false;
                }

                LOG.debug("Validated ticker {}: {} price events available", symbol.ticker(), priceEventCount);
            }

            LOG.info("Market data validation passed: {} tickers checked", symbols.size());
            return true;

        } catch (Exception e) {
            LOG.error("Failed to validate market data: {}", e.getMessage(), e);
            return false; // Fail safe: trigger backfill if validation fails
        }
    }

    /**
     * Extract all ticker symbols from a strategy using StrategyConfigParser.
     * Combines stock and crypto tickers from the strategy configuration.
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
