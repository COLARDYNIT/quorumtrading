package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.ConfigurableBrokerPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.AssetInfo;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCapabilities;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes broker operations to the appropriate broker adapter based on simulation mode
 * and broker account configuration.
 *
 * <p><b>Routing Strategy (Two Modes):</b></p>
 *
 * <p><b>1. Per-Account Routing (Preferred):</b></p>
 * <p>When a simulation has a linked BrokerAccountId, routes to the broker specified by that account:</p>
 * <ul>
 *   <li>MOCK → MockBrokerAdapter (configured with account's MockBrokerConfig)</li>
 *   <li>ALPACA_PAPER → AlpacaBrokerAdapter (configured with account's credentials)</li>
 *   <li>ALPACA_LIVE → AlpacaBrokerAdapter (configured with account's credentials)</li>
 * </ul>
 *
 * <p><b>2. Legacy Global Routing (Fallback):</b></p>
 * <p>When no BrokerAccountId is linked, uses the original mode-based routing:</p>
 * <ul>
 *   <li>BACKTEST Mode (SIMULATING status) → Always MockBroker</li>
 *   <li>LIVE Mode (LIVE status) → Configured broker by asset type</li>
 * </ul>
 *
 * <p><b>Per-Account Configuration:</b></p>
 * <p>Call {@link #getBrokerForAccount(BrokerAccountId)} to route based on account configuration.
 * The account's BrokerType determines which adapter to use, and the adapter is configured with
 * the account's specific settings (MockBrokerConfig or BrokerCredentials).</p>
 *
 * <p><b>Configuration Properties (for legacy routing):</b></p>
 * <pre>
 * quorumtrading.broker.crypto.type=mock        # Crypto broker: mock|alpaca
 * quorumtrading.broker.stock.type=alpaca       # Stock broker: mock|alpaca
 * </pre>
 *
 * <p><b>Error Handling (FAIL FAST):</b></p>
 * <ul>
 *   <li>If broker account not found → throws IllegalStateException</li>
 *   <li>If broker adapter unavailable → throws IllegalStateException with diagnostic message</li>
 *   <li><b>NO SILENT FALLBACK:</b> Operations fail immediately on configuration errors</li>
 * </ul>
 *
 * <p><b>CDI Configuration:</b></p>
 * <ul>
 *   <li>@Priority(100) makes this the preferred BrokerPort when injected without qualifiers</li>
 *   <li>This prevents ambiguous dependency errors with @Named broker adapters</li>
 * </ul>
 *
 * @see com.quorumtrading.adapter.broker.mock.MockBrokerAdapter
 * @see com.quorumtrading.adapter.broker.alpaca.AlpacaBrokerAdapter
 * @see BrokerAccountRepository
 */
@ApplicationScoped
@Priority(100)
public class BrokerRouter implements BrokerPort {

    private static final Logger LOG = Logger.getLogger(BrokerRouter.class);

    @Inject
    @Named("mockBroker")
    Instance<BrokerPort> mockBroker;

    @Inject
    @Named("alpacaBroker")
    Instance<BrokerPort> alpacaBroker;

    @Inject
    @Named("saxoBroker")
    Instance<BrokerPort> saxoBroker;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    Instance<BrokerAccountRepository> brokerAccountRepository;

    @Inject
    Instance<SimulationPortfolioRepository> simulationPortfolioRepository;

    /**
     * Price source for valuing the capital ledger's positions.
     *
     * <p>An {@code Instance} because an isolated adapter context may not wire persistence at all;
     * a ledger holding positions that cannot be priced is reported as unvaluable (null) rather
     * than falling back to a broker figure that is not per-simulation.</p>
     */
    @Inject
    Instance<MarketEventRepository> marketEventRepository;

    @ConfigProperty(name = "quorumtrading.broker.crypto.type", defaultValue = "mock")
    String cryptoBrokerType;

    @ConfigProperty(name = "quorumtrading.broker.stock.type", defaultValue = "mock")
    String stockBrokerType;

    @ConfigProperty(name = "quorumtrading.broker.saxo.enabled", defaultValue = "false")
    boolean saxoEnabled;

    // Cache of configured accounts (tracks which accounts have been set up on adapters)
    private final Map<BrokerAccountId, Boolean> configuredAccounts = new ConcurrentHashMap<>();

    // ========== Per-Account Routing API ==========

    /**
     * Gets the appropriate broker for a specific broker account.
     * Configures the broker adapter with the account's settings if not already configured.
     *
     * <p>This is the preferred routing method for new code. It:</p>
     * <ol>
     *   <li>Looks up the BrokerAccount from the repository</li>
     *   <li>Determines the broker type (MOCK, ALPACA_PAPER, ALPACA_LIVE)</li>
     *   <li>Configures the adapter with account-specific settings</li>
     *   <li>Returns the configured broker port</li>
     * </ol>
     *
     * @param accountId the broker account ID
     * @return the configured BrokerPort for this account
     * @throws IllegalStateException if account not found or broker adapter unavailable
     */
    public BrokerPort getBrokerForAccount(BrokerAccountId accountId) {
        if (brokerAccountRepository.isUnsatisfied()) {
            throw new IllegalStateException(
                "BrokerAccountRepository not available. Ensure persistence adapter is on classpath.");
        }
        BrokerAccount account = brokerAccountRepository.get().findById(accountId)
            .orElseThrow(() -> new IllegalStateException(
                "Broker account not found: " + accountId.value()));

        return configureBrokerForAccount(account);
    }

    /**
     * Configures and returns the broker adapter for a specific account.
     * Ensures the adapter is configured with the account's settings.
     *
     * @param account the broker account entity
     * @return the configured BrokerPort
     */
    private BrokerPort configureBrokerForAccount(BrokerAccount account) {
        BrokerType brokerType = account.getBrokerType();
        BrokerAccountId accountId = account.getId();

        requireIntegrationEnabled(brokerType, accountId);

        // Configure the adapter if not already done
        if (!configuredAccounts.containsKey(accountId)) {
            configureAdapterForAccount(account);
            configuredAccounts.put(accountId, true);
        }

        return switch (brokerType) {
            case MOCK -> {
                if (mockBroker.isUnsatisfied()) {
                    throw new IllegalStateException("MockBrokerAdapter not available");
                }
                yield mockBroker.get();
            }
            case ALPACA_PAPER, ALPACA_LIVE -> {
                if (alpacaBroker.isUnsatisfied()) {
                    throw new IllegalStateException(
                        String.format("AlpacaBrokerAdapter not available for %s account %s. " +
                            "Check if quorumtrading-adapter-broker-alpaca module is on classpath.",
                            brokerType, accountId.value().toString().substring(0, 8)));
                }
                yield alpacaBroker.get();
            }
            case SAXO_SIM, SAXO_LIVE -> {
                if (saxoBroker.isUnsatisfied()) {
                    throw new IllegalStateException(
                        String.format("SaxoBrokerAdapter not available for %s account %s. " +
                            "Check if quorumtrading-adapter-broker-saxo module is on classpath " +
                            "and the SaxoBrokerAdapter bean is created " +
                            "(@ApplicationScoped + @Named(\"saxoBroker\")).",
                            brokerType, accountId.value().toString().substring(0, 8)));
                }
                yield saxoBroker.get();
            }
        };
    }

    /**
     * Configures the appropriate broker adapter with account-specific settings.
     * Called once per account when first accessed.
     * Uses ConfigurableBrokerPort interface to avoid direct adapter dependencies.
     */
    private void configureAdapterForAccount(BrokerAccount account) {
        BrokerType brokerType = account.getBrokerType();
        BrokerAccountId accountId = account.getId();

        requireIntegrationEnabled(brokerType, accountId);

        LOG.infof("Configuring broker adapter for account %s (type: %s)",
            accountId.value().toString().substring(0, 8),
            brokerType);

        BrokerPort adapter = switch (brokerType) {
            case MOCK -> mockBroker.isUnsatisfied() ? null : mockBroker.get();
            case ALPACA_PAPER, ALPACA_LIVE -> alpacaBroker.isUnsatisfied() ? null : alpacaBroker.get();
            case SAXO_SIM, SAXO_LIVE -> saxoBroker.isUnsatisfied() ? null : saxoBroker.get();
        };

        if (adapter instanceof ConfigurableBrokerPort configurable) {
            configurable.configureForAccount(account);
        } else if (adapter != null) {
            LOG.warnf("Broker adapter for %s does not implement ConfigurableBrokerPort, " +
                     "per-account configuration not supported", brokerType);
        }
    }

    /**
     * Refuses to route to a broker whose integration is switched off on this instance.
     *
     * <p>Every per-account routing path funnels through here, so flipping
     * {@code quorumtrading.broker.saxo.enabled} to {@code false} stops trades, balance
     * queries and plan executions for <em>already-linked</em> Saxo accounts too — not just
     * new onboarding. The message tells the operator exactly how to recover.</p>
     */
    private void requireIntegrationEnabled(BrokerType brokerType, BrokerAccountId accountId) {
        if (brokerType.isSaxo() && !saxoEnabled) {
            throw new com.quorumtrading.application.exception.BrokerIntegrationDisabledException(
                String.format(
                    "Saxo integration is disabled on this instance, so broker account %s (%s) "
                    + "cannot be used — not even for accounts linked before it was disabled. "
                    + "To re-enable: set SAXO_ENABLED=true (quorumtrading.broker.saxo.enabled) "
                    + "and restart. Alternatively, link the simulation or plan to a different "
                    + "broker account.",
                    accountId.value().toString().substring(0, 8),
                    brokerType));
        }
    }

    /**
     * Links a simulation to a broker account in the appropriate adapter.
     * This sets up the mapping so that BrokerPort methods using SimulationId
     * can route to the correct account.
     *
     * @param simulationId the simulation ID
     * @param accountId the broker account ID
     */
    public void linkSimulationToAccount(SimulationId simulationId, BrokerAccountId accountId) {
        if (brokerAccountRepository.isUnsatisfied()) {
            throw new IllegalStateException(
                "BrokerAccountRepository not available. Ensure persistence adapter is on classpath.");
        }
        BrokerAccount account = brokerAccountRepository.get().findById(accountId)
            .orElseThrow(() -> new IllegalStateException(
                "Broker account not found: " + accountId.value()));

        // Ensure adapter is configured
        configureAdapterForAccount(account);

        // Get the broker and link the simulation
        BrokerPort broker = configureBrokerForAccount(account);
        if (broker instanceof ConfigurableBrokerPort configurable) {
            configurable.linkSimulationToAccount(simulationId, accountId);
        }

        // The adapter can only seed this simulation's isolated state from the ACCOUNT's capital;
        // the ledger knows what the simulation was actually funded with. Replace it immediately.
        SimulationCapitalLedger.replaceAccountSeed(resolve(simulationPortfolioRepository), simulationId, broker);

        LOG.debugf("Linked simulation %s to broker account %s (type: %s)",
            simulationId.value().toString().substring(0, 8),
            accountId.value().toString().substring(0, 8),
            account.getBrokerType());
    }

    /**
     * Removes broker account configuration from adapters.
     * Called when a broker account is deleted.
     *
     * @param accountId the broker account ID to remove
     */
    public void removeAccountConfiguration(BrokerAccountId accountId) {
        configuredAccounts.remove(accountId);

        // Remove from both adapters if they implement ConfigurableBrokerPort
        if (!mockBroker.isUnsatisfied() && mockBroker.get() instanceof ConfigurableBrokerPort configurable) {
            configurable.removeAccountConfiguration(accountId);
        }
        if (!alpacaBroker.isUnsatisfied() && alpacaBroker.get() instanceof ConfigurableBrokerPort configurable) {
            configurable.removeAccountConfiguration(accountId);
        }
        if (!saxoBroker.isUnsatisfied() && saxoBroker.get() instanceof ConfigurableBrokerPort configurable) {
            configurable.removeAccountConfiguration(accountId);
        }

        LOG.infof("Removed broker configuration for account %s",
            accountId.value().toString().substring(0, 8));
    }

    // ========== Legacy Routing (for backward compatibility) ==========

    /**
     * Check if simulation is in LIVE mode (real-time trading).
     * BACKTEST simulations (SIMULATING status) always use MockBroker.
     *
     * @param simulationId Simulation to check
     * @return true if LIVE mode, false if BACKTEST mode
     */
    private boolean isLiveSimulation(SimulationId simulationId) {
        Optional<Simulation> simOpt = simulationRepository.findById(simulationId);
        if (simOpt.isEmpty()) {
            LOG.warnf("Simulation %s not found, defaulting to BACKTEST mode (MockBroker)",
                simulationId.value().toString().substring(0, 8));
            return false;
        }

        Simulation simulation = simOpt.get();
        boolean isLive = simulation.getStatus() == SimulationStatus.LIVE;

        if (LOG.isDebugEnabled()) {
            LOG.debugf("Simulation %s is in %s mode (status: %s)",
                simulationId.value().toString().substring(0, 8),
                isLive ? "LIVE" : "BACKTEST",
                simulation.getStatus());
        }

        return isLive;
    }

    /**
     * Route to the appropriate broker based on simulation mode and investment type.
     *
     * <p><b>Routing Logic (Priority Order):</b></p>
     * <ol>
     *   <li>Per-Account Routing: If simulation has linked BrokerAccountId, use that account's broker</li>
     *   <li>BACKTEST mode (SIMULATING) → Always MockBroker</li>
     *   <li>LIVE mode → Route by configured broker type for asset class</li>
     * </ol>
     *
     * @param simulationId Simulation identifier
     * @param investmentType Asset class (CRYPTO or STOCK)
     * @return BrokerPort implementation to use
     */
    private BrokerPort routeBroker(SimulationId simulationId, InvestmentType investmentType) {
        // PRIORITY 1: Check if simulation has a linked broker account
        // This ensures simulations created with explicit mock/alpaca broker accounts
        // always use their intended broker, regardless of global configuration
        Optional<Simulation> simOpt = simulationRepository.findById(simulationId);
        if (simOpt.isPresent()) {
            Simulation simulation = simOpt.get();
            BrokerAccountId brokerAccountId = simulation.getBrokerAccountId();
            if (brokerAccountId != null) {
                LOG.debugf("Simulation %s has linked broker account %s → using per-account routing",
                    simulationId.value().toString().substring(0, 8),
                    brokerAccountId.value().toString().substring(0, 8));
                return getBrokerForAccount(brokerAccountId);
            }
        }

        // PRIORITY 2: Legacy routing for simulations without linked broker accounts
        // BACKTEST mode always uses MockBroker
        if (!isLiveSimulation(simulationId)) {
            if (mockBroker.isUnsatisfied()) {
                throw new IllegalStateException("MockBroker required for BACKTEST mode but not available");
            }
            LOG.debugf("BACKTEST mode (no linked account) → routing to MockBroker");
            return mockBroker.get();
        }

        // PRIORITY 3: LIVE mode → route by configured broker type
        String brokerType = investmentType == InvestmentType.CRYPTO
            ? cryptoBrokerType
            : stockBrokerType;

        BrokerPort broker = switch (brokerType.toLowerCase()) {
            case "alpaca" -> {
                if (!alpacaBroker.isUnsatisfied()) {
                    yield alpacaBroker.get();
                } else {
                    // FAIL HARD: Never fallback to MockBroker for LIVE simulations when Alpaca is configured
                    throw new IllegalStateException(String.format(
                        "LIVE simulation %s requires Alpaca broker for %s trading, but AlpacaBrokerAdapter is not available. " +
                        "Check: (1) quorumtrading-adapter-broker-alpaca module is on classpath, " +
                        "(2) AlpacaBrokerAdapter bean is created (@ApplicationScoped + @Named(\"alpacaBroker\")), " +
                        "(3) ALPACA_API_KEY environment variable is set. " +
                        "Configuration: quorumtrading.broker.%s.type=%s",
                        simulationId.value().toString().substring(0, 8),
                        investmentType.getDisplayName(),
                        investmentType == InvestmentType.CRYPTO ? "crypto" : "stock",
                        brokerType
                    ));
                }
            }
            case "mock" -> {
                if (mockBroker.isUnsatisfied()) {
                    throw new IllegalStateException("MockBroker requested but not available");
                }
                yield mockBroker.get();
            }
            default -> {
                // FAIL HARD: Unknown broker type for LIVE simulation
                throw new IllegalStateException(String.format(
                    "LIVE simulation %s has unknown broker type '%s' configured for %s trading. " +
                    "Valid values: 'alpaca' or 'mock'. " +
                    "Configuration: quorumtrading.broker.%s.type=%s",
                    simulationId.value().toString().substring(0, 8),
                    brokerType,
                    investmentType.getDisplayName(),
                    investmentType == InvestmentType.CRYPTO ? "crypto" : "stock",
                    brokerType
                ));
            }
        };

        if (LOG.isDebugEnabled()) {
            LOG.debugf("LIVE mode → routing %s trade to %s broker",
                investmentType.getDisplayName(),
                brokerType);
        }

        return broker;
    }

    @Override
    public TradeResult executeTrade(ExecuteTradeCommand command) {
        InvestmentType investmentType = command.symbol().type();
        BrokerPort broker = routeBroker(command.simulationId(), investmentType);

        LOG.infof("Routing %s trade for %s (simulation: %s)",
            command.action(),
            command.symbol().ticker(),
            command.simulationId().value().toString().substring(0, 8)
        );

        TradeResult result = broker.executeTrade(command);
        recordFillInSimulationPortfolio(command, result);
        return result;
    }

    /**
     * Apply a fill to the simulation's capital ledger.
     *
     * <p>This is the one place cash accounting happens, because this method is the single choke
     * point every trade passes through — consensus execution, {@code sell_position} and
     * {@code liquidate_all_positions} all route here.</p>
     *
     * <p>It used to live inside the Alpaca and Saxo adapters and nowhere else, so the mock broker
     * — every backtest — never maintained {@code SimulationPortfolio} at all. Its cash therefore
     * sat frozen at the seed value for the entire run while {@code portfolio_snapshots} tracked
     * reality, leaving the platform with two disagreeing ledgers behind one port. Keeping the
     * accounting here means a new broker adapter cannot forget it, and satisfies the layering
     * rule that adapters do not own domain bookkeeping.</p>
     *
     * <p>A failure here never fails the trade: the order already executed at the broker, and
     * throwing would lose that fact entirely. It is logged loudly instead.</p>
     */
    /** True when the simulation is linked to a broker account, i.e. real capital is ring-fenced. */
    private boolean isBrokerBacked(SimulationId simulationId) {
        return simulationRepository.findById(simulationId)
            .map(Simulation::getBrokerAccountId)
            .isPresent();
    }

    private void recordFillInSimulationPortfolio(ExecuteTradeCommand command, TradeResult result) {
        if (result == null || !result.isSuccessful()) {
            return;
        }
        if (simulationPortfolioRepository.isUnsatisfied()) {
            return; // Isolated adapter/test context without persistence wired in.
        }

        SimulationPortfolioRepository repo = simulationPortfolioRepository.get();
        String simRef = command.simulationId().value().toString().substring(0, 8);

        Optional<SimulationPortfolio> portfolioOpt = repo.findById(command.simulationId());
        if (portfolioOpt.isEmpty()) {
            // A broker-less backtest legitimately has no capital ledger — there is no real
            // account to ring-fence, and the mock broker's own state backs the snapshots. Only a
            // broker-backed simulation missing its ledger is an actual isolation failure, and
            // conflating the two would either cry wolf on every backtest fill or hide the case
            // that matters.
            if (isBrokerBacked(command.simulationId())) {
                LOG.errorf("CAPITAL ISOLATION FAILURE: no SimulationPortfolio for simulation %s — "
                    + "the trade executed at the broker but cannot be tracked, so this "
                    + "simulation's budget enforcement is not being applied.", simRef);
            } else {
                LOG.debugf("No capital ledger for broker-less simulation %s; snapshots remain the "
                    + "single record of cash.", simRef);
            }
            return;
        }

        SimulationPortfolio portfolio = portfolioOpt.get();
        java.math.BigDecimal filledQty = result.filledQuantity();
        Money fillPrice = result.executionPrice();
        Money totalCost = fillPrice.multiply(filledQty);

        try {
            if (command.action() == com.quorumtrading.model.enums.TradeAction.BUY) {
                portfolio.recordBuy(command.symbol(), filledQty, fillPrice, totalCost);
            } else {
                portfolio.recordSell(command.symbol(), filledQty, fillPrice, totalCost);
            }
            repo.save(portfolio);
            LOG.infof("Ledger: simulation %s %s %s x %s @ %s (cash now %s)",
                simRef, command.action(), filledQty, command.symbol().ticker(), fillPrice,
                portfolio.getCashAvailable());
        } catch (IllegalStateException e) {
            // Insufficient cash, or no position to sell. The trade already happened at the
            // broker, so the ledger is now behind — say so rather than silently drifting.
            LOG.errorf("Failed to record fill in SimulationPortfolio for simulation %s: %s. "
                + "The trade executed at the broker, so the capital ledger is now inconsistent.",
                simRef, e.getMessage());
        }
    }

    @Override
    public Optional<TradeResult> getOrderStatus(OrderId orderId) {
        // Order status queries go to all brokers (we don't know which one handled it)
        // Try mock broker first (most common), then Alpaca if available
        if (!mockBroker.isUnsatisfied()) {
            Optional<TradeResult> result = mockBroker.get().getOrderStatus(orderId);
            if (result.isPresent()) {
                return result;
            }
        }

        if (!alpacaBroker.isUnsatisfied()) {
            return alpacaBroker.get().getOrderStatus(orderId);
        }

        return Optional.empty();
    }

    /**
     * An optional bean, or null when it is not wired — the shape {@link SimulationCapitalLedger}
     * expects, since the application layer is CDI-free everywhere except this class.
     */
    private static <T> T resolve(Instance<T> bean) {
        return bean.isUnsatisfied() ? null : bean.get();
    }

    /**
     * The simulation's own ledger states its cash and positions; only a simulation without one
     * (a broker-less backtest) reads the broker. Reading the mock adapter's account-seeded
     * placeholder here is what wrote a $12,000 account capital into a $1,000 plan-funded
     * simulation's snapshots every five minutes.
     *
     * @see SimulationCapitalLedger
     */
    @Override
    public Portfolio getCurrentPortfolio(SimulationId simulationId) {
        return SimulationCapitalLedger.portfolio(resolve(simulationPortfolioRepository), simulationId,
            // InvestmentType doesn't matter for portfolio queries
            () -> routeBroker(simulationId, InvestmentType.STOCK).getCurrentPortfolio(simulationId));
    }

    /**
     * Same authority as {@link #getCurrentPortfolio}: order sizing must not be able to spend the
     * account's capital, nor be capped by a placeholder that pre-dates a contribution.
     */
    @Override
    public Money getAvailableBalance(SimulationId simulationId) {
        return SimulationCapitalLedger.cash(resolve(simulationPortfolioRepository), simulationId,
            // InvestmentType doesn't matter for balance queries
            () -> routeBroker(simulationId, InvestmentType.STOCK).getAvailableBalance(simulationId));
    }

    @Override
    public void initializePortfolio(SimulationId simulationId, Money initialCash) {
        // Route to broker based on simulation mode
        BrokerPort broker = routeBroker(simulationId, InvestmentType.STOCK); // InvestmentType doesn't matter for initialization

        broker.initializePortfolio(simulationId, initialCash);
    }

    @Override
    public void initializePortfolio(SimulationId simulationId, Money cashAvailable, Map<Symbol, com.quorumtrading.model.entity.Position> positions) {
        // Route to broker based on simulation mode
        BrokerPort broker = routeBroker(simulationId, InvestmentType.STOCK); // InvestmentType doesn't matter for initialization

        broker.initializePortfolio(simulationId, cashAvailable, positions);
    }

    @Override
    public void clearPortfolio(SimulationId simulationId) {
        // Route to broker based on simulation mode
        BrokerPort broker = routeBroker(simulationId, InvestmentType.STOCK); // InvestmentType doesn't matter for clear

        broker.clearPortfolio(simulationId);

        // Per-account routing: budget enforcement is handled by BrokerAccount.reserve()/release()
    }

    /**
     * The ledger supplies cash and positions; the market supplies prices. Asking the broker for a
     * total meant asking it for cash too, and its cash is account-level.
     */
    @Override
    public Money getPortfolioValue(SimulationId simulationId, java.time.Instant timestamp) {
        return SimulationCapitalLedger.value(resolve(simulationPortfolioRepository), resolve(marketEventRepository),
            simulationId, timestamp,
            // InvestmentType doesn't matter for value queries
            () -> routeBroker(simulationId, InvestmentType.STOCK).getPortfolioValue(simulationId, timestamp));
    }

    @Override
    public boolean isSymbolTradable(Symbol symbol) {
        // For symbol validation, we don't have a specific simulation context
        // Check with the broker that would be used for this symbol type

        // Determine which broker would handle this symbol
        BrokerPort broker;
        if (symbol.type() == InvestmentType.CRYPTO) {
            broker = getBrokerByType(cryptoBrokerType);
        } else {
            broker = getBrokerByType(stockBrokerType);
        }

        // Delegate validation to the appropriate broker
        return broker.isSymbolTradable(symbol);
    }

    @Override
    public Set<Symbol> validateSymbols(Set<Symbol> symbols) {
        // For bulk validation, we may have both crypto and stock symbols
        // We need to validate with the appropriate broker for each type

        Set<Symbol> validSymbols = new java.util.HashSet<>();

        // Group symbols by investment type
        Set<Symbol> cryptoSymbols = new java.util.HashSet<>();
        Set<Symbol> stockSymbols = new java.util.HashSet<>();

        for (Symbol symbol : symbols) {
            if (symbol.type() == InvestmentType.CRYPTO) {
                cryptoSymbols.add(symbol);
            } else {
                stockSymbols.add(symbol);
            }
        }

        // Validate crypto symbols with crypto broker
        if (!cryptoSymbols.isEmpty()) {
            BrokerPort cryptoBroker = getBrokerByType(cryptoBrokerType);
            validSymbols.addAll(cryptoBroker.validateSymbols(cryptoSymbols));
        }

        // Validate stock symbols with stock broker
        if (!stockSymbols.isEmpty()) {
            BrokerPort stockBroker = getBrokerByType(stockBrokerType);
            validSymbols.addAll(stockBroker.validateSymbols(stockSymbols));
        }

        return validSymbols;
    }

    /**
     * Get a broker by its type configuration string.
     * Used when no specific simulation context is available.
     *
     * @param brokerType Configuration string ("mock" or "alpaca")
     * @return The broker instance
     * @throws IllegalStateException if broker is not available
     */
    private BrokerPort getBrokerByType(String brokerType) {
        if ("alpaca".equalsIgnoreCase(brokerType)) {
            if (!alpacaBroker.isResolvable()) {
                throw new IllegalStateException(
                    "Alpaca broker is configured but not available. " +
                    "Check if Alpaca adapter is enabled and properly configured."
                );
            }
            return alpacaBroker.get();
        } else if ("mock".equalsIgnoreCase(brokerType)) {
            return mockBroker.get();
        } else {
            throw new IllegalStateException(
                "Unknown broker type: " + brokerType + ". Valid types: 'mock', 'alpaca'"
            );
        }
    }

    @Override
    public Optional<Money> getAccountBalance() {
        // For pre-simulation balance queries, try the configured LIVE broker
        // Stock broker type typically determines the broker for LIVE trading
        // (Alpaca for real trading, mock for testing)

        // Try stock broker first (typically Alpaca)
        if ("alpaca".equalsIgnoreCase(stockBrokerType) && alpacaBroker.isResolvable()) {
            Optional<Money> balance = alpacaBroker.get().getAccountBalance();
            if (balance.isPresent()) {
                LOG.infof("Account balance from Alpaca (configured for stocks): %s", balance.get());
                return balance;
            }
        }

        // Try crypto broker if different from stock broker
        if (!cryptoBrokerType.equalsIgnoreCase(stockBrokerType)
                && "alpaca".equalsIgnoreCase(cryptoBrokerType)
                && alpacaBroker.isResolvable()) {
            Optional<Money> balance = alpacaBroker.get().getAccountBalance();
            if (balance.isPresent()) {
                LOG.infof("Account balance from Alpaca (configured for crypto): %s", balance.get());
                return balance;
            }
        }

        // Fallback to mock broker (will return empty)
        if (!mockBroker.isUnsatisfied()) {
            return mockBroker.get().getAccountBalance();
        }

        LOG.debug("No broker available for pre-simulation balance query");
        return Optional.empty();
    }

    @Override
    public void cancelAllOpenOrders(SimulationId simulationId) {
        BrokerPort broker = routeBroker(simulationId, InvestmentType.STOCK); // InvestmentType doesn't matter for cancel
        broker.cancelAllOpenOrders(simulationId);
    }

    @Override
    public Optional<AssetInfo> getAssetInfo(String ticker) {
        // Try stock broker first (typically Alpaca for real validation)
        BrokerPort stockBroker = getBrokerByType(stockBrokerType);
        Optional<AssetInfo> result = stockBroker.getAssetInfo(ticker);
        if (result.isPresent()) {
            return result;
        }

        // Try crypto broker if different
        if (!cryptoBrokerType.equalsIgnoreCase(stockBrokerType)) {
            BrokerPort cryptoBroker = getBrokerByType(cryptoBrokerType);
            return cryptoBroker.getAssetInfo(ticker);
        }

        return Optional.empty();
    }

    // ========== Account-Aware Overrides (per-account routing) ==========

    /**
     * Account-aware tradability check.
     *
     * <p>Resolves the broker for the linked account (e.g. a Saxo account uses its own
     * reference-data Uic lookup) and delegates the account-aware tradability check to
     * that broker. This is the path EU-instrument validation takes when a simulation or
     * find-ticker request carries an explicit broker account, rather than falling back
     * to the global default broker which only knows US tradability.</p>
     *
     * @param symbol    the symbol to validate (ticker and investment type)
     * @param accountId the linked broker account to validate against
     * @return true if the symbol is tradable on that account, false otherwise
     */
    @Override
    public boolean isSymbolTradable(Symbol symbol, BrokerAccountId accountId) {
        BrokerPort broker = getBrokerForAccount(accountId);
        return broker.isSymbolTradable(symbol, accountId);
    }

    /**
     * Account-aware asset-info lookup.
     *
     * <p>Resolves the broker for the linked account and delegates the account-aware
     * asset-info lookup so exchange/currency/Uic enrichment comes from the venue the
     * account actually trades on.</p>
     *
     * @param ticker    the ticker symbol to look up
     * @param accountId the linked broker account to resolve against
     * @return asset information, or empty if the broker doesn't support asset lookups
     */
    @Override
    public Optional<AssetInfo> getAssetInfo(String ticker, BrokerAccountId accountId) {
        BrokerPort broker = getBrokerForAccount(accountId);
        return broker.getAssetInfo(ticker, accountId);
    }

    /**
     * Capabilities for a specific broker account.
     *
     * <p>Resolves the broker for the linked account and returns that broker's
     * capabilities (e.g. Saxo reports whole-share equities, while Alpaca/Mock report
     * fully fractional). Callers in the sizing pipeline consult this to decide whether
     * an order quantity may carry a fractional component for the account being traded.</p>
     *
     * @param accountId the linked broker account
     * @return the capabilities reported by that account's broker
     */
    public BrokerCapabilities capabilities(BrokerAccountId accountId) {
        return getBrokerForAccount(accountId).capabilities();
    }

    /**
     * Capabilities for the broker that backs a given broker account.
     *
     * <p>Convenience alias of {@link #capabilities(BrokerAccountId)} for call sites that
     * read more naturally as "the capabilities for this account".</p>
     *
     * @param accountId the linked broker account
     * @return the capabilities reported by that account's broker
     */
    public BrokerCapabilities capabilitiesForAccount(BrokerAccountId accountId) {
        return capabilities(accountId);
    }

    /**
     * Capabilities for the broker that will execute a given simulation's trades.
     *
     * <p>If the simulation has a linked broker account, returns that account's broker
     * capabilities (whole-share for Saxo, fractional for Alpaca/Mock). If the simulation
     * has no linked account (legacy global routing — always the mock/alpaca path that
     * supports fractional on every asset class), returns the default fully-fractional
     * capabilities so existing backtest behavior is unchanged.</p>
     *
     * @param simulationId the simulation whose executing broker's capabilities are needed
     * @return the capabilities of the broker that will execute this simulation's trades
     */
    public BrokerCapabilities capabilitiesForSimulation(SimulationId simulationId) {
        Optional<Simulation> simOpt = simulationRepository.findById(simulationId);
        if (simOpt.isPresent()) {
            BrokerAccountId accountId = simOpt.get().getBrokerAccountId();
            if (accountId != null) {
                return capabilities(accountId);
            }
        }
        // No linked account → legacy mock/alpaca routing, both fully fractional.
        return BrokerCapabilities.fractional();
    }
}
