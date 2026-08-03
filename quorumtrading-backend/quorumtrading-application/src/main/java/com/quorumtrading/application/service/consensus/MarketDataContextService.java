package com.quorumtrading.application.service.consensus;

import com.quorumtrading.application.orchestration.StrategyParser;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.service.AuthorizationService;
import com.quorumtrading.application.usecase.GetMarketDataContextUseCase;
import com.quorumtrading.application.usecase.command.GetMarketDataContextCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.marketdata.MarketDataContext;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.Timeframe;
import com.quorumtrading.model.orchestration.StrategyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Returns the market data context for each ticker configured in a simulation's
 * strategy, exactly as the agents would see it during consensus evaluation
 * (frontend display use case).
 *
 * <p>Plain POJO wired via {@code ApplicationConfig} (CDI convention: application
 * services are produced, not annotated). Extracted move-only from
 * {@code ReachConsensusService}, reusing {@link ConsensusContextBuilder} for the
 * fetch/filter logic — behavior preserved.</p>
 */
public class MarketDataContextService implements GetMarketDataContextUseCase {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataContextService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final StrategyParser strategyParser;
    private final AuthorizationService authorizationService;
    private final ConsensusContextBuilder contextBuilder;

    public MarketDataContextService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            StrategyParser strategyParser,
            AuthorizationService authorizationService,
            ConsensusContextBuilder contextBuilder) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.strategyParser = strategyParser;
        this.authorizationService = authorizationService;
        this.contextBuilder = contextBuilder;
    }

    @Override
    public Map<String, MarketDataContext> execute(GetMarketDataContextCommand command) {
        logger.info("Retrieving market data context for simulation: {} at timestamp: {}",
            command.simulationId().value().toString().substring(0, 8), command.analysisTimestamp());

        try {
            // Step 1: Fetch Simulation
            Simulation simulation = simulationRepository.findById(command.simulationId())
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + command.simulationId()));

            // Step 2: Fetch Strategy
            Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + simulation.getStrategyId()));

            // Authorization check
            authorizationService.requireView(command.requestingUserId(), simulation, strategy);

            // Step 3: Parse strategy configuration
            StrategyConfig strategyConfig = strategyParser.parse(strategy.getContent());

            // Step 4: Fetch multi-timeframe market data (same as consensus uses)
            Map<Timeframe, List<MarketEvent>> marketDataForTickers =
                contextBuilder.fetchMultiTimeframeMarketDataForTickers(
                    strategyConfig,
                    command.analysisTimestamp()
                );

            // Step 5: Build MarketDataContext for each ticker
            Set<String> allTickers = new HashSet<>();
            strategyConfig.getStockTickers().stream()
                .map(symbol -> symbol.ticker())
                .forEach(allTickers::add);
            strategyConfig.getCryptoTickers().stream()
                .map(symbol -> symbol.ticker())
                .forEach(allTickers::add);

            Map<String, MarketDataContext> contextByTicker = new HashMap<>();

            for (String ticker : allTickers) {
                // Filter market events to only this ticker's data
                Map<Timeframe, List<MarketEvent>> filteredData = contextBuilder.filterMarketEventsByTimeframe(
                    marketDataForTickers,
                    ticker
                );

                // Create MarketDataContext (same logic as executeConsensusForTicker)
                MarketDataContext context = MarketDataContext.from(
                    filteredData,
                    Set.of(ticker),
                    command.analysisTimestamp()
                );

                contextByTicker.put(ticker, context);
            }

            logger.info("Built market data context for {} tickers", contextByTicker.size());
            return contextByTicker;

        } catch (Exception e) {
            logger.error("Failed to build market data context for simulation: {}",
                command.simulationId(), e);
            throw new RuntimeException("Failed to build market data context", e);
        }
    }
}
