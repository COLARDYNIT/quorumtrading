package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DisplayName("RSI Value Object Tests")
class RSITest {

    @Test
    @DisplayName("Should identify oversold condition when RSI < 30")
    void shouldIdentifyOversoldCondition() {
        // Given
        RSI rsi = new RSI(new BigDecimal("25"), Instant.now());

        // Then
        assertThat(rsi.isOversold()).isTrue();
        assertThat(rsi.isOverbought()).isFalse();
    }

    @Test
    @DisplayName("Should identify overbought condition when RSI > 70")
    void shouldIdentifyOverboughtCondition() {
        // Given
        RSI rsi = new RSI(new BigDecimal("75"), Instant.now());

        // Then
        assertThat(rsi.isOverbought()).isTrue();
        assertThat(rsi.isOversold()).isFalse();
    }

    @Test
    @DisplayName("Should reject RSI values below 0")
    void shouldRejectNegativeValues() {
        // When/Then
        assertThatThrownBy(() -> new RSI(new BigDecimal("-10"), Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RSI must be between 0 and 100");
    }

    @Test
    @DisplayName("Should reject RSI values above 100")
    void shouldRejectValuesAbove100() {
        // When/Then
        assertThatThrownBy(() -> new RSI(new BigDecimal("110"), Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RSI must be between 0 and 100");
    }

    @Test
    @DisplayName("Should accept boundary values 0 and 100")
    void shouldAcceptBoundaryValues() {
        // When/Then
        assertThatCode(() -> new RSI(BigDecimal.ZERO, Instant.now())).doesNotThrowAnyException();
        assertThatCode(() -> new RSI(new BigDecimal("100"), Instant.now())).doesNotThrowAnyException();
    }
}
