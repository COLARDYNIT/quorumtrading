package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TickerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Application service that maintains the registry of tickers from all active strategies.
 *
 * <p>This service aggregates ticker symbols across all strategies to determine
 * what market data needs to be collected by the data collection adapters.</p>
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Query all strategies from repository</li>
 *   <li>Parse strategy configurations to extract tickers</li>
 *   <li>Aggregate tickers into a unified {@link TickerRegistry}</li>
 *   <li>Provide this registry to market data collection services</li>
 * </ul>
 *
 * <p><b>Hexagonal Architecture:</b></p>
 * This service sits in the application layer, using the {@link StrategyRepository} port
 * to access strategies and the {@link StrategyConfigParser} domain service to parse them.
 * Market data adapters (outer layer) will consume the resulting {@link TickerRegistry}.
 */
public class TickerRegistryService {

    private static final Logger log = LoggerFactory.getLogger(TickerRegistryService.class);

    private final StrategyRepository strategyRepository;

    public TickerRegistryService(StrategyRepository strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    /**
     * Get all unique tickers from all strategies.
     *
     * <p>Aggregates stock and crypto tickers from every strategy in the repository,
     * providing a complete view of what market data should be collected.</p>
     *
     * <p><b>Performance Note:</b> This method queries all strategies and parses their
     * configurations. Consider caching if called frequently, though typical usage
     * is from a scheduled job (every 5 minutes), so performance impact is minimal.</p>
     *
     * @return Combined set of stock and crypto symbols across all strategies
     */
    public TickerRegistry getAllRequiredTickers() {
        List<Strategy> strategies = strategyRepository.findAll();

        if (strategies.isEmpty()) {
            log.debug("No strategies found in repository - returning empty ticker registry");
            return TickerRegistry.empty();
        }

        Set<Symbol> cryptoSymbols = new HashSet<>();
        Set<Symbol> stockSymbols = new HashSet<>();

        for (Strategy strategy : strategies) {
            try {
                StrategyConfig config = new StrategyConfigParser().parse(strategy.getContent());

                // Extract crypto symbols (already Symbol value objects from StrategyConfig)
                if (config.getCryptoTickers() != null) {
                    cryptoSymbols.addAll(config.getCryptoTickers());
                }

                // Extract stock symbols (already Symbol value objects from StrategyConfig)
                if (config.getStockTickers() != null) {
                    stockSymbols.addAll(config.getStockTickers());
                }

            } catch (Exception e) {
                // Silently skip unparseable strategies (e.g. missing tickers)
                log.debug("Skipping strategy {} — ticker extraction failed: {}",
                    strategy.getId(), e.getMessage());
            }
        }

        Set<Symbol> rejected = TickerRegistry.rejectedStockTickers(stockSymbols);
        if (!rejected.isEmpty()) {
            // Loud on purpose: the strategy looks configured but its equity quotes are being
            // discarded, and before this guard they were poisoning the crypto price series
            // for the same ticker across every simulation on the instance.
            log.warn("Ignoring {} stock ticker(s) that are known crypto symbols: {}. "
                    + "A strategy declares them under stock_tickers, but a price bar's asset class "
                    + "is derived from the ticker string, so equity quotes for these symbols would "
                    + "overwrite the crypto price series. Move them to crypto_tickers.",
                rejected.size(), rejected.stream().map(Symbol::ticker).sorted().toList());
        }

        TickerRegistry registry = new TickerRegistry(cryptoSymbols, stockSymbols);
        log.info("Built ticker registry from {} strategies: {} crypto symbols, {} stock symbols",
            strategies.size(), registry.cryptoTickers().size(), registry.stockTickers().size());

        return registry;
    }

    /**
     * Get symbols for a specific asset type.
     *
     * @param assetType "CRYPTO" or "STOCK"
     * @return Set of Symbol value objects for the requested asset type
     */
    public Set<Symbol> getSymbolsByAssetType(String assetType) {
        TickerRegistry registry = getAllRequiredTickers();

        return switch (assetType.toUpperCase()) {
            case "CRYPTO" -> registry.cryptoTickers();
            case "STOCK" -> registry.stockTickers();
            default -> throw new IllegalArgumentException("Unknown asset type: " + assetType);
        };
    }
}
