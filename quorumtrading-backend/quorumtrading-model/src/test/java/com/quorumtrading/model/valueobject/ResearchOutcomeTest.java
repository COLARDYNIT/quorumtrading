package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResearchOutcomeTest {

    @Test
    void shouldHaveAllOutcomes() {
        // Then
        assertThat(ResearchOutcome.values()).containsExactlyInAnyOrder(
            ResearchOutcome.STRATEGY_CREATED,
            ResearchOutcome.NO_OPPORTUNITY,
            ResearchOutcome.FAILED
        );
    }

    @Test
    void shouldIdentifySuccessfulOutcome() {
        // Then
        assertThat(ResearchOutcome.STRATEGY_CREATED.isSuccessful()).isTrue();
        assertThat(ResearchOutcome.NO_OPPORTUNITY.isSuccessful()).isTrue();
        assertThat(ResearchOutcome.FAILED.isSuccessful()).isFalse();
    }
}
