package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for IndicatorContext value object.
 *
 * <p>Tests the toPromptText() method which formats all indicators for LLM consumption.</p>
 *
 * <p><b>Expected output format:</b></p>
 * <pre>
 * ## Technical Indicators (Stocks Only)
 *
 * ### AAPL
 * - SMA-50: $185.42
 * - SMA-200: $178.91
 * - RSI-14: 62.3 (neutral)
 * - MACD: Line=2.15, Signal=1.89, Histogram=+0.26 (bullish)
 * - Bollinger Bands: Upper=$192.10, Middle=$185.00, Lower=$177.90
 *
 * *Note: Technical indicators not calculated for crypto assets.*
 * </pre>
 *
 * <p><b>Interpretations:</b></p>
 * <ul>
 *   <li>RSI: (overbought) if >= 70, (oversold) if <= 30, else (neutral)</li>
 *   <li>MACD histogram: (bullish) if > 0, (bearish) if < 0</li>
 *   <li>Null values show "N/A" with warning in parentheses</li>
 * </ul>
 */
@DisplayName("IndicatorContext Tests")
class IndicatorContextTest {

    @Test
    @DisplayName("Should create empty context")
    void shouldCreateEmptyContext() {
        IndicatorContext context = IndicatorContext.empty();
        assertThat(context.tickerIndicators()).isEmpty();
        assertThat(context.toPromptText()).isEqualTo("No technical indicators available.");
    }

    @Test
    @DisplayName("Should format single ticker prompt text with all indicators")
    void shouldFormatSingleTickerPromptText() {
        Symbol aapl = new Symbol("AAPL", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.MOVING_AVERAGE,
            new IndicatorValue.MovingAverageValue(new BigDecimal("185.42"), new BigDecimal("178.91")));
        values.put(IndicatorType.RSI,
            new IndicatorValue.RSIValue(new BigDecimal("62.3")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(aapl, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(aapl, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("AAPL");
        assertThat(prompt).contains("SMA-50: $185.42");
        assertThat(prompt).contains("SMA-200: $178.91");
        assertThat(prompt).contains("RSI-14: 62.3");
    }

    @Test
    @DisplayName("Should include warnings in prompt text when data is insufficient")
    void shouldIncludeWarningsInPromptText() {
        Symbol nvda = new Symbol("NVDA", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.MOVING_AVERAGE,
            new IndicatorValue.MovingAverageValue(new BigDecimal("142.18"), null));

        Map<IndicatorType, String> warnings = new EnumMap<>(IndicatorType.class);
        warnings.put(IndicatorType.MOVING_AVERAGE, "Only 150 days of data, need 200 for SMA-200");

        IndicatorSnapshot snapshot = new IndicatorSnapshot(nvda, Instant.now(), values, warnings);
        IndicatorContext context = new IndicatorContext(Map.of(nvda, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("NVDA");
        assertThat(prompt).contains("SMA-50: $142.18");
        assertThat(prompt).contains("SMA-200: N/A");
        assertThat(prompt).containsAnyOf("Warning", "150 days");
    }

    @Test
    @DisplayName("Should format all indicator types correctly")
    void shouldFormatAllIndicatorTypes() {
        Symbol aapl = new Symbol("AAPL", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.MOVING_AVERAGE,
            new IndicatorValue.MovingAverageValue(new BigDecimal("185.42"), new BigDecimal("178.91")));
        values.put(IndicatorType.RSI,
            new IndicatorValue.RSIValue(new BigDecimal("62.3")));
        values.put(IndicatorType.MACD,
            new IndicatorValue.MACDValue(
                new BigDecimal("2.15"), new BigDecimal("1.89"), new BigDecimal("0.26")));
        values.put(IndicatorType.BOLLINGER_BANDS,
            new IndicatorValue.BollingerValue(
                new BigDecimal("192.10"), new BigDecimal("185.00"), new BigDecimal("177.90")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(aapl, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(aapl, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("SMA-50");
        assertThat(prompt).contains("RSI-14");
        assertThat(prompt).contains("MACD");
        assertThat(prompt).contains("Bollinger");
    }

    @Test
    @DisplayName("Should show RSI overbought interpretation when >= 70")
    void shouldShowRsiOverboughtInterpretation() {
        Symbol ticker = new Symbol("TEST", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("75.5")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("(overbought)");
    }

    @Test
    @DisplayName("Should show RSI oversold interpretation when <= 30")
    void shouldShowRsiOversoldInterpretation() {
        Symbol ticker = new Symbol("TEST", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("25.0")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("(oversold)");
    }

    @Test
    @DisplayName("Should show RSI neutral interpretation when between 30 and 70")
    void shouldShowRsiNeutralInterpretation() {
        Symbol ticker = new Symbol("TEST", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("50.0")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("(neutral)");
    }

    @Test
    @DisplayName("Should show RSI exactly at boundary 70 as overbought")
    void shouldShowRsiAtBoundary70AsOverbought() {
        Symbol ticker = new Symbol("TEST", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("70")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("(overbought)");
    }

    @Test
    @DisplayName("Should show RSI exactly at boundary 30 as oversold")
    void shouldShowRsiAtBoundary30AsOversold() {
        Symbol ticker = new Symbol("TEST", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("30")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("(oversold)");
    }

    @Test
    @DisplayName("Should show MACD bullish interpretation when histogram > 0")
    void shouldShowMacdBullishInterpretation() {
        Symbol ticker = new Symbol("TEST", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.MACD,
            new IndicatorValue.MACDValue(
                new BigDecimal("2.15"), new BigDecimal("1.89"), new BigDecimal("0.26")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("(bullish)");
        assertThat(prompt).contains("+0.26");
    }

    @Test
    @DisplayName("Should show MACD bearish interpretation when histogram < 0")
    void shouldShowMacdBearishInterpretation() {
        Symbol ticker = new Symbol("TEST", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.MACD,
            new IndicatorValue.MACDValue(
                new BigDecimal("1.50"), new BigDecimal("2.00"), new BigDecimal("-0.50")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("(bearish)");
        assertThat(prompt).contains("-0.50");
    }

    @Test
    @DisplayName("Should format Bollinger Bands with dollar signs")
    void shouldFormatBollingerBandsWithDollarSigns() {
        Symbol ticker = new Symbol("TEST", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.BOLLINGER_BANDS,
            new IndicatorValue.BollingerValue(
                new BigDecimal("192.10"), new BigDecimal("185.00"), new BigDecimal("177.90")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("Upper=$192.10");
        assertThat(prompt).contains("Middle=$185.00");
        assertThat(prompt).contains("Lower=$177.90");
    }

    @Test
    @DisplayName("Should include header and footer in prompt text")
    void shouldIncludeHeaderAndFooterInPromptText() {
        Symbol ticker = new Symbol("AAPL", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("50.0")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("## Technical Indicators (Stocks Only)");
        assertThat(prompt).contains("*Note: Technical indicators not calculated for crypto assets.*");
    }

    @Test
    @DisplayName("Should not claim crypto is uncalculated when crypto indicators are present")
    void shouldNotContradictItselfForCrypto() {
        Symbol btc = new Symbol("BTCUSD", InvestmentType.CRYPTO);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("53.5")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(btc, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(btc, snapshot));

        String prompt = context.toPromptText();

        // A populated BTC table under a "Stocks Only" header, followed by a note saying crypto
        // indicators are not calculated, tells the model to disbelieve the numbers it was given.
        assertThat(prompt).doesNotContain("(Stocks Only)");
        assertThat(prompt).doesNotContain("not calculated for crypto assets");
        assertThat(prompt).contains("RSI-14: 53.5");
    }

    @Test
    @DisplayName("Should drop both stock-only qualifiers when the block mixes asset types")
    void shouldNotQualifyMixedAssetBlock() {
        Symbol btc = new Symbol("BTCUSD", InvestmentType.CRYPTO);
        Symbol aapl = new Symbol("AAPL", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("50.0")));

        IndicatorContext context = new IndicatorContext(Map.of(
            btc, new IndicatorSnapshot(btc, Instant.now(), values, Map.of()),
            aapl, new IndicatorSnapshot(aapl, Instant.now(), values, Map.of())
        ));

        String prompt = context.toPromptText();

        assertThat(prompt).contains("## Technical Indicators");
        assertThat(prompt).doesNotContain("(Stocks Only)");
        assertThat(prompt).doesNotContain("(Crypto Only)");
        assertThat(prompt).doesNotContain("not calculated for crypto assets");
    }

    @Test
    @DisplayName("Should format multiple tickers sorted alphabetically")
    void shouldFormatMultipleTickersSortedAlphabetically() {
        Symbol aapl = new Symbol("AAPL", InvestmentType.STOCK);
        Symbol nvda = new Symbol("NVDA", InvestmentType.STOCK);
        Symbol msft = new Symbol("MSFT", InvestmentType.STOCK);

        Map<IndicatorType, IndicatorValue> aaplValues = new EnumMap<>(IndicatorType.class);
        aaplValues.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("55.0")));
        IndicatorSnapshot aaplSnapshot = new IndicatorSnapshot(aapl, Instant.now(), aaplValues, Map.of());

        Map<IndicatorType, IndicatorValue> nvdaValues = new EnumMap<>(IndicatorType.class);
        nvdaValues.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("65.0")));
        IndicatorSnapshot nvdaSnapshot = new IndicatorSnapshot(nvda, Instant.now(), nvdaValues, Map.of());

        Map<IndicatorType, IndicatorValue> msftValues = new EnumMap<>(IndicatorType.class);
        msftValues.put(IndicatorType.RSI, new IndicatorValue.RSIValue(new BigDecimal("45.0")));
        IndicatorSnapshot msftSnapshot = new IndicatorSnapshot(msft, Instant.now(), msftValues, Map.of());

        IndicatorContext context = new IndicatorContext(Map.of(
            aapl, aaplSnapshot,
            nvda, nvdaSnapshot,
            msft, msftSnapshot
        ));

        String prompt = context.toPromptText();
        int aaplIndex = prompt.indexOf("### AAPL");
        int msftIndex = prompt.indexOf("### MSFT");
        int nvdaIndex = prompt.indexOf("### NVDA");

        // AAPL should come before MSFT which should come before NVDA (alphabetical order)
        assertThat(aaplIndex).isLessThan(msftIndex);
        assertThat(msftIndex).isLessThan(nvdaIndex);
    }

    @Test
    @DisplayName("Should show N/A when indicator value is completely missing")
    void shouldShowNaWhenIndicatorValueIsMissing() {
        Symbol ticker = new Symbol("TEST", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        // No MOVING_AVERAGE value added, but warning exists

        Map<IndicatorType, String> warnings = new EnumMap<>(IndicatorType.class);
        warnings.put(IndicatorType.MOVING_AVERAGE, "No price data available");

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, warnings);
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("Moving Averages: N/A");
        assertThat(prompt).contains("No price data available");
    }

    @Test
    @DisplayName("Should show SMA gap percentage ABOVE when SMA-50 > SMA-200")
    void shouldShowSmaGapAbove() {
        Symbol ticker = new Symbol("AAPL", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.MOVING_AVERAGE,
            new IndicatorValue.MovingAverageValue(new BigDecimal("185.42"), new BigDecimal("178.91")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("+3.64%");
        assertThat(prompt).contains("ABOVE");
    }

    @Test
    @DisplayName("Should show SMA gap percentage BELOW when SMA-50 < SMA-200")
    void shouldShowSmaGapBelow() {
        Symbol ticker = new Symbol("QQQ", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.MOVING_AVERAGE,
            new IndicatorValue.MovingAverageValue(new BigDecimal("532.56"), new BigDecimal("533.09")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("-0.10%");
        assertThat(prompt).contains("BELOW");
    }

    @Test
    @DisplayName("Should format EMA-50 and EMA-200 in prompt text")
    void shouldFormatEmaInPromptText() {
        Symbol aapl = new Symbol("AAPL", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.MOVING_AVERAGE,
            new IndicatorValue.MovingAverageValue(
                new BigDecimal("185.42"), new BigDecimal("178.91"),
                new BigDecimal("186.10"), new BigDecimal("179.50")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(aapl, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(aapl, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("EMA-50: $186.10");
        assertThat(prompt).contains("EMA-200: $179.50");
        assertThat(prompt).contains("EMA-50 vs EMA-200:");
    }

    @Test
    @DisplayName("Should not show EMA lines when EMA values are null")
    void shouldNotShowEmaWhenNull() {
        Symbol aapl = new Symbol("AAPL", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        // Use the backwards-compatible 2-arg constructor (EMA fields null)
        values.put(IndicatorType.MOVING_AVERAGE,
            new IndicatorValue.MovingAverageValue(new BigDecimal("185.42"), new BigDecimal("178.91")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(aapl, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(aapl, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("SMA-50: $185.42");
        assertThat(prompt).contains("SMA-200: $178.91");
        assertThat(prompt).doesNotContain("EMA-50");
        assertThat(prompt).doesNotContain("EMA-200");
    }

    @Test
    @DisplayName("Should show EMA gap percentage with direction")
    void shouldShowEmaGapPercentage() {
        Symbol ticker = new Symbol("QQQ", InvestmentType.STOCK);
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.MOVING_AVERAGE,
            new IndicatorValue.MovingAverageValue(
                new BigDecimal("500.00"), new BigDecimal("480.00"),
                new BigDecimal("505.00"), new BigDecimal("485.00")));

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, Instant.now(), values, Map.of());
        IndicatorContext context = new IndicatorContext(Map.of(ticker, snapshot));

        String prompt = context.toPromptText();
        assertThat(prompt).contains("EMA-50 vs EMA-200:");
        assertThat(prompt).contains("ABOVE");
    }

    @Test
    @DisplayName("Should reject null tickerIndicators map")
    void shouldRejectNullTickerIndicatorsMap() {
        assertThatThrownBy(() -> new IndicatorContext(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tickerIndicators");
    }
}
