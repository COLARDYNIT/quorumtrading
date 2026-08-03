package com.quorumtrading.adapter.marketdata.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.marketdata.saxo.client.InjectWireMock;
import com.quorumtrading.adapter.marketdata.saxo.client.TestTokenProviderProducer;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock-backed tests for the Saxo market-data live-quote path ({@code MD2-live-quotes}).
 *
 * <p>Drives the real {@link SaxoMarketDataAdapter} → {@link SaxoQuoteUicResolver} →
 * {@code saxo-md-ref} (instrument search) + {@code saxo-openapi} (InfoPrices) rest-client chain
 * through {@link SaxoMarketDataWireMockResource}, stubbing both Saxo surfaces. Proves:</p>
 * <ul>
 *   <li><b>quote + OHL mapping</b> — an EU symbol resolves to its {@code (Uic, AssetType)}, the
 *       InfoPrices call is made with {@code FieldGroups=Quote,PriceInfo,PriceInfoDetails}, and the
 *       result maps to a PRICE {@link MarketEvent} carrying {@code source="saxo"}, the venue
 *       currency (EUR), the day High/Low/Open, and the {@code delayed}/{@code delayedByMinutes}
 *       flags; the per-account OAuth bearer flows on both calls;</li>
 *   <li><b>NoAccess → empty</b> — a quote whose {@code PriceType*} is {@code NoAccess} (no
 *       entitlement) yields no event, gracefully (not an error);</li>
 *   <li><b>realtime flag</b> — {@code DelayedByMinutes=0} sets {@code delayed=false}; and</li>
 *   <li><b>US symbol not covered / unresolvable → empty</b>.</li>
 * </ul>
 *
 * <p>All HTTP is stubbed; no real Saxo gateway is ever contacted.</p>
 */
@QuarkusTest
@QuarkusTestResource(value = SaxoMarketDataWireMockResource.class, restrictToAnnotatedClass = true)
class SaxoMarketDataAdapterWireMockTest {

    private static final String EXPECTED_BEARER = "Bearer " + TestTokenProviderProducer.TEST_ACCESS_TOKEN;

    @Inject
    SaxoMarketDataAdapter adapter;

    @Inject
    SaxoQuoteUicResolver resolver;

    @InjectWireMock
    WireMockServer wireMock;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
        resolver.clearCache();
    }

    // --- stub helpers -------------------------------------------------------

    /** Stub the venue-pinned instrument search for one Saxo asset type. */
    private void stubSearch(String assetType, String exchangeId, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo(assetType))
                .withQueryParam("ExchangeId", equalTo(exchangeId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubEmptySearch(String assetType, String exchangeId) {
        stubSearch(assetType, exchangeId, "{\"Data\": []}");
    }

    private static String searchHit(long uic, String symbol, String assetType, String exchangeId, String ccy) {
        return """
                {"Data": [
                  {"Identifier": %d, "Symbol": "%s", "Description": "Rheinmetall AG",
                   "AssetType": "%s", "ExchangeId": "%s", "CurrencyCode": "%s"}
                ]}
                """.formatted(uic, symbol, assetType, exchangeId, ccy);
    }

    private void stubInfoPrices(String body) {
        wireMock.stubFor(get(urlPathEqualTo("/trade/v1/infoprices"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /** A delayed (15-min) entitled quote with day High/Low + day Open. */
    private static String entitledQuoteBody(long uic) {
        return """
                {
                  "Uic": %d,
                  "AssetType": "Stock",
                  "LastUpdated": "2026-06-04T09:30:00Z",
                  "Quote": {
                    "Bid": 615.40, "Ask": 615.80, "Mid": 615.60,
                    "DelayedByMinutes": 15, "MarketState": "Open",
                    "PriceTypeBid": "Indicative", "PriceTypeAsk": "Indicative"
                  },
                  "PriceInfo": { "High": 620.00, "Low": 610.10 },
                  "PriceInfoDetails": {
                    "Open": 612.00, "Volume": 1234567, "LastTraded": 615.50, "LastClose": 611.20
                  }
                }
                """.formatted(uic);
    }

    // --- tests --------------------------------------------------------------

    @Test
    void resolvesEuSymbolAndMapsQuoteWithOhlAndDelayFlag() {
        // Rheinmetall on Xetra → Etf search empty, Stock search hits Uic 211.
        stubEmptySearch("Etf", "XETR");
        stubSearch("Stock", "XETR", searchHit(211L, "RHM:xetr", "Stock", "XETR", "EUR"));
        stubInfoPrices(entitledQuoteBody(211L));

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        List<MarketEvent> events = adapter.fetchSymbolQuote(rhm);

        assertThat(events).hasSize(1);
        MarketEvent event = events.get(0);
        assertThat(event.eventType()).isEqualTo(EventType.PRICE);
        assertThat(event.source()).isEqualTo("saxo");
        assertThat(event.symbol()).isEqualTo("RHM:XETR");

        // Decision price = Mid; currency = EUR from the venue; OHL carried from field groups.
        assertThat(event.rawData()).containsEntry("currency", "EUR");
        assertThat((BigDecimal) event.rawData().get("price")).isEqualByComparingTo("615.60");
        assertThat((BigDecimal) event.rawData().get("high")).isEqualByComparingTo("620.00");
        assertThat((BigDecimal) event.rawData().get("low")).isEqualByComparingTo("610.10");
        assertThat((BigDecimal) event.rawData().get("open")).isEqualByComparingTo("612.00");
        assertThat(event.rawData()).containsEntry("uic", 211L);

        // Delayed-by-15 → delayed=true, delayedByMinutes carried.
        assertThat(event.rawData()).containsEntry("delayed", true);
        assertThat(event.rawData()).containsEntry("delayedByMinutes", 15);

        // The InfoPrices call requested all three field groups + carried the bearer.
        wireMock.verify(getRequestedFor(urlPathEqualTo("/trade/v1/infoprices"))
                .withQueryParam("FieldGroups", equalTo("Quote,PriceInfo,PriceInfoDetails"))
                .withHeader("Authorization", equalTo(EXPECTED_BEARER)));
        // The search call carried the bearer too.
        wireMock.verify(getRequestedFor(urlPathEqualTo("/ref/v1/instruments"))
                .withHeader("Authorization", equalTo(EXPECTED_BEARER)));
    }

    @Test
    void noAccessEntitlementYieldsEmpty() {
        stubEmptySearch("Etf", "XETR");
        stubSearch("Stock", "XETR", searchHit(211L, "RHM:xetr", "Stock", "XETR", "EUR"));
        stubInfoPrices("""
                {
                  "Uic": 211, "AssetType": "Stock",
                  "Quote": {
                    "DelayedByMinutes": 15, "MarketState": "Open",
                    "PriceTypeBid": "NoAccess", "PriceTypeAsk": "NoAccess"
                  }
                }
                """);

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        List<MarketEvent> events = adapter.fetchSymbolQuote(rhm);

        assertThat(events).isEmpty();
    }

    @Test
    void realtimeQuoteSetsDelayedFalse() {
        stubEmptySearch("Etf", "XETR");
        stubSearch("Stock", "XETR", searchHit(211L, "RHM:xetr", "Stock", "XETR", "EUR"));
        stubInfoPrices("""
                {
                  "Uic": 211, "AssetType": "Stock",
                  "Quote": {
                    "Mid": 615.60, "DelayedByMinutes": 0, "MarketState": "Open",
                    "PriceTypeBid": "Indicative", "PriceTypeAsk": "Indicative"
                  }
                }
                """);

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        List<MarketEvent> events = adapter.fetchSymbolQuote(rhm);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).rawData()).containsEntry("delayed", false);
        assertThat(events.get(0).rawData()).containsEntry("delayedByMinutes", 0);
    }

    @Test
    void usSymbolIsNotCoveredAndReturnsEmpty() {
        Symbol aapl = new Symbol("AAPL", InvestmentType.STOCK);
        assertThat(adapter.coversInstrument(aapl)).isFalse();
        assertThat(adapter.fetchSymbolQuote(aapl)).isEmpty();
        // No Saxo call should have been made for a US symbol.
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/ref/v1/instruments")));
    }

    @Test
    void unresolvableEuSymbolReturnsEmpty() {
        // Neither Etf nor Stock search returns a hit.
        stubEmptySearch("Etf", "XETR");
        stubEmptySearch("Stock", "XETR");

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        assertThat(adapter.fetchSymbolQuote(rhm)).isEmpty();
        // No InfoPrices call when the instrument did not resolve.
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/trade/v1/infoprices")));
    }

    @Test
    void coversInstrumentTrueForEuSymbol() {
        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        assertThat(adapter.coversInstrument(rhm)).isTrue();
        assertThat(adapter.getSupportedFeatures())
                .contains(com.quorumtrading.model.enums.MarketDataFeature.STOCK,
                        com.quorumtrading.model.enums.MarketDataFeature.EU_INSTRUMENT);
        assertThat(adapter.getProviderName()).isEqualTo("saxo");
    }
}
