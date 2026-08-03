package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for IndicatorType enum.
 * Verifies enum values exist and display names are correct.
 */
@DisplayName("IndicatorType Enum Tests")
class IndicatorTypeTest {

    @Test
    @DisplayName("Should have all expected enum values")
    void shouldHaveAllExpectedEnumValues() {
        // When/Then
        assertThat(IndicatorType.values())
            .hasSize(4)
            .containsExactlyInAnyOrder(
                IndicatorType.RSI,
                IndicatorType.MOVING_AVERAGE,
                IndicatorType.MACD,
                IndicatorType.BOLLINGER_BANDS
            );
    }

    @Test
    @DisplayName("Should return correct display name for RSI")
    void shouldReturnCorrectDisplayNameForRSI() {
        // When/Then
        assertThat(IndicatorType.RSI.getDisplayName())
            .isEqualTo("Relative Strength Index");
    }

    @Test
    @DisplayName("Should return correct display name for MOVING_AVERAGE")
    void shouldReturnCorrectDisplayNameForMovingAverage() {
        // When/Then
        assertThat(IndicatorType.MOVING_AVERAGE.getDisplayName())
            .isEqualTo("Moving Average");
    }

    @Test
    @DisplayName("Should return correct display name for MACD")
    void shouldReturnCorrectDisplayNameForMACD() {
        // When/Then
        assertThat(IndicatorType.MACD.getDisplayName())
            .isEqualTo("Moving Average Convergence Divergence");
    }

    @Test
    @DisplayName("Should return correct display name for BOLLINGER_BANDS")
    void shouldReturnCorrectDisplayNameForBollingerBands() {
        // When/Then
        assertThat(IndicatorType.BOLLINGER_BANDS.getDisplayName())
            .isEqualTo("Bollinger Bands");
    }

    @Test
    @DisplayName("Should convert string to enum using valueOf")
    void shouldConvertStringToEnumUsingValueOf() {
        // When/Then
        assertThat(IndicatorType.valueOf("RSI")).isEqualTo(IndicatorType.RSI);
        assertThat(IndicatorType.valueOf("MOVING_AVERAGE")).isEqualTo(IndicatorType.MOVING_AVERAGE);
        assertThat(IndicatorType.valueOf("MACD")).isEqualTo(IndicatorType.MACD);
        assertThat(IndicatorType.valueOf("BOLLINGER_BANDS")).isEqualTo(IndicatorType.BOLLINGER_BANDS);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid string in valueOf")
    void shouldThrowExceptionForInvalidValueOf() {
        // When/Then
        assertThatThrownBy(() -> IndicatorType.valueOf("INVALID"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
