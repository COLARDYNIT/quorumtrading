package com.quorumtrading.application.usecase;

import com.quorumtrading.application.event.IndicatorUpdateEvent;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.service.IndicatorEnrichmentService;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.IndicatorContext;
import com.quorumtrading.model.valueobject.IndicatorSnapshot;
import com.quorumtrading.model.valueobject.PriceBar;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Use case for broadcasting real-time technical indicator updates to subscribed clients.
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Calculate technical indicators (RSI, MACD, SMA, Bollinger) for simulation tickers</li>
 *   <li>Track previous prices for price change calculations</li>
 *   <li>Fire CDI events that trigger WebSocket broadcasts</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <ul>
 *   <li>Called periodically by a scheduler to update indicators</li>
 *   <li>Called when price updates are received</li>
 * </ul>
 *
 * @see IndicatorUpdateEvent
 * @see IndicatorEnrichmentService
 */
public class BroadcastIndicatorsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(BroadcastIndicatorsUseCase.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final IndicatorEnrichmentService indicatorEnrichmentService;
    private final HistoricalPricePort historicalPricePort;
    private final EventPublisher<IndicatorUpdateEvent> indicatorEventPublisher;
    private final StrategyConfigParser strategyConfigParser;

    /**
     * Cache of previous prices per ticker for calculating price changes.
     * Key: ticker symbol (e.g., "AAPL"), Value: previous price.
     */
    private final Map<String, BigDecimal> previousPrices = new ConcurrentHashMap<>();

    public BroadcastIndicatorsUseCase(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            IndicatorEnrichmentService indicatorEnrichmentService,
            HistoricalPricePort historicalPricePort,
            EventPublisher<IndicatorUpdateEvent> indicatorEventPublisher) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.indicatorEnrichmentService = indicatorEnrichmentService;
        this.historicalPricePort = historicalPricePort;
        this.indicatorEventPublisher = indicatorEventPublisher;
        this.strategyConfigParser = new StrategyConfigParser();
    }

    /**
     * Broadcast indicator updates for a specific simulation.
     *
     * @param simulationId The simulation to broadcast indicators for
     */
    public void broadcastForSimulation(SimulationId simulationId) {
        LOG.debug("Broadcasting indicators for simulation: {}", simulationId);

        Optional<Simulation> simulationOpt = simulationRepository.findById(simulationId);
        if (simulationOpt.isEmpty()) {
            LOG.warn("Simulation not found: {}", simulationId);
            return;
        }

        Simulation simulation = simulationOpt.get();

        // Only broadcast for active simulations
        if (!isActiveSimulation(simulation)) {
            LOG.debug("Skipping non-active simulation: {} (status={})",
                simulationId, simulation.getStatus());
            return;
        }

        // Get strategy and parse tickers
        Optional<Strategy> strategyOpt = strategyRepository.findById(simulation.getStrategyId());
        if (strategyOpt.isEmpty()) {
            LOG.warn("Strategy not found for simulation: {}", simulationId);
            return;
        }

        Strategy strategy = strategyOpt.get();
        StrategyConfig config = strategyConfigParser.parse(strategy.getContent());

        // Get all tickers (both stock and crypto, though indicators are mainly for stocks)
        Set<Symbol> allTickers = new HashSet<>();
        allTickers.addAll(config.getStockTickers());
        allTickers.addAll(config.getCryptoTickers());

        if (allTickers.isEmpty()) {
            LOG.debug("No tickers configured for simulation: {}", simulationId);
            return;
        }

        // Calculate and broadcast indicators for each ticker
        for (Symbol ticker : allTickers) {
            try {
                broadcastTickerIndicators(simulationId, ticker);
            } catch (Exception e) {
                LOG.error("Failed to broadcast indicators for ticker {} in simulation {}: {}",
                    ticker.ticker(), simulationId, e.getMessage());
            }
        }
    }

    /**
     * Broadcast indicator updates for all active simulations.
     * Useful for scheduled periodic updates.
     */
    public void broadcastForAllActiveSimulations() {
        LOG.info("Broadcasting indicators for all active simulations");

        // Find running simulations (SIMULATING or LIVE)
        List<Simulation> simulatingSimulations = simulationRepository.findWithFilters(
            Optional.of(SimulationStatus.SIMULATING),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        List<Simulation> liveSimulations = simulationRepository.findWithFilters(
            Optional.of(SimulationStatus.LIVE),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        Set<Simulation> activeSimulations = new HashSet<>();
        activeSimulations.addAll(simulatingSimulations);
        activeSimulations.addAll(liveSimulations);

        LOG.info("Found {} active simulations to broadcast indicators for", activeSimulations.size());

        for (Simulation simulation : activeSimulations) {
            try {
                broadcastForSimulation(simulation.getSimulationId());
            } catch (Exception e) {
                LOG.error("Failed to broadcast indicators for simulation {}: {}",
                    simulation.getSimulationId(), e.getMessage());
            }
        }
    }

    /**
     * Broadcast indicators for a specific ticker in a simulation.
     */
    private void broadcastTickerIndicators(SimulationId simulationId, Symbol ticker) {
        // Get historical prices for indicator calculation
        List<PriceBar> priceBars = historicalPricePort.getHistoricalPrices(ticker, 200);

        if (priceBars == null || priceBars.isEmpty()) {
            LOG.debug("No price data available for ticker: {}", ticker.ticker());
            return;
        }

        // Get current price (latest bar)
        PriceBar latestBar = priceBars.get(priceBars.size() - 1);
        BigDecimal currentPrice = latestBar.close();

        // Get previous price for change calculation
        BigDecimal previousPrice = previousPrices.get(ticker.ticker());

        // Update price cache
        previousPrices.put(ticker.ticker(), currentPrice);

        // Calculate indicators using existing service
        List<Symbol> singleTicker = List.of(ticker);
        IndicatorContext context = indicatorEnrichmentService.calculateIndicators(singleTicker);

        IndicatorSnapshot snapshot = context.tickerIndicators().get(ticker);
        if (snapshot == null) {
            LOG.debug("No indicators calculated for ticker: {}", ticker.ticker());
            return;
        }

        // Fire event for WebSocket broadcast
        IndicatorUpdateEvent event = new IndicatorUpdateEvent(
            simulationId,
            ticker,
            currentPrice,
            previousPrice,
            snapshot,
            Instant.now()
        );

        indicatorEventPublisher.publish(event);
        LOG.debug("Published IndicatorUpdateEvent for simulation={}, ticker={}, price={}",
            simulationId, ticker.ticker(), currentPrice);
    }

    /**
     * Check if simulation is in an active state that should receive indicator updates.
     */
    private boolean isActiveSimulation(Simulation simulation) {
        SimulationStatus status = simulation.getStatus();
        return status == SimulationStatus.SIMULATING || status == SimulationStatus.LIVE;
    }
}
