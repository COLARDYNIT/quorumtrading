package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class ViabilityScoreTest {

    @Test
    void shouldCreateValidScore() {
        // When
        ViabilityScore score = new ViabilityScore(new BigDecimal("75.50"));

        // Then
        assertThat(score.value()).isEqualByComparingTo(new BigDecimal("75.50"));
    }

    @Test
    void shouldAcceptZeroScore() {
        // When
        ViabilityScore score = new ViabilityScore(BigDecimal.ZERO);

        // Then
        assertThat(score.value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldAcceptHundredScore() {
        // When
        ViabilityScore score = new ViabilityScore(new BigDecimal("100.00"));

        // Then
        assertThat(score.value()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void shouldRejectNegativeScore() {
        // When/Then
        assertThatThrownBy(() -> new ViabilityScore(new BigDecimal("-1.00")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Viability score must be between 0 and 100");
    }

    @Test
    void shouldRejectScoreAboveHundred() {
        // When/Then
        assertThatThrownBy(() -> new ViabilityScore(new BigDecimal("100.01")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Viability score must be between 0 and 100");
    }

    @Test
    void shouldRejectNullScore() {
        // When/Then
        assertThatThrownBy(() -> new ViabilityScore(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Viability score cannot be null");
    }

    @Test
    void shouldBeEqualWhenSameValues() {
        // Given
        ViabilityScore score1 = new ViabilityScore(new BigDecimal("75.50"));
        ViabilityScore score2 = new ViabilityScore(new BigDecimal("75.50"));

        // Then
        assertThat(score1).isEqualTo(score2);
        assertThat(score1.hashCode()).isEqualTo(score2.hashCode());
    }
}
