package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResearchStatusTest {

    @Test
    void shouldHaveAllStatuses() {
        // Then
        assertThat(ResearchStatus.values()).containsExactlyInAnyOrder(
            ResearchStatus.ACTIVE,
            ResearchStatus.PAUSED,
            ResearchStatus.COMPLETED
        );
    }

    @Test
    void shouldIdentifyActiveStatus() {
        // When
        boolean isActive = ResearchStatus.ACTIVE.isActive();

        // Then
        assertThat(isActive).isTrue();
    }

    @Test
    void shouldIdentifyNonActiveStatuses() {
        // Then
        assertThat(ResearchStatus.PAUSED.isActive()).isFalse();
        assertThat(ResearchStatus.COMPLETED.isActive()).isFalse();
    }

    @Test
    void shouldAllowTransitionFromActiveToPaused() {
        // When
        boolean canTransition = ResearchStatus.ACTIVE.canTransitionTo(ResearchStatus.PAUSED);

        // Then
        assertThat(canTransition).isTrue();
    }

    @Test
    void shouldAllowTransitionFromPausedToActive() {
        // When
        boolean canTransition = ResearchStatus.PAUSED.canTransitionTo(ResearchStatus.ACTIVE);

        // Then
        assertThat(canTransition).isTrue();
    }

    @Test
    void shouldRejectTransitionFromCompletedToActive() {
        // When
        boolean canTransition = ResearchStatus.COMPLETED.canTransitionTo(ResearchStatus.ACTIVE);

        // Then
        assertThat(canTransition).isFalse();
    }

    @Test
    void shouldRejectTransitionFromCompletedToPaused() {
        // When
        boolean canTransition = ResearchStatus.COMPLETED.canTransitionTo(ResearchStatus.PAUSED);

        // Then
        assertThat(canTransition).isFalse();
    }
}
