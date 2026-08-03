package com.quorumtrading.adapter.broker.alpaca;

import com.quorumtrading.adapter.broker.alpaca.client.AlpacaTradingClient;
import com.quorumtrading.adapter.broker.alpaca.client.dto.AccountResponse;
import com.quorumtrading.adapter.broker.alpaca.client.dto.AssetResponse;
import com.quorumtrading.adapter.broker.alpaca.client.dto.OrderRequest;
import com.quorumtrading.adapter.broker.alpaca.client.dto.OrderResponse;
import com.quorumtrading.adapter.broker.alpaca.client.dto.PositionResponse;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.ConfigurableBrokerPort;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.enums.TradeStatus;
import com.quorumtrading.model.valueobject.ApiKeyCredentials;
import com.quorumtrading.model.valueobject.AssetInfo;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.rest.client.RestClientBuilder;

/**
 * Alpaca broker adapter for paper trading and production stock/crypto trading.
 *
 * <p>Uses Alpaca Trading API v2 via Quarkus REST Client.
 * Supports both paper trading (sandbox) and live trading.</p>
 *
 * <p><b>Per-Account Configuration:</b></p>
 * <p>This adapter supports multiple Alpaca accounts with different credentials.
 * Each BrokerAccount can have its own API credentials, enabling multiple concurrent
 * simulations with different Alpaca accounts (paper or live).</p>
 *
 * <p><b>Account Configuration Flow:</b></p>
 * <ol>
 *   <li>Call {@link #configureAccount(BrokerAccountId, BrokerCredentials, BrokerType)} to register credentials</li>
 *   <li>Call {@link #linkSimulationToAccount(SimulationId, BrokerAccountId)} to link simulation to account</li>
 *   <li>Simulation-scoped API calls then go through a per-account REST client built from the
 *       linked account's credentials and broker type (paper vs live base URL)</li>
 * </ol>
 *
 * <p><b>Fail-closed guarantee:</b> if a simulation is linked to a broker account but that
 * account has no registered credentials, simulation-scoped calls throw
 * {@link IllegalStateException} instead of silently falling back to the globally configured
 * credentials. Only simulations with no account link use the global-config default client
 * (backwards-compatible global-config mode).</p>
 *
 * <p><b>Fallback Configuration Properties (for backward compatibility):</b></p>
 * <ul>
 *   <li>quorumtrading.broker.alpaca.api-key: Alpaca API key</li>
 *   <li>quorumtrading.broker.alpaca.api-secret: Alpaca API secret</li>
 *   <li>quorumtrading.broker.alpaca.base-url: API base URL (paper or live)</li>
 *   <li>quorumtrading.broker.alpaca.enabled: Enable/disable Alpaca (default: false)</li>
 * </ul>
 *
 * <p><b>CDI Configuration:</b></p>
 * <p>This bean is only injectable via @Named("alpacaBroker") to avoid ambiguous dependency
 * conflicts with BrokerRouter (which is the default BrokerPort implementation).</p>
 *
 * @see BrokerCredentials
 * @see BrokerAccountId
 */
@ApplicationScoped
@Named("alpacaBroker")
public class AlpacaBrokerAdapter implements ConfigurableBrokerPort, BrokerCredentialValidationPort {

    private static final Logger LOG = Logger.getLogger(AlpacaBrokerAdapter.class);

    // Per-account credentials cache (keyed by BrokerAccountId)
    private final Map<BrokerAccountId, AccountCredentialsInfo> accountCredentials = new ConcurrentHashMap<>();

    // Simulation to account mapping (for BrokerPort interface compatibility)
    private final Map<SimulationId, BrokerAccountId> simulationToAccount = new ConcurrentHashMap<>();

    // Cached programmatic Alpaca clients per account (keyed by BrokerAccountId).
    // Built lazily by buildClient(); invalidated when configureAccount() re-registers credentials.
    private final Map<BrokerAccountId, AlpacaTradingClient> accountClients = new ConcurrentHashMap<>();

    // Asset info cache with 1-hour TTL to avoid repeated API calls for ticker validation
    private final Map<String, CachedAsset> assetCache = new ConcurrentHashMap<>();
    private static final long ASSET_CACHE_TTL_SECONDS = 3600;

    private record CachedAsset(AssetInfo info, Instant cachedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plusSeconds(ASSET_CACHE_TTL_SECONDS));
        }
    }

    @RestClient
    AlpacaTradingClient tradingClient; // Default/fallback client (uses global config)

    @jakarta.inject.Inject
    jakarta.enterprise.inject.Instance<SimulationPortfolioRepository> simulationPortfolioRepositoryInstance;

    @ConfigProperty(name = "quorumtrading.broker.alpaca.enabled", defaultValue = "false")
    boolean enabled;

    /**
     * Gets the SimulationPortfolioRepository if available (it won't be in isolated adapter tests).
     */
    private SimulationPortfolioRepository getSimulationPortfolioRepository() {
        if (simulationPortfolioRepositoryInstance.isResolvable()) {
            return simulationPortfolioRepositoryInstance.get();
        }
        return null;
    }

    // ========== Per-Account Configuration API ==========

    /**
     * Configures the Alpaca adapter for a specific broker account with its credentials.
     * This method must be called before using the adapter for simulations tied to this account.
     *
     * @param accountId the broker account ID
     * @param credentials the API credentials for this account
     * @param brokerType the broker type (ALPACA_PAPER or ALPACA_LIVE)
     */
    public void configureAccount(BrokerAccountId accountId, BrokerCredentials credentials, BrokerType brokerType) {
        LOG.infof("Configuring Alpaca broker for account %s (type: %s)",
            accountId.value().toString().substring(0, 8),
            brokerType);

        if (brokerType != BrokerType.ALPACA_PAPER && brokerType != BrokerType.ALPACA_LIVE) {
            throw new IllegalArgumentException("AlpacaBrokerAdapter only supports ALPACA_PAPER and ALPACA_LIVE broker types, got: " + brokerType);
        }

        accountCredentials.put(accountId, new AccountCredentialsInfo(credentials, brokerType));
        // Invalidate any cached client so the next call rebuilds with the new credentials
        accountClients.remove(accountId);
    }

    /**
     * Links a simulation to a broker account for routing purposes.
     * This allows the BrokerPort interface methods (which use SimulationId) to
     * route to the correct account credentials.
     *
     * @param simulationId the simulation ID
     * @param accountId the broker account ID to link to
     */
    public void linkSimulationToAccount(SimulationId simulationId, BrokerAccountId accountId) {
        LOG.debugf("Linking simulation %s to Alpaca account %s",
            simulationId.value().toString().substring(0, 8),
            accountId.value().toString().substring(0, 8));
        simulationToAccount.put(simulationId, accountId);
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
     * Removes a broker account configuration.
     * Called when an account is deleted or credentials are invalidated.
     *
     * @param accountId the broker account ID to remove
     */
    public void removeAccount(BrokerAccountId accountId) {
        LOG.infof("Removing Alpaca account configuration for %s",
            accountId.value().toString().substring(0, 8));
        accountCredentials.remove(accountId);
        accountClients.remove(accountId);
    }

    /**
     * Gets the credentials for a broker account, if configured.
     *
     * @param accountId the broker account ID
     * @return the account credentials info, or empty if not configured
     */
    public Optional<AccountCredentialsInfo> getAccountCredentials(BrokerAccountId accountId) {
        return Optional.ofNullable(accountCredentials.get(accountId));
    }

    /**
     * Record holding credentials and broker type for an account.
     */
    public record AccountCredentialsInfo(BrokerCredentials credentials, BrokerType brokerType) {}

    // ========== ConfigurableBrokerPort Interface Implementation ==========

    @Override
    public void configureForAccount(BrokerAccount account) {
        BrokerType brokerType = account.getBrokerType();
        if (brokerType != BrokerType.ALPACA_PAPER && brokerType != BrokerType.ALPACA_LIVE) {
            LOG.warnf("AlpacaBrokerAdapter received non-Alpaca account type: %s", brokerType);
            return;
        }
        if (account.getCredentials() != null) {
            configureAccount(account.getId(), account.getCredentials(), brokerType);
        } else {
            LOG.warnf("BrokerAccount %s has no credentials - cannot configure Alpaca",
                account.getId().value().toString().substring(0, 8));
        }
    }

    @Override
    public boolean isAccountConfigured(BrokerAccountId accountId) {
        return accountCredentials.containsKey(accountId);
    }

    @Override
    public void removeAccountConfiguration(BrokerAccountId accountId) {
        removeAccount(accountId);
    }

    // ========== Internal Helpers ==========

    /**
     * Gets the effective trading client for a simulation.
     *
     * <p>If the simulation is linked to a broker account, returns (and caches) a per-account
     * client built from that account's credentials. If the simulation is linked but the
     * account has no registered credentials, this FAILS CLOSED with
     * {@link IllegalStateException} — we must never silently trade with the globally
     * configured (operator) credentials on behalf of a linked account.</p>
     *
     * <p>Only simulations with no account link use the default global-config client
     * (backwards-compatible global-config mode).</p>
     *
     * <p>Package-private for testing.</p>
     *
     * @param simulationId the simulation ID
     * @return the Alpaca trading client to use
     * @throws IllegalStateException if the simulation is linked to an account without credentials
     */
    AlpacaTradingClient getEffectiveClient(SimulationId simulationId) {
        BrokerAccountId accountId = simulationToAccount.get(simulationId);
        if (accountId == null) {
            // No account link: backwards-compatible global-config mode
            return tradingClient;
        }
        LOG.debugf("Using per-account Alpaca client for account %s (simulation %s)",
            accountId.value().toString().substring(0, 8),
            simulationId.value().toString().substring(0, 8));
        return getEffectiveClient(accountId);
    }

    /**
     * Gets the effective trading client for a broker account.
     *
     * <p>FAILS CLOSED: throws if no credentials are registered for the account —
     * never falls back to the global-config client.</p>
     *
     * <p>Package-private for testing.</p>
     *
     * @param accountId the broker account ID
     * @return the per-account Alpaca trading client
     * @throws IllegalStateException if the account has no registered credentials
     */
    AlpacaTradingClient getEffectiveClient(BrokerAccountId accountId) {
        AccountCredentialsInfo info = accountCredentials.get(accountId);
        if (info == null) {
            throw new IllegalStateException(
                "No credentials configured for broker account " + accountId.value() +
                ". Refusing to fall back to globally configured Alpaca credentials.");
        }
        return accountClients.computeIfAbsent(accountId, id -> buildClient(info));
    }

    /**
     * Builds a programmatic REST client for an account's credentials.
     *
     * <p>The base URL is chosen from the account's broker type (paper vs live) and the
     * auth headers come from the account's own API key/secret — NOT the global config
     * used by {@code AlpacaAuthHeadersFactory}.</p>
     *
     * <p>Package-private (non-static) so tests can substitute a recording fake.</p>
     *
     * @param info the account credentials and broker type
     * @return a new Alpaca trading client authenticated as that account
     * @throws IllegalStateException if the credentials are not API key/secret credentials
     *                               or the broker type is not an Alpaca type
     */
    AlpacaTradingClient buildClient(AccountCredentialsInfo info) {
        if (!(info.credentials() instanceof ApiKeyCredentials apiKeyCredentials)) {
            throw new IllegalStateException(
                "Alpaca requires API key/secret credentials, got: "
                    + info.credentials().getClass().getSimpleName());
        }
        String baseUrl = baseUrlFor(info.brokerType());
        return RestClientBuilder.newBuilder()
            .baseUri(URI.create(baseUrl))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .register(new jakarta.ws.rs.client.ClientRequestFilter() {
                @Override
                public void filter(jakarta.ws.rs.client.ClientRequestContext requestContext) {
                    requestContext.getHeaders().add("APCA-API-KEY-ID", apiKeyCredentials.apiKey());
                    requestContext.getHeaders().add("APCA-API-SECRET-KEY", apiKeyCredentials.apiSecret());
                }
            })
            .build(AlpacaTradingClient.class);
    }

    /**
     * Resolves the Alpaca API base URL for a broker type.
     *
     * @param brokerType ALPACA_PAPER or ALPACA_LIVE
     * @return the API base URL
     * @throws IllegalStateException for non-Alpaca broker types
     */
    private static String baseUrlFor(BrokerType brokerType) {
        return switch (brokerType) {
            case ALPACA_PAPER -> "https://paper-api.alpaca.markets";
            case ALPACA_LIVE -> "https://api.alpaca.markets";
            default -> throw new IllegalStateException(
                "Unsupported broker type for Alpaca client: " + brokerType);
        };
    }

    /**
     * Checks if the adapter is enabled for a simulation.
     * Returns true if either global enabled flag is set OR account is configured.
     */
    private boolean isEnabledForSimulation(SimulationId simulationId) {
        if (enabled) {
            return true;
        }
        // Check if simulation has a configured account
        BrokerAccountId accountId = simulationToAccount.get(simulationId);
        if (accountId != null && accountCredentials.containsKey(accountId)) {
            return true;
        }
        return false;
    }

    @Override
    public TradeResult executeTrade(ExecuteTradeCommand command) {
        if (!enabled) {
            LOG.warn("Alpaca broker is disabled. Enable via quorumtrading.broker.alpaca.enabled=true");
            return new TradeResult(
                TradeStatus.REJECTED,
                new OrderId(java.util.UUID.randomUUID()),
                null,
                null,
                "Alpaca broker is disabled",
                Instant.now()
            );
        }

        String ticker = command.symbol().ticker();
        boolean isCrypto = command.symbol().type() == InvestmentType.CRYPTO;

        // For stocks: validate market hours (extended hours: 4 AM - 8 PM EST)
        if (!isCrypto) {
            MarketHoursStatus marketStatus = checkMarketHours(Instant.now());
            if (!marketStatus.isOpen()) {
                LOG.warnf("Stock order rejected for %s: market closed. %s",
                    ticker, marketStatus.getReason());
                return new TradeResult(
                    TradeStatus.REJECTED,
                    new OrderId(java.util.UUID.randomUUID()),
                    null,
                    null,
                    String.format("Market closed: %s", marketStatus.getReason()),
                    Instant.now()
                );
            }
        }

        try {
            // Resolve the per-account client (fail-closed) before touching the API
            AlpacaTradingClient client = getEffectiveClient(command.simulationId());

            String side = command.action() == TradeAction.BUY ? "buy" : "sell";

            // IMPORTANT: Alpaca requires "day" for fractional crypto orders
            // "ioc" rejected with: "ioc orders are only accepted during market hours"
            // "gtc" rejected with: "fractional orders must be DAY orders"
            // Crypto uses "day" WITHOUT extended_hours (trades 24/7)
            // Stocks use "day" WITHOUT extended_hours (we only trade during regular hours 9:30 AM - 4 PM EST)
            String timeInForce = "day";
            boolean enableExtendedHours = false; // Disabled - we only trade during regular market hours

            OrderRequest orderRequest = OrderRequest.marketOrder(
                ticker,
                command.quantity().toString(),
                side,
                timeInForce,
                enableExtendedHours
            );

            OrderResponse orderResponse = client.submitOrder(orderRequest);

            LOG.infof("Alpaca order submitted: %s %s %s @ market (order ID: %s, status: %s)",
                command.action(),
                command.quantity(),
                command.symbol().ticker(),
                orderResponse.getId(),
                orderResponse.getStatus()
            );

            // Poll for order fill (market orders usually fill quickly)
            OrderResponse filledOrder = waitForOrderFill(client, orderResponse.getId(), 10, 500);

            if (filledOrder != null) {
                // Capital-ledger accounting is applied centrally in BrokerRouter, which every
                // trade routes through — doing it here as well would double-count the fill.
                return mapToTradeResult(filledOrder);
            } else {
                // Timeout - return current order status (may be pending)
                LOG.warnf("Order %s did not fill within timeout, returning pending status", orderResponse.getId());
                return mapToTradeResult(orderResponse);
            }

        } catch (Exception e) {
            // Enhanced logging for 403 errors - capture full error details
            String errorMessage = e.getMessage();
            String errorDetails = "";

            // Check if this is a WebApplicationException with response body
            if (e instanceof jakarta.ws.rs.WebApplicationException) {
                jakarta.ws.rs.WebApplicationException webEx = (jakarta.ws.rs.WebApplicationException) e;
                int status = webEx.getResponse().getStatus();

                try {
                    String responseBody = webEx.getResponse().readEntity(String.class);
                    errorDetails = String.format("HTTP %d - Response body: %s", status, responseBody);
                } catch (Exception readEx) {
                    errorDetails = String.format("HTTP %d - Could not read response body", status);
                }
            }

            LOG.errorf(e, "Failed to execute trade via Alpaca: %s %s %s. Error details: %s",
                command.action(), command.quantity(), command.symbol().ticker(), errorDetails);

            return new TradeResult(
                TradeStatus.REJECTED,
                new OrderId(java.util.UUID.randomUUID()),
                null,
                null,
                "Alpaca API error: " + errorMessage + (errorDetails.isEmpty() ? "" : " | " + errorDetails),
                Instant.now()
            );
        }
    }

    @Override
    public Optional<TradeResult> getOrderStatus(OrderId orderId) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            // No simulation/account context on this method signature: query with the
            // default global-config client. Orders placed via a per-account client on a
            // different Alpaca account will not be visible here.
            OrderResponse orderResponse = tradingClient.getOrder(orderId.value().toString());
            return Optional.of(mapToTradeResult(orderResponse));
        } catch (Exception e) {
            LOG.debugf("Order %s not found in Alpaca: %s", orderId.value(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Portfolio getCurrentPortfolio(SimulationId simulationId) {
        if (!enabled) {
            return new Portfolio(
                simulationId,
                new HashMap<>(),
                Money.usd(BigDecimal.ZERO)
            );
        }

        // MANDATORY: Use SimulationPortfolio for capital isolation.
        // Never fall back to full Alpaca account — that would bypass trading limits.
        SimulationPortfolioRepository repo = getSimulationPortfolioRepository();
        if (repo != null) {
            Optional<SimulationPortfolio> simPortfolio = repo.findById(simulationId);
            if (simPortfolio.isPresent()) {
                SimulationPortfolio sp = simPortfolio.get();
                LOG.debugf("Returning isolated portfolio for simulation %s: cash=%s, positions=%d",
                    simulationId.value().toString().substring(0, 8),
                    sp.getCashAvailable(), sp.getPositions().size());
                return new Portfolio(simulationId, new HashMap<>(sp.getPositions()), sp.getCashAvailable());
            }
        }

        // No SimulationPortfolio found — refuse to return full Alpaca account balance.
        // Returning the full account would allow a simulation to trade beyond its allocated budget.
        LOG.errorf("CAPITAL ISOLATION FAILURE: No SimulationPortfolio found for simulation %s. " +
            "Returning zero-balance portfolio to prevent trading beyond budget. " +
            "This simulation may need to be recreated.",
            simulationId.value().toString().substring(0, 8));
        return new Portfolio(simulationId, new HashMap<>(), Money.usd(BigDecimal.ZERO));
    }

    @Override
    public Money getAvailableBalance(SimulationId simulationId) {
        if (!enabled) {
            return Money.usd(BigDecimal.ZERO);
        }

        // MANDATORY: Use SimulationPortfolio for capital isolation.
        // Never fall back to full Alpaca account — that would bypass trading limits.
        SimulationPortfolioRepository repo = getSimulationPortfolioRepository();
        if (repo != null) {
            Optional<SimulationPortfolio> simPortfolio = repo.findById(simulationId);
            if (simPortfolio.isPresent()) {
                Money available = simPortfolio.get().getCashAvailable();
                LOG.debugf("Returning isolated balance for simulation %s: %s",
                    simulationId.value().toString().substring(0, 8), available);
                return available;
            }
        }

        // No SimulationPortfolio found — refuse to return full Alpaca account balance.
        // Returning the full account would allow a simulation to trade beyond its allocated budget.
        LOG.errorf("CAPITAL ISOLATION FAILURE: No SimulationPortfolio found for simulation %s. " +
            "Returning zero balance to prevent trading beyond budget. " +
            "This simulation may need to be recreated.",
            simulationId.value().toString().substring(0, 8));
        return Money.usd(BigDecimal.ZERO);
    }

    @Override
    public void initializePortfolio(SimulationId simulationId, Money initialCash) {
        // Capital isolation is managed via SimulationPortfolio (created by SimulationExecutor).
        // This method validates that the Alpaca account has sufficient total cash to cover allocations.

        if (!enabled) {
            LOG.debugf("Alpaca disabled, skipping portfolio initialization for simulation %s",
                simulationId.value().toString().substring(0, 8));
            return;
        }

        try {
            AccountResponse account = getEffectiveClient(simulationId).getAccount();
            Money alpacaCash = Money.usd(account.getCashAsDecimal());

            // Check if SimulationPortfolio exists (it should, created by SimulationExecutor)
            SimulationPortfolioRepository repo = getSimulationPortfolioRepository();
            boolean hasSimPortfolio = repo != null && repo.existsById(simulationId);

            LOG.infof("Alpaca portfolio init (simulation %s): alpaca cash=%s, required=%s, SimulationPortfolio=%s",
                simulationId.value().toString().substring(0, 8),
                alpacaCash, initialCash, hasSimPortfolio ? "ready" : "MISSING");

            if (alpacaCash.compareTo(initialCash) < 0) {
                LOG.warnf("Alpaca account cash (%s) is less than simulation allocation (%s) for simulation %s. " +
                    "Capital isolation will still enforce the allocation limit via SimulationPortfolio.",
                    alpacaCash, initialCash, simulationId.value().toString().substring(0, 8));
            }

        } catch (Exception e) {
            LOG.errorf(e, "Failed to verify Alpaca account state for simulation %s",
                simulationId.value().toString().substring(0, 8));
        }
    }

    @Override
    public void initializePortfolio(SimulationId simulationId, Money cashAvailable, Map<Symbol, Position> positions) {
        // Alpaca manages its own account state externally.
        // For LIVE simulations resuming from previous state, we log the expected state
        // but do NOT modify the Alpaca account.

        if (!enabled) {
            LOG.debugf("Alpaca disabled, skipping portfolio initialization for simulation %s",
                simulationId.value().toString().substring(0, 8));
            return;
        }

        LOG.infof("Alpaca portfolio initialization for simulation %s: expected cash=%s, %d positions",
            simulationId.value().toString().substring(0, 8),
            cashAvailable,
            positions.size());

        // Log expected positions for debugging
        if (LOG.isDebugEnabled()) {
            for (Map.Entry<Symbol, Position> entry : positions.entrySet()) {
                Position pos = entry.getValue();
                LOG.debugf("Expected position: %s x %s @ %s",
                    pos.symbol().ticker(),
                    pos.quantity(),
                    pos.averagePrice());
            }
        }

        // NOTE: We do NOT sync positions TO Alpaca.
        // The Alpaca account should already have these positions from previous trades.
        // This method is called during consensus startup to restore broker state,
        // but for Alpaca, the state lives externally in the real account.
    }

    @Override
    public void clearPortfolio(SimulationId simulationId) {
        // Alpaca manages its own account state - clearing is not supported.
        // To clear an Alpaca account, you must manually liquidate all positions via the Alpaca UI/API.
        // This is intentional - we don't want automated code to accidentally liquidate real positions.

        LOG.warnf("Cannot clear Alpaca account automatically (simulation: %s). " +
                  "Alpaca state is managed externally. " +
                  "To reset, manually liquidate positions via Alpaca dashboard.",
            simulationId.value().toString().substring(0, 8));

        // Clear simulation-to-account mapping
        simulationToAccount.remove(simulationId);

        // Note: SimulationPortfolio is NOT deleted here — it serves as the final audit record.
        // It is cleaned up by SimulationExecutor.saveFinalSimulationPortfolio() on completion.
    }

    @Override
    public Money getPortfolioValue(SimulationId simulationId, Instant timestamp) {
        if (!enabled) {
            LOG.warn("Alpaca broker disabled - returning NULL (cannot determine portfolio value)");
            return null;
        }

        // Use SimulationPortfolio for capital isolation when available
        SimulationPortfolioRepository repo = getSimulationPortfolioRepository();
        if (repo != null) {
            Optional<SimulationPortfolio> simPortfolio = repo.findById(simulationId);
            if (simPortfolio.isPresent()) {
                return calculateIsolatedPortfolioValue(simPortfolio.get(), simulationId);
            }
        }

        // Fallback: no SimulationPortfolio (legacy or pre-isolation simulation)
        LOG.warnf("No SimulationPortfolio found for simulation %s — falling back to full Alpaca equity (no capital isolation!)",
            simulationId.value().toString().substring(0, 8));
        return getFullAccountEquity(simulationId);
    }

    /**
     * Calculate portfolio value for an isolated simulation.
     * Uses the simulation's own positions valued at Alpaca's current market prices,
     * plus the simulation's available cash.
     */
    private Money calculateIsolatedPortfolioValue(SimulationPortfolio simPortfolio, SimulationId simulationId) {
        try {
            Money totalValue = simPortfolio.getCashAvailable();

            // Value positions using Alpaca's reported market values
            List<PositionResponse> alpacaPositions = getEffectiveClient(simulationId).getPositions();
            Map<String, PositionResponse> alpacaPositionMap = new HashMap<>();
            for (PositionResponse ap : alpacaPositions) {
                alpacaPositionMap.put(ap.getSymbol(), ap);
            }

            for (Map.Entry<Symbol, Position> entry : simPortfolio.getPositions().entrySet()) {
                Position simPosition = entry.getValue();
                PositionResponse alpacaPosition = alpacaPositionMap.get(simPosition.symbol().ticker());

                if (alpacaPosition != null && alpacaPosition.getCurrentPrice() != null) {
                    // Use Alpaca's current price for accurate valuation
                    Money currentPrice = Money.usd(new BigDecimal(alpacaPosition.getCurrentPrice()));
                    Money positionValue = currentPrice.multiply(simPosition.quantity());
                    totalValue = totalValue.add(positionValue);
                } else {
                    // Fallback to cost basis if Alpaca doesn't have the position data
                    Money positionValue = simPosition.averagePrice().multiply(simPosition.quantity());
                    totalValue = totalValue.add(positionValue);
                    LOG.warnf("No Alpaca price for %s in simulation %s — using cost basis for valuation",
                        simPosition.symbol().ticker(), simulationId.value().toString().substring(0, 8));
                }
            }

            LOG.debugf("Isolated portfolio value for simulation %s: %s (cash: %s, positions: %d)",
                simulationId.value().toString().substring(0, 8),
                totalValue, simPortfolio.getCashAvailable(), simPortfolio.getPositions().size());

            if (!totalValue.isPositive()) {
                LOG.errorf("Calculated isolated portfolio value is invalid (%s) for simulation %s — returning NULL",
                    totalValue, simulationId.value().toString().substring(0, 8));
                return null;
            }

            return totalValue;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to calculate isolated portfolio value for simulation %s",
                simulationId.value().toString().substring(0, 8));
            return null;
        }
    }

    /**
     * Fallback: Get full Alpaca account equity (no capital isolation).
     * Only used for legacy simulations without SimulationPortfolio.
     */
    private Money getFullAccountEquity(SimulationId simulationId) {
        try {
            AccountResponse account = getEffectiveClient(simulationId).getAccount();
            String equity = account.getEquity();

            if (equity == null || equity.trim().isEmpty()) {
                LOG.warn("Alpaca equity field is null/empty");
                return null;
            }

            BigDecimal equityValue = new BigDecimal(equity);
            if (equityValue.compareTo(BigDecimal.ZERO) <= 0) {
                LOG.errorf("Alpaca equity is $0 or negative (%s) — returning NULL", equityValue);
                return null;
            }

            return Money.usd(equityValue);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch portfolio value from Alpaca");
            return null;
        }
    }

    /**
     * Wait for an order to fill by polling the order status.
     *
     * @param client The trading client the order was submitted with (per-account or default)
     * @param orderId The order ID to poll
     * @param maxAttempts Maximum number of polling attempts (default: 10)
     * @param delayMs Delay between polling attempts in milliseconds (default: 500ms)
     * @return The filled OrderResponse, or null if timeout
     */
    private OrderResponse waitForOrderFill(AlpacaTradingClient client, String orderId, int maxAttempts, long delayMs) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                OrderResponse order = client.getOrder(orderId);
                String status = order.getStatus();

                LOG.debugf("Order %s poll attempt %d/%d: status=%s, filledAvgPrice=%s",
                    orderId, attempt, maxAttempts, status, order.getFilledAvgPrice());

                // Check if order is filled
                if ("filled".equalsIgnoreCase(status) && order.getFilledAvgPrice() != null) {
                    LOG.infof("Order %s filled after %d attempts: price=%s, qty=%s",
                        orderId, attempt, order.getFilledAvgPrice(), order.getFilledQty());
                    return order;
                }

                // Check if order was rejected/cancelled
                if (isTerminalStatus(status)) {
                    LOG.warnf("Order %s reached terminal status: %s", orderId, status);
                    return order;
                }

                // Detect partial fill: some quantity filled but order still open
                if ("partially_filled".equalsIgnoreCase(status) && order.getFilledAvgPrice() != null) {
                    LOG.infof("Order %s partially filled after %d attempts: filledQty=%s, filledAvgPrice=%s — cancelling remainder",
                        orderId, attempt, order.getFilledQty(), order.getFilledAvgPrice());
                    try {
                        client.cancelOrder(orderId);
                    } catch (Exception cancelEx) {
                        LOG.warnf("Failed to cancel remainder of partially filled order %s: %s",
                            orderId, cancelEx.getMessage());
                    }
                    return order;
                }

                // Sleep before next attempt
                if (attempt < maxAttempts) {
                    Thread.sleep(delayMs);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warnf("Order polling interrupted for %s", orderId);
                return null;
            } catch (Exception e) {
                LOG.debugf("Failed to poll order %s: %s", orderId, e.getMessage());
            }
        }

        LOG.warnf("Order %s did not fill after %d attempts — cancelling unfilled order", orderId, maxAttempts);
        try {
            client.cancelOrder(orderId);
            LOG.infof("Cancelled unfilled order %s after timeout", orderId);
        } catch (Exception cancelEx) {
            LOG.warnf("Failed to cancel unfilled order %s: %s", orderId, cancelEx.getMessage());
        }
        return null;
    }

    /**
     * Check if order status is terminal (won't change anymore).
     */
    private boolean isTerminalStatus(String status) {
        if (status == null) return false;
        return switch (status.toLowerCase()) {
            case "filled", "canceled", "expired", "rejected", "suspended", "replaced" -> true;
            default -> false;
        };
    }

    /**
     * Map Alpaca OrderResponse to TradeResult.
     */
    private TradeResult mapToTradeResult(OrderResponse orderResponse) {
        OrderId orderId = new OrderId(java.util.UUID.fromString(orderResponse.getId()));
        TradeStatus status = mapOrderStatus(orderResponse.getStatus());

        Money executionPrice = null;
        BigDecimal filledQty = null;
        String rejectionReason = null;

        if ((status == TradeStatus.EXECUTED || status == TradeStatus.PARTIAL_FILL)
                && orderResponse.getFilledAvgPrice() != null) {
            // Alpaca API returns all prices in dollars (both crypto and stocks)
            executionPrice = Money.usd(new BigDecimal(orderResponse.getFilledAvgPrice()));
            filledQty = new BigDecimal(orderResponse.getFilledQty());
        } else if (status == TradeStatus.REJECTED) {
            rejectionReason = "Order status: " + orderResponse.getStatus();
        }

        return new TradeResult(
            status,
            orderId,
            executionPrice,
            filledQty,
            rejectionReason,
            Instant.now()
        );
    }

    /**
     * Map Alpaca order status to TradeStatus.
     */
    private TradeStatus mapOrderStatus(String alpacaStatus) {
        if (alpacaStatus == null) {
            return TradeStatus.PENDING;
        }

        return switch (alpacaStatus.toLowerCase()) {
            case "filled" -> TradeStatus.EXECUTED;
            case "canceled", "expired", "rejected", "suspended" -> TradeStatus.REJECTED;
            case "partially_filled" -> TradeStatus.PARTIAL_FILL;
            case "new", "accepted", "pending_new", "accepted_for_bidding" -> TradeStatus.PENDING;
            default -> {
                LOG.warnf("Unknown Alpaca order status: %s", alpacaStatus);
                yield TradeStatus.PENDING;
            }
        };
    }

    @Override
    public boolean isSymbolTradable(Symbol symbol) {
        if (!enabled) {
            LOG.warn("Alpaca broker is disabled. Returning false for ticker validation.");
            return false;
        }

        String ticker = symbol.ticker();

        if ("MARKET".equals(ticker)) {
            return false;
        }

        // Query Alpaca Asset API (with cache)
        Optional<AssetInfo> assetInfo = getAssetInfo(ticker);
        return assetInfo.map(AssetInfo::tradable).orElse(false);
    }

    @Override
    public Set<Symbol> validateSymbols(Set<Symbol> symbols) {
        if (!enabled) {
            LOG.warn("Alpaca broker is disabled. Returning empty set for ticker validation.");
            return Set.of();
        }

        Set<Symbol> validSymbols = new HashSet<>();
        for (Symbol symbol : symbols) {
            if (isSymbolTradable(symbol)) {
                validSymbols.add(symbol);
            }
        }
        return validSymbols;
    }

    @Override
    public Optional<AssetInfo> getAssetInfo(String ticker) {
        if (!enabled) {
            return Optional.empty();
        }

        // Check cache first
        CachedAsset cached = assetCache.get(ticker);
        if (cached != null && !cached.isExpired()) {
            LOG.debugf("Asset cache hit for %s: tradable=%s", ticker, cached.info().tradable());
            return Optional.of(cached.info());
        }

        // Query Alpaca API (asset metadata is account-agnostic; the default client is fine)
        try {
            AssetResponse response = tradingClient.getAsset(ticker);
            AssetInfo info;
            if (response.isTradable()) {
                info = AssetInfo.tradable(response.getSymbol(), response.getName(),
                    response.getExchange(), response.getAssetClass());
            } else {
                info = AssetInfo.notTradable(response.getSymbol(), response.getName(),
                    response.getExchange(), response.getStatus(), response.getAssetClass());
            }
            assetCache.put(ticker, new CachedAsset(info, Instant.now()));
            LOG.debugf("Asset lookup for %s: name=%s, exchange=%s, tradable=%s",
                ticker, info.name(), info.exchange(), info.tradable());
            return Optional.of(info);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                AssetInfo notFound = AssetInfo.notFound(ticker);
                assetCache.put(ticker, new CachedAsset(notFound, Instant.now()));
                LOG.debugf("Asset not found on Alpaca: %s", ticker);
                return Optional.of(notFound);
            }
            LOG.warnf("Alpaca asset lookup failed for %s: HTTP %d", ticker, e.getResponse().getStatus());
            return Optional.empty();
        } catch (Exception e) {
            LOG.warnf("Alpaca asset lookup failed for %s: %s", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Money> getAccountBalance() {
        if (!enabled) {
            LOG.warn("Alpaca broker is disabled. Cannot fetch account balance.");
            return Optional.empty();
        }

        try {
            // No simulation/account context on this method signature: reports the balance
            // of the globally configured (default) Alpaca account only.
            AccountResponse account = tradingClient.getAccount();
            Money balance = Money.usd(account.getCashAsDecimal());
            LOG.infof("Alpaca account balance: %s", balance);
            return Optional.of(balance);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch Alpaca account balance");
            return Optional.empty();
        }
    }

    @Override
    public void cancelAllOpenOrders(com.quorumtrading.model.valueobject.SimulationId simulationId) {
        if (!enabled) {
            return;
        }
        try {
            AlpacaTradingClient client = getEffectiveClient(simulationId);
            List<OrderResponse> openOrders = client.getOrders("open", null, null, null, null, null);
            if (openOrders == null || openOrders.isEmpty()) {
                LOG.infof("No open orders to cancel for simulation %s", simulationId);
                return;
            }
            int cancelled = 0;
            for (OrderResponse order : openOrders) {
                try {
                    client.cancelOrder(order.getId());
                    cancelled++;
                } catch (Exception e) {
                    LOG.warnf("Failed to cancel order %s: %s", order.getId(), e.getMessage());
                }
            }
            LOG.infof("Cancelled %d/%d open orders for simulation %s", cancelled, openOrders.size(), simulationId);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to list/cancel open orders for simulation %s", simulationId);
        }
    }

    /**
     * Check if stock market is open for trading (regular hours: 9:30 AM - 4:00 PM EST).
     * We only trade during regular market hours to avoid the Alpaca restriction that
     * extended hours orders must be DAY limit orders (not market orders).
     * Crypto trades 24/7 and should not use this method.
     *
     * @param orderTime The time to check
     * @return MarketHoursStatus indicating if market is open and reason if closed
     */
    private MarketHoursStatus checkMarketHours(Instant orderTime) {
        ZonedDateTime estTime = orderTime.atZone(ZoneId.of("America/New_York"));
        int hour = estTime.getHour();
        int minute = estTime.getMinute();
        DayOfWeek day = estTime.getDayOfWeek();

        // Weekend: market closed
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return new MarketHoursStatus(false,
                String.format("Weekend (%s). Regular hours: Mon-Fri 9:30 AM - 4:00 PM EST", day));
        }

        // Regular market hours: 9:30 AM - 4:00 PM EST
        // Open at 9:30 (hour 9, minute >= 30) through 3:59 PM (hour 15)
        boolean isAfterOpen = (hour > 9) || (hour == 9 && minute >= 30);
        boolean isBeforeClose = hour < 16;

        if (isAfterOpen && isBeforeClose) {
            return new MarketHoursStatus(true, null);
        }

        // Outside regular hours
        return new MarketHoursStatus(false,
            String.format("Outside regular market hours (current: %02d:%02d EST). Regular hours: 9:30 AM - 4:00 PM EST",
                hour, minute));
    }

    // ========== BrokerCredentialValidationPort Implementation ==========

    /**
     * This validator supports the Alpaca broker family only ({@code ALPACA_PAPER}, {@code ALPACA_LIVE}).
     *
     * @param brokerType the broker type a validation is requested for
     * @return {@code true} for Alpaca paper/live broker types
     */
    @Override
    public boolean supports(BrokerType brokerType) {
        return brokerType != null && brokerType.isAlpaca();
    }

    /**
     * Validates Alpaca API credentials by calling the Alpaca API to fetch account info.
     *
     * <p>Creates a programmatic REST client with the provided credentials (not the global config)
     * and makes a test call to /v2/account to verify the credentials work.</p>
     *
     * @param brokerType  the broker type (must be ALPACA_PAPER or ALPACA_LIVE)
     * @param credentials the API credentials to validate
     * @return validation result with account balance if successful
     */
    @Override
    public CredentialValidationResult validate(BrokerType brokerType, BrokerCredentials credentials) {
        LOG.infof("Validating Alpaca credentials for broker type: %s", brokerType);

        // Alpaca authenticates with a static API key/secret pair.
        if (!(credentials instanceof ApiKeyCredentials apiKeyCredentials)) {
            return CredentialValidationResult.failure(
                "Alpaca validation requires API key/secret credentials, got: "
                    + credentials.getClass().getSimpleName());
        }

        // Reject non-Alpaca broker types before building a client
        if (brokerType != BrokerType.ALPACA_PAPER && brokerType != BrokerType.ALPACA_LIVE) {
            return CredentialValidationResult.failure(
                "Invalid broker type for Alpaca validation: " + brokerType +
                ". Expected ALPACA_PAPER or ALPACA_LIVE.");
        }

        try {
            // Create a programmatic REST client with the candidate credentials
            AlpacaTradingClient validationClient =
                buildClient(new AccountCredentialsInfo(apiKeyCredentials, brokerType));

            // Make API call to fetch account info
            AccountResponse account = validationClient.getAccount();

            // Extract cash balance
            Money balance = Money.usd(account.getCashAsDecimal());

            // Extract equity (cash + positions market value)
            Money equity = null;
            String equityStr = account.getEquity();
            if (equityStr != null && !equityStr.trim().isEmpty()) {
                try {
                    equity = Money.usd(new java.math.BigDecimal(equityStr));
                } catch (NumberFormatException e) {
                    LOG.warnf("Failed to parse Alpaca equity value: %s", equityStr);
                }
            }

            LOG.infof("Alpaca credentials validated successfully. Cash: %s, Equity: %s", balance, equity);
            return CredentialValidationResult.success(balance, equity);

        } catch (jakarta.ws.rs.WebApplicationException webEx) {
            // HTTP error from Alpaca API
            int status = webEx.getResponse().getStatus();
            String errorMessage;
            if (status == 401 || status == 403) {
                errorMessage = "Invalid API credentials. Please check your API key and secret.";
            } else if (status == 429) {
                errorMessage = "Rate limit exceeded. Please try again later.";
            } else {
                errorMessage = "Alpaca API error (HTTP " + status + "): " + webEx.getMessage();
            }
            LOG.warnf("Alpaca credential validation failed: %s", errorMessage);
            return CredentialValidationResult.failure(errorMessage);

        } catch (Exception e) {
            // Network or other errors
            String errorMessage = "Failed to connect to Alpaca API: " + e.getMessage();
            LOG.warnf(e, "Alpaca credential validation failed with exception");
            return CredentialValidationResult.failure(errorMessage);
        }
    }

    /**
     * Simple value object for market hours status.
     */
    private static class MarketHoursStatus {
        private final boolean open;
        private final String reason;

        public MarketHoursStatus(boolean open, String reason) {
            this.open = open;
            this.reason = reason;
        }

        public boolean isOpen() {
            return open;
        }

        public String getReason() {
            return reason;
        }
    }
}
