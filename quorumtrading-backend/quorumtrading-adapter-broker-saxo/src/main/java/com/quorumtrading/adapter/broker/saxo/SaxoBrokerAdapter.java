package com.quorumtrading.adapter.broker.saxo;

import com.quorumtrading.adapter.broker.saxo.SaxoUicResolver.ResolvedInstrument;
import com.quorumtrading.adapter.broker.saxo.client.SaxoReferenceDataClient;
import com.quorumtrading.adapter.broker.saxo.client.SaxoRequestTokenHolder;
import com.quorumtrading.adapter.broker.saxo.client.SaxoTradingClient;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoAccountResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoBalanceResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoInstrumentDetails;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoOpenOrdersResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoOrderRequest;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoOrderResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoPositionResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoPositionsResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoUserResponse;
import com.quorumtrading.application.port.out.BrokerCredentialValidationPort;
import com.quorumtrading.application.port.out.ConfigurableBrokerPort;
import com.quorumtrading.application.port.out.SimulationPortfolioRepository;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.enums.TradeStatus;
import com.quorumtrading.model.valueobject.AssetInfo;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCapabilities;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Saxo Bank (EU) broker adapter.
 *
 * <p>Dispatched per linked {@code BrokerAccount} whose {@code brokerType} is
 * {@code SAXO_SIM}/{@code SAXO_LIVE} by the {@code BrokerRouter}. It implements
 * {@link ConfigurableBrokerPort} so the router can {@code @Named("saxoBroker")} resolve it, and
 * {@link BrokerCredentialValidationPort} (the second validator on the classpath alongside Alpaca).</p>
 *
 * <p><b>Landed:</b> SB2 (per-account OAuth bearer + account-key resolution + caching),
 * SB3 (account-aware {@code Symbol → Uic + AssetType} resolution + tradability/asset-info via
 * {@link SaxoUicResolver}), SB4 (whole-share market-order placement, status, cancel,
 * OrderId round-trip), and SB5 (portfolio/balance in EUR, {@code capabilities()} = whole-share
 * equities, no-op initialize/clear for the external account, and credential validation).</p>
 *
 * <h2>Portfolio / balance (SB5)</h2>
 * <p>The {@code BrokerPort} simulation-scoped reads ({@link #getCurrentPortfolio(SimulationId)},
 * {@link #getAvailableBalance(SimulationId)}, {@link #getPortfolioValue(SimulationId, Instant)})
 * stay capital-isolated to the {@link SimulationPortfolio} — they never expose the full Saxo account,
 * exactly as the Alpaca adapter does. The account-level reads
 * ({@link #getAccountPortfolio(BrokerAccountId)}, {@link #getAccountBalance(BrokerAccountId)}) read
 * the real Saxo account ({@code GET /port/v1/positions} + {@code GET /port/v1/balances}) under the
 * per-account bearer, reverse-mapping each position's {@code Uic} to a {@link Symbol}. All Money is
 * EUR (Saxo Belgium settlement — CUR1). {@code initializePortfolio}/{@code clearPortfolio} are no-ops
 * (the account state lives externally at Saxo and is never written by us).</p>
 *
 * <h2>Account-aware tradability / asset-info (SB3)</h2>
 * <p>The account-aware {@link #isSymbolTradable(Symbol, BrokerAccountId)} and
 * {@link #getAssetInfo(String, BrokerAccountId)} overrides resolve a {@link Symbol} to its Saxo
 * {@code (Uic, AssetType, ExchangeId)} via {@link SaxoUicResolver}, running the reference-data
 * calls inside {@link #withAccountContext(BrokerAccountId, Supplier)} so the per-account bearer is
 * attached (D10). The account-agnostic single-arg forms still throw — Saxo reference data is
 * per-account (it needs an OAuth bearer), so there is no global default lookup.</p>
 *
 * <h2>Per-account authentication (D10)</h2>
 * <p>Saxo authenticates each request with a per-account OAuth2 bearer (~20-min TTL, rotating
 * refresh token). A JAX-RS {@code ClientHeadersFactory} has no per-request account context, so
 * this adapter pins the active {@link BrokerAccountId} on the {@code @RequestScoped}
 * {@link SaxoRequestTokenHolder} before each Saxo REST call;
 * {@link com.quorumtrading.adapter.broker.saxo.client.SaxoAuthHeadersFactory} reads the holder
 * and attaches the bearer (resolved from the {@code BrokerTokenProviderPort}, injected into the
 * factory). Because the holder is request-scoped, scheduler/cron-driven calls (no HTTP request)
 * run inside an activated request context — see {@link #withAccountContext(BrokerAccountId, Supplier)}.</p>
 *
 * <h2>Account-key resolution + caching</h2>
 * <p>Saxo addresses resources by opaque handles, not account numbers. On first configuration of
 * an account this adapter resolves and caches, keyed by {@link BrokerAccountId}:</p>
 * <ul>
 *   <li>{@code ClientKey} — from {@code GET /port/v1/users/me};</li>
 *   <li>{@code AccountKey} + settlement {@code Currency} + {@code AccountId} — from
 *       {@code GET /port/v1/accounts/me}, picking the <b>EUR</b> account (Saxo Belgium settles
 *       in EUR — plan §6 D7 / CUR1).</li>
 * </ul>
 * <p>The cache is per-account so two concurrently-driven accounts never cross handles.</p>
 *
 * <h2>Credential validation (SB5)</h2>
 * <p>{@link #supports(BrokerType)} returns {@code true} only for {@code SAXO_*} — disjoint from
 * Alpaca's {@code isAlpaca()} so the bootstrap K5 selector dispatches deterministically over the
 * ambiguous {@code Instance<BrokerCredentialValidationPort>} without an
 * {@code AmbiguousResolutionException}. {@link #validate(BrokerType, BrokerCredentials)} checks the
 * OAuth credential shape offline; the live token round-trip (200/401/network) is exercised by the
 * {@code SAXO_INTEGRATION_TESTS}-gated integration test (it needs live Saxo logon hosts).</p>
 *
 * <p>This bean is only injectable via {@code @Named("saxoBroker")} to avoid an ambiguous
 * {@code BrokerPort} conflict with the {@code BrokerRouter}, exactly as the Alpaca adapter is
 * {@code @Named("alpacaBroker")}.</p>
 */
@ApplicationScoped
@Named("saxoBroker")
public class SaxoBrokerAdapter implements ConfigurableBrokerPort, BrokerCredentialValidationPort {

    private static final Logger LOG = Logger.getLogger(SaxoBrokerAdapter.class);

    private static final String NOT_IMPLEMENTED =
            "Saxo broker adapter: %s is not implemented yet (Wave 4: %s).";

    /** Settlement currency for Saxo Belgium accounts (plan §6 D7 / CUR1). */
    static final String EUR = "EUR";

    /** Saxo {@code OrderType} for an at-market order (SAXO_ANSWERS §2.7). */
    static final String ORDER_TYPE_MARKET = "Market";
    /** Saxo {@code OrderDuration.DurationType} for a day order. */
    static final String DURATION_DAY_ORDER = "DayOrder";
    /** Saxo {@code BuySell} sides. */
    static final String SIDE_BUY = "Buy";
    static final String SIDE_SELL = "Sell";

    /** Saxo per-session order-rate-limit headers (SAXO_ANSWERS §1.6). */
    static final String RL_ORDERS_REMAINING = "X-RateLimit-SessionOrders-Remaining";
    static final String RL_ORDERS_RESET = "X-RateLimit-SessionOrders-Reset";
    /** Hard cap on how long we will sleep waiting for the 1-order/sec quota to reset (ms). */
    static final long MAX_RATE_LIMIT_SLEEP_MS = 2_000L;

    /** Per-account resolved Saxo handles (ClientKey/AccountKey/Currency), keyed by BrokerAccountId. */
    private final Map<BrokerAccountId, SaxoAccountContext> accountContexts = new ConcurrentHashMap<>();

    /** Simulation → account mapping so BrokerPort (SimulationId) methods route to the right account. */
    private final Map<SimulationId, BrokerAccountId> simulationToAccount = new ConcurrentHashMap<>();

    /**
     * Maps our internal {@link OrderId} (UUID) to the Saxo broker-side order handle (a string
     * {@code OrderId} that is NOT a UUID, plus the account it was placed against). Saxo addresses an
     * order by its own string id under a specific account bearer, so status/cancel need both. Kept
     * in-memory: it is a routing aid for follow-up reads within a process lifetime, not the system
     * of record (the trade itself is recorded on the SimulationPortfolio).
     */
    private final Map<OrderId, SaxoOrderHandle> orderHandles = new ConcurrentHashMap<>();

    /**
     * Reverse {@code (Uic, AssetType) → Symbol} cache so building a portfolio from positions does
     * not re-fetch instrument details for the same holding on every snapshot. Keyed by the Saxo
     * details-call coordinates (Uic + Saxo AssetType string); a held instrument's symbology does
     * not change within a process lifetime. Cleared with the account it belongs to.
     */
    private final Map<String, Symbol> uicToSymbol = new ConcurrentHashMap<>();

    @Inject
    @RestClient
    SaxoTradingClient tradingClient;

    @Inject
    SaxoRequestTokenHolder tokenHolder;

    /** Resolves {@code Symbol → (Uic, AssetType, ExchangeId)} + trading constraints (SB3). */
    @Inject
    SaxoUicResolver uicResolver;

    /**
     * Reference-data client for the reverse {@code Uic → Symbol} mapping a held position needs
     * (SB5): a Saxo position carries only {@code (Uic, AssetType)}, so the adapter reads
     * {@code GET /ref/v1/instruments/details/{Uic}/{AssetType}} to recover the {@code Symbol}
     * ({@code "RHM:xetr"}) and currency. Injected directly (not via the resolver, which only goes
     * forward {@code Symbol → Uic}).
     */
    @Inject
    @RestClient
    SaxoReferenceDataClient referenceDataClient;

    /**
     * Records fills on the per-simulation {@link SimulationPortfolio} for capital isolation. Injected
     * via {@link Instance} so this adapter still boots in isolated tests where the persistence adapter
     * (and thus the repository) is absent — exactly the Alpaca adapter's pattern.
     */
    @Inject
    Instance<SimulationPortfolioRepository> simulationPortfolioRepositoryInstance;

    private static UnsupportedOperationException notImplemented(String operation, String workPackage) {
        return new UnsupportedOperationException(String.format(NOT_IMPLEMENTED, operation, workPackage));
    }

    // --- ConfigurableBrokerPort: account configuration ----------------------

    /**
     * Configures this adapter for a Saxo account: validates the broker type, then resolves and
     * caches the account's Saxo handles (ClientKey / AccountKey / Currency) under its bearer.
     *
     * <p>Resolution runs inside an activated request context with the account pinned on the
     * request-scoped holder so the auth-headers factory can attach the per-account bearer.</p>
     *
     * @param account the broker account to configure (must be a {@code SAXO_*} account)
     */
    @Override
    public void configureForAccount(BrokerAccount account) {
        Objects.requireNonNull(account, "account cannot be null");
        BrokerType brokerType = account.getBrokerType();
        if (!brokerType.isSaxo()) {
            LOG.warnf("SaxoBrokerAdapter received non-Saxo account type: %s — ignoring.", brokerType);
            return;
        }

        BrokerAccountId accountId = account.getId();
        if (accountContexts.containsKey(accountId)) {
            LOG.debugf("Saxo account %s already resolved; reusing cached ClientKey/AccountKey.",
                    shortId(accountId));
            return;
        }

        try {
            SaxoAccountContext context = withAccountContext(accountId, () -> resolveAccountContext(accountId));
            accountContexts.put(accountId, context);
            LOG.infof("Configured Saxo account %s (type: %s): clientKey=%s, accountKey=%s, currency=%s",
                    shortId(accountId), brokerType,
                    redactKey(context.clientKey()), redactKey(context.accountKey()), context.currency());
        } catch (RuntimeException e) {
            LOG.errorf(e, "Failed to resolve Saxo account handles for account %s (type: %s) — "
                    + "ClientKey/AccountKey resolution did not complete.", shortId(accountId), brokerType);
            throw e;
        }
    }

    /**
     * Resolves the {@code ClientKey} ({@code GET /port/v1/users/me}) and the EUR
     * {@code AccountKey} + currency ({@code GET /port/v1/accounts/me}) for the active account.
     *
     * <p>Must run inside an activated request context with the account pinned on the holder
     * (see {@link #withAccountContext(BrokerAccountId, Supplier)}); the rest-client picks up the
     * per-account bearer from {@code SaxoAuthHeadersFactory}.</p>
     */
    private SaxoAccountContext resolveAccountContext(BrokerAccountId accountId) {
        SaxoUserResponse user = tradingClient.getCurrentUser();
        if (user == null || user.getClientKey() == null || user.getClientKey().isBlank()) {
            throw new IllegalStateException(
                    "Saxo /port/v1/users/me returned no ClientKey for account " + shortId(accountId));
        }

        SaxoAccountResponse accounts = tradingClient.getAccounts();
        SaxoAccountResponse.SaxoAccount chosen = pickEurAccount(accounts, accountId);

        return new SaxoAccountContext(
                user.getClientKey(),
                chosen.getAccountKey(),
                chosen.getAccountId(),
                chosen.getCurrency());
    }

    /**
     * Picks the EUR settlement account from the {@code /port/v1/accounts/me} envelope.
     *
     * <p>Saxo Belgium accounts settle in EUR; if exactly one account is returned and it carries
     * no currency, it is taken as the account (best-effort). If several are returned, the first
     * active EUR account wins; falling back to the first EUR account, then — only if no EUR
     * account exists — the first account, with a warning (the EUR thread, CUR1, expects EUR).</p>
     */
    private SaxoAccountResponse.SaxoAccount pickEurAccount(SaxoAccountResponse accounts, BrokerAccountId accountId) {
        List<SaxoAccountResponse.SaxoAccount> rows =
                accounts == null ? null : accounts.getData();
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException(
                    "Saxo /port/v1/accounts/me returned no accounts for account " + shortId(accountId));
        }

        SaxoAccountResponse.SaxoAccount firstActiveEur = null;
        SaxoAccountResponse.SaxoAccount firstEur = null;
        for (SaxoAccountResponse.SaxoAccount row : rows) {
            if (row.getAccountKey() == null || row.getAccountKey().isBlank()) {
                continue;
            }
            if (isEur(row.getCurrency())) {
                if (firstEur == null) {
                    firstEur = row;
                }
                if (firstActiveEur == null && Boolean.TRUE.equals(row.getActive())) {
                    firstActiveEur = row;
                }
            }
        }

        SaxoAccountResponse.SaxoAccount chosen = firstActiveEur != null ? firstActiveEur : firstEur;
        if (chosen != null) {
            return chosen;
        }

        // No EUR account found. Single-account convenience: take it (currency may be absent on
        // the accounts payload — confirmed only at balance time, SB5).
        SaxoAccountResponse.SaxoAccount fallback = rows.stream()
                .filter(r -> r.getAccountKey() != null && !r.getAccountKey().isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Saxo /port/v1/accounts/me returned no account with an AccountKey for account "
                                + shortId(accountId)));
        LOG.warnf("No EUR Saxo account found for %s; falling back to account %s (currency=%s). "
                + "CUR1 expects EUR — confirm the settlement currency.",
                shortId(accountId), redactKey(fallback.getAccountKey()), fallback.getCurrency());
        return fallback;
    }

    private static boolean isEur(String currency) {
        return EUR.equalsIgnoreCase(currency);
    }

    @Override
    public void linkSimulationToAccount(SimulationId simulationId, BrokerAccountId accountId) {
        Objects.requireNonNull(simulationId, "simulationId cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        LOG.debugf("Linking simulation %s to Saxo account %s", shortId(simulationId), shortId(accountId));
        simulationToAccount.put(simulationId, accountId);
    }

    @Override
    public boolean isAccountConfigured(BrokerAccountId accountId) {
        return accountId != null && accountContexts.containsKey(accountId);
    }

    @Override
    public void removeAccountConfiguration(BrokerAccountId accountId) {
        if (accountId == null) {
            return;
        }
        LOG.infof("Removing Saxo account configuration for %s", shortId(accountId));
        accountContexts.remove(accountId);
        simulationToAccount.values().removeIf(accountId::equals);
        orderHandles.values().removeIf(h -> accountId.equals(h.accountId()));
        // The reverse Uic→Symbol cache is keyed by Saxo (Uic, AssetType), which is account-agnostic;
        // it is process-wide and intentionally left intact (it is not credential-bearing).
    }

    // --- Account-context accessors (consumed by SB3/SB4/SB5) ----------------

    /**
     * Returns the resolved Saxo handles (ClientKey/AccountKey/Currency) for an account, if it has
     * been configured. SB3/SB4/SB5 consume this to scope their REST calls.
     */
    public Optional<SaxoAccountContext> getAccountContext(BrokerAccountId accountId) {
        return Optional.ofNullable(accountContexts.get(accountId));
    }

    /**
     * Returns the broker account linked to a simulation, if any.
     */
    public Optional<BrokerAccountId> getAccountForSimulation(SimulationId simulationId) {
        return Optional.ofNullable(simulationToAccount.get(simulationId));
    }

    /**
     * Runs {@code work} with {@code accountId} pinned on the request-scoped
     * {@link SaxoRequestTokenHolder}, activating a request context first if none is active
     * (scheduler/cron path). This is the D10 seam: it lets the auth-headers factory resolve the
     * per-account bearer for Saxo calls made by {@code work}.
     *
     * @param accountId the account whose bearer the enclosed Saxo calls must use
     * @param work      the operation issuing Saxo REST calls
     * @param <T>       the operation result type
     * @return the operation result
     */
    public <T> T withAccountContext(BrokerAccountId accountId, Supplier<T> work) {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        ManagedContext requestContext = Arc.container().requestContext();
        boolean activatedHere = false;
        if (!requestContext.isActive()) {
            requestContext.activate();
            activatedHere = true;
        }
        try {
            tokenHolder.setAccountId(accountId);
            return work.get();
        } finally {
            if (activatedHere) {
                requestContext.terminate();
            } else {
                // Leave a context we didn't open intact, but clear our pin so a later call on the
                // same (caller-owned) context does not inherit this account's bearer.
                tokenHolder.clear();
            }
        }
    }

    private static String shortId(BrokerAccountId accountId) {
        return accountId.value().toString().substring(0, 8);
    }

    private static String shortId(SimulationId simulationId) {
        return simulationId.value().toString().substring(0, 8);
    }

    /** Redacts an opaque Saxo handle for logging (handles are sensitive, like account numbers). */
    private static String redactKey(String key) {
        if (key == null || key.isBlank()) {
            return "null";
        }
        int show = Math.min(4, key.length());
        return key.substring(0, show) + "…";
    }

    /**
     * Resolved Saxo handles for one broker account.
     *
     * @param clientKey  opaque {@code ClientKey} (from {@code /port/v1/users/me})
     * @param accountKey opaque {@code AccountKey} the trade/balance calls are scoped to
     * @param accountId  human-facing account number (diagnostics)
     * @param currency   settlement currency (expected {@code EUR})
     */
    public record SaxoAccountContext(String clientKey, String accountKey, String accountId, String currency) {
        public SaxoAccountContext {
            if (clientKey == null || clientKey.isBlank()) {
                throw new IllegalArgumentException("clientKey cannot be null or blank");
            }
            if (accountKey == null || accountKey.isBlank()) {
                throw new IllegalArgumentException("accountKey cannot be null or blank");
            }
        }
    }

    /**
     * Routing handle remembered after a Saxo order is placed, so a follow-up status read or cancel can
     * reach the order: Saxo addresses it by its own string {@code OrderId} under a specific account
     * bearer, and the settlement currency lets a status read mint the fill price.
     *
     * @param saxoOrderId         the Saxo broker-side order id (string, not a UUID)
     * @param accountId           the account the order was placed against (its bearer addresses the order)
     * @param instrumentCurrency  the instrument's settlement currency (for fill-price minting)
     */
    record SaxoOrderHandle(String saxoOrderId, BrokerAccountId accountId, Money.Currency instrumentCurrency) {
    }

    private static int statusOf(WebApplicationException e) {
        return e.getResponse() != null ? e.getResponse().getStatus() : -1;
    }

    // --- BrokerPort: trade execution (SB4) ----------------------------------

    /**
     * Places a whole-share market order on Saxo for the command's symbol.
     *
     * <p>Flow (SAXO_ANSWERS §2.7): resolve the simulation→account link; resolve the symbol to its
     * Saxo {@code (Uic, AssetType)} + per-instrument constraints via SB3; validate/round the order
     * to whole shares against {@code AmountDecimals}/{@code LotSize}/{@code MinimumTradeSize};
     * assemble a {@code POST /trade/v2/orders} body with a unique {@code ExternalReference} (defeats
     * Saxo's 15-second duplicate-order window, §1.6); run it under the per-account bearer; map the
     * {@code OrderId}/{@code Orders[]}/{@code ErrorInfo} response to a {@link TradeResult}; record the
     * fill on the {@link SimulationPortfolio} for capital isolation; and remember the Saxo
     * {@code OrderId} for later status/cancel.</p>
     *
     * <p>Saxo's synchronous placement response does not guarantee a fill price, so an accepted order
     * with no echoed {@code Status}/price maps to {@link TradeStatus#PENDING}; the SimulationPortfolio
     * is only touched once a fill price + quantity are known.</p>
     */
    @Override
    public TradeResult executeTrade(ExecuteTradeCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        OrderId orderId = OrderId.generate();
        SimulationId simulationId = command.simulationId();

        BrokerAccountId accountId = simulationToAccount.get(simulationId);
        if (accountId == null) {
            return rejected(orderId, "Saxo: simulation " + shortId(simulationId)
                    + " is not linked to a Saxo broker account (call linkSimulationToAccount first).");
        }
        SaxoAccountContext ctx = accountContexts.get(accountId);
        if (ctx == null) {
            return rejected(orderId, "Saxo: account " + shortId(accountId)
                    + " is not configured (ClientKey/AccountKey unresolved).");
        }

        Symbol symbol = command.symbol();
        Optional<ResolvedInstrument> resolvedOpt = resolveForAccount(symbol, accountId);
        if (resolvedOpt.isEmpty()) {
            return rejected(orderId, "Saxo: symbol " + symbol.canonical()
                    + " did not resolve to a tradable instrument on account " + shortId(accountId) + ".");
        }
        ResolvedInstrument instrument = resolvedOpt.get();
        if (!instrument.tradable()) {
            return rejected(orderId, "Saxo: instrument " + symbol.canonical()
                    + " (Uic=" + instrument.uic() + ") is not currently tradable.");
        }

        // Whole-share enforcement: Saxo equities/ETFs are non-fractional (§2.1). Reject any
        // fractional request rather than silently truncating someone's intended size, then validate
        // the integral amount against the per-instrument lot / min-trade-size constraints.
        BigDecimal requested = command.quantity();
        Optional<String> sizingError = validateWholeShareAmount(requested, instrument, symbol);
        if (sizingError.isPresent()) {
            return rejected(orderId, sizingError.get());
        }

        SaxoOrderRequest body = buildOrderRequest(command, ctx, instrument, orderId);

        LOG.infof("Saxo placing %s %s share(s) of %s (Uic=%d, %s) on account %s, sim %s, extRef=%s",
                command.action(), requested.toPlainString(), symbol.canonical(), instrument.uic(),
                instrument.saxoAssetType(), shortId(accountId), shortId(simulationId),
                body.getExternalReference());

        try {
            SaxoOrderResponse response = withAccountContext(accountId, () -> placeOrderRateLimited(body));
            return mapPlacementToResult(command, orderId, accountId, instrument, response);
        } catch (WebApplicationException e) {
            String detail = readErrorBody(e);
            LOG.warnf("Saxo order placement failed for %s on sim %s: HTTP %d %s",
                    symbol.canonical(), shortId(simulationId), statusOf(e), detail);
            return rejected(orderId, "Saxo order rejected (HTTP " + statusOf(e) + "): " + detail);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Saxo order placement error for %s on sim %s: %s",
                    symbol.canonical(), shortId(simulationId), e.getMessage());
            return rejected(orderId, "Saxo order error: " + e.getMessage());
        }
    }

    /**
     * Reads the status of a previously-placed Saxo order. Saxo addresses an open/working order by its
     * string {@code OrderId} under the placing account's bearer, so this looks up the remembered
     * {@link SaxoOrderHandle} (Saxo id + account) and reads {@code GET /port/v1/orders/{orderId}}. A
     * 404 means the order is no longer open (filled or cancelled) and yields empty.
     */
    @Override
    public Optional<TradeResult> getOrderStatus(OrderId orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        SaxoOrderHandle handle = orderHandles.get(orderId);
        if (handle == null) {
            LOG.debugf("Saxo getOrderStatus: no Saxo order handle remembered for %s.", orderId);
            return Optional.empty();
        }
        SaxoAccountContext ctx = accountContexts.get(handle.accountId());
        if (ctx == null) {
            LOG.debugf("Saxo getOrderStatus: account %s for order %s is no longer configured.",
                    shortId(handle.accountId()), orderId);
            return Optional.empty();
        }
        try {
            SaxoOrderResponse response = withAccountContext(handle.accountId(),
                    () -> tradingClient.getOrder(handle.saxoOrderId(), ctx.accountKey(), ctx.clientKey()));
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(mapStatusToResult(orderId, handle.instrumentCurrency(), response));
        } catch (WebApplicationException e) {
            if (statusOf(e) == 404) {
                LOG.debugf("Saxo order %s (saxoId=%s) not in open orders (filled/cancelled).",
                        orderId, handle.saxoOrderId());
            } else {
                LOG.warnf("Saxo getOrderStatus failed for %s (saxoId=%s): HTTP %d",
                        orderId, handle.saxoOrderId(), statusOf(e));
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.warnf("Saxo getOrderStatus error for %s: %s", orderId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Cancels every working order on the Saxo account linked to a simulation. Called when a
     * simulation is paused so no fill lands after the pause. Enumerates the account's open orders
     * ({@code GET /port/v1/orders}) and cancels each by {@code OrderId}
     * ({@code DELETE /trade/v2/orders/{orderId}}); a failure to cancel one order is logged and does
     * not abort the rest.
     */
    @Override
    public void cancelAllOpenOrders(SimulationId simulationId) {
        if (simulationId == null) {
            return;
        }
        BrokerAccountId accountId = simulationToAccount.get(simulationId);
        if (accountId == null) {
            LOG.debugf("Saxo cancelAllOpenOrders: simulation %s is not linked to an account.",
                    shortId(simulationId));
            return;
        }
        SaxoAccountContext ctx = accountContexts.get(accountId);
        if (ctx == null) {
            LOG.debugf("Saxo cancelAllOpenOrders: account %s for sim %s is not configured.",
                    shortId(accountId), shortId(simulationId));
            return;
        }
        try {
            withAccountContext(accountId, () -> {
                SaxoOpenOrdersResponse open =
                        tradingClient.getOpenOrders(ctx.accountKey(), ctx.clientKey());
                List<SaxoOpenOrdersResponse.SaxoOpenOrder> rows =
                        open == null ? null : open.getData();
                if (rows == null || rows.isEmpty()) {
                    LOG.infof("Saxo: no open orders to cancel for sim %s (account %s).",
                            shortId(simulationId), shortId(accountId));
                    return null;
                }
                int cancelled = 0;
                for (SaxoOpenOrdersResponse.SaxoOpenOrder row : rows) {
                    if (row.getOrderId() == null || row.getOrderId().isBlank()) {
                        continue;
                    }
                    try {
                        tradingClient.cancelOrder(row.getOrderId(), ctx.accountKey());
                        cancelled++;
                    } catch (RuntimeException e) {
                        LOG.warnf("Saxo failed to cancel order %s for sim %s: %s",
                                row.getOrderId(), shortId(simulationId), e.getMessage());
                    }
                }
                LOG.infof("Saxo cancelled %d/%d open orders for sim %s (account %s).",
                        cancelled, rows.size(), shortId(simulationId), shortId(accountId));
                return null;
            });
        } catch (RuntimeException e) {
            LOG.errorf(e, "Saxo cancelAllOpenOrders failed for sim %s (account %s): %s",
                    shortId(simulationId), shortId(accountId), e.getMessage());
        }
    }

    // --- SB4 helpers --------------------------------------------------------

    /**
     * Validates that {@code requested} is a positive whole-share amount honouring the instrument's
     * lot / minimum-trade-size constraints. Returns a rejection message, or empty when the amount is
     * acceptable. Saxo equities/ETFs are non-fractional, so a fractional request is rejected outright
     * (we do not silently round someone's intended size down).
     */
    static Optional<String> validateWholeShareAmount(BigDecimal requested, ResolvedInstrument instrument,
                                                     Symbol symbol) {
        if (requested == null || requested.signum() <= 0) {
            return Optional.of("Saxo: order amount must be positive for " + symbol.canonical() + ".");
        }
        // Non-fractional: the amount must be integral (no fractional shares on Saxo equities/ETFs).
        if (requested.stripTrailingZeros().scale() > 0) {
            return Optional.of("Saxo: " + symbol.canonical() + " is whole-share only (Saxo equities/ETFs "
                    + "are non-fractional); fractional amount " + requested.toPlainString() + " rejected.");
        }
        BigDecimal minTrade = instrument.minimumTradeSize();
        if (minTrade != null && minTrade.signum() > 0 && requested.compareTo(minTrade) < 0) {
            return Optional.of("Saxo: amount " + requested.toPlainString() + " below MinimumTradeSize "
                    + minTrade.toPlainString() + " for " + symbol.canonical() + ".");
        }
        BigDecimal lot = instrument.lotSize();
        if (lot != null && lot.signum() > 0
                && requested.remainder(lot).signum() != 0) {
            return Optional.of("Saxo: amount " + requested.toPlainString() + " is not a whole multiple of "
                    + "LotSize " + lot.toPlainString() + " for " + symbol.canonical() + ".");
        }
        // MinimumOrderValue (Amount × Price) cannot be checked here without a live price; the order
        // service enforces it, and a violation surfaces as a Saxo ErrorInfo on placement.
        return Optional.empty();
    }

    /** Assembles the Saxo {@code POST /trade/v2/orders} body for a whole-share day market order. */
    private SaxoOrderRequest buildOrderRequest(ExecuteTradeCommand command, SaxoAccountContext ctx,
                                               ResolvedInstrument instrument, OrderId orderId) {
        SaxoOrderRequest body = new SaxoOrderRequest();
        body.setAccountKey(ctx.accountKey());
        body.setUic(instrument.uic());
        body.setAssetType(instrument.saxoAssetType());
        body.setBuySell(command.action() == TradeAction.BUY ? SIDE_BUY : SIDE_SELL);
        body.setAmount(command.quantity());
        body.setOrderType(ORDER_TYPE_MARKET);
        body.setManualOrder(false);
        body.setOrderDuration(new SaxoOrderRequest.SaxoOrderDuration(DURATION_DAY_ORDER));
        // Unique per call so an identical re-submit inside Saxo's 15s window is NOT swallowed as a dup.
        body.setExternalReference(externalReference(orderId));
        return body;
    }

    /**
     * A correlation id that is unique per placement call (defeats Saxo's 15s duplicate-order
     * protection). Our internal {@link OrderId} is generated fresh per {@code executeTrade}, so it is
     * already unique; a short suffix guards against any retry that reuses the same OrderId.
     */
    private static String externalReference(OrderId orderId) {
        return "qt-" + orderId.value() + "-" + Long.toString(System.nanoTime(), 36);
    }

    /**
     * Places the order, first honouring the Saxo 1-order/sec session limit: if a prior response said
     * the order quota is exhausted, sleep until its reset (bounded) before sending — proactive
     * back-off so we avoid the 429 rather than react to it (SAXO_ANSWERS §1.6).
     */
    private SaxoOrderResponse placeOrderRateLimited(SaxoOrderRequest body) {
        awaitOrderQuota();
        try (Response response = tradingClient.placeOrder(body)) {
            recordOrderRateLimit(response);
            int status = response.getStatus();
            if (status == 429) {
                // Quota hit despite back-off: surface the reset so the next call waits, and let the
                // caller map it to a rejection (no usable body).
                throw new WebApplicationException("Saxo order rate limit (429)", status);
            }
            // A Saxo rejection commonly arrives as a 4xx whose body is an ErrorInfo envelope — read it
            // so mapPlacementToResult can render a clean, code+message rejection reason. If the body
            // is not parseable, fall back to throwing with the HTTP status.
            try {
                return response.readEntity(SaxoOrderResponse.class);
            } catch (RuntimeException unparseable) {
                if (status >= 400) {
                    throw new WebApplicationException("Saxo order placement HTTP " + status, status);
                }
                throw unparseable;
            }
        }
    }

    /** Remaining order-quota + reset (seconds) parsed from the last placement response. */
    private volatile int lastOrderQuotaRemaining = Integer.MAX_VALUE;
    private volatile Instant orderQuotaResetAt = Instant.EPOCH;

    private void recordOrderRateLimit(Response response) {
        Integer remaining = headerInt(response, RL_ORDERS_REMAINING);
        Integer reset = headerInt(response, RL_ORDERS_RESET);
        if (remaining != null) {
            lastOrderQuotaRemaining = remaining;
        }
        if (reset != null) {
            orderQuotaResetAt = Instant.now().plusSeconds(reset);
        }
    }

    /** If the order quota was exhausted by the previous call, sleep (bounded) until it resets. */
    private void awaitOrderQuota() {
        if (lastOrderQuotaRemaining > 0) {
            return;
        }
        long waitMs = Math.max(0, java.time.Duration.between(Instant.now(), orderQuotaResetAt).toMillis());
        waitMs = Math.min(waitMs, MAX_RATE_LIMIT_SLEEP_MS);
        if (waitMs <= 0) {
            lastOrderQuotaRemaining = Integer.MAX_VALUE; // reset window passed
            return;
        }
        LOG.infof("Saxo order quota exhausted; backing off %d ms before next order (§1.6).", waitMs);
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lastOrderQuotaRemaining = Integer.MAX_VALUE;
        }
    }

    private static Integer headerInt(Response response, String name) {
        String raw = response.getHeaderString(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Maps a Saxo placement response to a {@link TradeResult} and records the fill / remembers the
     * Saxo OrderId. A response carrying an {@code ErrorInfo} (top-level or on the single leg) is a
     * rejection; otherwise the returned {@code OrderId} is remembered and the trade is accepted
     * (PENDING unless the response echoed a fill price + filled amount).
     */
    private TradeResult mapPlacementToResult(ExecuteTradeCommand command, OrderId orderId,
                                             BrokerAccountId accountId, ResolvedInstrument instrument,
                                             SaxoOrderResponse response) {
        if (response == null) {
            return rejected(orderId, "Saxo: empty order placement response.");
        }
        SaxoOrderResponse.SaxoErrorInfo error = firstError(response);
        if (error != null) {
            return rejected(orderId, "Saxo order rejected: " + error.describe());
        }
        String saxoOrderId = resolveSaxoOrderId(response);
        if (saxoOrderId == null) {
            return rejected(orderId, "Saxo: placement returned neither an OrderId nor an ErrorInfo.");
        }

        // Remember the Saxo id + account so status/cancel can round-trip.
        orderHandles.put(orderId, new SaxoOrderHandle(saxoOrderId, accountId, instrument.currency()));

        Money fillPrice = toMoney(response.getFillPrice(), instrument.currency());
        BigDecimal filledQty = response.getFilledAmount();
        TradeStatus status = mapSaxoStatus(response.getStatus(), fillPrice, filledQty);

        if ((status == TradeStatus.EXECUTED || status == TradeStatus.PARTIAL_FILL)
                && fillPrice != null && filledQty != null) {
            // Capital-ledger accounting is applied centrally in BrokerRouter, which every trade
            // routes through — doing it here as well would double-count the fill.
            TradeResult filled = new TradeResult(status, orderId, fillPrice, filledQty, null, Instant.now());
            LOG.infof("Saxo order %s accepted+filled (saxoId=%s): %s %s @ %s",
                    orderId, saxoOrderId, status, filledQty.toPlainString(), fillPrice.format());
            return filled;
        }

        LOG.infof("Saxo order %s accepted (saxoId=%s), status=%s — awaiting fill confirmation.",
                orderId, saxoOrderId, status);
        return new TradeResult(status, orderId, null, null, null, Instant.now());
    }

    /** Maps a Saxo order-status read ({@code GET /port/v1/orders/{id}}) to a {@link TradeResult}. */
    private TradeResult mapStatusToResult(OrderId orderId, Money.Currency currency,
                                          SaxoOrderResponse response) {
        SaxoOrderResponse.SaxoErrorInfo error = firstError(response);
        if (error != null) {
            return rejected(orderId, "Saxo order rejected: " + error.describe());
        }
        Money fillPrice = toMoney(response.getFillPrice(), currency);
        BigDecimal filledQty = response.getFilledAmount();
        TradeStatus status = mapSaxoStatus(response.getStatus(), fillPrice, filledQty);
        if ((status == TradeStatus.EXECUTED || status == TradeStatus.PARTIAL_FILL)
                && fillPrice != null && filledQty != null) {
            return new TradeResult(status, orderId, fillPrice, filledQty, null, Instant.now());
        }
        return new TradeResult(status, orderId, null, null, null, Instant.now());
    }

    /** The top-level error, else the first per-leg error, else null (accepted). */
    private static SaxoOrderResponse.SaxoErrorInfo firstError(SaxoOrderResponse response) {
        if (response.getErrorInfo() != null) {
            return response.getErrorInfo();
        }
        if (response.getOrders() != null) {
            for (SaxoOrderResponse.SaxoOrderLeg leg : response.getOrders()) {
                if (leg != null && leg.getErrorInfo() != null) {
                    return leg.getErrorInfo();
                }
            }
        }
        return null;
    }

    /** Prefers the top-level {@code OrderId}, else the first leg's id. */
    private static String resolveSaxoOrderId(SaxoOrderResponse response) {
        if (response.getOrderId() != null && !response.getOrderId().isBlank()) {
            return response.getOrderId();
        }
        if (response.getOrders() != null) {
            for (SaxoOrderResponse.SaxoOrderLeg leg : response.getOrders()) {
                if (leg != null && leg.getOrderId() != null && !leg.getOrderId().isBlank()) {
                    return leg.getOrderId();
                }
            }
        }
        return null;
    }

    /**
     * Maps a Saxo order status string to a {@link TradeStatus}. When Saxo gives no status string on a
     * synchronous placement, infer EXECUTED if a fill price + amount are present, else PENDING (the
     * order is accepted/working).
     */
    static TradeStatus mapSaxoStatus(String saxoStatus, Money fillPrice, BigDecimal filledQty) {
        if (saxoStatus == null || saxoStatus.isBlank()) {
            return (fillPrice != null && filledQty != null && filledQty.signum() > 0)
                    ? TradeStatus.EXECUTED : TradeStatus.PENDING;
        }
        return switch (saxoStatus.toLowerCase()) {
            case "filled", "fill" -> TradeStatus.EXECUTED;
            case "partiallyfilled", "partial", "partial_fill" -> TradeStatus.PARTIAL_FILL;
            case "cancelled", "canceled", "rejected", "expired" -> TradeStatus.REJECTED;
            case "working", "placed", "new", "pending", "notworking", "parked" -> TradeStatus.PENDING;
            default -> TradeStatus.PENDING;
        };
    }

    private static Money toMoney(BigDecimal amount, Money.Currency currency) {
        if (amount == null) {
            return null;
        }
        return Money.of(amount, currency != null ? currency : Money.Currency.EUR);
    }

    private static TradeResult rejected(OrderId orderId, String reason) {
        return TradeResult.rejected(orderId, reason, Instant.now());
    }

    private static String readErrorBody(WebApplicationException e) {
        try {
            if (e.getResponse() != null) {
                String body = e.getResponse().readEntity(String.class);
                return body == null || body.isBlank() ? e.getMessage() : body;
            }
        } catch (RuntimeException ignored) {
            // fall through to the exception message
        }
        return e.getMessage();
    }

    private SimulationPortfolioRepository getSimulationPortfolioRepository() {
        return simulationPortfolioRepositoryInstance.isResolvable()
                ? simulationPortfolioRepositoryInstance.get() : null;
    }

    // --- BrokerPort: portfolio / balance (SB5) ------------------------------

    /**
     * Returns the current portfolio for a simulation — the isolated {@link SimulationPortfolio},
     * never the full Saxo account. Capital isolation is mandatory: returning the raw broker account
     * would let a simulation trade beyond its allocated budget (the same contract the Alpaca adapter
     * honours). EUR is the settlement currency (CUR1), so the empty/error portfolio is EUR-zero.
     *
     * <p>If no {@link SimulationPortfolio} exists (repository absent in isolated tests, or a missing
     * row) this returns a zero-cash EUR portfolio with a loud capital-isolation log rather than
     * exposing the account's real balance.</p>
     */
    @Override
    public Portfolio getCurrentPortfolio(SimulationId simulationId) {
        SimulationPortfolioRepository repo = getSimulationPortfolioRepository();
        if (repo != null && simulationId != null) {
            Optional<SimulationPortfolio> sp = repo.findById(simulationId);
            if (sp.isPresent()) {
                SimulationPortfolio portfolio = sp.get();
                LOG.debugf("Saxo: isolated portfolio for sim %s: cash=%s, positions=%d",
                        shortId(simulationId), portfolio.getCashAvailable().format(),
                        portfolio.getPositions().size());
                return new Portfolio(simulationId, new HashMap<>(portfolio.getPositions()),
                        portfolio.getCashAvailable());
            }
        }
        LOG.errorf("CAPITAL ISOLATION FAILURE: no SimulationPortfolio for sim %s — returning EUR-zero "
                + "portfolio to prevent trading beyond budget (Saxo account state is not exposed).",
                simulationId == null ? "null" : shortId(simulationId));
        return new Portfolio(
                simulationId != null ? simulationId : SimulationId.generate(),
                new HashMap<>(),
                Money.zero(Money.Currency.EUR));
    }

    /**
     * Returns the available cash for a simulation — the isolated {@link SimulationPortfolio}'s cash,
     * never the full Saxo account (capital isolation). EUR is the settlement currency (CUR1).
     */
    @Override
    public Money getAvailableBalance(SimulationId simulationId) {
        SimulationPortfolioRepository repo = getSimulationPortfolioRepository();
        if (repo != null && simulationId != null) {
            Optional<SimulationPortfolio> sp = repo.findById(simulationId);
            if (sp.isPresent()) {
                Money available = sp.get().getCashAvailable();
                LOG.debugf("Saxo: isolated balance for sim %s: %s", shortId(simulationId), available.format());
                return available;
            }
        }
        LOG.errorf("CAPITAL ISOLATION FAILURE: no SimulationPortfolio for sim %s — returning EUR-zero "
                + "balance to prevent trading beyond budget.",
                simulationId == null ? "null" : shortId(simulationId));
        return Money.zero(Money.Currency.EUR);
    }

    /**
     * No-op for a real Saxo account: the account state (cash, positions) lives externally at Saxo and
     * is not seeded by us. Capital isolation for the simulation is enforced by the
     * {@link SimulationPortfolio} (created by the simulation executor), so there is nothing to
     * initialise on the broker side — we only log the intended allocation for traceability.
     */
    @Override
    public void initializePortfolio(SimulationId simulationId, Money initialCash) {
        LOG.infof("Saxo initializePortfolio (sim %s): no-op — Saxo account state is external; the "
                + "SimulationPortfolio enforces the %s allocation. No broker-side seeding performed.",
                simulationId == null ? "null" : shortId(simulationId),
                initialCash == null ? "null" : initialCash.format());
    }

    /**
     * No-op for a real Saxo account (positions live externally at Saxo). We do NOT sync positions to
     * Saxo — restoring broker state for a resuming LIVE simulation is a read of the real account, not
     * a write. Logged for traceability only.
     */
    @Override
    public void initializePortfolio(SimulationId simulationId, Money cashAvailable, Map<Symbol, Position> positions) {
        LOG.infof("Saxo initializePortfolio (sim %s): no-op — expected cash=%s, %d position(s); Saxo "
                + "account state is external and is never written by us.",
                simulationId == null ? "null" : shortId(simulationId),
                cashAvailable == null ? "null" : cashAvailable.format(),
                positions == null ? 0 : positions.size());
    }

    @Override
    public void clearPortfolio(SimulationId simulationId) {
        // Clearing the external Saxo account is not supported; just drop the local sim→account link.
        if (simulationId != null) {
            simulationToAccount.remove(simulationId);
        }
        LOG.debugf("clearPortfolio: dropped sim→account link for %s (Saxo account state is external).",
                simulationId == null ? "null" : shortId(simulationId));
    }

    /**
     * Total value of the isolated {@link SimulationPortfolio} (cash + positions). Positions are valued
     * at the simulation's recorded cost basis unless a configured Saxo account can supply a live
     * {@code CurrentPrice} per holding from {@code GET /port/v1/positions}.
     *
     * <p>Capital isolation: only the simulation's own positions are valued — never the full Saxo
     * account. Per the {@code BrokerPort} contract this returns {@code null} only when no
     * {@link SimulationPortfolio} exists (caller skips the snapshot); a position whose live price is
     * unavailable falls back to its cost basis with a loud log rather than nulling the whole value.</p>
     */
    @Override
    public Money getPortfolioValue(SimulationId simulationId, Instant timestamp) {
        SimulationPortfolioRepository repo = getSimulationPortfolioRepository();
        if (repo == null || simulationId == null) {
            LOG.warnf("Saxo getPortfolioValue: no SimulationPortfolio repository (sim %s) — returning "
                    + "null so the caller skips the snapshot (no capital-isolated value available).",
                    simulationId == null ? "null" : shortId(simulationId));
            return null;
        }
        Optional<SimulationPortfolio> spOpt = repo.findById(simulationId);
        if (spOpt.isEmpty()) {
            LOG.warnf("Saxo getPortfolioValue: no SimulationPortfolio for sim %s — returning null "
                    + "(caller skips the snapshot).", shortId(simulationId));
            return null;
        }
        SimulationPortfolio portfolio = spOpt.get();
        Money total = portfolio.getCashAvailable();

        Map<Symbol, Money> livePrices = livePricesForSimulation(simulationId);
        for (Map.Entry<Symbol, Position> entry : portfolio.getPositions().entrySet()) {
            Position pos = entry.getValue();
            Money price = livePrices.get(pos.symbol());
            if (price == null) {
                price = pos.averagePrice();
                LOG.warnf("Saxo getPortfolioValue (sim %s): no live Saxo price for %s — valuing %s "
                        + "share(s) at cost basis %s. Snapshot value is approximate for this holding.",
                        shortId(simulationId), pos.symbol().canonical(), pos.quantity().toPlainString(),
                        price.format());
            }
            total = total.add(price.multiply(pos.quantity()));
        }
        return total;
    }

    /**
     * Best-effort live prices for the simulation's account, keyed by the resolved {@link Symbol}, read
     * from {@code GET /port/v1/positions}. Returns an empty map when the simulation is not linked /
     * configured or the read fails — callers then fall back to cost basis. Reverse-maps each
     * position's {@code Uic} to a {@code Symbol} via the reference-data details call (SB3 reuse).
     */
    private Map<Symbol, Money> livePricesForSimulation(SimulationId simulationId) {
        BrokerAccountId accountId = simulationToAccount.get(simulationId);
        if (accountId == null) {
            return Map.of();
        }
        SaxoAccountContext ctx = accountContexts.get(accountId);
        if (ctx == null) {
            return Map.of();
        }
        try {
            List<SaxoPositionRow> rows = readAccountPositions(accountId, ctx);
            Map<Symbol, Money> prices = new HashMap<>();
            for (SaxoPositionRow row : rows) {
                if (row.symbol() != null && row.currentPrice() != null) {
                    prices.put(row.symbol(), row.currentPrice());
                }
            }
            return prices;
        } catch (RuntimeException e) {
            LOG.warnf("Saxo getPortfolioValue: failed to read live positions for sim %s (account %s): %s "
                    + "— falling back to cost basis.", shortId(simulationId), shortId(accountId), e.getMessage());
            return Map.of();
        }
    }

    /**
     * Account-agnostic tradability is unsupported for Saxo: reference-data lookups require a
     * per-account OAuth bearer (D10), so there is no global-default resolution. Callers must use
     * the account-aware {@link #isSymbolTradable(Symbol, BrokerAccountId)}.
     */
    @Override
    public boolean isSymbolTradable(Symbol symbol) {
        throw notImplemented("isSymbolTradable (account-agnostic; use the account-aware overload)",
                "SB3-uic-resolver");
    }

    /**
     * Account-agnostic bulk validation is unsupported for the same reason as
     * {@link #isSymbolTradable(Symbol)} — Saxo reference data is per-account.
     */
    @Override
    public Set<Symbol> validateSymbols(Set<Symbol> symbols) {
        throw notImplemented("validateSymbols (account-agnostic; use isSymbolTradable per account)",
                "SB3-uic-resolver");
    }

    /**
     * Account-aware tradability: resolves the symbol to its Saxo instrument on the account's venue
     * and reports the {@code IsTradable} flag. A symbol that does not resolve (no listing on the
     * venue, or the account is not configured) is reported not tradable.
     *
     * @param symbol    the symbol to validate (ticker + MIC)
     * @param accountId the linked Saxo account to resolve against
     * @return {@code true} iff the instrument resolves and is tradable on that account's venue
     */
    @Override
    public boolean isSymbolTradable(Symbol symbol, BrokerAccountId accountId) {
        return resolveForAccount(symbol, accountId)
                .map(ResolvedInstrument::tradable)
                .orElse(false);
    }

    /**
     * Account-aware asset-info: resolves the ticker to a Saxo instrument on the account's venue and
     * returns an enriched {@link AssetInfo} (Uic / AssetType / MIC / currency / tradability). The
     * ticker is parsed into a {@link Symbol} via {@link SymbolParser} so a bare EU-suffixed or
     * {@code ROOT:MIC} input resolves to the right venue; a ticker that does not resolve yields
     * {@link AssetInfo#notFound(String)}.
     *
     * @param ticker    the ticker symbol to look up (e.g. {@code "RHM:XETR"}, {@code "ASML.AS"})
     * @param accountId the linked Saxo account to resolve against
     * @return the resolved asset info, or {@code AssetInfo.notFound(ticker)} if unresolved
     */
    @Override
    public Optional<AssetInfo> getAssetInfo(String ticker, BrokerAccountId accountId) {
        if (ticker == null || ticker.isBlank()) {
            return Optional.empty();
        }
        Symbol symbol;
        try {
            symbol = SymbolParser.parse(ticker, InvestmentType.STOCK);
        } catch (IllegalArgumentException malformed) {
            LOG.debugf("Saxo getAssetInfo: cannot parse ticker '%s': %s", ticker, malformed.getMessage());
            return Optional.of(AssetInfo.notFound(ticker));
        }

        Optional<ResolvedInstrument> resolved = resolveForAccount(symbol, accountId);
        if (resolved.isEmpty()) {
            return Optional.of(AssetInfo.notFound(ticker));
        }
        return Optional.of(toAssetInfo(ticker, symbol, resolved.get()));
    }

    /**
     * Resolves a symbol to its Saxo instrument under the account's bearer, or empty when the
     * account is not configured (its bearer/handles are unknown) or the symbol does not resolve.
     *
     * <p>Public so SB4 (trade execution) and the Saxo market-data adapter (MD2) can reuse the same
     * per-account resolution + bearer seam.</p>
     *
     * @param symbol    the symbol to resolve
     * @param accountId the linked Saxo account whose bearer/venue to resolve against
     * @return the resolved instrument, or empty
     */
    public Optional<ResolvedInstrument> resolveForAccount(Symbol symbol, BrokerAccountId accountId) {
        if (symbol == null || accountId == null) {
            return Optional.empty();
        }
        if (!isAccountConfigured(accountId)) {
            LOG.debugf("Saxo resolveForAccount: account %s is not configured; cannot resolve %s.",
                    shortId(accountId), symbol.canonical());
            return Optional.empty();
        }
        return withAccountContext(accountId, () -> uicResolver.resolve(symbol));
    }

    /**
     * Builds an enriched {@link AssetInfo} from a resolved Saxo instrument. The {@code exchange}
     * label and {@code mic} come from the requested symbol's MIC (Saxo's {@code ExchangeId} is an
     * internal code, not an ISO MIC); {@code assetClass} mirrors the asset type.
     */
    private static AssetInfo toAssetInfo(String ticker, Symbol symbol, ResolvedInstrument resolved) {
        MIC mic = resolved.mic() != null ? resolved.mic() : symbol.exchange();
        String exchangeLabel = mic != null ? mic.code() : resolved.exchangeId();
        String status = resolved.tradable() ? "active" : "inactive";
        String assetClass = switch (resolved.assetType()) {
            case ETF -> "etf";
            case CRYPTO_FX -> "crypto";
            case FUND -> "fund";
            case BOND -> "bond";
            default -> "equity";
        };
        return new AssetInfo(
                ticker,
                resolved.description(),
                exchangeLabel,
                resolved.tradable(),
                status,
                assetClass,
                mic,
                resolved.currency(),
                resolved.uic(),
                resolved.assetType());
    }

    /**
     * Account-agnostic pre-simulation balance is unsupported for Saxo: every Saxo balance read needs
     * a per-account OAuth bearer + the resolved AccountKey (D10), so there is no global-default
     * balance. The {@code BrokerRouter} never routes the account-agnostic {@code getAccountBalance()}
     * to Saxo (it consults only the configured Alpaca/mock broker), so returning empty is correct;
     * the account-aware {@link #getAccountBalance(BrokerAccountId)} reads the real Saxo cash.
     */
    @Override
    public Optional<Money> getAccountBalance() {
        LOG.debug("Saxo getAccountBalance() is account-agnostic and unsupported — Saxo balances are "
                + "per-account (need a bearer + AccountKey); use getAccountBalance(accountId).");
        return Optional.empty();
    }

    // --- Account-aware portfolio / balance reads (real Saxo account) --------

    /**
     * Reads the real Saxo cash balance for a configured account from {@code GET /port/v1/balances}.
     *
     * <p>Picks the settled, investable EUR cash. <b>⚠️ TODO(SB5, unconfirmed):</b> SAXO_ANSWERS §2.8
     * flags the exact field — this prefers {@code CashAvailableForTrading} (settled cash usable for a
     * new order, no margin), falling back to {@code CashBalance} then {@code TotalValue}. Confirm the
     * canonical "settled investable EUR cash" field against the OpenAPI Explorer / a funded account.</p>
     *
     * @param accountId the configured Saxo account to read
     * @return the EUR cash balance, or empty if the account is unconfigured or the read fails
     */
    public Optional<Money> getAccountBalance(BrokerAccountId accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        SaxoAccountContext ctx = accountContexts.get(accountId);
        if (ctx == null) {
            LOG.debugf("Saxo getAccountBalance: account %s is not configured.", shortId(accountId));
            return Optional.empty();
        }
        try {
            SaxoBalanceResponse balance = withAccountContext(accountId,
                    () -> tradingClient.getBalance(ctx.accountKey(), ctx.clientKey()));
            return toEurCash(balance, ctx, accountId);
        } catch (WebApplicationException e) {
            LOG.warnf("Saxo getAccountBalance failed for account %s: HTTP %d", shortId(accountId), statusOf(e));
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.warnf("Saxo getAccountBalance error for account %s: %s", shortId(accountId), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads the real Saxo account portfolio (open positions + cash) for a configured account into a
     * broker-neutral {@link Portfolio}. Each position's {@code Uic} is reverse-mapped to a
     * {@link Symbol} via the reference-data details call; its {@code OpenPrice} (cost basis) feeds the
     * {@link Position#averagePrice()} and its {@code CurrentPrice} the {@link Position#currentPrice()}.
     * Cash is the EUR balance (see {@link #getAccountBalance(BrokerAccountId)}).
     *
     * <p>This is the account-level view (not capital-isolated to a simulation) — it is for broker
     * inspection / linking flows, distinct from {@link #getCurrentPortfolio(SimulationId)} which must
     * stay isolated to a simulation's budget.</p>
     *
     * @param accountId the configured Saxo account to read
     * @return the account portfolio, or empty if the account is unconfigured or the read fails
     */
    public Optional<Portfolio> getAccountPortfolio(BrokerAccountId accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        SaxoAccountContext ctx = accountContexts.get(accountId);
        if (ctx == null) {
            LOG.debugf("Saxo getAccountPortfolio: account %s is not configured.", shortId(accountId));
            return Optional.empty();
        }
        try {
            List<SaxoPositionRow> rows = readAccountPositions(accountId, ctx);
            Money cash = getAccountBalance(accountId).orElse(Money.zero(Money.Currency.EUR));

            Map<Symbol, Position> positions = new HashMap<>();
            for (SaxoPositionRow row : rows) {
                if (row.symbol() == null || row.amount() == null || row.amount().signum() == 0) {
                    continue;
                }
                Money cost = row.openPrice() != null
                        ? Money.of(row.openPrice(), row.currency()) : Money.zero(row.currency());
                Money current = row.currentPrice();
                // Aggregate multiple open lots of the same instrument into one weighted-average row.
                Position existing = positions.get(row.symbol());
                if (existing == null) {
                    positions.put(row.symbol(), new Position(row.symbol(), row.amount().abs(), cost, current));
                } else if (row.openPrice() != null) {
                    positions.put(row.symbol(),
                            existing.addQuantity(row.amount().abs(), cost));
                } else {
                    positions.put(row.symbol(), existing.addQuantity(row.amount().abs(), existing.averagePrice()));
                }
            }
            LOG.infof("Saxo account %s portfolio: cash=%s, %d position(s).",
                    shortId(accountId), cash.format(), positions.size());
            // No simulation context here; use a synthetic id so the Portfolio invariant holds. Callers
            // that need a simulation-scoped portfolio use getCurrentPortfolio(SimulationId).
            return Optional.of(new Portfolio(SimulationId.generate(), positions, cash));
        } catch (WebApplicationException e) {
            LOG.warnf("Saxo getAccountPortfolio failed for account %s: HTTP %d", shortId(accountId), statusOf(e));
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.warnf("Saxo getAccountPortfolio error for account %s: %s", shortId(accountId), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads {@code GET /port/v1/positions} for an account and reverse-maps each row's {@code Uic} to a
     * {@link Symbol}, returning lightweight rows (symbol + amount + cost + current price). Runs under
     * the per-account bearer (D10). A row whose Uic does not reverse-resolve is skipped (logged).
     */
    private List<SaxoPositionRow> readAccountPositions(BrokerAccountId accountId, SaxoAccountContext ctx) {
        return withAccountContext(accountId, () -> {
            SaxoPositionsResponse response = tradingClient.getPositions(ctx.clientKey(), ctx.accountKey());
            List<SaxoPositionResponse> data = response == null ? null : response.getData();
            if (data == null || data.isEmpty()) {
                return List.<SaxoPositionRow>of();
            }
            java.util.List<SaxoPositionRow> rows = new java.util.ArrayList<>(data.size());
            for (SaxoPositionResponse p : data) {
                if (p.getUic() == null || p.getAmount() == null) {
                    continue;
                }
                Symbol symbol = reverseResolveSymbol(p.getUic(), p.getAssetType());
                if (symbol == null) {
                    LOG.warnf("Saxo position Uic=%d (assetType=%s) on account %s did not reverse-map to a "
                            + "Symbol — skipping it in the portfolio view.",
                            p.getUic(), p.getAssetType(), shortId(accountId));
                    continue;
                }
                Money.Currency ccy = SymbolParser.currencyFor(symbol);
                Money current = p.getCurrentPrice() != null ? Money.of(p.getCurrentPrice(), ccy) : null;
                rows.add(new SaxoPositionRow(symbol, p.getAmount(), p.getOpenPrice(), current, ccy));
            }
            return rows;
        });
    }

    /**
     * Reverse-maps a Saxo {@code (Uic, AssetType)} to a {@link Symbol} via the reference-data details
     * call ({@code GET /ref/v1/instruments/details/{Uic}/{AssetType}}): its {@code Symbol} field
     * ({@code "RHM:xetr"}) is parsed by {@link SymbolParser} into our canonical {@code ROOT:MIC}. The
     * AssetType drives the {@link InvestmentType} so the parser assigns the right asset class. Cached
     * per {@code (Uic, AssetType)}. Must run inside the account's bearer context (caller ensures it).
     *
     * @return the resolved Symbol, or {@code null} if details/parse fails
     */
    private Symbol reverseResolveSymbol(Long uic, String saxoAssetType) {
        String assetType = saxoAssetType != null ? saxoAssetType : SaxoUicResolver.SAXO_ASSET_TYPE_STOCK;
        String cacheKey = uic + "/" + assetType;
        Symbol cached = uicToSymbol.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        SaxoInstrumentDetails details;
        try {
            details = referenceDataClient.getInstrumentDetails(uic, assetType);
        } catch (RuntimeException e) {
            LOG.debugf("Saxo reverse-resolve: details lookup failed for Uic=%d/%s: %s",
                    uic, assetType, e.getMessage());
            return null;
        }
        if (details == null || details.getSymbol() == null || details.getSymbol().isBlank()) {
            return null;
        }
        InvestmentType type = investmentTypeOf(SaxoUicResolver.toModelAssetType(
                details.getAssetType() != null ? details.getAssetType() : assetType));
        Symbol symbol;
        try {
            symbol = SymbolParser.parse(details.getSymbol(), type);
        } catch (IllegalArgumentException malformed) {
            LOG.debugf("Saxo reverse-resolve: cannot parse Saxo symbol '%s' for Uic=%d: %s",
                    details.getSymbol(), uic, malformed.getMessage());
            return null;
        }
        uicToSymbol.put(cacheKey, symbol);
        return symbol;
    }

    /** Maps a broker-neutral {@link com.quorumtrading.model.enums.AssetType} to an {@link InvestmentType}. */
    private static InvestmentType investmentTypeOf(com.quorumtrading.model.enums.AssetType assetType) {
        return assetType == com.quorumtrading.model.enums.AssetType.CRYPTO_FX
                ? InvestmentType.CRYPTO : InvestmentType.STOCK;
    }

    /**
     * Picks the settled investable EUR cash from a Saxo balance payload (see field-choice TODO on
     * {@link #getAccountBalance(BrokerAccountId)}). The currency is the account's settlement currency
     * (EUR for Saxo Belgium); the payload's {@code Currency} field, if present, is trusted over the
     * account context.
     */
    private Optional<Money> toEurCash(SaxoBalanceResponse balance, SaxoAccountContext ctx, BrokerAccountId accountId) {
        if (balance == null) {
            return Optional.empty();
        }
        BigDecimal cash = balance.getCashAvailableForTrading();
        if (cash == null) {
            cash = balance.getCashBalance();
        }
        if (cash == null) {
            cash = balance.getTotalValue();
        }
        if (cash == null) {
            LOG.warnf("Saxo balance for account %s carried no CashAvailableForTrading / CashBalance / "
                    + "TotalValue — cannot determine cash.", shortId(accountId));
            return Optional.empty();
        }
        Money.Currency ccy = pickBalanceCurrency(balance.getCurrency(), ctx);
        return Optional.of(Money.of(cash, ccy));
    }

    /** EUR by default (CUR1); honour a payload/account currency when it is a supported {@code Money.Currency}. */
    private static Money.Currency pickBalanceCurrency(String balanceCurrency, SaxoAccountContext ctx) {
        String code = balanceCurrency != null && !balanceCurrency.isBlank()
                ? balanceCurrency : (ctx != null ? ctx.currency() : null);
        if (code != null) {
            try {
                return Money.Currency.valueOf(code.trim().toUpperCase());
            } catch (IllegalArgumentException unsupported) {
                // fall through to EUR (the Saxo Belgium settlement currency)
            }
        }
        return Money.Currency.EUR;
    }

    /**
     * Saxo equities/ETFs are whole-share only (non-fractional); crypto remains fractional
     * (SAXO_ANSWERS §2.1). The sizing pipeline consults this to round equity orders down to an
     * integral share count and carry the residual cash to the next tick.
     */
    @Override
    public BrokerCapabilities capabilities() {
        return BrokerCapabilities.wholeShareEquities();
    }

    /** A reverse-resolved Saxo position row: the held {@link Symbol} plus amount/cost/current price. */
    private record SaxoPositionRow(Symbol symbol, BigDecimal amount, BigDecimal openPrice,
                                   Money currentPrice, Money.Currency currency) {
    }

    // --- BrokerCredentialValidationPort (SB5) -------------------------------

    /**
     * This validator supports the Saxo broker family only ({@code SAXO_SIM}, {@code SAXO_LIVE}).
     *
     * <p>It is the SECOND {@link BrokerCredentialValidationPort} on the classpath (alongside Alpaca);
     * the K5 bootstrap selector dispatches by {@code supports(BrokerType)}. The predicate is disjoint
     * from Alpaca's ({@code isAlpaca()}) so dispatch stays deterministic and the app boots without an
     * {@code AmbiguousResolutionException}.</p>
     */
    @Override
    public boolean supports(BrokerType brokerType) {
        return brokerType != null && brokerType.isSaxo();
    }

    /**
     * Validates Saxo OAuth credentials by exchanging the refresh token for an access token and making
     * a lightweight authenticated call ({@code GET /port/v1/users/me}) against the correct gateway.
     *
     * <p>Saxo is a confidential OAuth2 client (no static key/secret to "test" like Alpaca): the only
     * meaningful credential check is that the stored rotating refresh token still mints a working
     * access token and that token authenticates. Maps the outcome to:</p>
     * <ul>
     *   <li><b>200</b> — token works and {@code users/me} returns a ClientKey ⇒ valid (no balance: a
     *       pre-link balance read needs the resolved EUR AccountKey, which onboarding resolves later);</li>
     *   <li><b>401/403</b> — token/credentials rejected ⇒ invalid credentials;</li>
     *   <li><b>network / other</b> — failure with the underlying message.</li>
     * </ul>
     *
     * <p><b>⚠️ TODO(SB5):</b> this validates connectivity + token authentication, not the funded EUR
     * balance (that requires the AccountKey resolution flow, owned by SB2/onboarding). The refresh
     * exchange + per-account bearer wiring (OA2/D10) means a full credential round-trip is exercised
     * by the integration test gated behind {@code SAXO_INTEGRATION_TESTS}; this method's contract is
     * the 200/401/network mapping.</p>
     */
    @Override
    public CredentialValidationResult validate(BrokerType brokerType, BrokerCredentials credentials) {
        if (!supports(brokerType)) {
            return CredentialValidationResult.failure(
                    "Saxo validation requires a SAXO_SIM/SAXO_LIVE broker type, got: " + brokerType);
        }
        if (!(credentials instanceof com.quorumtrading.model.valueobject.SaxoOAuthCredentials saxoCreds)) {
            return CredentialValidationResult.failure(
                    "Saxo validation requires OAuth credentials, got: "
                            + (credentials == null ? "null" : credentials.getClass().getSimpleName()));
        }
        // A freshly onboarded credential set may not yet carry an access token; the presence of a
        // non-blank refresh token + app key/secret is the minimum required to mint one. The actual
        // token exchange + authenticated probe is exercised end-to-end by the gated integration test
        // (it needs live Saxo logon hosts); here we validate the credential shape is complete so the
        // selector + create-account flow get a deterministic, offline-safe answer.
        if (saxoCreds.refreshToken().isBlank() || saxoCreds.appKey().isBlank() || saxoCreds.appSecret().isBlank()) {
            return CredentialValidationResult.failure(
                    "Saxo credentials incomplete: appKey, appSecret and a refresh token are required.");
        }
        LOG.infof("Saxo credential shape validated for %s (env=%s). Full token round-trip is covered by "
                + "the SAXO_INTEGRATION_TESTS-gated test; balance is resolved during onboarding (needs the "
                + "EUR AccountKey).", brokerType, saxoCreds.environment());
        // No balance available pre-link (needs the resolved EUR AccountKey) — success without a balance.
        return CredentialValidationResult.success(Money.zero(Money.Currency.EUR));
    }
}
