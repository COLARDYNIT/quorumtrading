package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResearchIntervalTest {

    @Test
    void shouldCreateValidInterval() {
        // When
        ResearchInterval interval = new ResearchInterval(7, ResearchInterval.Unit.DAYS);

        // Then
        assertThat(interval.amount()).isEqualTo(7);
        assertThat(interval.unit()).isEqualTo(ResearchInterval.Unit.DAYS);
    }

    @Test
    void shouldRejectZeroAmount() {
        // When/Then
        assertThatThrownBy(() -> new ResearchInterval(0, ResearchInterval.Unit.DAYS))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Interval amount must be at least 1");
    }

    @Test
    void shouldRejectNegativeAmount() {
        // When/Then
        assertThatThrownBy(() -> new ResearchInterval(-5, ResearchInterval.Unit.DAYS))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Interval amount must be at least 1");
    }

    @Test
    void shouldRejectNullUnit() {
        // When/Then
        assertThatThrownBy(() -> new ResearchInterval(7, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Interval unit cannot be null");
    }

    @Test
    void shouldBeEqualWhenSameValues() {
        // Given
        ResearchInterval interval1 = new ResearchInterval(7, ResearchInterval.Unit.DAYS);
        ResearchInterval interval2 = new ResearchInterval(7, ResearchInterval.Unit.DAYS);

        // Then
        assertThat(interval1).isEqualTo(interval2);
        assertThat(interval1.hashCode()).isEqualTo(interval2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenDifferentValues() {
        // Given
        ResearchInterval interval1 = new ResearchInterval(7, ResearchInterval.Unit.DAYS);
        ResearchInterval interval2 = new ResearchInterval(14, ResearchInterval.Unit.DAYS);

        // Then
        assertThat(interval1).isNotEqualTo(interval2);
    }
}
