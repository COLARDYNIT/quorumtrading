package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DisplayName("BollingerBands Value Object Tests")
class BollingerBandsTest {

    @Test
    @DisplayName("Should identify price above upper band (overbought)")
    void shouldIdentifyPriceAboveUpperBand() {
        // Given: BTC price at $45,000 with bands (upper=$44,000, middle=$43,000, lower=$42,000)
        BollingerBands bands = new BollingerBands(
            new BigDecimal("44000"),  // upper band
            new BigDecimal("43000"),  // middle band (SMA)
            new BigDecimal("42000"),  // lower band
            Instant.now()
        );
        BigDecimal price = new BigDecimal("45000");

        // Then
        assertThat(bands.isPriceAboveUpperBand(price)).isTrue();
        assertThat(bands.isPriceBelowLowerBand(price)).isFalse();
        assertThat(bands.isPriceInsideBands(price)).isFalse();
    }

    @Test
    @DisplayName("Should identify price below lower band (oversold)")
    void shouldIdentifyPriceBelowLowerBand() {
        // Given: ETH price at $2,000 with bands (upper=$2,400, middle=$2,300, lower=$2,200)
        BollingerBands bands = new BollingerBands(
            new BigDecimal("2400"),  // upper band
            new BigDecimal("2300"),  // middle band (SMA)
            new BigDecimal("2200"),  // lower band
            Instant.now()
        );
        BigDecimal price = new BigDecimal("2000");

        // Then
        assertThat(bands.isPriceBelowLowerBand(price)).isTrue();
        assertThat(bands.isPriceAboveUpperBand(price)).isFalse();
        assertThat(bands.isPriceInsideBands(price)).isFalse();
    }

    @Test
    @DisplayName("Should identify price inside bands (normal range)")
    void shouldIdentifyPriceInsideBands() {
        // Given: BTC price at $43,500 with bands (upper=$44,000, middle=$43,000, lower=$42,000)
        BollingerBands bands = new BollingerBands(
            new BigDecimal("44000"),  // upper band
            new BigDecimal("43000"),  // middle band (SMA)
            new BigDecimal("42000"),  // lower band
            Instant.now()
        );
        BigDecimal price = new BigDecimal("43500");

        // Then
        assertThat(bands.isPriceInsideBands(price)).isTrue();
        assertThat(bands.isPriceAboveUpperBand(price)).isFalse();
        assertThat(bands.isPriceBelowLowerBand(price)).isFalse();
    }

    @Test
    @DisplayName("Should accept price exactly on band boundaries")
    void shouldAcceptPriceOnBandBoundaries() {
        // Given
        BollingerBands bands = new BollingerBands(
            new BigDecimal("44000"),  // upper band
            new BigDecimal("43000"),  // middle band (SMA)
            new BigDecimal("42000"),  // lower band
            Instant.now()
        );

        // Then: Price exactly on lower band is considered inside
        assertThat(bands.isPriceInsideBands(new BigDecimal("42000"))).isTrue();
        assertThat(bands.isPriceBelowLowerBand(new BigDecimal("42000"))).isFalse();

        // Then: Price exactly on upper band is considered inside
        assertThat(bands.isPriceInsideBands(new BigDecimal("44000"))).isTrue();
        assertThat(bands.isPriceAboveUpperBand(new BigDecimal("44000"))).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid band ordering (lower > middle)")
    void shouldRejectInvalidBandOrderingLowerGreaterThanMiddle() {
        // When/Then
        assertThatThrownBy(() -> new BollingerBands(
            new BigDecimal("44000"),  // upper band
            new BigDecimal("43000"),  // middle band
            new BigDecimal("43500"),  // lower band (INVALID: > middle)
            Instant.now()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Lower band must be <= middle band");
    }

    @Test
    @DisplayName("Should reject invalid band ordering (middle > upper)")
    void shouldRejectInvalidBandOrderingMiddleGreaterThanUpper() {
        // When/Then
        assertThatThrownBy(() -> new BollingerBands(
            new BigDecimal("42000"),  // upper band (INVALID: < middle)
            new BigDecimal("43000"),  // middle band
            new BigDecimal("41000"),  // lower band
            Instant.now()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Middle band must be <= upper band");
    }

    @Test
    @DisplayName("Should accept valid band ordering (lower <= middle <= upper)")
    void shouldAcceptValidBandOrdering() {
        // When/Then: Equal bands (zero volatility scenario)
        assertThatCode(() -> new BollingerBands(
            new BigDecimal("43000"),  // upper band
            new BigDecimal("43000"),  // middle band (same)
            new BigDecimal("43000"),  // lower band (same)
            Instant.now()
        )).doesNotThrowAnyException();

        // When/Then: Normal band ordering
        assertThatCode(() -> new BollingerBands(
            new BigDecimal("44000"),  // upper band
            new BigDecimal("43000"),  // middle band
            new BigDecimal("42000"),  // lower band
            Instant.now()
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject null upper band")
    void shouldRejectNullUpperBand() {
        // When/Then
        assertThatThrownBy(() -> new BollingerBands(
            null,                     // upper band (INVALID)
            new BigDecimal("43000"),  // middle band
            new BigDecimal("42000"),  // lower band
            Instant.now()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Upper band cannot be null");
    }

    @Test
    @DisplayName("Should reject null middle band")
    void shouldRejectNullMiddleBand() {
        // When/Then
        assertThatThrownBy(() -> new BollingerBands(
            new BigDecimal("44000"),  // upper band
            null,                     // middle band (INVALID)
            new BigDecimal("42000"),  // lower band
            Instant.now()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Middle band cannot be null");
    }

    @Test
    @DisplayName("Should reject null lower band")
    void shouldRejectNullLowerBand() {
        // When/Then
        assertThatThrownBy(() -> new BollingerBands(
            new BigDecimal("44000"),  // upper band
            new BigDecimal("43000"),  // middle band
            null,                     // lower band (INVALID)
            Instant.now()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Lower band cannot be null");
    }

    @Test
    @DisplayName("Should reject null calculation timestamp")
    void shouldRejectNullCalculationTimestamp() {
        // When/Then
        assertThatThrownBy(() -> new BollingerBands(
            new BigDecimal("44000"),  // upper band
            new BigDecimal("43000"),  // middle band
            new BigDecimal("42000"),  // lower band
            null                      // timestamp (INVALID)
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Calculation timestamp cannot be null");
    }

    @Test
    @DisplayName("Should reject null price in isPriceAboveUpperBand()")
    void shouldRejectNullPriceInIsPriceAboveUpperBand() {
        // Given
        BollingerBands bands = new BollingerBands(
            new BigDecimal("44000"),
            new BigDecimal("43000"),
            new BigDecimal("42000"),
            Instant.now()
        );

        // When/Then
        assertThatThrownBy(() -> bands.isPriceAboveUpperBand(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Price cannot be null");
    }

    @Test
    @DisplayName("Should reject null price in isPriceBelowLowerBand()")
    void shouldRejectNullPriceInIsPriceBelowLowerBand() {
        // Given
        BollingerBands bands = new BollingerBands(
            new BigDecimal("44000"),
            new BigDecimal("43000"),
            new BigDecimal("42000"),
            Instant.now()
        );

        // When/Then
        assertThatThrownBy(() -> bands.isPriceBelowLowerBand(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Price cannot be null");
    }

    @Test
    @DisplayName("Should reject null price in isPriceInsideBands()")
    void shouldRejectNullPriceInIsPriceInsideBands() {
        // Given
        BollingerBands bands = new BollingerBands(
            new BigDecimal("44000"),
            new BigDecimal("43000"),
            new BigDecimal("42000"),
            Instant.now()
        );

        // When/Then
        assertThatThrownBy(() -> bands.isPriceInsideBands(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Price cannot be null");
    }
}
