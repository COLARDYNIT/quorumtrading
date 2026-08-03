package com.quorumtrading.application.port.out;

import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.SimulationId;

import java.util.Optional;

/**
 * Port for broker operations (trade execution, order status, portfolio queries).
 *
 * This abstraction allows for:
 * - Mock broker adapter for testing and backtesting (with time-travel support)
 * - Real broker adapters for production (Alpaca, Interactive Brokers, etc.)
 *
 * Implementations must support:
 * - Executing trades at historical timestamps (for backtesting)
 * - Maintaining portfolio state per simulation
 * - Validating trades (balance checks, position limits, etc.)
 */
public interface BrokerPort {

    /**
     * Execute a trade (buy or sell).
     *
     * For backtesting, the executionTimestamp in the command determines
     * the historical time at which the trade should be executed.
     *
     * @param command Trade execution parameters
     * @return Result of the trade execution (status, fill details, etc.)
     */
    TradeResult executeTrade(ExecuteTradeCommand command);

    /**
     * Get the status of a previously submitted order.
     *
     * @param orderId The order identifier
     * @return Order status if found
     */
    Optional<TradeResult> getOrderStatus(OrderId orderId);

    /**
     * Get the current portfolio state for a simulation.
     *
     * @param simulationId The simulation identifier
     * @return Current portfolio with positions and cash balance
     */
    Portfolio getCurrentPortfolio(SimulationId simulationId);

    /**
     * Get the available cash balance for a simulation.
     *
     * @param simulationId The simulation identifier
     * @return Available cash (not including margin/buying power)
     */
    Money getAvailableBalance(SimulationId simulationId);

    /**
     * Reset/initialize portfolio state for a simulation.
     * Used when starting a new simulation with initial cash.
     *
     * @param simulationId The simulation identifier
     * @param initialCash Initial cash balance
     */
    void initializePortfolio(SimulationId simulationId, Money initialCash);

    /**
     * Initialize portfolio state for a simulation with existing positions.
     * Used when restoring portfolio state from a snapshot (e.g., after server restart).
     * This ensures positions are not lost when the in-memory broker state is cleared.
     *
     * @param simulationId The simulation identifier
     * @param cashAvailable Current cash balance
     * @param positions Map of existing positions to restore
     */
    void initializePortfolio(SimulationId simulationId, Money cashAvailable, java.util.Map<com.quorumtrading.model.valueobject.Symbol, com.quorumtrading.model.entity.Position> positions);

    /**
     * Clear portfolio state for a simulation.
     * Used for cleanup after simulation completion.
     *
     * @param simulationId The simulation identifier
     */
    void clearPortfolio(SimulationId simulationId);

    /**
     * Get total portfolio value as calculated by the broker.
     *
     * This ensures portfolio snapshots match what the broker actually reports,
     * solving the price source mismatch problem where:
     * - Trades execute at broker's prices (e.g., Alpaca's market data)
     * - But portfolio valuation uses our price sources (Coinbase, Finnhub)
     *
     * Implementation guidelines:
     * - Real brokers (Alpaca, IBKR): Return broker's reported equity/portfolio value
     * - Mock brokers: Calculate value using market data from MarketEventRepository
     *
     * For backtesting scenarios, the timestamp parameter allows historical valuation.
     * For live trading, use Instant.now() or the latest available data.
     *
     * DATA INTEGRITY:
     * - Returns NULL if market prices are unavailable for one or more positions
     * - NO fallback prices (average, purchase price, etc.) - data quality over completeness
     * - Callers MUST handle null return by skipping snapshot creation
     *
     * @param simulationId The simulation identifier
     * @param timestamp The evaluation timestamp (for historical data in backtests)
     * @return Total portfolio value (cash + positions valued at broker's prices), or NULL if prices unavailable
     */
    Money getPortfolioValue(SimulationId simulationId, java.time.Instant timestamp);

    /**
     * Validate if a symbol is tradable through this broker.
     *
     * This method checks if a given symbol can be traded on the broker platform,
     * helping prevent strategies from using invalid or non-existent tickers.
     *
     * Implementation guidelines:
     * - Real brokers (Alpaca): Query the broker's API to check if the asset exists and is tradable
     * - Mock broker: Can accept any ticker for testing purposes, or use a configurable list
     *
     * @param symbol The symbol to validate (ticker and investment type)
     * @return true if the symbol is tradable, false if it doesn't exist or is not tradable
     */
    boolean isSymbolTradable(com.quorumtrading.model.valueobject.Symbol symbol);

    /**
     * Bulk validate multiple symbols for tradability.
     *
     * This method efficiently validates multiple symbols in a single operation,
     * useful when validating all tickers in a strategy before saving.
     *
     * @param symbols Set of symbols to validate
     * @return Set of tradable symbols (subset of input that are actually tradable)
     */
    java.util.Set<com.quorumtrading.model.valueobject.Symbol> validateSymbols(java.util.Set<com.quorumtrading.model.valueobject.Symbol> symbols);

    /**
     * Get the broker account balance independent of any simulation.
     *
     * This method is used for LIVE simulation creation to fetch the actual
     * broker account balance before the simulation exists. Unlike
     * {@link #getAvailableBalance(SimulationId)}, this doesn't require
     * a simulation context.
     *
     * Implementation guidelines:
     * - Real brokers (Alpaca): Return actual account cash balance
     * - Mock brokers: Return Optional.empty() (no pre-existing account state)
     *
     * @return Account cash balance, or Optional.empty() if broker doesn't support
     *         pre-simulation balance queries (e.g., mock broker)
     */
    java.util.Optional<Money> getAccountBalance();

    /**
     * Cancel all open orders for a simulation.
     * Called when a simulation is paused to prevent fills after pause.
     *
     * Default no-op implementation for brokers where orders are instant (e.g., mock broker).
     *
     * @param simulationId the simulation whose orders to cancel
     */
    default void cancelAllOpenOrders(SimulationId simulationId) {
        // No-op by default — mock broker orders are instant
    }

    /**
     * Get detailed asset information from the broker.
     *
     * Used for ticker validation with rich error messages. Returns information
     * about whether the asset exists, its exchange, and tradability status.
     *
     * Implementation guidelines:
     * - Real brokers (Alpaca): Query the broker's asset API, return detailed info
     * - Mock broker: Return a mock AssetInfo with tradable=true for any valid Symbol
     *
     * @param ticker the ticker symbol to look up
     * @return asset information, or empty if the broker doesn't support asset lookups
     */
    default java.util.Optional<com.quorumtrading.model.valueobject.AssetInfo> getAssetInfo(String ticker) {
        return java.util.Optional.empty();
    }

    /**
     * Account-aware tradability check.
     *
     * <p>Validates a symbol against a specific linked broker account, so EU-instrument
     * routing can reach the venue/credentials of the account that will actually trade it
     * (e.g. a Saxo account's reference-data Uic lookup) rather than the global default.</p>
     *
     * <p>Defaults to the account-agnostic {@link #isSymbolTradable(com.quorumtrading.model.valueobject.Symbol)}
     * so existing adapters keep today's behavior unchanged. Account-aware brokers override this.</p>
     *
     * @param symbol    the symbol to validate (ticker and investment type)
     * @param accountId the linked broker account to validate against
     * @return true if the symbol is tradable on that account, false otherwise
     */
    default boolean isSymbolTradable(
            com.quorumtrading.model.valueobject.Symbol symbol,
            com.quorumtrading.model.valueobject.BrokerAccountId accountId) {
        return isSymbolTradable(symbol);
    }

    /**
     * Account-aware asset-info lookup.
     *
     * <p>Returns detailed asset information resolved against a specific linked broker
     * account, enabling exchange/currency/Uic enrichment from the venue the account
     * trades on (e.g. Saxo reference data) rather than the global default broker.</p>
     *
     * <p>Defaults to the account-agnostic {@link #getAssetInfo(String)} so existing
     * adapters keep today's behavior unchanged. Account-aware brokers override this.</p>
     *
     * @param ticker    the ticker symbol to look up
     * @param accountId the linked broker account to resolve against
     * @return asset information, or empty if the broker doesn't support asset lookups
     */
    default java.util.Optional<com.quorumtrading.model.valueobject.AssetInfo> getAssetInfo(
            String ticker,
            com.quorumtrading.model.valueobject.BrokerAccountId accountId) {
        return getAssetInfo(ticker);
    }

    /**
     * Whether this broker validates symbols against a real instrument universe.
     *
     * <p>Simulation brokers accept every well-formed ticker by design, so a
     * {@code tradable} answer from one carries no information about whether the
     * instrument exists or could be bought. Callers that report tradability to a
     * user need to know the difference: "the broker confirmed this is tradable"
     * is a false assurance when the broker confirms everything, and the user
     * discovers it only when a real account rejects the order.</p>
     *
     * <p>Defaults to {@code true} — real brokers answer from their own reference
     * data. The mock adapter overrides this.</p>
     *
     * @return {@code true} if a tradability answer from this broker is meaningful
     */
    default boolean providesInstrumentUniverse() {
        return true;
    }

    /**
     * Describe the broker's trading capabilities (e.g. fractional-share support).
     *
     * <p>Defaults to fully-fractional capabilities so existing adapters (Alpaca, the
     * mock broker) keep today's behavior unchanged. Brokers without fractional shares
     * on equities/ETFs (e.g. Saxo EU) override this to return
     * {@link com.quorumtrading.model.valueobject.BrokerCapabilities#wholeShareEquities()}.</p>
     *
     * @return the broker's capabilities; fractional on every asset class by default
     */
    default com.quorumtrading.model.valueobject.BrokerCapabilities capabilities() {
        return com.quorumtrading.model.valueobject.BrokerCapabilities.fractional();
    }
}
