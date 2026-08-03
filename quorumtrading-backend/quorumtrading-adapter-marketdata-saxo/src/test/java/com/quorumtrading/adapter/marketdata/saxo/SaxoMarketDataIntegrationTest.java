package com.quorumtrading.adapter.marketdata.saxo;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.SymbolParser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link SaxoMarketDataAdapter} against a <b>real Saxo LIVE account</b>.
 *
 * <p><b>Gated.</b> Runs only when {@code SAXO_INTEGRATION_TESTS=true}; otherwise the whole class is
 * skipped via {@link EnabledIfEnvironmentVariable} <em>before</em> Quarkus boots it, so CI and a
 * credential-less local {@code ./mvnw test} stay green and <b>never open a real socket</b>. Mirrors
 * {@code SaxoBrokerIntegrationTest} and {@code AlpacaBrokerIntegrationTest}.</p>
 *
 * <p><b>LIVE-only by necessity.</b> SAXO_ANSWERS' headline finding: Saxo SIM serves <em>no</em>
 * equity/ETF market data (Forex only) — so this read-only quote/charts path can only be exercised
 * against LIVE. Without an exchange subscription LIVE prices are delayed 15 min
 * ({@code DelayedByMinutes>0}); with no entitlement at all the quote reports {@code NoAccess}, which
 * the adapter maps to an empty result (not an error). The assertions therefore tolerate an empty
 * result and only insist that, <em>if</em> an event is produced, it is well-formed.</p>
 *
 * <h2>Required environment to run</h2>
 * <ul>
 *   <li>{@code SAXO_INTEGRATION_TESTS=true} — the gate;</li>
 *   <li>a valid per-account LIVE OAuth bearer reachable through the configured
 *       {@code BrokerTokenProviderPort} (the refresh service lands in Wave 4/5 — until then supply a
 *       token provider / fresh bearer via env/system properties);</li>
 *   <li>{@code quarkus.rest-client.saxo-openapi.url} / {@code quarkus.rest-client.saxo-md-ref.url}
 *       pointed at the real LIVE gateway (the test {@code application.properties} placeholders point
 *       at {@code http://localhost:1} for the WireMock suite, so override these via system
 *       properties / env when running against LIVE);</li>
 *   <li>{@code SAXO_INTEGRATION_SYMBOL} — optional; the EU symbol to price (defaults to
 *       {@code RHM:XETR}, Rheinmetall on Xetra).</li>
 * </ul>
 *
 * <p>Read-only by design: it fetches a quote and probes the earliest available chart sample. It
 * places no order and writes no state.</p>
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "SAXO_INTEGRATION_TESTS", matches = "true")
class SaxoMarketDataIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SaxoMarketDataIntegrationTest.class);

    /** Default EU instrument to price when no override is supplied. */
    private static final String DEFAULT_SYMBOL = "RHM:XETR";

    @Inject
    SaxoMarketDataAdapter adapter;

    private static Symbol targetSymbol() {
        String raw = System.getenv("SAXO_INTEGRATION_SYMBOL");
        String token = (raw == null || raw.isBlank()) ? DEFAULT_SYMBOL : raw.trim();
        return SymbolParser.parse(token, InvestmentType.STOCK);
    }

    @Test
    void adapterIsInjectableAndCoversTheEuSymbol() {
        assertNotNull(adapter, "SaxoMarketDataAdapter should be injectable");
        Symbol symbol = targetSymbol();
        assertTrue(symbol.isEuropean(),
                "SAXO_INTEGRATION_SYMBOL must be a European-venue symbol (carrying a non-US MIC)");
        assertTrue(adapter.coversInstrument(symbol),
                "adapter must cover the EU symbol " + symbol.canonical()
                        + " (is quorumtrading.marketdata.saxo.enabled=true?)");
    }

    @Test
    void fetchesAWellFormedLiveQuoteOrEmptyWhenUnentitled() {
        Symbol symbol = targetSymbol();

        // LIVE quote read-only. May legitimately be empty (NoAccess / unentitled exchange feed).
        List<MarketEvent> events = adapter.fetchSymbolQuote(symbol);
        log.info("Saxo LIVE quote for {}: {} event(s)", symbol.canonical(), events.size());

        if (events.isEmpty()) {
            log.warn("Saxo LIVE quote returned no event for {} — likely an entitlement gap "
                    + "(NoAccess) for this exchange feed; confirm the subscription per SAXO_ANSWERS §3.1.",
                    symbol.canonical());
            return;
        }

        MarketEvent event = events.get(0);
        assertTrue(event.eventType() == EventType.PRICE, "the event must be a PRICE event");
        assertTrue("saxo".equals(event.source()), "the event source must be 'saxo'");
        assertNotNull(event.rawData().get("price"), "a live quote must carry a price");
        assertNotNull(event.rawData().get("currency"), "a live quote must carry the venue currency");
        // The delay flag must be present so callers can tell realtime from exchange-delayed prices.
        assertTrue(event.rawData().containsKey("delayed"),
                "a live quote must carry the 'delayed' flag (0-min delay = realtime)");
        log.info("Saxo LIVE quote {}: price={} {} delayed={} delayedByMinutes={}",
                symbol.canonical(), event.rawData().get("price"), event.rawData().get("currency"),
                event.rawData().get("delayed"), event.rawData().get("delayedByMinutes"));
    }

    @Test
    void probesAnEmpiricalEarliestChartDateOrEmpty() {
        Symbol symbol = targetSymbol();

        // Earliest-available is measured by paginating Charts backward — never an assumed floor.
        Optional<LocalDate> earliest =
                adapter.earliestAvailable(symbol, EventType.PRICE, Granularity.ONE_DAY);
        log.info("Saxo LIVE earliest daily chart sample for {}: {}",
                symbol.canonical(), earliest.map(LocalDate::toString).orElse("<none / unentitled>"));

        // If history is available it must be a real past date; empty is acceptable (no entitlement).
        earliest.ifPresent(date -> assertTrue(!date.isAfter(LocalDate.now()),
                "the empirical earliest date cannot be in the future"));
    }

    @Test
    void backfillsRecentDailyBarsOrEmptyWhenUnentitled() {
        Symbol symbol = targetSymbol();
        Instant end = Instant.now();
        Instant start = end.minus(30, ChronoUnit.DAYS);

        List<MarketEvent> bars = adapter.fetchSymbolBars(
                symbol, SaxoChartsHorizon.ONE_DAY, start, end);
        log.info("Saxo LIVE daily backfill for {} over last 30d: {} bar(s)",
                symbol.canonical(), bars.size());

        // Each produced bar must be well-formed and inside the window.
        for (MarketEvent bar : bars) {
            assertTrue(bar.eventType() == EventType.PRICE, "each bar must be a PRICE event");
            assertTrue("saxo".equals(bar.source()), "each bar source must be 'saxo'");
            assertNotNull(bar.rawData().get("close"), "each bar must carry a close price");
            assertTrue(bar.rawData().get("close") instanceof BigDecimal,
                    "the close price must be a BigDecimal");
            assertTrue(!bar.timestamp().isBefore(start) && !bar.timestamp().isAfter(end),
                    "each bar's timestamp must fall inside the requested window");
        }
    }
}
