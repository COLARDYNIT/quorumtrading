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
 * Tests for IndicatorSnapshot value object.
 *
 * <p>This class follows the design from 2025-11-24-indicator-calculation-integration-plan.md
 * which uses a record structure with Map<IndicatorType, IndicatorValue> values
 * and Map<IndicatorType, String> warnings.</p>
 */
@DisplayName("IndicatorSnapshot Tests")
class IndicatorSnapshotTest {

    @Test
    @DisplayName("Should create snapshot with values and warnings")
    void shouldCreateSnapshotWithValuesAndWarnings() {
        Symbol ticker = new Symbol("AAPL", InvestmentType.STOCK);
        Instant now = Instant.now();

        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        values.put(IndicatorType.MOVING_AVERAGE,
            new IndicatorValue.MovingAverageValue(new BigDecimal("185.42"), null));
        values.put(IndicatorType.RSI,
            new IndicatorValue.RSIValue(new BigDecimal("62.3")));

        Map<IndicatorType, String> warnings = new EnumMap<>(IndicatorType.class);
        warnings.put(IndicatorType.MOVING_AVERAGE, "Only 150 days of data, need 200 for SMA-200");

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, now, values, warnings);

        assertThat(snapshot.ticker()).isEqualTo(ticker);
        assertThat(snapshot.calculatedAt()).isEqualTo(now);
        assertThat(snapshot.values()).hasSize(2);
        assertThat(snapshot.warnings()).hasSize(1);
    }

    @Test
    @DisplayName("Should create empty snapshot with reason applied to all indicator types")
    void shouldCreateEmptySnapshotWithReason() {
        Symbol ticker = new Symbol("NVDA", InvestmentType.STOCK);
        IndicatorSnapshot snapshot = IndicatorSnapshot.empty(ticker, "No price history available");

        assertThat(snapshot.ticker()).isEqualTo(ticker);
        assertThat(snapshot.values()).isEmpty();
        assertThat(snapshot.warnings()).hasSize(4); // All 4 indicator types
        snapshot.warnings().values().forEach(warning ->
            assertThat(warning).isEqualTo("No price history available")
        );
    }

    @Test
    @DisplayName("Should reject null ticker")
    void shouldRejectNullTicker() {
        assertThatThrownBy(() ->
            new IndicatorSnapshot(null, Instant.now(), Map.of(), Map.of())
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should reject null calculatedAt")
    void shouldRejectNullCalculatedAt() {
        Symbol ticker = new Symbol("AAPL", InvestmentType.STOCK);
        assertThatThrownBy(() ->
            new IndicatorSnapshot(ticker, null, Map.of(), Map.of())
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should reject null values map")
    void shouldRejectNullValuesMap() {
        Symbol ticker = new Symbol("AAPL", InvestmentType.STOCK);
        assertThatThrownBy(() ->
            new IndicatorSnapshot(ticker, Instant.now(), null, Map.of())
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should reject null warnings map")
    void shouldRejectNullWarningsMap() {
        Symbol ticker = new Symbol("AAPL", InvestmentType.STOCK);
        assertThatThrownBy(() ->
            new IndicatorSnapshot(ticker, Instant.now(), Map.of(), null)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should store all indicator types in values map")
    void shouldStoreAllIndicatorTypesInValuesMap() {
        Symbol ticker = new Symbol("AAPL", InvestmentType.STOCK);
        Instant now = Instant.now();

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

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, now, values, Map.of());

        assertThat(snapshot.values()).hasSize(4);
        assertThat(snapshot.values().get(IndicatorType.MOVING_AVERAGE))
            .isInstanceOf(IndicatorValue.MovingAverageValue.class);
        assertThat(snapshot.values().get(IndicatorType.RSI))
            .isInstanceOf(IndicatorValue.RSIValue.class);
        assertThat(snapshot.values().get(IndicatorType.MACD))
            .isInstanceOf(IndicatorValue.MACDValue.class);
        assertThat(snapshot.values().get(IndicatorType.BOLLINGER_BANDS))
            .isInstanceOf(IndicatorValue.BollingerValue.class);
    }

    @Test
    @DisplayName("Empty snapshot should have all 4 indicator warnings")
    void emptySnapshotShouldHaveAllFourIndicatorWarnings() {
        Symbol ticker = new Symbol("TEST", InvestmentType.STOCK);
        IndicatorSnapshot snapshot = IndicatorSnapshot.empty(ticker, "Database unavailable");

        assertThat(snapshot.warnings()).containsKeys(
            IndicatorType.RSI,
            IndicatorType.MOVING_AVERAGE,
            IndicatorType.MACD,
            IndicatorType.BOLLINGER_BANDS
        );
        assertThat(snapshot.warnings().get(IndicatorType.RSI)).isEqualTo("Database unavailable");
        assertThat(snapshot.warnings().get(IndicatorType.MOVING_AVERAGE)).isEqualTo("Database unavailable");
        assertThat(snapshot.warnings().get(IndicatorType.MACD)).isEqualTo("Database unavailable");
        assertThat(snapshot.warnings().get(IndicatorType.BOLLINGER_BANDS)).isEqualTo("Database unavailable");
    }
}
