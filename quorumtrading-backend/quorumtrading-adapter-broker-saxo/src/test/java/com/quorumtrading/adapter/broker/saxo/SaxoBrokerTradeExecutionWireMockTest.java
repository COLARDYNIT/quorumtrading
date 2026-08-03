package com.quorumtrading.adapter.broker.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.broker.saxo.SaxoReferenceDataWireMockResource.InjectRefWireMock;
import com.quorumtrading.adapter.broker.saxo.SaxoTradingWireMockResource.InjectWireMock;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.BrokerAccount;
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
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock-backed test for the SB4 trade-execution path on {@link SaxoBrokerAdapter}.
 *
 * <p>Wires both the {@code saxo-trading} surface ({@link SaxoTradingWireMockResource}, for account
 * configuration + order placement/status/cancel) and the {@code saxo-ref} surface
 * ({@link SaxoReferenceDataWireMockResource}, for {@code Symbol → Uic + AssetType} resolution), then
 * drives the real adapter end-to-end through the real Quarkus rest-client + Jackson + auth-headers
 * machinery. Proves the WP's named cases:</p>
 * <ul>
 *   <li><b>market order body correct</b> — the {@code POST /trade/v2/orders} JSON carries the resolved
 *       Uic/AssetType, the share-count Amount, BuySell, OrderType=Market, ManualOrder=false, a
 *       {@code DayOrder} duration, the AccountKey, and a non-blank ExternalReference;</li>
 *   <li><b>OrderId round-trip</b> — a Saxo string OrderId is remembered against our internal UUID
 *       OrderId so a follow-up status read addresses the same Saxo order;</li>
 *   <li><b>status</b> — {@code getOrderStatus} reads {@code GET /port/v1/orders/{id}} and maps a fill;</li>
 *   <li><b>cancel</b> — {@code cancelAllOpenOrders} enumerates open orders and DELETEs each;</li>
 *   <li><b>whole-share</b> — a fractional amount is rejected before any order is sent.</li>
 * </ul>
 */
@QuarkusTest
@QuarkusTestResource(SaxoTradingWireMockResource.class)
@QuarkusTestResource(SaxoReferenceDataWireMockResource.class)
class SaxoBrokerTradeExecutionWireMockTest {

    private static final long UIC = 210840L;

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

    private static BrokerAccount saxoAccount(BrokerAccountId id) {
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-token-abc", null, null, SaxoEnvironment.SIM);
        return new BrokerAccount(
                id,
                new UserId(java.util.UUID.randomUUID()),
                "Saxo SIM test",
                com.quorumtrading.model.enums.BrokerType.SAXO_SIM,
                creds,
                Money.of(new BigDecimal("10000.00"), Money.Currency.EUR),
                null,
                Money.zero(Money.Currency.EUR),
                com.quorumtrading.model.enums.AccountStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now());
    }

    private void configureAccount(BrokerAccountId id) {
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/users/me"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"ClientKey\":\"ck-1\",\"UserKey\":\"uk-1\"}")));
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/accounts/me"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[{\"AccountKey\":\"ak-eur\",\"AccountId\":\"EUR1\","
                                + "\"Currency\":\"EUR\",\"Active\":true}]}")));
        adapter.configureForAccount(saxoAccount(id));
    }

    /** Stubs the SB3 resolution for RHM:XETR → tradable Stock with whole-share constraints. */
    private void stubResolveTradableStock() {
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Etf"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[]}")));
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Stock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[{\"Identifier\":" + UIC + ",\"Symbol\":\"RHM:xetr\","
                                + "\"AssetType\":\"Stock\",\"ExchangeId\":\"XETR\",\"CurrencyCode\":\"EUR\"}]}")));
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments/details/" + UIC + "/Stock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Uic\":" + UIC + ",\"AssetType\":\"Stock\",\"Symbol\":\"RHM:xetr\","
                                + "\"Description\":\"Rheinmetall AG\",\"CurrencyCode\":\"EUR\",\"ExchangeId\":\"XETR\","
                                + "\"IsTradable\":true,\"LotSize\":1,\"MinimumTradeSize\":1,\"AmountDecimals\":0}")));
    }

    private SimulationId linkedSimulation(BrokerAccountId accountId) {
        SimulationId simId = SimulationId.generate();
        adapter.linkSimulationToAccount(simId, accountId);
        return simId;
    }

    private static ExecuteTradeCommand buyMarket(SimulationId simId, BigDecimal qty) {
        return ExecuteTradeCommand.market(
                simId, TradeAction.BUY, Symbol.parse("RHM:XETR"), qty, Instant.now());
    }

    @Test
    void marketOrderBodyIsCorrect() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubResolveTradableStock();
        SimulationId simId = linkedSimulation(accountId);

        tradingWireMock.stubFor(post(urlPathEqualTo("/trade/v2/orders"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"OrderId\":\"SX-1001\",\"Orders\":[{\"OrderId\":\"SX-1001\"}]}")));

        TradeResult result = adapter.executeTrade(buyMarket(simId, new BigDecimal("3")));

        // Accepted (no ErrorInfo, OrderId present) → PENDING since the stub echoed no fill price.
        assertThat(result.status()).isEqualTo(TradeStatus.PENDING);

        // The market-order body carried every required Saxo field.
        tradingWireMock.verify(postRequestedFor(urlPathEqualTo("/trade/v2/orders"))
                .withRequestBody(matchingJsonPath("$.AccountKey", equalTo("ak-eur")))
                .withRequestBody(matchingJsonPath("$.Uic", equalTo(String.valueOf(UIC))))
                .withRequestBody(matchingJsonPath("$.AssetType", equalTo("Stock")))
                .withRequestBody(matchingJsonPath("$.BuySell", equalTo("Buy")))
                .withRequestBody(matchingJsonPath("$.Amount", equalTo("3")))
                .withRequestBody(matchingJsonPath("$.OrderType", equalTo("Market")))
                .withRequestBody(matchingJsonPath("$.ManualOrder", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.OrderDuration.DurationType", equalTo("DayOrder")))
                .withRequestBody(matchingJsonPath("$.ExternalReference")));
    }

    @Test
    void filledOrderMapsToExecutedWithPriceAndQuantity() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubResolveTradableStock();
        SimulationId simId = linkedSimulation(accountId);

        tradingWireMock.stubFor(post(urlPathEqualTo("/trade/v2/orders"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"OrderId\":\"SX-2002\",\"Status\":\"Filled\","
                                + "\"FillPrice\":480.50,\"FilledAmount\":2}")));

        TradeResult result = adapter.executeTrade(buyMarket(simId, new BigDecimal("2")));

        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(result.executionPrice()).isEqualTo(Money.of(new BigDecimal("480.50"), Money.Currency.EUR));
        assertThat(result.filledQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void rejectedOrderMapsErrorInfoToRejection() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubResolveTradableStock();
        SimulationId simId = linkedSimulation(accountId);

        tradingWireMock.stubFor(post(urlPathEqualTo("/trade/v2/orders"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"ErrorInfo\":{\"ErrorCode\":\"IllegalAmount\","
                                + "\"Message\":\"Order value below minimum\"}}")));

        TradeResult result = adapter.executeTrade(buyMarket(simId, new BigDecimal("1")));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getRejectionReason()).hasValueSatisfying(r ->
                assertThat(r).contains("IllegalAmount").contains("Order value below minimum"));
    }

    @Test
    void httpRejectionWithErrorInfoBodyMapsToRejection() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubResolveTradableStock();
        SimulationId simId = linkedSimulation(accountId);

        // Saxo rejects with a 400 whose body is an ErrorInfo envelope.
        tradingWireMock.stubFor(post(urlPathEqualTo("/trade/v2/orders"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"ErrorInfo\":{\"ErrorCode\":\"MarketClosed\","
                                + "\"Message\":\"The market is closed\"}}")));

        TradeResult result = adapter.executeTrade(buyMarket(simId, new BigDecimal("1")));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getRejectionReason()).hasValueSatisfying(r ->
                assertThat(r).contains("MarketClosed"));
    }

    @Test
    void orderIdRoundTripsForStatusRead() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubResolveTradableStock();
        SimulationId simId = linkedSimulation(accountId);

        tradingWireMock.stubFor(post(urlPathEqualTo("/trade/v2/orders"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"OrderId\":\"SX-3003\",\"Orders\":[{\"OrderId\":\"SX-3003\"}]}")));

        TradeResult placed = adapter.executeTrade(buyMarket(simId, new BigDecimal("5")));
        OrderId ourOrderId = placed.orderId();

        // The remembered Saxo string id (SX-3003) is what the status read must address.
        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/orders/SX-3003"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"OrderId\":\"SX-3003\",\"Status\":\"Filled\","
                                + "\"FillPrice\":475.00,\"FilledAmount\":5}")));

        Optional<TradeResult> status = adapter.getOrderStatus(ourOrderId);

        assertThat(status).isPresent();
        assertThat(status.get().status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(status.get().filledQuantity()).isEqualByComparingTo("5");
        assertThat(status.get().orderId()).isEqualTo(ourOrderId);
        // Proves the round-trip: the status read hit the Saxo id, scoped to the account's keys.
        tradingWireMock.verify(com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/port/v1/orders/SX-3003"))
                .withQueryParam("AccountKey", equalTo("ak-eur"))
                .withQueryParam("ClientKey", equalTo("ck-1")));
    }

    @Test
    void getOrderStatusReturnsEmptyWhenSaxoOrderNoLongerOpen() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubResolveTradableStock();
        SimulationId simId = linkedSimulation(accountId);

        tradingWireMock.stubFor(post(urlPathEqualTo("/trade/v2/orders"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"OrderId\":\"SX-4004\"}")));
        TradeResult placed = adapter.executeTrade(buyMarket(simId, new BigDecimal("1")));

        // Order filled/closed → no longer in the open-orders surface → 404.
        tradingWireMock.stubFor(get(urlPathMatching("/port/v1/orders/SX-4004"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(adapter.getOrderStatus(placed.orderId())).isEmpty();
    }

    @Test
    void cancelAllOpenOrdersEnumeratesAndDeletesEach() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        SimulationId simId = linkedSimulation(accountId);

        tradingWireMock.stubFor(get(urlPathEqualTo("/port/v1/orders"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[{\"OrderId\":\"SX-5005\",\"Status\":\"Working\"},"
                                + "{\"OrderId\":\"SX-5006\",\"Status\":\"Working\"}]}")));
        tradingWireMock.stubFor(delete(urlPathMatching("/trade/v2/orders/SX-50.*"))
                .willReturn(aResponse().withStatus(200)));

        adapter.cancelAllOpenOrders(simId);

        tradingWireMock.verify(deleteRequestedFor(urlPathEqualTo("/trade/v2/orders/SX-5005"))
                .withQueryParam("AccountKey", equalTo("ak-eur")));
        tradingWireMock.verify(deleteRequestedFor(urlPathEqualTo("/trade/v2/orders/SX-5006"))
                .withQueryParam("AccountKey", equalTo("ak-eur")));
    }

    @Test
    void fractionalAmountIsRejectedBeforeAnyOrderIsSent() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        stubResolveTradableStock();
        SimulationId simId = linkedSimulation(accountId);

        TradeResult result = adapter.executeTrade(buyMarket(simId, new BigDecimal("2.5")));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getRejectionReason()).hasValueSatisfying(r ->
                assertThat(r).contains("whole-share"));
        // No order POST was attempted.
        tradingWireMock.verify(0, postRequestedFor(urlPathEqualTo("/trade/v2/orders")));
    }

    @Test
    void wholeShareBelowMinimumTradeSizeIsRejected() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        configureAccount(accountId);
        // MinimumTradeSize = 10 → an order for 3 shares is below the floor.
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Etf"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[]}")));
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Stock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Data\":[{\"Identifier\":" + UIC + ",\"Symbol\":\"RHM:xetr\","
                                + "\"AssetType\":\"Stock\",\"ExchangeId\":\"XETR\",\"CurrencyCode\":\"EUR\"}]}")));
        refWireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments/details/" + UIC + "/Stock"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"Uic\":" + UIC + ",\"AssetType\":\"Stock\",\"Symbol\":\"RHM:xetr\","
                                + "\"Description\":\"Rheinmetall AG\",\"CurrencyCode\":\"EUR\",\"ExchangeId\":\"XETR\","
                                + "\"IsTradable\":true,\"LotSize\":1,\"MinimumTradeSize\":10,\"AmountDecimals\":0}")));
        SimulationId simId = linkedSimulation(accountId);

        TradeResult result = adapter.executeTrade(buyMarket(simId, new BigDecimal("3")));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getRejectionReason()).hasValueSatisfying(r ->
                assertThat(r).contains("MinimumTradeSize"));
        tradingWireMock.verify(0, postRequestedFor(urlPathEqualTo("/trade/v2/orders")));
    }

    @Test
    void unlinkedSimulationIsRejected() {
        SimulationId orphan = SimulationId.generate();

        TradeResult result = adapter.executeTrade(buyMarket(orphan, new BigDecimal("1")));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getRejectionReason()).hasValueSatisfying(r ->
                assertThat(r).contains("not linked"));
    }
}
