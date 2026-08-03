package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class BacktestConfigurationTest {

    @Test
    void shouldCreateConfigWithLengthOnly() {
        // Given
        Duration length = Duration.ofDays(30);

        // When
        BacktestConfiguration config = new BacktestConfiguration(length, null, null);

        // Then
        assertThat(config.backtestLength()).isEqualTo(length);
        assertThat(config.periodStart()).isNull();
        assertThat(config.periodEnd()).isNull();
    }

    @Test
    void shouldCreateConfigWithPeriod() {
        // Given
        Duration length = Duration.ofDays(30);
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end = Instant.parse("2024-01-31T00:00:00Z");

        // When
        BacktestConfiguration config = new BacktestConfiguration(length, start, end);

        // Then
        assertThat(config.backtestLength()).isEqualTo(length);
        assertThat(config.periodStart()).isEqualTo(start);
        assertThat(config.periodEnd()).isEqualTo(end);
    }

    @Test
    void shouldRejectNullLength() {
        // When/Then
        assertThatThrownBy(() -> new BacktestConfiguration(null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Backtest length cannot be null");
    }

    @Test
    void shouldRejectNegativeDuration() {
        // When/Then
        assertThatThrownBy(() -> new BacktestConfiguration(Duration.ofDays(-5), null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Backtest length must be positive");
    }

    @Test
    void shouldRejectZeroDuration() {
        // When/Then
        assertThatThrownBy(() -> new BacktestConfiguration(Duration.ZERO, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Backtest length must be positive");
    }

    @Test
    void shouldRejectPeriodEndBeforeStart() {
        // Given
        Instant start = Instant.parse("2024-01-31T00:00:00Z");
        Instant end = Instant.parse("2024-01-01T00:00:00Z");

        // When/Then
        assertThatThrownBy(() -> new BacktestConfiguration(Duration.ofDays(30), start, end))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Period end must be after period start");
    }

    @Test
    void shouldRejectPartialPeriod() {
        // Given
        Instant start = Instant.parse("2024-01-01T00:00:00Z");

        // When/Then
        assertThatThrownBy(() -> new BacktestConfiguration(Duration.ofDays(30), start, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Both period start and end must be provided or both null");
    }
}
