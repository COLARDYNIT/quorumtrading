package com.quorumtrading.application.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ResearchExecutionStartedEvent Tests")
class ResearchExecutionStartedEventTest {

    private static final UUID RESEARCH_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    @Test
    @DisplayName("Should create event with factory method")
    void shouldCreateEventWithFactoryMethod() {
        // When
        ResearchExecutionStartedEvent event = ResearchExecutionStartedEvent.of(
                RESEARCH_ID,
                EXECUTION_ID
        );

        // Then
        assertThat(event).isNotNull();
        assertThat(event.researchId()).isEqualTo(RESEARCH_ID);
        assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should create event with constructor")
    void shouldCreateEventWithConstructor() {
        // When
        ResearchExecutionStartedEvent event = new ResearchExecutionStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                NOW
        );

        // Then
        assertThat(event).isNotNull();
        assertThat(event.researchId()).isEqualTo(RESEARCH_ID);
        assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(event.timestamp()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Should reject null research ID")
    void shouldRejectNullResearchId() {
        // When/Then
        assertThatThrownBy(() -> new ResearchExecutionStartedEvent(
                null,
                EXECUTION_ID,
                NOW
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Research ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null execution ID")
    void shouldRejectNullExecutionId() {
        // When/Then
        assertThatThrownBy(() -> new ResearchExecutionStartedEvent(
                RESEARCH_ID,
                null,
                NOW
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Execution ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null timestamp")
    void shouldRejectNullTimestamp() {
        // When/Then
        assertThatThrownBy(() -> new ResearchExecutionStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Timestamp cannot be null");
    }

    @Test
    @DisplayName("Should be immutable (record type)")
    void shouldBeImmutable() {
        // Given
        ResearchExecutionStartedEvent event1 = new ResearchExecutionStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                NOW
        );

        ResearchExecutionStartedEvent event2 = new ResearchExecutionStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                NOW
        );

        // When/Then - Records provide automatic equals/hashCode
        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString (record type)")
    void shouldHaveMeaningfulToString() {
        // Given
        ResearchExecutionStartedEvent event = new ResearchExecutionStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                NOW
        );

        // When
        String toString = event.toString();

        // Then - Records provide automatic toString with all fields
        assertThat(toString)
                .contains("ResearchExecutionStartedEvent")
                .contains(RESEARCH_ID.toString())
                .contains(EXECUTION_ID.toString())
                .contains(NOW.toString());
    }
}
