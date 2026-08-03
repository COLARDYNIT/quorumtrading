package com.quorumtrading.application.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.math.BigDecimal;

/**
 * Configuration for automatic backtesting triggered after research execution.
 *
 * When a research execution completes and auto-backtesting is enabled,
 * simulations will be created for the generated strategy using these configuration values.
 */
@ConfigMapping(prefix = "quorumtrading.auto-backtest")
public interface AutoBacktestConfig {

    /**
     * Number of weeks to backtest (from current date backwards).
     * For example, if set to 1, backtests will run from (now - 1 week) to now.
     *
     * @return the backtest period in weeks
     */
    int periodWeeks();

    /**
     * Initial cash amount to use for auto-generated backtests.
     * This will be the starting portfolio balance for each simulation.
     *
     * @return the initial cash amount in USD
     */
    BigDecimal initialCash();

    /**
     * Whether to automatically check data availability before creating simulations.
     * If enabled, the service will verify that sufficient market data exists before
     * starting a backtest.
     *
     * @return true if data availability checking is enabled, false otherwise
     */
    @WithDefault("true")
    boolean checkDataAvailability();

    /**
     * Whether to automatically request backfill when data is insufficient.
     * If enabled and data coverage is below the threshold, a backfill request
     * will be automatically created.
     *
     * @return true if automatic backfill is enabled, false otherwise
     */
    @WithDefault("true")
    boolean autoBackfill();

    /**
     * Whether to wait for backfill completion before starting the simulation.
     * If enabled and backfill is requested, the service will wait (up to maxWaitSeconds)
     * for the backfill to complete before proceeding with simulation creation.
     *
     * @return true if waiting for backfill is enabled, false otherwise
     */
    @WithDefault("false")
    boolean waitForBackfill();

    /**
     * Maximum time to wait for backfill completion (in seconds).
     * Only applies when waitForBackfill is enabled.
     * If backfill doesn't complete within this time, the simulation creation will fail.
     *
     * @return maximum wait time in seconds
     */
    @WithDefault("300")
    int maxWaitSeconds();

    /**
     * Minimum data coverage percentage required to proceed with simulation.
     * If actual coverage is below this threshold, backfill will be requested
     * (if autoBackfill is enabled).
     *
     * @return coverage threshold as percentage (0-100)
     */
    @WithDefault("80")
    BigDecimal coverageThreshold();
}
