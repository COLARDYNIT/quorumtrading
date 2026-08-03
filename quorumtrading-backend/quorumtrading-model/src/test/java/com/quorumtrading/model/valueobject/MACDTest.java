package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DisplayName("MACD Value Object Tests")
class MACDTest {

    @Test
    @DisplayName("Should identify bullish crossover when MACD > signal and histogram > 0")
    void shouldIdentifyBullishCrossover() {
        // Given: MACD line above signal line with positive histogram
        MACD macd = new MACD(
            new BigDecimal("100"),    // MACD line
            new BigDecimal("95"),     // Signal line
            new BigDecimal("5"),      // Histogram (MACD - Signal)
            Instant.now()
        );

        // Then
        assertThat(macd.isBullishCrossover()).isTrue();
        assertThat(macd.isBearishCrossover()).isFalse();
    }

    @Test
    @DisplayName("Should identify bearish crossover when MACD < signal and histogram < 0")
    void shouldIdentifyBearishCrossover() {
        // Given: MACD line below signal line with negative histogram
        MACD macd = new MACD(
            new BigDecimal("95"),     // MACD line
            new BigDecimal("100"),    // Signal line
            new BigDecimal("-5"),     // Histogram (MACD - Signal)
            Instant.now()
        );

        // Then
        assertThat(macd.isBearishCrossover()).isTrue();
        assertThat(macd.isBullishCrossover()).isFalse();
    }

    @Test
    @DisplayName("Should not identify crossover when histogram is zero (neutral state)")
    void shouldNotIdentifyCrossoverWhenNeutral() {
        // Given: MACD line equals signal line
        MACD macd = new MACD(
            new BigDecimal("100"),
            new BigDecimal("100"),
            BigDecimal.ZERO,
            Instant.now()
        );

        // Then
        assertThat(macd.isBullishCrossover()).isFalse();
        assertThat(macd.isBearishCrossover()).isFalse();
    }

    @Test
    @DisplayName("Should reject null MACD line")
    void shouldRejectNullMacdLine() {
        // When/Then
        assertThatThrownBy(() -> new MACD(
            null,
            new BigDecimal("95"),
            new BigDecimal("5"),
            Instant.now()
        ))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("MACD line cannot be null");
    }

    @Test
    @DisplayName("Should reject null signal line")
    void shouldRejectNullSignalLine() {
        // When/Then
        assertThatThrownBy(() -> new MACD(
            new BigDecimal("100"),
            null,
            new BigDecimal("5"),
            Instant.now()
        ))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Signal line cannot be null");
    }

    @Test
    @DisplayName("Should reject null histogram")
    void shouldRejectNullHistogram() {
        // When/Then
        assertThatThrownBy(() -> new MACD(
            new BigDecimal("100"),
            new BigDecimal("95"),
            null,
            Instant.now()
        ))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Histogram cannot be null");
    }

    @Test
    @DisplayName("Should reject null timestamp")
    void shouldRejectNullTimestamp() {
        // When/Then
        assertThatThrownBy(() -> new MACD(
            new BigDecimal("100"),
            new BigDecimal("95"),
            new BigDecimal("5"),
            null
        ))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Calculation timestamp cannot be null");
    }
}
