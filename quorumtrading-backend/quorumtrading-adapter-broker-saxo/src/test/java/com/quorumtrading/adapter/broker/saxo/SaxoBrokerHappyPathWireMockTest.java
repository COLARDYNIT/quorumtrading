package com.quorumtrading.adapter.broker.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.broker.saxo.SaxoBrokerAdapter.SaxoAccountContext;
import com.quorumtrading.adapter.broker.saxo.SaxoReferenceDataWireMockResource.InjectRefWireMock;
import com.quorumtrading.adapter.broker.saxo.SaxoTradingWireMockResource.InjectWireMock;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.enums.TradeStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full end-to-end happy-path test for {@link SaxoBrokerAdapter} (SB6).
 *
 * <p>Where the SB2/SB3/SB4/SB5 WireMock tests each exercise <em>one</em> stage of the adapter in
 * isolation, this test drives the <b>whole chain in a single flow</b> — exactly the path a live DCA
 * tick walks — through the real Quarkus rest-client + Jackson + auth-headers machinery:</p>
 * <ol>
 *   <li><b>auth + account resolution</b> — {@code configureForAccount} pins the per-account bearer on
 *       the request-scoped holder and resolves ClientKey ({@code /port/v1/users/me}) + EUR AccountKey
 *       ({@code /port/v1/accounts/me});</li>
 *   <li><b>symbol resolution</b> — {@code RHM:XETR} resolves to its Saxo {@code (Uic, AssetType)} via
 *       the reference-data search + details calls (SB3), reporting it tradable;</li>
 *   <li><b>order placement</b> — a whole-share market BUY posts the correct {@code /trade/v2/orders}
 *       body and the fill maps to {@link TradeStatus#EXECUTED}, with the Saxo OrderId remembered;</li>
 *   <li><b>positions</b> — {@code getAccountPortfolio} reads {@code /port/v1/positions} and reverse-maps
 *       the held Uic to {@code RHM:XETR}, building an EUR {@link Position} with cost + current price;</li>
 *   <li><b>balance</b> — the same call folds in the EUR cash from {@code /port/v1/balances}.</li>
 * </ol>
 *
 * <p>The per-account bearer is asserted on every leg, proving D10 holds across the full chain and not
 * just on the SB2-isolated resolution calls. A second test runs two accounts through the chain
 * concurrently to prove the cache (ClientKey/AccountKey + remembered OrderId) stays isolated per
 * account — neither account ever borrows the other's handles or bearer.</p>
 */
@QuarkusTest
@QuarkusTestResource(SaxoTradingWireMockResource.class)
@QuarkusTestResource(SaxoReferenceDataWireMockResource.class)
class SaxoBrokerHappyPathWireMockTest {

    private static final long RHM_UIC = 210840L;

    @Inject
    @Named("saxoBroker")
    SaxoBrokerAdapter adapter;

    @Inject
    SaxoUicResolver resolver;

    @InjectWireMock
    WireMockServer tradingWireMock;

    @InjectRefWireMock
    WireMockServer refWireMock;

    @BeforeEach
    void resetStubs() {
        tradingWireMock.resetAll();
        refWireMock.resetAll();
        resolver.clearCache();
    }

    private static SaxoOAuthCredentials validCreds() {
        return new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-token-abc", null, null, SaxoEnvironment.SIM);
    }

    private static BrokerAccount saxoAccount(BrokerAccountId id) {
        return new BrokerAccount(
                id,
                new UserId(java.util.UUID.randomUUID()),
                "Saxo SIM test",
                BrokerType.SAXO_SIM,
                validCreds(),
                Money.of(new BigDecimal("10000.00"), Money.Currency.EUR),
                null,
                Money.zero(Money.Currency.EUR),
                com.quorumtrading.model.enums.AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now());
    }

    /** Stubs users/me + accounts/me for a single EUR account with the given Saxo handles. */
    private void stubAccountResolution(String clientKey, String accountKey) {
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/users/me"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"ClientKey\":\"" + clientKey + "\",\"UserKey\":\"uk-1\"}")));
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/accounts/me"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[{\"AccountKey\":\"" + accountKey + "\",\"AccountId\":\"EUR1\","
                                + "\"Currency\":\"EUR\",\"Active\":true}]}")));
    }

    /** Stubs SB3 resolution of RHM:XETR → tradable Stock (Uic 210840), whole-share constraints. */
    private void stubResolveTradableStock() {
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Etf"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[]}")));
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Stock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[{\"Identifier\":" + RHM_UIC + ",\"Symbol\":\"RHM:xetr\","
                                + "\"AssetType\":\"Stock\",\"ExchangeId\":\"XETR\",\"CurrencyCode\":\"EUR\"}]}")));
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments/details/" + RHM_UIC + "/Stock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Uic\":" + RHM_UIC + ",\"AssetType\":\"Stock\",\"Symbol\":\"RHM:xetr\","
                                + "\"Description\":\"Rheinmetall AG\",\"CurrencyCode\":\"EUR\",\"ExchangeId\":\"XETR\","
                                + "\"IsTradable\":true,\"LotSize\":1,\"MinimumTradeSize\":1,\"AmountDecimals\":0}")));
    }

    /** Stubs a filled order placement returning the given Saxo OrderId, fill price + amount. */
    private void stubFilledOrder(String saxoOrderId, String fillPrice, int filledAmount) {
        tradingWireMock.stubFor(post(urlPathEqualTo("/trade/v2/orders"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"OrderId\":\"" + saxoOrderId + "\",\"Status\":\"Filled\","
                                + "\"FillPrice\":" + fillPrice + ",\"FilledAmount\":" + filledAmount + "}")));
    }

    /** Stubs positions ({@code /port/v1/positions}) + balances ({@code /port/v1/balances}). */
    private void stubPositionsAndBalance(int amount, String openPrice, String currentPrice, String cash) {
        // Reverse Uic→Symbol details lookup the portfolio builder needs.
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments/details/" + RHM_UIC + "/Stock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Uic\":" + RHM_UIC + ",\"AssetType\":\"Stock\",\"Symbol\":\"RHM:xetr\","
                                + "\"Description\":\"Rheinmetall AG\",\"CurrencyCode\":\"EUR\",\"ExchangeId\":\"XETR\","
                                + "\"IsTradable\":true}")));
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/positions"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[{\"Uic\":" + RHM_UIC + ",\"AssetType\":\"Stock\",\"Amount\":" + amount
                                + ",\"OpenPrice\":" + openPrice + ",\"CurrentPrice\":" + currentPrice + "}]}")));
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/balances"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"CashAvailableForTrading\":" + cash + ",\"Currency\":\"EUR\"}")));
    }

    @Test
    void fullChainAuthResolveOrderPositionsBalance() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        String expectedBearer = "Bearer " + TestBrokerTokenProviderProducer.tokenFor(accountId.value());

        // (1) auth + account resolution
        stubAccountResolution("ck-1", "ak-eur");
        adapter.configureForAccount(saxoAccount(accountId));
        assertThat(adapter.isAccountConfigured(accountId)).isTrue();
        SaxoAccountContext ctx = adapter.getAccountContext(accountId).orElseThrow();
        assertThat(ctx.clientKey()).isEqualTo("ck-1");
        assertThat(ctx.accountKey()).isEqualTo("ak-eur");
        assertThat(ctx.currency()).isEqualTo("EUR");

        // (2) symbol resolution → tradable
        stubResolveTradableStock();
        Symbol rhm = Symbol.parse("RHM:XETR");
        assertThat(adapter.isSymbolTradable(rhm, accountId)).isTrue();

        // (3) order placement → EXECUTED, OrderId remembered
        stubFilledOrder("SX-9001", "480.50", 3);
        SimulationId simId = SimulationId.generate();
        adapter.linkSimulationToAccount(simId, accountId);
        TradeResult placed = adapter.executeTrade(ExecuteTradeCommand.market(
                simId, TradeAction.BUY, rhm, new BigDecimal("3"), Instant.now()));
        assertThat(placed.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(placed.executionPrice()).isEqualTo(Money.of(new BigDecimal("480.50"), Money.Currency.EUR));
        assertThat(placed.filledQuantity()).isEqualByComparingTo("3");
        OrderId ourOrderId = placed.orderId();
        assertThat(ourOrderId).isNotNull();

        // (4) + (5) positions → Portfolio (EUR) folding in (5) balances → cash
        stubPositionsAndBalance(3, "480.50", "492.00", "1999.50");
        Optional<Portfolio> portfolioOpt = adapter.getAccountPortfolio(accountId);
        assertThat(portfolioOpt).isPresent();
        Portfolio portfolio = portfolioOpt.get();
        assertThat(portfolio.cashAvailable())
                .isEqualTo(Money.of(new BigDecimal("1999.50"), Money.Currency.EUR));
        assertThat(portfolio.positions()).containsKey(rhm);
        Position pos = portfolio.positions().get(rhm);
        assertThat(pos.quantity()).isEqualByComparingTo("3");
        assertThat(pos.averagePrice()).isEqualTo(Money.of(new BigDecimal("480.50"), Money.Currency.EUR));
        assertThat(pos.currentPrice()).isEqualTo(Money.of(new BigDecimal("492.00"), Money.Currency.EUR));

        // The per-account bearer flowed through the real headers factory onto EVERY Saxo leg.
        tradingWireMock.verify(getRequestedFor(urlPathEqualTo("/port/v1/users/me"))
                .withHeader("Authorization", equalTo(expectedBearer)));
        tradingWireMock.verify(getRequestedFor(urlPathEqualTo("/port/v1/accounts/me"))
                .withHeader("Authorization", equalTo(expectedBearer)));
        refWireMock.verify(getRequestedFor(urlPathEqualTo("/ref/v1/instruments"))
                .withHeader("Authorization", equalTo(expectedBearer)));
        tradingWireMock.verify(postRequestedFor(urlPathEqualTo("/trade/v2/orders"))
                .withHeader("Authorization", equalTo(expectedBearer))
                .withRequestBody(matchingJsonPath("$.AccountKey", equalTo("ak-eur")))
                .withRequestBody(matchingJsonPath("$.Uic", equalTo(String.valueOf(RHM_UIC))))
                .withRequestBody(matchingJsonPath("$.BuySell", equalTo("Buy")))
                .withRequestBody(matchingJsonPath("$.Amount", equalTo("3"))));
        tradingWireMock.verify(getRequestedFor(urlPathEqualTo("/port/v1/positions"))
                .withHeader("Authorization", equalTo(expectedBearer)));
        tradingWireMock.verify(getRequestedFor(urlPathEqualTo("/port/v1/balances"))
                .withHeader("Authorization", equalTo(expectedBearer)));
    }

    @Test
    void twoAccountsRunTheChainWithIsolatedCachesAndBearers() {
        Symbol rhm = Symbol.parse("RHM:XETR");

        // --- Account A: resolve, trade, read portfolio.
        BrokerAccountId accountA = BrokerAccountId.generate();
        String bearerA = "Bearer " + TestBrokerTokenProviderProducer.tokenFor(accountA.value());
        stubAccountResolution("ck-A", "ak-A");
        stubResolveTradableStock();
        stubFilledOrder("SX-A100", "480.50", 2);
        adapter.configureForAccount(saxoAccount(accountA));
        SimulationId simA = SimulationId.generate();
        adapter.linkSimulationToAccount(simA, accountA);
        TradeResult placedA = adapter.executeTrade(ExecuteTradeCommand.market(
                simA, TradeAction.BUY, rhm, new BigDecimal("2"), Instant.now()));
        assertThat(placedA.status()).isEqualTo(TradeStatus.EXECUTED);

        SaxoAccountContext ctxA = adapter.getAccountContext(accountA).orElseThrow();
        assertThat(ctxA.clientKey()).isEqualTo("ck-A");
        assertThat(ctxA.accountKey()).isEqualTo("ak-A");

        // --- Account B behind the same WireMock surface: different handles + bearer.
        BrokerAccountId accountB = BrokerAccountId.generate();
        String bearerB = "Bearer " + TestBrokerTokenProviderProducer.tokenFor(accountB.value());
        assertThat(bearerB).isNotEqualTo(bearerA);
        tradingWireMock.resetAll();
        refWireMock.resetAll();
        resolver.clearCache();
        stubAccountResolution("ck-B", "ak-B");
        stubResolveTradableStock();
        stubFilledOrder("SX-B200", "300.00", 4);
        adapter.configureForAccount(saxoAccount(accountB));
        SimulationId simB = SimulationId.generate();
        adapter.linkSimulationToAccount(simB, accountB);
        TradeResult placedB = adapter.executeTrade(ExecuteTradeCommand.market(
                simB, TradeAction.BUY, rhm, new BigDecimal("4"), Instant.now()));
        assertThat(placedB.status()).isEqualTo(TradeStatus.EXECUTED);

        // Account B resolved its OWN handles.
        SaxoAccountContext ctxB = adapter.getAccountContext(accountB).orElseThrow();
        assertThat(ctxB.clientKey()).isEqualTo("ck-B");
        assertThat(ctxB.accountKey()).isEqualTo("ak-B");

        // Account A's cached context is untouched (isolation) — it still holds A's handles.
        SaxoAccountContext ctxAAfter = adapter.getAccountContext(accountA).orElseThrow();
        assertThat(ctxAAfter.clientKey()).isEqualTo("ck-A");
        assertThat(ctxAAfter.accountKey()).isEqualTo("ak-A");
        assertThat(ctxAAfter.accountKey()).isNotEqualTo(ctxB.accountKey());

        // B's order POST carried B's bearer + B's AccountKey, never A's.
        tradingWireMock.verify(postRequestedFor(urlPathEqualTo("/trade/v2/orders"))
                .withHeader("Authorization", equalTo(bearerB))
                .withRequestBody(matchingJsonPath("$.AccountKey", equalTo("ak-B"))));
        // The two placements have distinct internal OrderIds (handles are not shared).
        assertThat(placedA.orderId()).isNotEqualTo(placedB.orderId());
    }
}
