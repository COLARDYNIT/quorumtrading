package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DisplayName("MovingAverage Value Object Tests")
class MovingAverageTest {

    @Test
    @DisplayName("Should identify when MA is above another MA")
    void shouldIdentifyWhenAbove() {
        // Given
        Instant now = Instant.now();
        MovingAverage fastMA = new MovingAverage(new BigDecimal("50000"), 20, now);
        MovingAverage slowMA = new MovingAverage(new BigDecimal("48000"), 50, now);

        // Then
        assertThat(fastMA.isAbove(slowMA)).isTrue();
        assertThat(slowMA.isAbove(fastMA)).isFalse();
    }

    @Test
    @DisplayName("Should reject negative period")
    void shouldRejectNegativePeriod() {
        // When/Then
        assertThatThrownBy(() -> new MovingAverage(new BigDecimal("50000"), -1, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Period must be positive");
    }

    @Test
    @DisplayName("Should reject zero period")
    void shouldRejectZeroPeriod() {
        // When/Then
        assertThatThrownBy(() -> new MovingAverage(new BigDecimal("50000"), 0, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Period must be positive");
    }
}
