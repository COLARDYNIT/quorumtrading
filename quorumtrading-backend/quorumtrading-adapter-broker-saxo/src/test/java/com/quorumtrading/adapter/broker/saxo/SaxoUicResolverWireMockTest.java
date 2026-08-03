package com.quorumtrading.adapter.broker.saxo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quorumtrading.adapter.broker.saxo.SaxoReferenceDataWireMockResource.InjectRefWireMock;
import com.quorumtrading.adapter.broker.saxo.SaxoUicResolver.ResolvedInstrument;
import com.quorumtrading.model.enums.AssetType;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock-backed tests for {@link SaxoUicResolver} (SB3).
 *
 * <p>Drives the real Quarkus {@code saxo-ref} rest-client + Jackson through
 * {@link SaxoReferenceDataWireMockResource}, stubbing Saxo's {@code /ref/v1/instruments} search
 * and {@code /ref/v1/instruments/details/{uic}/{assetType}} details endpoints to prove:</p>
 * <ul>
 *   <li><b>Symbol → Uic</b> — a venue-pinned search + details resolves the {@code (Uic, AssetType,
 *       ExchangeId)} triple, and the search was pinned with the Saxo {@code ExchangeId} mapped from
 *       the symbol's MIC;</li>
 *   <li><b>IsTradable true / false / 404</b> — tradability is surfaced from the details flag, and a
 *       404 on details (no listing on this venue) yields an empty resolution;</li>
 *   <li><b>Etf vs Stock fallback</b> — the resolver tries {@code Etf} first then falls back to
 *       {@code Stock} when {@code Etf} returns no hit;</li>
 *   <li><b>lot / min-value read</b> — {@code LotSize} / {@code MinimumTradeSize} /
 *       {@code MinimumOrderValue} / {@code AmountDecimals} surface onto the resolution; and</li>
 *   <li><b>multi-listing via ExchangeId</b> — two listings of the same root on different venues
 *       resolve to the correct venue's Uic because the search is pinned by {@code ExchangeId}.</li>
 * </ul>
 */
@QuarkusTest
@QuarkusTestResource(SaxoReferenceDataWireMockResource.class)
class SaxoUicResolverWireMockTest {

    @Inject
    SaxoUicResolver resolver;

    @InjectRefWireMock
    WireMockServer wireMock;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
        resolver.clearCache();
    }

    // --- search stubs -------------------------------------------------------

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
                  {"Identifier": %d, "Symbol": "%s", "Description": "Test Instr",
                   "AssetType": "%s", "ExchangeId": "%s", "CurrencyCode": "%s"}
                ]}
                """.formatted(uic, symbol, assetType, exchangeId, ccy);
    }

    private void stubDetails(long uic, String assetType, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments/details/" + uic + "/" + assetType))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubDetails404(long uic, String assetType) {
        wireMock.stubFor(get(urlPathEqualTo("/ref/v1/instruments/details/" + uic + "/" + assetType))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ErrorCode\": \"NotFound\"}")));
    }

    private static String detailsBody(long uic, String assetType, String exchangeId, String ccy,
                                      boolean tradable, String lotSize, String minTradeSize,
                                      String minOrderValue, int amountDecimals) {
        return """
                {"Uic": %d, "AssetType": "%s", "Symbol": "RHM:xetr", "Description": "Rheinmetall AG",
                 "CurrencyCode": "%s", "ExchangeId": "%s", "IsTradable": %s, "PrimaryListing": %d,
                 "LotSize": %s, "MinimumTradeSize": %s, "MinimumOrderValue": %s, "AmountDecimals": %d,
                 "TickSize": 0.01}
                """.formatted(uic, assetType, ccy, exchangeId, tradable, uic,
                lotSize, minTradeSize, minOrderValue, amountDecimals);
    }

    // --- tests --------------------------------------------------------------

    /** Symbol → Uic: a Xetra symbol resolves to its Uic + Stock AssetType, search pinned by ExchangeId. */
    @Test
    void resolvesSymbolToUicAndAssetTypePinnedByExchangeId() {
        // Etf tried first → no hit; Stock → the real hit.
        stubEmptySearch("Etf", "XETR");
        stubSearch("Stock", "XETR", searchHit(210840, "RHM:xetr", "Stock", "XETR", "EUR"));
        stubDetails(210840, "Stock",
                detailsBody(210840, "Stock", "XETR", "EUR", true, "1", "1", "0", 0));

        Optional<ResolvedInstrument> resolved = resolver.resolve(Symbol.parse("RHM:XETR"));

        assertThat(resolved).isPresent();
        ResolvedInstrument r = resolved.get();
        assertThat(r.uic()).isEqualTo(210840L);
        assertThat(r.assetType()).isEqualTo(AssetType.STOCK);
        assertThat(r.currency()).isEqualTo(Money.Currency.EUR);
        assertThat(r.tradable()).isTrue();

        // The search was venue-pinned with the Saxo ExchangeId mapped from the MIC (XETR → XETR).
        wireMock.verify(getRequestedFor(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("Keywords", equalTo("RHM"))
                .withQueryParam("AssetTypes", equalTo("Stock"))
                .withQueryParam("ExchangeId", equalTo("XETR")));
    }

    /** IsTradable true → resolution reports tradable. */
    @Test
    void isTradableTrueSurfaces() {
        stubEmptySearch("Etf", "XETR");
        stubSearch("Stock", "XETR", searchHit(1, "RHM:xetr", "Stock", "XETR", "EUR"));
        stubDetails(1, "Stock", detailsBody(1, "Stock", "XETR", "EUR", true, "1", "1", "0", 0));

        assertThat(resolver.resolve(Symbol.parse("RHM:XETR")).orElseThrow().tradable()).isTrue();
    }

    /** IsTradable false → resolves (instrument exists) but is reported not tradable. */
    @Test
    void isTradableFalseSurfaces() {
        stubEmptySearch("Etf", "XETR");
        stubSearch("Stock", "XETR", searchHit(2, "RHM:xetr", "Stock", "XETR", "EUR"));
        stubDetails(2, "Stock", detailsBody(2, "Stock", "XETR", "EUR", false, "1", "1", "0", 0));

        ResolvedInstrument r = resolver.resolve(Symbol.parse("RHM:XETR")).orElseThrow();
        assertThat(r.uic()).isEqualTo(2L);
        assertThat(r.tradable()).isFalse();
    }

    /** Details 404 on both Etf and Stock (no listing on this venue) → empty resolution. */
    @Test
    void detailsNotFound404YieldsEmpty() {
        // Etf search empty; Stock search returns a hit but its details 404 (delisted/no longer present).
        stubEmptySearch("Etf", "XETR");
        stubSearch("Stock", "XETR", searchHit(3, "RHM:xetr", "Stock", "XETR", "EUR"));
        stubDetails404(3, "Stock");

        assertThat(resolver.resolve(Symbol.parse("RHM:XETR"))).isEmpty();
    }

    /** No hit on either Etf or Stock search → empty resolution. */
    @Test
    void noSearchHitYieldsEmpty() {
        stubEmptySearch("Etf", "XETR");
        stubEmptySearch("Stock", "XETR");

        assertThat(resolver.resolve(Symbol.parse("RHM:XETR"))).isEmpty();
    }

    /** Etf vs Stock: an ETF symbol resolves on the Etf branch (tried first), Stock not consulted. */
    @Test
    void resolvesEtfOnFirstBranchWithoutFallingBackToStock() {
        stubSearch("Etf", "XAMS", searchHit(4001, "IWDA:xams", "Etf", "XAMS", "EUR"));
        stubDetails(4001, "Etf", detailsBody(4001, "Etf", "XAMS", "EUR", true, "1", "1", "0", 0));

        ResolvedInstrument r = resolver.resolve(Symbol.parse("IWDA:XAMS")).orElseThrow();
        assertThat(r.uic()).isEqualTo(4001L);
        assertThat(r.assetType()).isEqualTo(AssetType.ETF);

        // Etf branch hit → Stock search must NOT have been issued.
        wireMock.verify(getRequestedFor(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Etf")));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Stock")));
    }

    /**
     * Etf vs Stock fallback: a registration-dependent ETF that Saxo returns under {@code Stock}
     * (the legacy-app case, §2.4) resolves on the Stock fallback after the Etf branch is empty.
     */
    @Test
    void fallsBackToStockWhenEtfBranchEmpty() {
        stubEmptySearch("Etf", "XAMS");
        stubSearch("Stock", "XAMS", searchHit(4002, "IWDA:xams", "Stock", "XAMS", "EUR"));
        stubDetails(4002, "Stock", detailsBody(4002, "Stock", "XAMS", "EUR", true, "1", "1", "0", 0));

        ResolvedInstrument r = resolver.resolve(Symbol.parse("IWDA:XAMS")).orElseThrow();
        assertThat(r.uic()).isEqualTo(4002L);
        assertThat(r.assetType()).isEqualTo(AssetType.STOCK);

        // Both branches were consulted: Etf first (empty), then Stock.
        wireMock.verify(getRequestedFor(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Etf")));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/ref/v1/instruments"))
                .withQueryParam("AssetTypes", equalTo("Stock")));
    }

    /** lot / min-value: per-listing trading constraints surface onto the resolution. */
    @Test
    void surfacesLotSizeAndMinimumOrderValue() {
        stubEmptySearch("Etf", "XPAR");
        stubSearch("Stock", "XPAR", searchHit(5000, "MC:xpar", "Stock", "XPAR", "EUR"));
        // LotSize 5 (board lots of 5), MinimumTradeSize 5, MinimumOrderValue 1000, AmountDecimals 0.
        stubDetails(5000, "Stock", detailsBody(5000, "Stock", "XPAR", "EUR", true, "5", "5", "1000", 0));

        ResolvedInstrument r = resolver.resolve(Symbol.parse("MC:XPAR")).orElseThrow();
        assertThat(r.lotSize()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(r.minimumTradeSize()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(r.minimumOrderValue()).isEqualByComparingTo(new BigDecimal("1000"));
        // AmountDecimals 0 ⇒ whole-share-only (non-fractional) equity.
        assertThat(r.fractional()).isFalse();
    }

    /**
     * Multi-listing via ExchangeId: the same root (RHM) lists on Xetra and (hypothetically) Paris;
     * resolving the Xetra symbol pins ExchangeId=XETR and gets the Xetra Uic, not the Paris one.
     */
    @Test
    void multiListingResolvesCorrectVenueViaExchangeId() {
        // Xetra listing.
        stubEmptySearch("Etf", "XETR");
        stubSearch("Stock", "XETR", searchHit(210840, "RHM:xetr", "Stock", "XETR", "EUR"));
        stubDetails(210840, "Stock", detailsBody(210840, "Stock", "XETR", "EUR", true, "1", "1", "0", 0));
        // Paris listing of the same root, different Uic — pinned to a different ExchangeId.
        stubEmptySearch("Etf", "XPAR");
        stubSearch("Stock", "XPAR", searchHit(999999, "RHM:xpar", "Stock", "XPAR", "EUR"));
        stubDetails(999999, "Stock", detailsBody(999999, "Stock", "XPAR", "EUR", true, "1", "1", "0", 0));

        ResolvedInstrument xetra = resolver.resolve(Symbol.parse("RHM:XETR")).orElseThrow();
        assertThat(xetra.uic()).isEqualTo(210840L);

        resolver.clearCache();
        ResolvedInstrument paris = resolver.resolve(Symbol.parse("RHM:XPAR")).orElseThrow();
        assertThat(paris.uic()).isEqualTo(999999L);
    }

    /**
     * Search-hit disambiguation: a keyword search that returns several roots picks the hit whose
     * symbol root matches the requested ticker (not merely the first row).
     */
    @Test
    void picksHitWhoseRootMatchesTicker() {
        stubEmptySearch("Etf", "XETR");
        // Search returns a near-name first, then the exact RHM root.
        stubSearch("Stock", "XETR", """
                {"Data": [
                  {"Identifier": 111, "Symbol": "RHMX:xetr", "Description": "Other", "AssetType": "Stock",
                   "ExchangeId": "XETR", "CurrencyCode": "EUR"},
                  {"Identifier": 210840, "Symbol": "RHM:xetr", "Description": "Rheinmetall", "AssetType": "Stock",
                   "ExchangeId": "XETR", "CurrencyCode": "EUR"}
                ]}
                """);
        stubDetails(210840, "Stock", detailsBody(210840, "Stock", "XETR", "EUR", true, "1", "1", "0", 0));

        ResolvedInstrument r = resolver.resolve(Symbol.parse("RHM:XETR")).orElseThrow();
        assertThat(r.uic()).isEqualTo(210840L);
    }

    /** Cache: a second resolve of the same symbol does not re-hit the search/details endpoints. */
    @Test
    void cachesSuccessfulResolution() {
        stubEmptySearch("Etf", "XETR");
        stubSearch("Stock", "XETR", searchHit(210840, "RHM:xetr", "Stock", "XETR", "EUR"));
        stubDetails(210840, "Stock", detailsBody(210840, "Stock", "XETR", "EUR", true, "1", "1", "0", 0));

        resolver.resolve(Symbol.parse("RHM:XETR"));
        resolver.resolve(Symbol.parse("RHM:XETR"));

        // Details fetched exactly once despite two resolves (cache hit on the second).
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/ref/v1/instruments/details/210840/Stock")));
    }
}
