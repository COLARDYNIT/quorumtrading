package com.quorumtrading.adapter.persistence.adapter;

import com.quorumtrading.adapter.persistence.entity.PriceBarJpaEntity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.PriceBar;
import com.quorumtrading.model.valueobject.Symbol;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class HistoricalPriceAdapterTest {

    // Valid symbols for testing (1-5 uppercase letters only)
    private static final String TEST_SYMBOL = "THIST";
    private static final Symbol TEST_SYMBOL_VO = new Symbol(TEST_SYMBOL, InvestmentType.STOCK);
    private static final String OTHER_SYMBOL = "OTHIS";

    @Inject
    HistoricalPriceAdapter adapter;

    @BeforeEach
    @Transactional
    void setup() {
        PriceBarJpaEntity.delete("symbol = ?1", TEST_SYMBOL);
        PriceBarJpaEntity.delete("symbol = ?1", OTHER_SYMBOL);
        PriceBarJpaEntity.delete("symbol = ?1", "TWIND");
        PriceBarJpaEntity.delete("symbol = ?1", "TNONE");
    }

    @Test
    @Transactional
    void shouldReturnEmptyListWhenNoData() {
        List<PriceBar> bars = adapter.getHistoricalPrices(new Symbol("NOEXT", InvestmentType.STOCK), 200);
        assertTrue(bars.isEmpty());
    }

    @Test
    @Transactional
    void shouldReturnPriceBarsOrderedByTimestamp() {
        Instant now = Instant.now();
        insertPriceBar(TEST_SYMBOL, now.minus(2, ChronoUnit.DAYS), 100.0, 105.0, 99.0, 102.0, 1000);
        insertPriceBar(TEST_SYMBOL, now.minus(1, ChronoUnit.DAYS), 102.0, 108.0, 101.0, 106.0, 1200);
        insertPriceBar(TEST_SYMBOL, now, 106.0, 110.0, 104.0, 109.0, 1100);

        List<PriceBar> bars = adapter.getHistoricalPrices(new Symbol(TEST_SYMBOL, InvestmentType.STOCK), 7);

        assertEquals(3, bars.size());
        assertTrue(bars.get(0).timestamp().isBefore(bars.get(1).timestamp()));
        assertTrue(bars.get(1).timestamp().isBefore(bars.get(2).timestamp()));
        assertEquals(0, new BigDecimal("102.0").compareTo(bars.get(0).close()));
        assertEquals(0, new BigDecimal("109.0").compareTo(bars.get(2).close()));
    }

    @Test
    @Transactional
    void shouldOnlyReturnDataWithinDaysLimit() {
        Instant now = Instant.now();
        insertPriceBar(TEST_SYMBOL, now.minus(10, ChronoUnit.DAYS), 90.0, 95.0, 89.0, 92.0, 800);
        insertPriceBar(TEST_SYMBOL, now.minus(3, ChronoUnit.DAYS), 100.0, 105.0, 99.0, 102.0, 1000);
        insertPriceBar(TEST_SYMBOL, now, 106.0, 110.0, 104.0, 109.0, 1100);

        List<PriceBar> bars = adapter.getHistoricalPrices(new Symbol(TEST_SYMBOL, InvestmentType.STOCK), 7);

        assertEquals(2, bars.size());
    }

    @Test
    @Transactional
    void shouldFilterBySymbol() {
        Instant now = Instant.now();
        insertPriceBar(TEST_SYMBOL, now, 100.0, 105.0, 99.0, 102.0, 1000);
        insertPriceBar(OTHER_SYMBOL, now, 200.0, 210.0, 195.0, 205.0, 2000);

        List<PriceBar> bars = adapter.getHistoricalPrices(new Symbol(TEST_SYMBOL, InvestmentType.STOCK), 7);

        assertEquals(1, bars.size());
        assertEquals(0, new BigDecimal("102.0").compareTo(bars.get(0).close()));
    }

    @Test
    @Transactional
    void shouldReturnEmptyCoverageWhenNoData() {
        var coverage = adapter.getCoverage(new Symbol("NOEXT", InvestmentType.STOCK));
        assertTrue(coverage.isEmpty());
        assertEquals(0, coverage.barCount());
        assertNull(coverage.firstBar());
        assertTrue(coverage.sources().isEmpty());
    }

    @Test
    @Transactional
    void shouldSummarizeCoverageWithCountRangeAndSources() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        insertPriceBar(TEST_SYMBOL, now.minus(2, ChronoUnit.DAYS), 100.0, 105.0, 99.0, 102.0, 1000);
        insertPriceBar(TEST_SYMBOL, now.minus(1, ChronoUnit.DAYS), 102.0, 108.0, 101.0, 106.0, 1200);
        insertPriceBar(TEST_SYMBOL, now, 106.0, 110.0, 104.0, 109.0, 1100);
        insertPriceBar(OTHER_SYMBOL, now, 200.0, 210.0, 195.0, 205.0, 2000);

        var coverage = adapter.getCoverage(new Symbol(TEST_SYMBOL, InvestmentType.STOCK));

        assertEquals(3, coverage.barCount());
        assertEquals(3, coverage.distinctDays());
        assertEquals(now.minus(2, ChronoUnit.DAYS), coverage.firstBar());
        assertEquals(now, coverage.lastBar());
        assertEquals(List.of("test"), coverage.sources());
    }

    // =====================================================================
    // Intraday -> daily collapse
    //
    // DISTINCT ON exists precisely to collapse intraday bars to one row per day, yet every test
    // above inserts exactly one bar per day — so which bar it keeps was never asserted. It kept
    // the OLDEST, making the "daily close" roughly the day's opening price. Indicators computed
    // from that series (RSI, SMA, Bollinger) were reading opens labelled as closes, which is how
    // an RSI-14 came to rise on a down day.
    //
    // Two of the three read paths collapse: the day-count query and the explicit-resolution
    // query. The plain (from, to) range query deliberately returns raw bars and is not asserted
    // here — writing these tests against it first is what made that distinction visible.
    // =====================================================================

    @Test
    @Transactional
    void shouldKeepTheLastBarOfEachDayWhenCollapsingIntradayData() {
        Instant day = Instant.parse("2026-06-01T00:00:00Z");
        insertPriceBar(TEST_SYMBOL, day.plus(1, ChronoUnit.HOURS), 100.0, 101.0, 99.0, 100.5, 10);
        insertPriceBar(TEST_SYMBOL, day.plus(8, ChronoUnit.HOURS), 100.5, 106.0, 100.0, 105.0, 20);
        insertPriceBar(TEST_SYMBOL, day.plus(15, ChronoUnit.HOURS), 105.0, 110.0, 104.0, 109.0, 30);

        List<PriceBar> bars = adapter.getHistoricalPrices(
                TEST_SYMBOL_VO, day, day.plus(1, ChronoUnit.DAYS), "1day");

        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).close())
                .as("the daily close must be the last bar of the day, not the first")
                .isEqualByComparingTo(BigDecimal.valueOf(109.0));
    }

    @Test
    @Transactional
    void shouldCollapseEachDayIndependentlyToItsOwnLastBar() {
        Instant d1 = Instant.parse("2026-06-01T00:00:00Z");
        Instant d2 = Instant.parse("2026-06-02T00:00:00Z");
        insertPriceBar(TEST_SYMBOL, d1.plus(1, ChronoUnit.HOURS), 100.0, 101.0, 99.0, 100.0, 10);
        insertPriceBar(TEST_SYMBOL, d1.plus(20, ChronoUnit.HOURS), 100.0, 103.0, 99.0, 102.0, 10);
        insertPriceBar(TEST_SYMBOL, d2.plus(1, ChronoUnit.HOURS), 102.0, 104.0, 101.0, 103.0, 10);
        insertPriceBar(TEST_SYMBOL, d2.plus(20, ChronoUnit.HOURS), 103.0, 108.0, 102.0, 107.0, 10);

        List<PriceBar> bars = adapter.getHistoricalPrices(
                TEST_SYMBOL_VO, d1, d2.plus(1, ChronoUnit.DAYS), "1day");

        assertThat(bars).hasSize(2);
        assertThat(bars).extracting(b -> b.close().doubleValue())
                .as("a falling close after a rising one is exactly the shape indicators need")
                .containsExactly(102.0, 107.0);
    }

    /**
     * The day-count query must collapse a day to its LAST bar, exactly like the
     * explicit-resolution query above.
     *
     * <p>Both the bars and the as-of instant are pinned. This test used to derive the day from
     * {@code Instant.now()} and place the second bar at +10h, then read through the two-argument
     * overload whose as-of is {@code Instant.now()} — so before 10:00 UTC that bar was in the
     * future, the {@code timestamp <= asOf} filter dropped it, and the surviving first bar was
     * indistinguishable from the collapse keeping the wrong end of the day. The test failed
     * every morning and passed every afternoon, against correct production code.</p>
     */
    @Test
    @Transactional
    void shouldKeepTheLastBarWhenUsingTheDayCountQuery() {
        Instant day = Instant.parse("2026-06-10T00:00:00Z");
        Instant asOf = day.plus(23, ChronoUnit.HOURS);
        Instant firstBar = day.plus(1, ChronoUnit.HOURS);
        Instant lastBar = day.plus(10, ChronoUnit.HOURS);
        insertPriceBar(TEST_SYMBOL, firstBar, 50.0, 51.0, 49.0, 50.0, 10);
        insertPriceBar(TEST_SYMBOL, lastBar, 50.0, 60.0, 50.0, 59.0, 10);

        List<PriceBar> bars = adapter.getHistoricalPrices(TEST_SYMBOL_VO, 5, asOf);

        assertThat(bars).hasSize(1);
        // Assert the surviving row is the later bar, not merely that its close reads 59. A
        // close-only assertion is also satisfied by the later bar never being fetched at all.
        assertThat(bars.get(0).timestamp())
                .as("the row that survives the collapse must be the day's last bar")
                .isEqualTo(lastBar);
        assertThat(bars.get(0).close())
                .as("the two daily-collapse queries must agree on which bar is the close")
                .isEqualByComparingTo(BigDecimal.valueOf(59.0));
    }

    /**
     * A venue-qualified symbol must never be served the US root's bars.
     *
     * <p>{@code AZN} is AstraZeneca's New York depositary receipt in dollars;
     * {@code AZN:XLON} is its London ordinary share in pounds. They are different
     * securities. When lookups keyed on the bare root, asking for the London line
     * returned the New York bars, and a simulation labelled {@code AZN:XLON} bought
     * the receipt at a USD price and reported a completed London backtest.</p>
     *
     * <p>Returning nothing is the correct answer: an absence is visible and
     * recoverable, whereas another instrument's prices are neither.</p>
     */
    @Test
    @Transactional
    void venueQualifiedSymbolMustNotReadTheUsRootsBars() {
        Instant now = Instant.now();
        insertPriceBar(TEST_SYMBOL, now.minus(2, ChronoUnit.DAYS), 100.0, 105.0, 99.0, 102.0, 1000);
        insertPriceBar(TEST_SYMBOL, now.minus(1, ChronoUnit.DAYS), 102.0, 108.0, 101.0, 106.0, 1200);

        Symbol londonLine = Symbol.parse(TEST_SYMBOL + ":XLON");

        assertThat(adapter.getHistoricalPrices(londonLine, 7))
            .as("the London listing has no stored bars; the US root's must not stand in for them")
            .isEmpty();
        assertThat(adapter.getCoverage(londonLine).barCount())
            .as("coverage for a venue with no data must report none, not the root's count")
            .isZero();
    }

    @Test
    @Transactional
    void bareUsSymbolStillReadsItsOwnBars() {
        // The corollary that keeps the change non-breaking: for a US instrument the
        // canonical form IS the bare ticker, so every existing lookup is untouched.
        Instant now = Instant.now();
        insertPriceBar(TEST_SYMBOL, now.minus(1, ChronoUnit.DAYS), 102.0, 108.0, 101.0, 106.0, 1200);

        assertThat(adapter.getHistoricalPrices(TEST_SYMBOL_VO, 7)).hasSize(1);
        assertThat(adapter.getCoverage(TEST_SYMBOL_VO).barCount()).isEqualTo(1);
    }

    // --- windowed coverage: the question first/last bar cannot answer ---

    /** A crypto symbol so every calendar day in the window is expected to carry a bar. */
    private static final Symbol WINDOW_SYMBOL = new Symbol("TWIND", InvestmentType.CRYPTO);

    @Test
    @Transactional
    void windowedCoverageCountsOnlyBarsInsideTheWindow() {
        insertPriceBar("TWIND", Instant.parse("2025-12-31T00:00:00Z"), 1, 1, 1, 1, 1);
        insertPriceBar("TWIND", Instant.parse("2026-01-01T00:00:00Z"), 1, 1, 1, 1, 1);
        insertPriceBar("TWIND", Instant.parse("2026-01-02T00:00:00Z"), 1, 1, 1, 1, 1);
        insertPriceBar("TWIND", Instant.parse("2026-01-09T00:00:00Z"), 1, 1, 1, 1, 1);

        var coverage = adapter.getCoverage(WINDOW_SYMBOL,
            java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 5));

        assertThat(coverage.barCount())
            .as("the headline count is still every stored bar, window or no window")
            .isEqualTo(4);
        assertThat(coverage.window()).isNotNull();
        assertThat(coverage.window().expectedDays()).isEqualTo(5);
        assertThat(coverage.window().coveredDays()).isEqualTo(2);
        assertThat(coverage.window().gaps()).hasSize(1);
        assertThat(coverage.window().gaps().get(0).from()).isEqualTo(java.time.LocalDate.of(2026, 1, 3));
        assertThat(coverage.window().gaps().get(0).to()).isEqualTo(java.time.LocalDate.of(2026, 1, 5));
    }

    @Test
    @Transactional
    void windowedCoverageOnAnEmptySymbolIsOneWholeGapRatherThanSilence() {
        var coverage = adapter.getCoverage(new Symbol("TNONE", InvestmentType.CRYPTO),
            java.time.LocalDate.of(2026, 3, 1), java.time.LocalDate.of(2026, 3, 3));

        assertThat(coverage.isEmpty()).isTrue();
        assertThat(coverage.window().coveredDays()).isZero();
        assertThat(coverage.window().gaps()).hasSize(1);
        assertThat(coverage.window().gaps().get(0).days()).isEqualTo(3);
    }

    @Test
    @Transactional
    void windowedCoverageReportsACompleteWindowAsComplete() {
        insertPriceBar("TWIND", Instant.parse("2026-02-01T12:00:00Z"), 1, 1, 1, 1, 1);
        insertPriceBar("TWIND", Instant.parse("2026-02-02T12:00:00Z"), 1, 1, 1, 1, 1);

        var coverage = adapter.getCoverage(WINDOW_SYMBOL,
            java.time.LocalDate.of(2026, 2, 1), java.time.LocalDate.of(2026, 2, 2));

        assertThat(coverage.window().isComplete()).isTrue();
        assertThat(coverage.window().missingDays()).isZero();
    }

    private void insertPriceBar(String symbol, Instant timestamp,
            double open, double high, double low, double close, long volume) {
        PriceBarJpaEntity entity = new PriceBarJpaEntity();
        entity.timestamp = timestamp;
        entity.source = "test";
        entity.symbol = symbol;
        entity.ingestedAt = Instant.now();
        entity.open = BigDecimal.valueOf(open);
        entity.high = BigDecimal.valueOf(high);
        entity.low = BigDecimal.valueOf(low);
        entity.close = BigDecimal.valueOf(close);
        entity.volume = BigDecimal.valueOf(volume);
        entity.persist();
    }
}
