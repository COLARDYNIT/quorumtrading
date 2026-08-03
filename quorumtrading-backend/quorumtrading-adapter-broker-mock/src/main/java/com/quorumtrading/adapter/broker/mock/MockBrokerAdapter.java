package com.quorumtrading.adapter.broker.mock;

import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.ConfigurableBrokerPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.MockBrokerStatePort;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.AssetInfo;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCapabilities;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.ShareSizing;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock broker adapter for testing and backtesting with realistic transaction costs.
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Maintains portfolio state in-memory per broker account</li>
 *   <li>Supports historical trade execution (time-travel for backtesting)</li>
 *   <li>Validates trades (balance checks, position checks)</li>
 *   <li>Uses real market data from MarketEventRepository for pricing</li>
 *   <li>Thread-safe for concurrent simulations</li>
 *   <li>Realistic slippage modeling (base + market impact + random noise)</li>
 *   <li>Per-account configurable commission rates via MockBrokerConfig</li>
 * </ul>
 *
 * <p><b>Per-Account Configuration:</b></p>
 * <p>Cost parameters are now configured per BrokerAccount via MockBrokerConfig.
 * Call {@link #configureAccount(BrokerAccountId, MockBrokerConfig)} before using the broker
 * for a specific account. Simulations can map to different broker accounts with different
 * cost configurations.</p>
 *
 * <p><b>State Management:</b></p>
 * <ul>
 *   <li>Portfolio state is keyed by BrokerAccountId (supports multiple simulations per account)</li>
 *   <li>Legacy SimulationId-based methods still work by mapping to default account config</li>
 *   <li>Use {@link #initializePortfolioForAccount} for account-aware initialization</li>
 * </ul>
 *
 * <p><b>Fallback Configuration Properties (used when no account config provided):</b></p>
 * <ul>
 *   <li>quorumtrading.broker.mock.commission.rate: Commission percentage (default: 0.001 = 0.1%)</li>
 *   <li>quorumtrading.broker.mock.slippage.base: Base slippage percentage (default: 0.0005 = 0.05%)</li>
 *   <li>quorumtrading.broker.mock.slippage.impact-per-unit: Market impact per unit (default: 0.00001)</li>
 *   <li>quorumtrading.broker.mock.costs.enabled: Enable/disable costs (default: true)</li>
 * </ul>
 *
 * <p><b>CDI Configuration:</b></p>
 * <ul>
 *   <li>Qualified with @Named("mockBroker") for BrokerRouter pattern</li>
 *   <li>BrokerRouter is the default BrokerPort implementation</li>
 * </ul>
 *
 * @see MockBrokerConfig
 * @see BrokerAccountId
 */
@ApplicationScoped
@Named("mockBroker")
public class MockBrokerAdapter implements ConfigurableBrokerPort {

    private static final Logger LOG = Logger.getLogger(MockBrokerAdapter.class);

    // Per-account portfolio state (keyed by BrokerAccountId)
    private final Map<BrokerAccountId, SimulatedPortfolioState> accountPortfolios = new ConcurrentHashMap<>();

    // Per-account configuration (keyed by BrokerAccountId)
    private final Map<BrokerAccountId, MockBrokerConfig> accountConfigs = new ConcurrentHashMap<>();

    // Simulation to account mapping (for BrokerPort interface compatibility)
    private final Map<SimulationId, BrokerAccountId> simulationToAccount = new ConcurrentHashMap<>();

    // Legacy: SimulationId-keyed portfolios for backward compatibility (deprecated)
    private final Map<SimulationId, SimulatedPortfolioState> portfolios = new ConcurrentHashMap<>();

    /**
     * Simulations whose portfolio was seeded from the ACCOUNT's starting capital by
     * {@link #linkSimulationToAccount}, and which have not yet been given their own figure.
     *
     * <p>Linking seeds a placeholder so a simulation always has isolated state, but a mock account
     * describes the account's starting capital — not this simulation's share of it. A $250
     * plan-funded contribution against a $50,000 mock account was therefore handed the whole
     * $50,000: initializePortfolio() then found state already present and skipped, so the run
     * reported a portfolio value of $50,000 on $250 of funding, a return of +19,900%. The first
     * explicit initializePortfolio() call replaces a provisional seed instead of deferring to it.</p>
     */
    private final Set<SimulationId> provisionallySeeded = ConcurrentHashMap.newKeySet();
    private final Map<OrderId, TradeResult> orderHistory = new ConcurrentHashMap<>();

    @Inject
    MarketEventRepository marketEventRepository;

    @Inject
    MockBrokerStatePort mockBrokerStatePort;

    // Fallback transaction cost configuration (used when no per-account config)
    @ConfigProperty(name = "quorumtrading.broker.mock.commission.rate", defaultValue = "0.0")
    double commissionRate; // 0% for stocks (Alpaca commission-free)

    @ConfigProperty(name = "quorumtrading.broker.mock.commission.crypto-rate", defaultValue = "0.0025")
    double cryptoCommissionRate; // 0.25% for crypto (Alpaca taker tier 1)

    @ConfigProperty(name = "quorumtrading.broker.mock.slippage.base", defaultValue = "0.0005")
    double baseSlippageBps; // 0.05% base slippage

    @ConfigProperty(name = "quorumtrading.broker.mock.slippage.impact-per-unit", defaultValue = "0.00001")
    double impactPerUnit; // Price impact coefficient, applied per reference-priced unit of notional

    /**
     * Price level at which {@link #impactPerUnit} is calibrated, in USD.
     *
     * <p>Market impact is charged on the order's notional value, and this converts the
     * historical per-unit coefficient into a per-notional one without changing what it
     * means for an ordinary equity. At $100 a share the two formulations agree exactly,
     * so existing stock backtests produce identical fills; below it, the old formula
     * charged impact for the accident of a low unit price rather than for order size.</p>
     */
    private static final double IMPACT_REFERENCE_PRICE = 100.0;

    @ConfigProperty(name = "quorumtrading.broker.mock.slippage.volatility-multiplier", defaultValue = "1.5")
    double volatilityMultiplier; // Multiplier for volatile market conditions

    @ConfigProperty(name = "quorumtrading.broker.mock.costs.enabled", defaultValue = "true")
    boolean costsEnabled; // Allow disabling costs for testing

    // ========== Per-Account Configuration API ==========

    /**
     * Configures the mock broker for a specific account with its cost parameters.
     * This method must be called before using the broker for simulations tied to this account.
     *
     * @param accountId the broker account ID
     * @param config the mock broker configuration with cost parameters
     */
    public void configureAccount(BrokerAccountId accountId, MockBrokerConfig config) {
        LOG.infof("Configuring mock broker for account %s with config: commission=%.4f%%, slippageBase=%.4f%%",
            accountId.value().toString().substring(0, 8),
            config.commissionRate().multiply(BigDecimal.valueOf(100)),
            config.slippageBase().multiply(BigDecimal.valueOf(100)));
        accountConfigs.put(accountId, config);
    }

    /**
     * Links a simulation to a broker account for routing purposes.
     * This allows the BrokerPort interface methods (which use SimulationId) to
     * route to the correct account configuration and portfolio state.
     *
     * @param simulationId the simulation ID
     * @param accountId the broker account ID to link to
     */
    public void linkSimulationToAccount(SimulationId simulationId, BrokerAccountId accountId) {
        LOG.debugf("Linking simulation %s to broker account %s",
            simulationId.value().toString().substring(0, 8),
            accountId.value().toString().substring(0, 8));
        simulationToAccount.put(simulationId, accountId);

        // A mock account describes STARTING CAPITAL, not a shared pot: give this simulation its
        // own state seeded from the account, so two simulations on one account cannot see each
        // other's cash or positions. Only real brokers hold genuinely shared account state.
        portfolios.computeIfAbsent(simulationId, id -> {
            Money seed = seedCashForAccount(accountId);
            if (seed == null) {
                return null;
            }
            LOG.infof("Seeding isolated portfolio for simulation %s from account %s with %s (provisional)",
                id.value().toString().substring(0, 8),
                accountId.value().toString().substring(0, 8), seed);
            provisionallySeeded.add(id);
            return new SimulatedPortfolioState(seed);
        });
    }

    /** Starting capital for a mock account: its configured capital, else any seeded state. */
    private Money seedCashForAccount(BrokerAccountId accountId) {
        MockBrokerConfig config = accountConfigs.get(accountId);
        if (config != null && config.initialCapital() != null) {
            return config.initialCapital();
        }
        SimulatedPortfolioState accountState = accountPortfolios.get(accountId);
        return accountState != null ? accountState.getCashAvailable() : null;
    }

    /**
     * Gets the broker account ID for a simulation, if linked.
     *
     * @param simulationId the simulation ID
     * @return the linked broker account ID, or empty if not linked
     */
    public Optional<BrokerAccountId> getAccountForSimulation(SimulationId simulationId) {
        return Optional.ofNullable(simulationToAccount.get(simulationId));
    }

    /**
     * Initializes portfolio state for a broker account.
     * This is the account-aware version of initializePortfolio.
     *
     * @param accountId the broker account ID
     * @param initialCash the initial cash balance
     */
    public void initializePortfolioForAccount(BrokerAccountId accountId, Money initialCash) {
        if (accountPortfolios.get(accountId) == null) {
            LOG.infof("Initializing portfolio for broker account %s with %s",
                accountId.value().toString().substring(0, 8),
                initialCash);
            accountPortfolios.put(accountId, new SimulatedPortfolioState(initialCash));
        } else {
            LOG.debugf("Portfolio already initialized for broker account %s, skipping", accountId.value());
        }
    }

    /**
     * Initializes portfolio state for a broker account with existing positions.
     *
     * @param accountId the broker account ID
     * @param cashAvailable current cash balance
     * @param positions existing positions to restore
     */
    public void initializePortfolioForAccount(BrokerAccountId accountId, Money cashAvailable, Map<Symbol, Position> positions) {
        LOG.infof("Initializing portfolio for broker account %s with %s and %d positions",
            accountId.value().toString().substring(0, 8),
            cashAvailable,
            positions.size());

        SimulatedPortfolioState state = new SimulatedPortfolioState(cashAvailable);
        for (Map.Entry<Symbol, Position> entry : positions.entrySet()) {
            Position position = entry.getValue();
            state.addPosition(position.symbol(), position.quantity(), position.averagePrice());
        }
        accountPortfolios.put(accountId, state);
    }

    /**
     * Gets the portfolio state for a broker account.
     *
     * @param accountId the broker account ID
     * @return the portfolio state, or null if not initialized
     */
    public SimulatedPortfolioState getAccountPortfolio(BrokerAccountId accountId) {
        return accountPortfolios.get(accountId);
    }

    /**
     * Clears portfolio state for a broker account.
     *
     * @param accountId the broker account ID
     */
    public void clearAccountPortfolio(BrokerAccountId accountId) {
        LOG.infof("Clearing portfolio for broker account %s",
            accountId.value().toString().substring(0, 8));
        accountPortfolios.remove(accountId);
    }

    /**
     * Gets the cost configuration for a broker account.
     * Returns the account-specific config if configured, otherwise null.
     *
     * @param accountId the broker account ID
     * @return the account configuration, or null if not configured
     */
    public MockBrokerConfig getAccountConfig(BrokerAccountId accountId) {
        return accountConfigs.get(accountId);
    }

    // ========== ConfigurableBrokerPort Interface Implementation ==========

    @Override
    public void configureForAccount(BrokerAccount account) {
        if (account.getMockConfig() != null) {
            configureAccount(account.getId(), account.getMockConfig());
        } else {
            LOG.warnf("BrokerAccount %s has no MockBrokerConfig - using default configuration",
                account.getId().value().toString().substring(0, 8));
        }
    }

    @Override
    public boolean isAccountConfigured(BrokerAccountId accountId) {
        return accountConfigs.containsKey(accountId);
    }

    @Override
    public void removeAccountConfiguration(BrokerAccountId accountId) {
        LOG.infof("Removing configuration for mock broker account %s",
            accountId.value().toString().substring(0, 8));
        accountConfigs.remove(accountId);
        accountPortfolios.remove(accountId);
    }

    // ========== Internal Helpers ==========

    /**
     * Gets the effective portfolio state for a simulation.
     * Checks if simulation is linked to an account, otherwise falls back to legacy SimulationId mapping.
     */
    private SimulatedPortfolioState getEffectivePortfolioState(SimulationId simulationId) {
        // Always per-simulation. Simulated capital is not a shared pot: two simulations linked to
        // the same mock account used to resolve to ONE state, so each saw the other's cash and
        // positions — a $500 plan-funded simulation reported $10,000 and +900% because an
        // unrelated $10,000 backtest on the same account had written the shared state. Only a
        // real broker has genuinely shared account state, because there the money is shared.
        return portfolios.get(simulationId);
    }

    /**
     * Gets the effective cost parameters for a simulation.
     * Uses account-specific config if available, otherwise global config properties.
     */
    private CostParameters getEffectiveCostParameters(SimulationId simulationId) {
        BrokerAccountId accountId = simulationToAccount.get(simulationId);
        if (accountId != null) {
            MockBrokerConfig config = accountConfigs.get(accountId);
            if (config != null) {
                return new CostParameters(
                    config.commissionRate().doubleValue(),
                    config.cryptoCommissionRate().doubleValue(),
                    config.slippageBase().doubleValue(),
                    config.slippageImpactPerUnit().doubleValue(),
                    config.slippageVolatilityMultiplier().doubleValue(),
                    true, // costs always enabled for account-configured brokers
                    config.capabilities() // Alpaca- vs Saxo-flavoured fractional behaviour
                );
            }
        }
        // Fallback to global config properties (no account config = Alpaca-flavoured fractional)
        return new CostParameters(
            commissionRate,
            cryptoCommissionRate,
            baseSlippageBps,
            impactPerUnit,
            volatilityMultiplier,
            costsEnabled,
            BrokerCapabilities.fractional()
        );
    }

    /**
     * Internal record holding cost parameters.
     */
    private record CostParameters(
        double commissionRate,
        double cryptoCommissionRate,
        double slippageBase,
        double impactPerUnit,
        double volatilityMultiplier,
        boolean enabled,
        BrokerCapabilities capabilities
    ) {
        /**
         * Returns the commission rate for the given investment type.
         */
        double commissionRateFor(InvestmentType type) {
            return type == InvestmentType.CRYPTO ? cryptoCommissionRate : commissionRate;
        }
    }

    @Override
    public TradeResult executeTrade(ExecuteTradeCommand command) {
        LOG.infof("Executing %s trade for %s: %s x %s @ %s",
            command.action(),
            command.simulationId().value().toString().substring(0, 8),
            command.symbol().ticker(),
            command.quantity(),
            command.executionTimestamp()
        );

        // Get or throw if portfolio not initialized (checks account-linked state first)
        SimulatedPortfolioState state = getEffectivePortfolioState(command.simulationId());
        if (state == null) {
            OrderId orderId = OrderId.generate();
            TradeResult result = TradeResult.rejected(
                orderId,
                "Portfolio not initialized for simulation: " + command.simulationId(),
                command.executionTimestamp()
            );
            orderHistory.put(orderId, result);
            return result;
        }

        // Get cost parameters (account-specific or global fallback)
        CostParameters costs = getEffectiveCostParameters(command.simulationId());

        // Get market price at execution timestamp
        Money marketPrice = getHistoricalPrice(command.symbol(), command.executionTimestamp());
        if (marketPrice == null) {
            OrderId orderId = OrderId.generate();
            TradeResult result = TradeResult.rejected(
                orderId,
                String.format("No market data available for %s at %s",
                    command.symbol().ticker(), command.executionTimestamp()),
                command.executionTimestamp()
            );
            orderHistory.put(orderId, result);
            return result;
        }

        // Execute based on action (pass cost parameters)
        TradeResult result = command.action() == TradeAction.BUY
            ? executeBuy(command, state, marketPrice, costs)
            : executeSell(command, state, marketPrice, costs);

        // Store in order history (in-memory cache + DB write-through)
        orderHistory.put(result.orderId(), result);
        try {
            mockBrokerStatePort.saveOrder(command, result);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to persist order %s to database (in-memory cache still valid)",
                result.orderId().value().toString().substring(0, 8));
        }

        LOG.infof("Trade %s: %s - %s",
            result.orderId().toString().substring(0, 8),
            result.status(),
            result.isRejected() ? result.getRejectionReason().orElse("Unknown") : "Executed at " + marketPrice
        );

        return result;
    }

    /**
     * Applies the routed broker's lot granularity to a requested order quantity.
     *
     * <p>When the broker fills fractional quantities for {@code type} the requested
     * quantity is returned <b>verbatim</b> — same value, same scale — so the
     * fractional execution path stays byte-identical to the pre-FX3 behaviour. When
     * the broker only fills whole shares for {@code type} (Saxo-flavoured equities)
     * the quantity is floored to an integral share count via {@link ShareSizing#roundToLot},
     * with the cash residue carried by the caller.</p>
     */
    private static BigDecimal lotRound(BigDecimal quantity, InvestmentType type, BrokerCapabilities capabilities) {
        if (capabilities.supportsFractional(type)) {
            return quantity; // verbatim — preserve value and scale (legacy behaviour)
        }
        return ShareSizing.roundToLot(quantity, type, capabilities);
    }

    private TradeResult executeBuy(
            ExecuteTradeCommand command,
            SimulatedPortfolioState state,
            Money marketPrice,
            CostParameters costs) {

        // Model the routed broker's lot granularity: an Alpaca-flavoured mock keeps the
        // requested fractional quantity verbatim (byte-identical to legacy), while a
        // Saxo-flavoured mock (fractionalShares=false) floors equity BUYs to whole shares
        // and carries the unspent cash residue to the next tick. Crypto stays fractional.
        BigDecimal quantity = lotRound(command.quantity(), command.symbol().type(), costs.capabilities());

        // A non-fractional broker cannot fill an order that rounds below one whole share.
        if (quantity.signum() <= 0) {
            return TradeResult.rejected(
                OrderId.generate(),
                String.format(
                    "Order quantity %s for %s rounds below the broker's minimum lot (whole shares); nothing filled",
                    command.quantity().toPlainString(), command.symbol().ticker()),
                command.executionTimestamp()
            );
        }

        // Apply slippage to execution price (buy orders pay MORE than market price)
        Money slippage = calculateSlippage(marketPrice, quantity, true, costs);
        Money executionPrice = marketPrice.add(slippage);

        Money tradeCost = executionPrice.multiply(quantity);

        // Calculate commission (asset-type-aware: stocks vs crypto)
        double effectiveCommissionRate = costs.commissionRateFor(command.symbol().type());
        Money commission = costs.enabled()
            ? tradeCost.multiply(BigDecimal.valueOf(effectiveCommissionRate))
            : Money.usd(BigDecimal.ZERO);

        // Total cost = trade cost + commission
        Money totalCost = tradeCost.add(commission);

        // Validate sufficient funds
        if (state.getCashAvailable().compareTo(totalCost) < 0) {
            return TradeResult.rejected(
                OrderId.generate(),
                String.format("Insufficient funds. Need: %s (trade: %s + commission: %s), available: %s",
                    totalCost, tradeCost, commission, state.getCashAvailable()),
                command.executionTimestamp()
            );
        }

        // Log cost breakdown
        if (costs.enabled() && LOG.isDebugEnabled()) {
            LOG.debugf("BUY cost breakdown - Market: %s, Slippage: %s (%.3f%%), Execution: %s, Commission: %s (%.2f%% %s), Total: %s",
                marketPrice, slippage,
                slippage.amount().divide(marketPrice.amount(), 5, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)),
                executionPrice, commission, effectiveCommissionRate * 100, command.symbol().type(), totalCost);
        }

        // Execute buy: deduct total cost (includes commission). For a whole-share broker
        // the cash residue (requested cash − rounded-share cost) stays in cashAvailable and
        // is carried to the next tick.
        state.deductCash(totalCost);
        // Position is recorded at execution price (market price + slippage)
        state.addPosition(command.symbol(), quantity, executionPrice);

        return TradeResult.executed(
            OrderId.generate(),
            executionPrice,  // Return actual execution price (with slippage)
            quantity,        // Filled quantity reflects the broker's lot rounding
            command.executionTimestamp()
        );
    }

    private TradeResult executeSell(
            ExecuteTradeCommand command,
            SimulatedPortfolioState state,
            Money marketPrice,
            CostParameters costs) {

        // Validate position exists and has sufficient quantity
        if (!state.hasPosition(command.symbol())) {
            return TradeResult.rejected(
                OrderId.generate(),
                String.format("No position exists for symbol: %s", command.symbol().ticker()),
                command.executionTimestamp()
            );
        }

        // Round the sell quantity to the broker's lot granularity so a whole-share broker
        // cannot leave a fractional dust position behind. An Alpaca-flavoured mock passes
        // the requested quantity through verbatim (byte-identical to legacy); crypto stays
        // fractional regardless of flavour.
        BigDecimal quantity = lotRound(command.quantity(), command.symbol().type(), costs.capabilities());
        if (quantity.signum() <= 0) {
            return TradeResult.rejected(
                OrderId.generate(),
                String.format(
                    "Sell quantity %s for %s rounds below the broker's minimum lot (whole shares); nothing sold",
                    command.quantity().toPlainString(), command.symbol().ticker()),
                command.executionTimestamp()
            );
        }

        try {
            // Apply slippage to execution price (sell orders receive LESS than market price)
            Money slippage = calculateSlippage(marketPrice, quantity, false, costs);
            Money executionPrice = marketPrice.subtract(slippage);

            Money grossProceeds = executionPrice.multiply(quantity);

            // Calculate commission (asset-type-aware: stocks vs crypto)
            double effectiveCommissionRate = costs.commissionRateFor(command.symbol().type());
            Money commission = costs.enabled()
                ? grossProceeds.multiply(BigDecimal.valueOf(effectiveCommissionRate))
                : Money.usd(BigDecimal.ZERO);

            // Net proceeds = gross proceeds - commission
            Money netProceeds = grossProceeds.subtract(commission);

            // Log cost breakdown
            if (costs.enabled() && LOG.isDebugEnabled()) {
                LOG.debugf("SELL cost breakdown - Market: %s, Slippage: %s (%.3f%%), Execution: %s, Commission: %s (%.2f%% %s), Net Proceeds: %s",
                    marketPrice, slippage,
                    slippage.amount().divide(marketPrice.amount(), 5, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)),
                    executionPrice, commission, effectiveCommissionRate * 100, command.symbol().type(), netProceeds);
            }

            // Execute sell
            state.reducePosition(command.symbol(), quantity);
            state.addCash(netProceeds); // Add net proceeds after commission

            return TradeResult.executed(
                OrderId.generate(),
                executionPrice,  // Return actual execution price (with slippage)
                quantity,        // Filled quantity reflects the broker's lot rounding
                command.executionTimestamp()
            );

        } catch (IllegalArgumentException e) {
            return TradeResult.rejected(
                OrderId.generate(),
                e.getMessage(),
                command.executionTimestamp()
            );
        }
    }

    /**
     * Get historical price from market data repository.
     * Looks for price events within +/- 1 hour of the execution timestamp.
     */
    private Money getHistoricalPrice(Symbol symbol, Instant timestamp) {
        Instant from = timestamp.minus(1, ChronoUnit.DAYS);
        Instant to = timestamp;

        // Create a set with the single ticker (works for both crypto and stock)
        Set<String> tickerSet = Set.of(symbol.ticker());

        List<MarketEvent> priceEvents = marketEventRepository.findByTickersAndTimeRange(
            tickerSet,  // Crypto tickers (will match if symbol is crypto)
            tickerSet,  // Stock tickers (will match if symbol is stock)
            from,
            to,
            EventType.PRICE,
            1 // Only need one price
        );

        if (priceEvents.isEmpty()) {
            LOG.warnf("No price data found for %s near %s", symbol.ticker(), timestamp);
            return null;
        }

        // Extract price from raw data
        MarketEvent event = priceEvents.get(0);
        Object priceObj = event.rawData().get("price");

        if (priceObj instanceof Number) {
            return Money.usd(BigDecimal.valueOf(((Number) priceObj).doubleValue()));
        } else if (priceObj instanceof String) {
            return Money.usd(new BigDecimal((String) priceObj));
        }

        LOG.warnf("Invalid price data format for %s", symbol.ticker());
        return null;
    }

    @Override
    public Optional<TradeResult> getOrderStatus(OrderId orderId) {
        // Check in-memory cache first
        TradeResult cached = orderHistory.get(orderId);
        if (cached != null) {
            return Optional.of(cached);
        }
        // Fall back to database for cluster-safe lookup
        try {
            Optional<TradeResult> fromDb = mockBrokerStatePort.findOrder(orderId);
            fromDb.ifPresent(result -> orderHistory.put(orderId, result));
            return fromDb;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to look up order %s from database", orderId.value().toString().substring(0, 8));
            return Optional.empty();
        }
    }

    @Override
    public Portfolio getCurrentPortfolio(SimulationId simulationId) {
        SimulatedPortfolioState state = getEffectivePortfolioState(simulationId);
        if (state == null) {
            throw new IllegalArgumentException(
                "Portfolio not initialized for simulation: " + simulationId
            );
        }

        return new Portfolio(
            simulationId,
            state.getPositions(),
            state.getCashAvailable()
        );
    }

    @Override
    public Money getAvailableBalance(SimulationId simulationId) {
        SimulatedPortfolioState state = getEffectivePortfolioState(simulationId);
        if (state == null) {
            throw new IllegalArgumentException(
                "Portfolio not initialized for simulation: " + simulationId
            );
        }

        return state.getCashAvailable();
    }

    @Override
    public void initializePortfolio(SimulationId simulationId, Money initialCash) {
        // Keyed by simulation, never by account — see getEffectivePortfolioState. Seeding the
        // account-wide state here is what let two simulations share one pot of simulated cash.
        boolean replacingProvisionalSeed = provisionallySeeded.remove(simulationId);
        if (portfolios.get(simulationId) == null || replacingProvisionalSeed) {
            LOG.infof("Initializing portfolio for simulation %s with %s%s",
                simulationId.value().toString().substring(0, 8),
                initialCash,
                replacingProvisionalSeed ? " (replacing the account-seeded placeholder)" : ""
            );

            portfolios.put(simulationId, new SimulatedPortfolioState(initialCash));
        } else {
            LOG.debugf("Portfolio already initialized for simulation %s, skipping", simulationId.value());
        }
    }

    @Override
    public void initializePortfolio(SimulationId simulationId, Money cashAvailable, Map<Symbol, Position> positions) {
        // Keyed by simulation, never by account — see getEffectivePortfolioState.
        // This overload always overwrites, so an account-seeded placeholder is superseded either
        // way; clear the marker so a later two-arg call is not treated as replacing one.
        provisionallySeeded.remove(simulationId);
        SimulatedPortfolioState existingState = portfolios.get(simulationId);

        if (existingState == null) {
            // Fresh initialization - create new portfolio state
            LOG.infof("Initializing portfolio for simulation %s with %s and %d positions",
                simulationId.value().toString().substring(0, 8),
                cashAvailable,
                positions.size()
            );

            // Create portfolio state with cash
            SimulatedPortfolioState state = new SimulatedPortfolioState(cashAvailable);

            // Restore all positions
            for (Map.Entry<Symbol, Position> entry : positions.entrySet()) {
                Position position = entry.getValue();
                state.addPosition(
                    position.symbol(),
                    position.quantity(),
                    position.averagePrice()
                );

                LOG.debugf("Restored position: %s x %s @ %s",
                    position.symbol().ticker(),
                    position.quantity(),
                    position.averagePrice()
                );
            }

            portfolios.put(simulationId, state);
        } else {
            // Portfolio already exists - restore positions from snapshot (e.g., after server restart)
            // CRITICAL FIX: Always overwrite existing state to prevent position loss
            LOG.warnf("Portfolio already initialized for simulation %s - RESTORING state from snapshot with %s and %d positions (existing had %d positions)",
                simulationId.value().toString().substring(0, 8),
                cashAvailable,
                positions.size(),
                existingState.getPositions().size());

            // Create fresh portfolio state with cash
            SimulatedPortfolioState state = new SimulatedPortfolioState(cashAvailable);

            // Restore all positions from snapshot
            for (Map.Entry<Symbol, Position> entry : positions.entrySet()) {
                Position position = entry.getValue();
                state.addPosition(
                    position.symbol(),
                    position.quantity(),
                    position.averagePrice()
                );

                LOG.infof("RESTORED position from snapshot: %s x %s @ %s",
                    position.symbol().ticker(),
                    position.quantity(),
                    position.averagePrice()
                );
            }

            // Overwrite existing portfolio state
            portfolios.put(simulationId, state);
            LOG.infof("Portfolio state OVERWRITTEN for simulation %s - now has %d positions",
                simulationId.value().toString().substring(0, 8),
                positions.size());
        }
    }

    @Override
    public void clearPortfolio(SimulationId simulationId) {
        LOG.infof("Clearing portfolio for simulation %s",
            simulationId.value().toString().substring(0, 8)
        );

        provisionallySeeded.remove(simulationId);

        // Clear simulation-to-account mapping
        BrokerAccountId accountId = simulationToAccount.remove(simulationId);

        // Note: We do NOT clear account portfolio state here because other simulations
        // might be using the same account. Account portfolios are cleared explicitly
        // via clearAccountPortfolio() when the account itself is no longer needed.

        // Legacy: Clear SimulationId-keyed portfolio
        portfolios.remove(simulationId);

        // Also clear order history for this simulation (in-memory + DB)
        orderHistory.entrySet().removeIf(entry ->
            entry.getValue().toString().contains(simulationId.value().toString())
        );
        try {
            mockBrokerStatePort.deleteOrdersBySimulation(simulationId);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to delete orders from database for simulation %s",
                simulationId.value().toString().substring(0, 8));
        }
    }

    /**
     * Calculate realistic slippage based on order size and market conditions.
     *
     * Slippage model:
     * - Base slippage: Fixed percentage (configurable via CostParameters)
     * - Market impact: Increases with order size
     * - Random noise: Simulates market microstructure variance (±20%)
     *
     * @param marketPrice Current market price
     * @param quantity Order quantity
     * @param isBuy True for buy orders (positive slippage), false for sell orders
     * @param costs Cost parameters to use for calculation
     * @return Slippage amount in Money
     */
    private Money calculateSlippage(Money marketPrice, BigDecimal quantity, boolean isBuy, CostParameters costs) {
        if (!costs.enabled()) {
            return Money.usd(BigDecimal.ZERO);
        }

        // Base slippage component (fixed percentage)
        double baseSlippage = costs.slippageBase();

        // Market impact component, linear in the ORDER'S VALUE — not its unit count.
        //
        // This was `quantity * impactPerUnit`, which makes impact a function of how many
        // pieces the instrument happens to be divided into rather than how much money is
        // being moved. At the default coefficient that reaches 1% at 1,000 units, so the
        // same $5,000 order costs 0.05% in a $100 share, 69% in a $0.07 coin and roughly
        // 1,087,000% in one priced at $0.0000046. Measured: DOGE filled at $0.1147 against
        // a $0.0721 market price in the same snapshot, and a three-day backtest reported
        // -28.99% on an asset that moved -0.98%; SHIB demanded $67m for a $5,000 order, so
        // every BUY was rejected and the run completed at a tidy 0.00% that reads exactly
        // like a strategy whose rules never triggered. The fabricated loss reaches
        // returnPercent, TWR and every benchmark ranking.
        //
        // Notional is the honest measure of order size: moving $100k of a stock and $100k
        // of a coin push a market comparably, whatever the unit price. Dividing by the
        // reference price keeps the existing coefficient calibrated exactly as before for
        // a $100 instrument, so equity backtests are unchanged.
        double notional = marketPrice.amount().doubleValue() * quantity.doubleValue();
        double marketImpact = (notional / IMPACT_REFERENCE_PRICE) * costs.impactPerUnit();

        // Total slippage percentage
        double totalSlippagePct = baseSlippage + marketImpact;

        // Add random noise (±20% variance) to simulate real market conditions
        // This prevents predictable execution prices
        double noiseMultiplier = ThreadLocalRandom.current().nextDouble(0.8, 1.2);
        totalSlippagePct *= noiseMultiplier;

        // Calculate slippage amount
        BigDecimal slippageAmount = marketPrice.amount()
            .multiply(BigDecimal.valueOf(totalSlippagePct))
            .setScale(8, RoundingMode.HALF_UP); // 8 decimal places for crypto precision

        Money slippage = Money.usd(slippageAmount);

        if (LOG.isTraceEnabled()) {
            LOG.tracef("Slippage calculation - Base: %.4f%%, Impact: %.4f%%, Total: %.4f%%, Amount: %s",
                baseSlippage * 100,
                marketImpact * 100,
                totalSlippagePct * 100,
                slippage);
        }

        return slippage;
    }

    @Override
    public Money getPortfolioValue(SimulationId simulationId, Instant timestamp) {
        SimulatedPortfolioState state = getEffectivePortfolioState(simulationId);
        if (state == null) {
            throw new IllegalArgumentException(
                "Portfolio not initialized for simulation: " + simulationId
            );
        }

        // Start with cash
        Money totalValue = state.getCashAvailable();

        // Add positions valued at CURRENT market prices (from MarketEventRepository)
        // NO FALLBACKS - if price is missing, return null to signal incomplete data
        for (Map.Entry<Symbol, Position> entry : state.getPositions().entrySet()) {
            Symbol symbol = entry.getKey();
            Position position = entry.getValue();

            // Fetch current market price at timestamp
            Money marketPrice = fetchMarketPrice(symbol, timestamp);
            if (marketPrice == null) {
                // NO FALLBACK TO AVERAGE PRICE - return null to indicate missing data
                LOG.warnf("Cannot calculate portfolio value: missing market price for %s at %s",
                    symbol.ticker(), timestamp);
                return null; // Signal that portfolio value cannot be calculated accurately
            }

            Money positionValue = marketPrice.multiply(position.quantity());
            totalValue = totalValue.add(positionValue);

            if (LOG.isDebugEnabled()) {
                LOG.debugf("Position %s: %s x %s (market) = %s",
                    symbol.ticker(),
                    position.quantity(),
                    marketPrice,
                    positionValue);
            }
        }

        LOG.debugf("MockBroker portfolio value: %s (cash: %s) at %s for simulation %s",
            totalValue,
            state.getCashAvailable(),
            timestamp,
            simulationId.value().toString().substring(0, 8)
        );

        return totalValue;
    }

    /**
     * Fetch market price for a symbol at a specific timestamp.
     * Looks back 24 hours to find recent price data from MarketEventRepository.
     * Returns the most recent price within the window, ensuring temporal causality.
     *
     * @param symbol The symbol to fetch price for
     * @param timestamp The query timestamp (upper bound - ensures no future prices)
     * @return Market price if found within 24h window, null otherwise
     */
    private Money fetchMarketPrice(Symbol symbol, Instant timestamp) {
        Instant oneDayAgo = timestamp.minus(1, ChronoUnit.DAYS);
        Set<String> tickers = Set.of(symbol.ticker());

        // Ask in the symbol's OWN universe. Passing the same set as both crypto and stock said
        // "either kind will do", and since this result becomes the fill price AND the valuation
        // price, an equity bar for a same-named ticker could be executed against as if it were
        // the crypto's price.
        boolean isCrypto = symbol.type() == InvestmentType.CRYPTO;
        Set<String> cryptoTickers = isCrypto ? tickers : Set.of();
        Set<String> stockTickers = isCrypto ? Set.of() : tickers;

        // Query returns events sorted by timestamp DESC, so first result is most recent
        List<MarketEvent> events = marketEventRepository.findByTickersAndTimeRange(
            cryptoTickers,
            stockTickers,
            oneDayAgo,
            timestamp,  // CRITICAL: Upper bound prevents future price leakage
            EventType.PRICE,
            1 // limit to 1 most recent event
        );

        if (events.isEmpty()) {
            LOG.debugf("No market price found for %s within 24h of timestamp %s", symbol.ticker(), timestamp);
            return null;
        }

        MarketEvent event = events.get(0);

        // DEFENSIVE: Verify temporal causality to prevent future price leakage
        if (event.timestamp().isAfter(timestamp)) {
            LOG.errorf("CRITICAL: Market event at %s is from FUTURE relative to query timestamp %s! " +
                      "Symbol: %s. This indicates a bug in findByTickersAndTimeRange or data corruption.",
                      event.timestamp(), timestamp, symbol.ticker());
            return null; // Reject future data
        }

        Object priceObj = event.rawData().get("price");

        if (priceObj == null) {
            LOG.warnf("Price data missing in market event for %s at %s", symbol.ticker(), event.timestamp());
            return null;
        }

        try {
            Money price = Money.usd(new BigDecimal(priceObj.toString()));
            LOG.debugf("Found market price for %s: %s at %s (query timestamp: %s)",
                       symbol.ticker(), price, event.timestamp(), timestamp);
            return price;
        } catch (NumberFormatException e) {
            LOG.errorf("Invalid price format for %s: %s", symbol.ticker(), priceObj);
            return null;
        }
    }

    @Override
    public boolean isSymbolTradable(Symbol symbol) {
        // Mock broker accepts any valid ticker for testing purposes
        // This allows testing with any ticker, including ones that might not exist
        // in real brokers

        String ticker = symbol.ticker();

        // Special handling for market placeholder
        if ("MARKET".equals(ticker)) {
            return false;
        }

        // Accept any ticker that matches the Symbol validation pattern
        // Symbol already validates the format in its constructor
        // (1-5 uppercase letters or MARKET)
        // Since we got a valid Symbol object, the ticker is already validated
        return true;
    }

    @Override
    public Set<Symbol> validateSymbols(Set<Symbol> symbols) {
        // Mock broker accepts all valid symbols for testing
        // Filter out only the MARKET placeholder
        return symbols.stream()
            .filter(this::isSymbolTradable)
            .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Optional<Money> getAccountBalance() {
        // Mock broker has no pre-existing account state
        // Each simulation must be initialized with explicit initial cash
        LOG.debug("Mock broker has no pre-existing account balance - simulations require explicit initial cash");
        return Optional.empty();
    }

    /**
     * This adapter accepts every well-formed ticker, so a "tradable" answer from
     * it says nothing about whether the instrument exists. Declaring that lets
     * callers avoid presenting a rubber stamp as broker confirmation.
     */
    @Override
    public boolean providesInstrumentUniverse() {
        return false;
    }

    @Override
    public Optional<AssetInfo> getAssetInfo(String ticker) {
        if ("MARKET".equals(ticker)) {
            return Optional.of(AssetInfo.notFound(ticker));
        }
        // Mock broker returns tradable for any valid ticker (by design, for testing).
        //
        // The name is deliberately null rather than a synthetic "Mock Asset: X". This
        // adapter has no idea which company a ticker belongs to, and identity
        // verification during ticker resolution compares the broker's name against the
        // company that was asked for. A made-up name is not a harmless placeholder
        // there: it shares no token with any real company, so every resolution would
        // read as a name conflict and be refused. Reporting null says what is true —
        // this broker does not know — and verification correctly abstains.
        return Optional.of(AssetInfo.tradable(ticker, null, "MOCK", "us_equity"));
    }
}
