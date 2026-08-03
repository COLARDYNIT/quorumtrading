package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.usecase.DataCollectionResult;
import com.quorumtrading.application.usecase.TriggerDataCollectionUseCase;
import com.quorumtrading.application.usecase.command.TriggerDataCollectionCommand;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.RateLimit;
import com.quorumtrading.model.valueobject.TickerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service implementation for triggering market data collection.
 * Framework-agnostic, uses constructor injection.
 *
 * <p>Enforces rate limiting to prevent exceeding API quotas. With scheduler running every 5 minutes
 * (288 times/day), providers with daily limits (e.g., NewsAPI: 100 requests/day) would quickly
 * exceed their quotas without rate limiting.</p>
 *
 * <p><b>Strategy-Driven Data Collection:</b></p>
 * Uses {@link TickerRegistryService} to fetch tickers from all active strategies, ensuring
 * market data adapters only collect data for symbols actually being traded.
 */
public class TriggerDataCollectionService implements TriggerDataCollectionUseCase {

    private static final Logger log = LoggerFactory.getLogger(TriggerDataCollectionService.class);

    private final List<MarketDataPort> marketDataPorts;
    private final MarketEventRepository marketEventRepository;
    private final RateLimitTracker rateLimitTracker;
    private final TickerRegistryService tickerRegistryService;

    public TriggerDataCollectionService(
            List<MarketDataPort> marketDataPorts,
            MarketEventRepository marketEventRepository,
            RateLimitTracker rateLimitTracker,
            TickerRegistryService tickerRegistryService) {
        this.marketDataPorts = marketDataPorts;
        this.marketEventRepository = marketEventRepository;
        this.rateLimitTracker = rateLimitTracker;
        this.tickerRegistryService = tickerRegistryService;
    }

    @Override
    public DataCollectionResult execute(TriggerDataCollectionCommand command) {
        // Get current ticker requirements from all strategies
        TickerRegistry tickerRegistry = tickerRegistryService.getAllRequiredTickers();

        if (tickerRegistry.isEmpty()) {
            log.warn("No tickers configured in any strategy - adapters will fall back to their defaults");
        } else {
            log.debug("Collecting data for {} crypto tickers and {} stock tickers from strategies",
                tickerRegistry.cryptoTickers().size(), tickerRegistry.stockTickers().size());
        }

        List<String> allErrors = new ArrayList<>();
        int totalEventsCollected = 0;
        int skippedProviders = 0;

        // Determine which features are needed based on ticker registry
        Set<MarketDataFeature> requiredFeatures = determineRequiredFeatures(tickerRegistry);

        for (MarketDataPort port : marketDataPorts) {
            String providerName = port.getProviderName();
            RateLimit rateLimit = port.getRateLimit();

            // Skip providers that don't support any of the required features
            if (!supportsAnyRequiredFeature(port, requiredFeatures)) {
                log.debug("Skipping provider {} - doesn't support any required features: {}",
                    providerName, requiredFeatures);
                continue;
            }

            // Check rate limit before calling provider
            if (rateLimitTracker.wouldExceedLimit(providerName, rateLimit)) {
                int callsInWindow = rateLimitTracker.getCallsInWindow(providerName, rateLimit);
                log.info("Skipping provider {} - rate limit reached: {}/{} calls in current window ({})",
                    providerName, callsInWindow, rateLimit.maxRequests(), rateLimit);
                skippedProviders++;
                continue;
            }

            try {
                // Use date range if provided, otherwise fetch latest with strategy tickers
                // Note: Adapters (outer layer) use strings for API calls, so we convert Symbol → String
                List<MarketEvent> events = command.hasDateRange()
                    ? port.fetchDataInRange(
                        tickerRegistry.getCryptoTickerStrings(),
                        tickerRegistry.getStockTickerStrings(),
                        command.from(),
                        command.to())
                    : port.fetchLatestDataForTickers(tickerRegistry);

                // Record successful API call for rate limiting
                rateLimitTracker.recordCall(providerName);

                // Batch save events - repository will handle duplicate detection internally.
                // Latest-quote polls replace the day's prior quote row instead of accumulating.
                int savedCount = saveEvents(events, providerName, allErrors, !command.hasDateRange());
                totalEventsCollected += savedCount;

                log.debug("Provider {} collected {} events ({} saved, {} duplicates), {}/{} rate limit used",
                    providerName,
                    events.size(),
                    savedCount,
                    events.size() - savedCount,
                    rateLimitTracker.getCallsInWindow(providerName, rateLimit),
                    rateLimit.isUnlimited() ? "unlimited" : rateLimit.maxRequests());

            } catch (Exception e) {
                allErrors.add(providerName + ": Failed to fetch data: " + e.getMessage());
                // Still record the call attempt for rate limiting even if it failed
                rateLimitTracker.recordCall(providerName);
            }
        }

        if (skippedProviders > 0) {
            log.info("Data collection completed: {} events collected, {} providers skipped due to rate limits",
                totalEventsCollected, skippedProviders);
        }

        return new DataCollectionResult("all-providers", totalEventsCollected, allErrors);
    }

    /**
     * Save events to repository, handling duplicates gracefully.
     * Returns the count of successfully saved (non-duplicate) events.
     */
    private int saveEvents(List<MarketEvent> events, String providerName, List<String> allErrors, boolean latestQuoteMode) {
        int savedCount = 0;

        for (MarketEvent event : events) {
            try {
                MarketEvent saved = latestQuoteMode && event.eventType() == com.quorumtrading.model.enums.EventType.PRICE
                    ? marketEventRepository.saveLatestPriceQuote(event)
                    : marketEventRepository.save(event);
                // save() returns null for duplicates - only count successfully saved events
                if (saved != null) {
                    savedCount++;
                }
            } catch (Exception e) {
                // Log at debug level - duplicates are expected and not errors
                log.debug("Skipped duplicate event from {}: {} at {}",
                    providerName, event.symbol(), event.timestamp());
            }
        }

        return savedCount;
    }

    /**
     * Determine which features are needed based on the ticker registry.
     *
     * <p>Providers need to support at least one of these features to be useful:</p>
     * <ul>
     *   <li>CRYPTO - if crypto tickers are present</li>
     *   <li>STOCK - if stock tickers are present</li>
     *   <li>NEWS or SOCIAL_MEDIA - always useful for sentiment analysis</li>
     * </ul>
     */
    private Set<MarketDataFeature> determineRequiredFeatures(TickerRegistry tickerRegistry) {
        Set<MarketDataFeature> features = new java.util.HashSet<>();

        // Asset type features
        if (!tickerRegistry.cryptoTickers().isEmpty()) {
            features.add(MarketDataFeature.CRYPTO);
        }
        if (!tickerRegistry.stockTickers().isEmpty()) {
            features.add(MarketDataFeature.STOCK);
        }

        // News and social media are always valuable for strategies
        features.add(MarketDataFeature.NEWS);
        features.add(MarketDataFeature.SOCIAL_MEDIA);

        return features;
    }

    /**
     * Check if a provider supports any of the required features.
     *
     * <p>A provider is useful if it can provide ANY of the required data types.
     * For example, a NEWS provider is useful even if we also need CRYPTO prices.</p>
     */
    private boolean supportsAnyRequiredFeature(MarketDataPort port, Set<MarketDataFeature> requiredFeatures) {
        if (requiredFeatures.isEmpty()) {
            // No specific requirements, all providers are valid
            return true;
        }

        for (MarketDataFeature feature : requiredFeatures) {
            if (port.hasFeature(feature)) {
                return true;
            }
        }
        return false;
    }
}
