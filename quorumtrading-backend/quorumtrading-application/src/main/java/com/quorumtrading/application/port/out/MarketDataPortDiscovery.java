package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.MarketDataFeature;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for discovering available MarketDataPort implementations.
 *
 * This port abstracts the adapter discovery mechanism, removing direct CDI dependencies
 * from the application layer while maintaining hexagonal architecture principles.
 *
 * Implementation is provided by the bootstrap layer using CDI Instance&lt;MarketDataPort&gt;.
 */
public interface MarketDataPortDiscovery {

    /**
     * Find a market data port by provider name.
     *
     * @param providerName The name of the provider (e.g., "finnhub", "alpaca")
     * @return Optional containing the port if found, empty otherwise
     */
    Optional<MarketDataPort> findByProviderName(String providerName);

    /**
     * Get all available market data ports.
     *
     * @return List of all discovered market data port implementations
     */
    List<MarketDataPort> findAll();

    /**
     * Check if a specific provider is available.
     *
     * @param providerName The name of the provider to check
     * @return true if the provider is available, false otherwise
     */
    default boolean isProviderAvailable(String providerName) {
        return findByProviderName(providerName).isPresent();
    }

    /**
     * Find a market data port that supports all specified features.
     *
     * <p>Returns the first provider that supports ALL required features.
     * This enables capability-based provider selection instead of hard-coded provider names.</p>
     *
     * <p>Example usage:</p>
     * <pre>
     * // Find a provider for crypto backfill with candle data
     * Optional&lt;MarketDataPort&gt; provider = discovery.findByFeatures(
     *     MarketDataFeature.BACKFILL,
     *     MarketDataFeature.CRYPTO,
     *     MarketDataFeature.CANDLES
     * );
     * </pre>
     *
     * @param requiredFeatures Features that the provider must support
     * @return Optional containing a provider that supports all features, empty if none found
     */
    Optional<MarketDataPort> findByFeatures(MarketDataFeature... requiredFeatures);

    /**
     * Find all market data ports that support all specified features.
     *
     * <p>Returns all providers that support ALL required features, useful when
     * you want to try multiple providers or implement fallback logic.</p>
     *
     * @param requiredFeatures Features that providers must support
     * @return List of providers supporting all features, empty list if none found
     */
    List<MarketDataPort> findAllByFeatures(MarketDataFeature... requiredFeatures);
}
