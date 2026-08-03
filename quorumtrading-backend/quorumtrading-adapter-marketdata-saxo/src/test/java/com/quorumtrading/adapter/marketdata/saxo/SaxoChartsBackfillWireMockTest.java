package com.quorumtrading.adapter.marketdata.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.marketdata.saxo.client.InjectWireMock;
import com.quorumtrading.adapter.marketdata.saxo.client.TestTokenProviderProducer;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThan;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock-backed tests for the Saxo historical OHLC backfill path ({@code MD3-charts-backfill}).
 *
 * <p>Drives the real {@link SaxoMarketDataAdapter} → {@link SaxoQuoteUicResolver} →
 * {@link SaxoChartsPaginator} chain against stubbed {@code /ref/v1/instruments} (Uic resolution) and
 * {@code /chart/v1/charts} (paginated history) surfaces via {@link SaxoMarketDataWireMockResource}.
 * Proves:</p>
 * <ul>
 *   <li><b>UpTo pagination stitches</b> — the paginator walks {@code Mode=UpTo} + {@code Count=1200}
 *       backward across multiple pages keyed by the {@code Time} anchor, de-duplicates by timestamp,
 *       returns the full window oldest→newest, and stamps each bar as a {@code PRICE}
 *       {@link MarketEvent} with {@code source="saxo"} and the venue currency; the bearer flows;</li>
 *   <li><b>earliest is empirical</b> — pagination stops once an older page returns nothing new, and
 *       {@link SaxoMarketDataAdapter#earliestAvailable} reports the measured oldest sample's date —
 *       never an assumed floor;</li>
 *   <li><b>empty (NoAccess/no-history) → empty</b>, gracefully.</li>
 * </ul>
 *
 * <p>All HTTP is stubbed; no real Saxo gateway is ever contacted.</p>
 */
@QuarkusTest
@QuarkusTestResource(value = SaxoMarketDataWireMockResource.class, restrictToAnnotatedClass = true)
class SaxoChartsBackfillWireMockTest {

    private static final String EXPECTED_BEARER = "Bearer " + TestTokenProviderProducer.TEST_ACCESS_TOKEN;
    private static final long UIC = 211L;

    /** Newest-page anchor: an instant at/after the most recent sample (the window end). */
    private static final String WINDOW_END = "2026-06-05T00:00:00Z";

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

    /**
     * Stubs the two-page backward walk. Page 1 (the first {@code UpTo} request — anchored at the
     * window end, or at <em>now</em> for {@code earliestAvailable}) returns the two newest days;
     * page 2 (anchor strictly before the oldest of page 1, i.e. {@code <= 2026-06-02}) returns two
     * older days. Any further page (anchor {@code < 2026-06-01}) returns an empty array, which is
     * the empirical earliest signal.
     *
     * <p>Pages 2 and 3 match on their specific anchor-date patterns at high precedence; page 1 is
     * a low-precedence catch-all so it also serves an {@code Instant.now()} anchor — a date-literal
     * pattern here would silently expire the test on the wall-clock day the pattern stops matching
     * (it did: the original {@code 2026-06-0[3-9]} pattern made the suite fail from June 10 on).</p>
     */
    private void stubTwoPageHistory() {
        // Page 2: older window — anchor at/just before 2026-06-02 (page-1 oldest minus one step).
        wireMock.stubFor(get(urlPathEqualTo("/chart/v1/charts"))
                .atPriority(1)
                .withQueryParam("Mode", equalTo("UpTo"))
                .withQueryParam("Count", equalTo("1200"))
                .withQueryParam("Time", matching("2026-06-0[0-2].*"))
                .willReturn(json(samples(
                        sample("2026-06-02T00:00:00Z", "102"),
                        sample("2026-06-01T00:00:00Z", "101")))));

        // Page 3+: anything strictly older than 2026-06-01 — the array stops growing
        // (empirical earliest reached: page-2 oldest 2026-06-01 minus one day = 2026-05-31).
        wireMock.stubFor(get(urlPathEqualTo("/chart/v1/charts"))
                .atPriority(1)
                .withQueryParam("Mode", equalTo("UpTo"))
                .withQueryParam("Count", equalTo("1200"))
                .withQueryParam("Time", matching("2026-05.*|2026-0[1-4].*|202[0-5].*"))
                .willReturn(json("{\"Data\": []}")));

        // Page 1: newest window — any anchor newer than the patterns above (window end or "now").
        wireMock.stubFor(get(urlPathEqualTo("/chart/v1/charts"))
                .atPriority(5)
                .withQueryParam("Mode", equalTo("UpTo"))
                .withQueryParam("Count", equalTo("1200"))
                .willReturn(json(samples(
                        sample("2026-06-04T00:00:00Z", "104"),
                        sample("2026-06-03T00:00:00Z", "103")))));
    }

    // --- tests --------------------------------------------------------------

    @Test
    void upToPaginationStitchesMultiplePagesIntoOrderedBars() {
        stubResolveRhmStock();
        stubTwoPageHistory();

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        List<MarketEvent> bars = adapter.fetchSymbolBars(
                rhm, SaxoChartsHorizon.ONE_DAY, null, Instant.parse(WINDOW_END));

        // All four days from both pages, de-duplicated and ordered oldest→newest.
        assertThat(bars).hasSize(4);
        assertThat(bars).extracting(MarketEvent::timestamp).isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(bars.get(0).timestamp()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(bars.get(3).timestamp()).isEqualTo(Instant.parse("2026-06-04T00:00:00Z"));

        MarketEvent first = bars.get(0);
        assertThat(first.eventType()).isEqualTo(EventType.PRICE);
        assertThat(first.source()).isEqualTo("saxo");
        assertThat(first.symbol()).isEqualTo("RHM:XETR");
        assertThat(first.rawData()).containsEntry("currency", "EUR");
        assertThat(first.rawData()).containsEntry("uic", UIC);
        assertThat((BigDecimal) first.rawData().get("close")).isEqualByComparingTo("101");
        assertThat((BigDecimal) first.rawData().get("high")).isEqualByComparingTo("101.50");
        assertThat((BigDecimal) first.rawData().get("low")).isEqualByComparingTo("100.50");
        assertThat((BigDecimal) first.rawData().get("open")).isEqualByComparingTo("100");

        // At least two backward pages were requested (stitching), each carrying the bearer.
        wireMock.verify(moreThan(1), getRequestedFor(urlPathEqualTo("/chart/v1/charts"))
                .withQueryParam("Mode", equalTo("UpTo"))
                .withHeader("Authorization", equalTo(EXPECTED_BEARER)));
    }

    @Test
    void rangeWindowClipsSamplesToStartAndEnd() {
        stubResolveRhmStock();
        stubTwoPageHistory();

        // Clip to the two middle days only.
        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        List<MarketEvent> bars = adapter.fetchSymbolBars(
                rhm, SaxoChartsHorizon.ONE_DAY,
                Instant.parse("2026-06-02T00:00:00Z"),
                Instant.parse("2026-06-03T00:00:00Z"));

        assertThat(bars).extracting(MarketEvent::timestamp)
                .containsExactly(
                        Instant.parse("2026-06-02T00:00:00Z"),
                        Instant.parse("2026-06-03T00:00:00Z"));
    }

    @Test
    void earliestAvailableIsTheEmpiricalOldestSample() {
        stubResolveRhmStock();
        stubTwoPageHistory();

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        Optional<LocalDate> earliest = adapter.earliestAvailable(rhm, EventType.PRICE, Granularity.ONE_DAY);

        // Measured, not assumed: the oldest stitched sample is 2026-06-01.
        assertThat(earliest).contains(LocalDate.of(2026, 6, 1));

        // The Charts call always used UpTo (the backward/oldest-ward walk) with the 1200 cap
        // and the daily horizon (1440 minutes).
        wireMock.verify(getRequestedFor(urlPathEqualTo("/chart/v1/charts"))
                .withQueryParam("Mode", equalTo("UpTo"))
                .withQueryParam("Count", equalTo("1200"))
                .withQueryParam("Horizon", equalTo("1440")));
    }

    @Test
    void emptyHistoryYieldsNoBars() {
        stubResolveRhmStock();
        // Every charts page is empty (NoAccess / no entitlement / no history).
        wireMock.stubFor(get(urlPathEqualTo("/chart/v1/charts"))
                .willReturn(json("{\"Data\": []}")));

        Symbol rhm = SymbolParser.parse("RHM:XETR", InvestmentType.STOCK);
        assertThat(adapter.fetchSymbolBars(rhm, SaxoChartsHorizon.ONE_DAY, null, Instant.parse(WINDOW_END)))
                .isEmpty();
        assertThat(adapter.earliestAvailable(rhm, EventType.PRICE, Granularity.ONE_DAY)).isEmpty();
    }

    @Test
    void fetchDataInRangeRoutesEuStockTickerThroughCharts() {
        stubResolveRhmStock();
        stubTwoPageHistory();

        List<MarketEvent> bars = adapter.fetchDataInRange(
                Set.of(),
                Set.of("RHM:XETR"),
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse(WINDOW_END),
                Granularity.ONE_DAY);

        assertThat(bars).hasSize(4);
        assertThat(bars).allSatisfy(e -> {
            assertThat(e.eventType()).isEqualTo(EventType.PRICE);
            assertThat(e.source()).isEqualTo("saxo");
            assertThat(e.rawData()).containsEntry("currency", "EUR");
        });
    }

    @Test
    void fetchDataInRangeIgnoresUsTickers() {
        // No stubs needed: a US ticker is not an EU instrument this adapter covers.
        List<MarketEvent> bars = adapter.fetchDataInRange(
                Set.of(),
                Set.of("AAPL"),
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse(WINDOW_END),
                Granularity.ONE_DAY);

        assertThat(bars).isEmpty();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/chart/v1/charts")));
    }

    // --- JSON builders ------------------------------------------------------

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private static String samples(String... rows) {
        return "{\"Data\": [" + String.join(",", rows) + "], \"ChartInfo\": {\"Horizon\": 1440}}";
    }

    /** One daily OHLC sample: open=close-1, high=close+0.5, low=close-0.5, volume present. */
    private static String sample(String time, String close) {
        BigDecimal c = new BigDecimal(close);
        return """
                {"Time": "%s", "Open": %s, "High": %s, "Low": %s, "Close": %s, "Volume": 1000}
                """.formatted(
                time,
                c.subtract(BigDecimal.ONE).toPlainString(),
                c.add(new BigDecimal("0.50")).toPlainString(),
                c.subtract(new BigDecimal("0.50")).toPlainString(),
                c.toPlainString());
    }
}
