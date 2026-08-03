package com.quorumtrading.application.service;

import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for IndicatorCalculationService.
 * Verifies mathematical accuracy of technical indicator calculations.
 */
class IndicatorCalculationServiceTest {

    private IndicatorCalculationService service;

    @BeforeEach
    void setUp() {
        service = new IndicatorCalculationService();
    }

    // ========== SMA Tests ==========

    @Test
    void calculateSMA_shouldReturnCorrectAverage_withSufficientData() {
        // Given: 5 prices, calculate 3-period SMA
        List<BigDecimal> prices = Arrays.asList(
            new BigDecimal("10.00"),
            new BigDecimal("12.00"),
            new BigDecimal("14.00"),
            new BigDecimal("16.00"),
            new BigDecimal("18.00")
        );
        Instant now = Instant.now();

        // When
        Optional<MovingAverage> result = service.calculateSMA(prices, 3, now);

        // Then: SMA of last 3 = (14 + 16 + 18) / 3 = 16.00
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualByComparingTo("16.00");
        assertThat(result.get().period()).isEqualTo(3);
        assertThat(result.get().calculatedAt()).isEqualTo(now);
    }

    @Test
    void calculateSMA_shouldReturnEmpty_withInsufficientData() {
        // Given: Only 2 prices, need 5
        List<BigDecimal> prices = Arrays.asList(
            new BigDecimal("10.00"),
            new BigDecimal("12.00")
        );

        // When
        Optional<MovingAverage> result = service.calculateSMA(prices, 5, Instant.now());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void calculateSMA_shouldHandleSinglePrice() {
        // Given: Single price, period = 1
        List<BigDecimal> prices = List.of(new BigDecimal("100.50"));

        // When
        Optional<MovingAverage> result = service.calculateSMA(prices, 1, Instant.now());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualByComparingTo("100.50");
    }

    // ========== RSI Tests ==========

    @Test
    void calculateRSI_shouldReturnCorrectValue_withMixedGainsAndLosses() {
        // Given: Prices with gains and losses
        List<BigDecimal> prices = Arrays.asList(
            new BigDecimal("100"),
            new BigDecimal("102"), // +2 gain
            new BigDecimal("101"), // -1 loss
            new BigDecimal("103"), // +2 gain
            new BigDecimal("105"), // +2 gain
            new BigDecimal("104")  // -1 loss
        );

        // When: Calculate 4-period RSI
        Optional<RSI> result = service.calculateRSI(prices, 4, Instant.now());

        // Then: Last 4 changes: +2, -1, +2, -1
        // avgGain = (2+2)/4 = 1.0, avgLoss = (1+1)/4 = 0.5
        // RS = 1.0/0.5 = 2.0, RSI = 100 - (100/(1+2)) = 66.67
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualByComparingTo("66.67");
    }

    @Test
    void calculateRSI_shouldReturn100_whenAllGains() {
        // Given: All rising prices
        List<BigDecimal> prices = Arrays.asList(
            new BigDecimal("100"),
            new BigDecimal("102"),
            new BigDecimal("104"),
            new BigDecimal("106")
        );

        // When
        Optional<RSI> result = service.calculateRSI(prices, 3, Instant.now());

        // Then: avgLoss = 0, RSI = 100
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualByComparingTo("100.00");
        assertThat(result.get().isOverbought()).isTrue();
    }

    @Test
    void calculateRSI_shouldReturn0_whenAllLosses() {
        // Given: All falling prices
        List<BigDecimal> prices = Arrays.asList(
            new BigDecimal("100"),
            new BigDecimal("98"),
            new BigDecimal("96"),
            new BigDecimal("94")
        );

        // When
        Optional<RSI> result = service.calculateRSI(prices, 3, Instant.now());

        // Then: avgGain = 0, RSI = 0
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualByComparingTo("0.00");
        assertThat(result.get().isOversold()).isTrue();
    }

    @Test
    void calculateRSI_shouldReturnEmpty_withInsufficientData() {
        // Given: Only 3 prices, need 5 for 4-period RSI (period + 1)
        List<BigDecimal> prices = Arrays.asList(
            new BigDecimal("100"),
            new BigDecimal("102"),
            new BigDecimal("101")
        );

        // When
        Optional<RSI> result = service.calculateRSI(prices, 4, Instant.now());

        // Then
        assertThat(result).isEmpty();
    }

    // ========== MACD Tests ==========

    @Test
    void calculateMACD_shouldReturnCorrectValues_withSufficientData() {
        // Given: Trending prices
        List<BigDecimal> prices = Arrays.asList(
            bd("10"), bd("11"), bd("12"), bd("13"), bd("14"),
            bd("15"), bd("16"), bd("17"), bd("18"), bd("19"),
            bd("20"), bd("21"), bd("22"), bd("23"), bd("24"),
            bd("25"), bd("26"), bd("27"), bd("28"), bd("29"),
            bd("30"), bd("31"), bd("32"), bd("33"), bd("34"),
            bd("35"), bd("36"), bd("37"), bd("38"), bd("39"),
            bd("40")
        );

        // When: Calculate MACD(3, 6, 3) for faster test
        Optional<MACD> result = service.calculateMACD(prices, 3, 6, 3, Instant.now());

        // Then: Verify MACD calculation completes and all values are present
        assertThat(result).isPresent();
        assertThat(result.get().macdLine()).isNotNull();
        assertThat(result.get().signalLine()).isNotNull();
        assertThat(result.get().histogram()).isNotNull();
        // Histogram should be macdLine - signalLine
        assertThat(result.get().histogram())
            .isEqualByComparingTo(result.get().macdLine().subtract(result.get().signalLine()));
    }

    @Test
    void calculateMACD_shouldReturnEmpty_withInsufficientData() {
        // Given: Only 10 prices, need at least slowPeriod + signalPeriod
        List<BigDecimal> prices = Arrays.asList(
            bd("10"), bd("11"), bd("12"), bd("13"), bd("14"),
            bd("15"), bd("16"), bd("17"), bd("18"), bd("19")
        );

        // When: Need 26 + 9 = 35 prices minimum
        Optional<MACD> result = service.calculateMACD(prices, 12, 26, 9, Instant.now());

        // Then
        assertThat(result).isEmpty();
    }

    // ========== Bollinger Bands Tests ==========

    @Test
    void calculateBollingerBands_shouldReturnCorrectBands() {
        // Given: Prices with known variance
        List<BigDecimal> prices = Arrays.asList(
            bd("98"), bd("100"), bd("102"), bd("100"), bd("98")
        );

        // When: Calculate 5-period BB with 2.0 std dev
        Optional<BollingerBands> result = service.calculateBollingerBands(
            prices, 5, new BigDecimal("2.0"), Instant.now()
        );

        // Then: Middle = 100, stdDev ≈ 1.41, upper ≈ 102.82, lower ≈ 97.18
        assertThat(result).isPresent();
        assertThat(result.get().middleBand()).isEqualByComparingTo("99.60");
        assertThat(result.get().upperBand()).isGreaterThan(result.get().middleBand());
        assertThat(result.get().lowerBand()).isLessThan(result.get().middleBand());
    }

    @Test
    void calculateBollingerBands_shouldReturnEmpty_withInsufficientData() {
        // Given: Only 10 prices, need 20
        List<BigDecimal> prices = Arrays.asList(
            bd("10"), bd("11"), bd("12"), bd("13"), bd("14"),
            bd("15"), bd("16"), bd("17"), bd("18"), bd("19")
        );

        // When
        Optional<BollingerBands> result = service.calculateBollingerBands(
            prices, 20, new BigDecimal("2.0"), Instant.now()
        );

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void calculateBollingerBands_shouldValidateBandOrdering() {
        // Given: Prices
        List<BigDecimal> prices = Arrays.asList(
            bd("100"), bd("101"), bd("102"), bd("103"), bd("104")
        );

        // When
        Optional<BollingerBands> result = service.calculateBollingerBands(
            prices, 5, new BigDecimal("2.0"), Instant.now()
        );

        // Then: Verify lower <= middle <= upper
        assertThat(result).isPresent();
        BollingerBands bb = result.get();
        assertThat(bb.lowerBand()).isLessThanOrEqualTo(bb.middleBand());
        assertThat(bb.middleBand()).isLessThanOrEqualTo(bb.upperBand());
    }

    // ========== EMA Tests ==========

    @Test
    void calculateEMAIndicator_shouldReturnCorrectValue_withSufficientData() {
        // Given: 10 prices, calculate 3-period EMA
        List<BigDecimal> prices = Arrays.asList(
            bd("10"), bd("12"), bd("14"), bd("13"), bd("15"),
            bd("16"), bd("14"), bd("17"), bd("18"), bd("20")
        );
        Instant now = Instant.now();

        // When
        Optional<MovingAverage> result = service.calculateEMAIndicator(prices, 3, now);

        // Then: Should have a value and use specified period
        assertThat(result).isPresent();
        assertThat(result.get().period()).isEqualTo(3);
        assertThat(result.get().calculatedAt()).isEqualTo(now);
        // EMA should weight recent prices more heavily than SMA
        assertThat(result.get().value()).isNotNull();
    }

    @Test
    void calculateEMAIndicator_shouldReturnEmpty_withInsufficientData() {
        // Given: Only 2 prices, need 5
        List<BigDecimal> prices = Arrays.asList(bd("10"), bd("12"));

        // When
        Optional<MovingAverage> result = service.calculateEMAIndicator(prices, 5, Instant.now());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void calculateEMAIndicator_shouldReturnEmpty_withNullPrices() {
        // When
        Optional<MovingAverage> result = service.calculateEMAIndicator(null, 5, Instant.now());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void calculateEMAIndicator_shouldDifferFromSMA_forTrendingData() {
        // Given: Strongly uptrending prices — EMA should be higher than SMA
        // because EMA weights recent (higher) prices more heavily
        List<BigDecimal> prices = Arrays.asList(
            bd("100"), bd("102"), bd("104"), bd("106"), bd("108"),
            bd("110"), bd("115"), bd("120"), bd("125"), bd("130")
        );
        Instant now = Instant.now();

        // When
        Optional<MovingAverage> sma = service.calculateSMA(prices, 5, now);
        Optional<MovingAverage> ema = service.calculateEMAIndicator(prices, 5, now);

        // Then: Both should be present but different
        assertThat(sma).isPresent();
        assertThat(ema).isPresent();
        // In an uptrend, EMA should be higher than SMA (more responsive to recent highs)
        assertThat(ema.get().value()).isGreaterThan(sma.get().value());
    }

    @Test
    void calculateEMAIndicator_shouldReturnEmpty_whenBarCountEqualsPeriod() {
        // With exactly `period` bars the EMA is seeded from the SMA of those bars and the
        // smoothing loop never runs, so the "EMA" returned is byte-identical to the SMA.
        // Publishing that as an EMA is a lie about which indicator the caller is reading.
        List<BigDecimal> prices = trendingPrices(200);

        Optional<MovingAverage> ema = service.calculateEMAIndicator(prices, 200, Instant.now());

        assertThat(ema)
            .as("an unsmoothed EMA seed must not be published as an EMA")
            .isEmpty();
    }

    @Test
    void calculateEMAIndicator_shouldDifferFromSMA_whenGivenSmoothingHeadroom() {
        // Guards a real past defect: with no smoothing headroom the EMA is just its SMA
        // seed, and EMA-200 was reported as identical to SMA-200 for every ticker.
        //
        // The fixture must be a series where the two genuinely differ, which a constant
        // ramp is NOT: on a perfectly linear series EMA and SMA converge to exactly the
        // same value, so a ramp asserts nothing. It passed only because the old
        // two-decimal internal arithmetic left rounding noise between them, and raising
        // the calculation precision removed the noise along with the illusion.
        //
        // A step instead — a long flat stretch followed by a sharp move — separates them
        // by construction, because weighting recent bars more heavily is the entire
        // difference between the two measures.
        List<BigDecimal> prices = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            prices.add(bd("100"));
        }
        for (int i = 0; i < 60; i++) {
            prices.add(bd("300"));
        }
        Instant now = Instant.now();

        Optional<MovingAverage> sma = service.calculateSMA(prices, 200, now);
        Optional<MovingAverage> ema = service.calculateEMAIndicator(prices, 200, now);

        assertThat(sma).isPresent();
        assertThat(ema).isPresent();
        assertThat(ema.get().value())
            .as("the EMA must weight the recent move more heavily than the SMA does")
            .isGreaterThan(sma.get().value());
    }

    // ------------------------------------------------------------------
    // Low-priced assets. Rounding intermediates to a display scale silently
    // destroyed every indicator for anything trading below about a dollar —
    // which is most altcoins and any penny stock.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RSI on a sub-dollar asset reflects the price action, not a pinned 100")
    void rsiIsNotPinnedAtOneHundredForLowPricedAssets() {
        // A coin oscillating around $0.16 with a clear downward bias. Average daily
        // loss here is ~0.003, which at two decimal places rounded to zero — and the
        // zero-loss branch then reported "no losses at all", returning exactly 100.
        // Every low-priced asset read as maximally overbought, permanently, so no
        // RSI threshold in any strategy could ever fire.
        List<BigDecimal> prices = List.of(
            bd("0.1800"), bd("0.1780"), bd("0.1795"), bd("0.1760"), bd("0.1740"),
            bd("0.1755"), bd("0.1720"), bd("0.1700"), bd("0.1715"), bd("0.1680"),
            bd("0.1660"), bd("0.1675"), bd("0.1640"), bd("0.1620"), bd("0.1583"));

        Optional<RSI> rsi = service.calculateRSI(prices, 14, Instant.now());

        assertThat(rsi).isPresent();
        assertThat(rsi.get().value())
            .as("a falling series cannot have an RSI of 100")
            .isLessThan(bd("100"));
        assertThat(rsi.get().value())
            .as("more losses than gains must read below the neutral 50")
            .isLessThan(bd("50"));
    }

    @Test
    @DisplayName("a genuinely unbroken rally still reads 100")
    void rsiStillReportsOneHundredForAnUnbrokenRally() {
        // The boundary case must survive: the fix removes an arithmetic artefact,
        // not the real meaning of the extreme.
        List<BigDecimal> prices = new java.util.ArrayList<>();
        for (int i = 0; i <= 14; i++) {
            prices.add(new BigDecimal("0.1000").add(new BigDecimal("0.0010").multiply(new BigDecimal(i))));
        }

        assertThat(service.calculateRSI(prices, 14, Instant.now())).isPresent()
            .get().extracting(RSI::value)
            .isEqualTo(bd("100.00"));
    }

    @Test
    @DisplayName("a perfectly flat series is neutral, not overbought")
    void rsiOnAFlatSeriesIsNeutral() {
        // No gains and no losses. Reporting 100 would flag a motionless price as
        // maximally overbought; 50 says what is actually true — no directional signal.
        List<BigDecimal> flat = java.util.Collections.nCopies(15, bd("0.1600"));

        assertThat(service.calculateRSI(flat, 14, Instant.now())).isPresent()
            .get().extracting(RSI::value)
            .isEqualTo(bd("50.00"));
    }

    @Test
    @DisplayName("Bollinger bands on a sub-dollar asset do not collapse onto the middle line")
    void bollingerBandsHaveWidthForLowPricedAssets() {
        // Variance is a squared quantity, so for a $0.16 asset it lands near 0.000004
        // and rounded to 0.00 at two decimals. Standard deviation became zero, both
        // bands collapsed onto the SMA, and every band-based rule became unreachable.
        List<BigDecimal> prices = List.of(
            bd("0.1800"), bd("0.1780"), bd("0.1795"), bd("0.1760"), bd("0.1740"),
            bd("0.1755"), bd("0.1720"), bd("0.1700"), bd("0.1715"), bd("0.1680"),
            bd("0.1660"), bd("0.1675"), bd("0.1640"), bd("0.1620"), bd("0.1583"),
            bd("0.1610"), bd("0.1595"), bd("0.1570"), bd("0.1550"), bd("0.1583"));

        Optional<BollingerBands> bands = service.calculateBollingerBands(prices, 20, bd("2"), Instant.now());

        assertThat(bands).isPresent();
        BollingerBands b = bands.get();
        assertThat(b.upperBand())
            .as("the upper band must sit above the middle, or the band conveys nothing")
            .isGreaterThan(b.middleBand());
        assertThat(b.lowerBand()).isLessThan(b.middleBand());
    }

    @Test
    @DisplayName("an SMA below a dollar keeps enough decimals to distinguish anything")
    void movingAverageKeepsPrecisionForLowPricedAssets() {
        List<BigDecimal> prices = java.util.Collections.nCopies(50, bd("0.158260"));

        Optional<MovingAverage> sma = service.calculateSMA(prices, 50, Instant.now());

        assertThat(sma).isPresent();
        assertThat(sma.get().value())
            .as("rounding a $0.1583 average to 0.16 discards the signal entirely")
            .isEqualByComparingTo(bd("0.158260"));
    }

    @Test
    @DisplayName("EMA is reported at a readable scale, not at internal precision")
    void emaOutputIsRoundedForPresentation() {
        // Raising internal precision must not leak sixteen decimals into the response.
        Optional<MovingAverage> ema = service.calculateEMAIndicator(trendingPrices(60), 50, Instant.now());

        assertThat(ema).isPresent();
        assertThat(ema.get().value().scale())
            .as("a dollar-scale EMA should read like a price, not like a raw quotient")
            .isLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("equity-scale output is unchanged — two decimals, as before")
    void dollarScaleOutputIsUnchanged() {
        // The fix must not reformat every existing stock result.
        List<BigDecimal> prices = java.util.Collections.nCopies(50, bd("180.47"));

        assertThat(service.calculateSMA(prices, 50, Instant.now())).isPresent()
            .get().extracting(MovingAverage::value)
            .isEqualTo(bd("180.47"));
    }

    /** A steadily rising series, so SMA and EMA are guaranteed to diverge if both are real. */
    private List<BigDecimal> trendingPrices(int count) {
        List<BigDecimal> prices = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            prices.add(new BigDecimal("100").add(new BigDecimal(i)));
        }
        return prices;
    }

    // Helper method
    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }


    @Test
    @DisplayName("MACD keeps significant figures on a sub-dollar asset")
    void macdIsUsableForLowPricedAssets() {
        // MACD is a difference of moving averages, so it is far smaller than the price
        // behind it. Fixed four-decimal output gave a coin near $0.07 a histogram of
        // 0.0002 — one significant figure — and flattened one near $0.0000044 to a
        // literal 0.0000. No strategy threshold could ever fire against that.
        // A flat stretch followed by a rally. A constant ramp will not do: on a perfectly
        // linear series the signal line converges onto the MACD line and the histogram is
        // genuinely zero, so the fixture would prove nothing about precision.
        List<BigDecimal> prices = new java.util.ArrayList<>();
        for (int i = 0; i < 26; i++) {
            prices.add(new BigDecimal("0.0700"));
        }
        for (int i = 1; i <= 14; i++) {
            prices.add(new BigDecimal("0.0700").add(
                new BigDecimal("0.0004").multiply(new BigDecimal(i))));
        }

        Optional<MACD> macd = service.calculateMACD(prices, 12, 26, 9, Instant.now());

        assertThat(macd).isPresent();
        assertThat(macd.get().histogram().signum())
            .as("a steadily trending series must produce a non-zero histogram")
            .isNotZero();
        assertThat(macd.get().macdLine().signum()).isNotZero();
    }

    @Test
    @DisplayName("Bollinger bands stay positive and proportionate for a micro-priced asset")
    void bollingerBandsAreSaneForMicroPricedAssets() {
        // The hand-rolled sqrt seeded at value/2 could not converge for tiny variances:
        // it overshot to ~1.0 and then merely halved, landing on 2^-8 whatever the
        // input. A coin at $0.0000044 got a half-width ~900x its price and a NEGATIVE
        // lower band — a price that cannot exist.
        List<BigDecimal> prices = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            prices.add(new BigDecimal("0.00000440").add(
                new BigDecimal("0.00000001").multiply(new BigDecimal(i % 3))));
        }

        Optional<BollingerBands> bands = service.calculateBollingerBands(
            prices, 20, new BigDecimal("2"), Instant.now());

        assertThat(bands).isPresent();
        BollingerBands b = bands.get();
        assertThat(b.lowerBand().signum())
            .as("a lower band below zero is not a price")
            .isNotNegative();
        assertThat(b.upperBand())
            .as("the band must stay in the neighbourhood of the price, not 900x it")
            .isLessThan(new BigDecimal("0.0001"));
    }

    @Test
    @DisplayName("sqrt is accurate across magnitudes, not just convenient ones")
    void bollingerWidthTracksActualDispersion() {
        // A tight series and a loose one at the same price level must produce
        // visibly different band widths.
        List<BigDecimal> tight = java.util.Collections.nCopies(20, bd("0.1600"));
        List<BigDecimal> loose = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            loose.add(i % 2 == 0 ? bd("0.1400") : bd("0.1800"));
        }

        BollingerBands tightBands = service.calculateBollingerBands(
            tight, 20, bd("2"), Instant.now()).orElseThrow();
        BollingerBands looseBands = service.calculateBollingerBands(
            loose, 20, bd("2"), Instant.now()).orElseThrow();

        BigDecimal tightWidth = tightBands.upperBand().subtract(tightBands.lowerBand());
        BigDecimal looseWidth = looseBands.upperBand().subtract(looseBands.lowerBand());

        assertThat(tightWidth).isEqualByComparingTo("0");
        assertThat(looseWidth).isGreaterThan(new BigDecimal("0.07"));
    }

    @Test
    @DisplayName("prompt formatting keeps significant figures all the way down")
    void promptFormattingSurvivesMicroPrices() {
        // The last layer between a correct number and the model. "%.2f" turned a
        // sub-cent price into "$0.00", and a fixed eight-decimal floor turned every
        // meme-coin value into a multiple of 1e-8 — enough to quantise a MACD
        // histogram down to "0, +/-1e-8, +/-2e-8".
        assertThat(IndicatorContext.formatPromptPrice(bd("340.17"))).isEqualTo("340.17");
        assertThat(IndicatorContext.formatPromptPrice(bd("0.07212955"))).isEqualTo("0.07213");
        assertThat(IndicatorContext.formatPromptPrice(bd("0.00000461"))).isEqualTo("0.000004610");

        String histogram = IndicatorContext.formatPromptPrice(bd("0.0000000910"));
        assertThat(histogram)
            .as("a histogram of 9.1e-8 must not collapse to zero or to a single unit")
            .isEqualTo("0.00000009100");
        assertThat(new BigDecimal(histogram).signum()).isPositive();
    }

    @Test
    @DisplayName("equity prompt formatting is unchanged")
    void promptFormattingUnchangedForEquities() {
        assertThat(IndicatorContext.promptScaleFor(bd("340.17"))).isEqualTo(2);
        assertThat(IndicatorContext.promptScaleFor(bd("1.00"))).isEqualTo(2);
        assertThat(IndicatorContext.formatPromptPrice(null)).isEqualTo("N/A");
    }
}
