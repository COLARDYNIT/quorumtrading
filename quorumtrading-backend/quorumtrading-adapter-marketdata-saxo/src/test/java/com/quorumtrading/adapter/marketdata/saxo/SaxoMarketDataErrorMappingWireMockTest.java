package com.quorumtrading.adapter.marketdata.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.marketdata.saxo.client.InjectWireMock;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * WireMock-backed tests for how the Saxo market-data adapter maps Saxo HTTP error responses
 * ({@code 401}/{@code 429}/{@code 503}) — the resilience matrix the WP requires.
 *
 * <p>The adapter is deliberately fail-soft: an instrument that resolves but whose price/chart
 * endpoint returns an auth gap ({@code 401}), a throttle ({@code 429}), or a transient venue
 * outage ({@code 503}) yields <b>no event</b> for that ticker and <b>never throws</b>, so a single
 * unentitled or rate-limited symbol cannot abort a multi-ticker run. (Saxo surfaces 429 with
 * {@code X-RateLimit-*} headers per SAXO_ANSWERS §1.6; 401 is an expired/invalid bearer; 503 a
 * gateway blip.) The rest-client raises a {@code WebApplicationException} (a {@code RuntimeException})
 * on those statuses, which the adapter's quote/charts paths catch and degrade to an empty result.</p>
 *
 * <p>All HTTP is stubbed; no real Saxo gateway is ever contacted.</p>
 */
@QuarkusTest
@QuarkusTestResource(value = SaxoMarketDataWireMockResource.class, restrictToAnnotatedClass = true)
class SaxoMarketDataErrorMappingWireMockTest {

    private static final long UIC = 211L;

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

    /** Resolve RHM:XETR to a Stock Uic so the error path is exercised on the price/chart call. */
    private void stubResolveRhmStock() {
        wireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Etf"))
                .willReturn(json("{\"Data\": []}")));
        wireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Stock"))
                .willReturn(json("""
                        {"Data": [
                          {"Identifier": 211, "Symbol": "RHM:xetr", "Description": "Rheinmetall AG",
                           "AssetType": "Stock", "ExchangeId": "XETR", "CurrencyCode": "EUR"}
                        ]}
                        """)));
    }

    private void stubInfoPricesStatus(int status) {
        wireMock.stubFor(get(urlPathEqualTo("/trade/v1/infoprices"))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"Message\": \"error\"}")));
    }

    private void stubChartsStatus(int status) {
        wireMock.stubFor(get(urlPathEqualTo("/chart/v1/charts"))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"Message\": \"error\"}")));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    // --- tests --------------------------------------------------------------

    @ParameterizedTest(name = "InfoPrices HTTP {0} → empty quote, no throw")
    @ValueSource(ints = {401, 429, 503})
    void infoPricesErrorStatusYieldsEmptyQuoteGracefully(int status) {
        stubResolveRhmStock();
        stubInfoPricesStatus(status);

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);

        List<MarketEvent> events = adapter.fetchSymbolQuote(rhm);
        // Fail-soft: the throttled/unauthorised/unavailable feed produces no event, not an error.
        assertThat(events).isEmpty();
    }

    @ParameterizedTest(name = "InfoPrices HTTP {0} never propagates an exception")
    @ValueSource(ints = {401, 429, 503})
    void infoPricesErrorStatusNeverThrows(int status) {
        stubResolveRhmStock();
        stubInfoPricesStatus(status);

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        assertThatCode(() -> adapter.fetchSymbolQuote(rhm)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "Charts HTTP {0} → empty backfill + empty earliest, no throw")
    @ValueSource(ints = {401, 429, 503})
    void chartsErrorStatusYieldsEmptyBackfillGracefully(int status) {
        stubResolveRhmStock();
        stubChartsStatus(status);

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);

        // The Charts paginator swallows the error page (treats it as "no more data"), so the
        // backward stitch ends empty and earliestAvailable degrades to empty — never an error.
        List<MarketEvent> bars = adapter.fetchSymbolBars(
                rhm, SaxoChartsHorizon.ONE_DAY, null, Instant.parse("2026-06-05T00:00:00Z"));
        assertThat(bars).isEmpty();

        Optional<LocalDate> earliest =
                adapter.earliestAvailable(rhm, EventType.PRICE, Granularity.ONE_DAY);
        assertThat(earliest).isEmpty();
    }

    @ParameterizedTest(name = "instrument-search HTTP {0} → unresolved → empty, no throw")
    @ValueSource(ints = {401, 429, 503})
    void instrumentSearchErrorStatusYieldsEmptyResolution(int status) {
        // Both asset-type searches fail with the error status — the symbol never resolves, so the
        // adapter returns empty and makes no InfoPrices call (the resolver catches the HTTP error).
        wireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments"))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"Message\": \"error\"}")));

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        assertThatCode(() -> {
            assertThat(adapter.fetchSymbolQuote(rhm)).isEmpty();
        }).doesNotThrowAnyException();
    }
}
