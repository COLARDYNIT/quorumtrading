package com.quorumtrading.application.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.math.BigDecimal;

/**
 * Configuration for trading limits to prevent meaningless micro-trades.
 *
 * Configures minimum trade amounts (in USD) for crypto and stock trades.
 * Trades below these thresholds should be rejected by the orchestrator and
 * ExecuteConsensusDecisionCommand.
 *
 * Configuration properties:
 * - quorumtrading.trading.min-trade-amount.crypto (default: 100.0 USD)
 * - quorumtrading.trading.min-trade-amount.stock (default: 100.0 USD)
 *
 * Example configuration:
 * <pre>
 * quorumtrading.trading.min-trade-amount.crypto=100.0
 * quorumtrading.trading.min-trade-amount.stock=100.0
 * </pre>
 */
@ConfigMapping(prefix = "quorumtrading.trading.min-trade-amount")
public interface TradingLimitsConfig {

    /**
     * Minimum trade amount for crypto assets (in USD).
     * Trades below this threshold will be rejected.
     *
     * @return minimum crypto trade amount (default: 100.0)
     */
    @WithDefault("100.0")
    BigDecimal crypto();

    /**
     * Minimum trade amount for stock assets (in USD).
     * Trades below this threshold will be rejected.
     *
     * @return minimum stock trade amount (default: 100.0)
     */
    @WithDefault("100.0")
    BigDecimal stock();
}
