package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.ResearchAgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ResearchAgentCompletedEvent Tests")
class ResearchAgentCompletedEventTest {

    private static final UUID RESEARCH_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();
    private static final ResearchAgentType AGENT_TYPE = ResearchAgentType.STRATEGY_DESIGNER;
    private static final long DURATION_MS = 1500L;
    private static final Instant NOW = Instant.now();

    @Test
    @DisplayName("Should create event with factory method")
    void shouldCreateEventWithFactoryMethod() {
        // When
        ResearchAgentCompletedEvent event = ResearchAgentCompletedEvent.of(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                DURATION_MS
        );

        // Then
        assertThat(event).isNotNull();
        assertThat(event.researchId()).isEqualTo(RESEARCH_ID);
        assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(event.agentType()).isEqualTo(AGENT_TYPE);
        assertThat(event.durationMs()).isEqualTo(DURATION_MS);
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should create event with constructor")
    void shouldCreateEventWithConstructor() {
        // When
        ResearchAgentCompletedEvent event = new ResearchAgentCompletedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                DURATION_MS,
                NOW
        );

        // Then
        assertThat(event).isNotNull();
        assertThat(event.researchId()).isEqualTo(RESEARCH_ID);
        assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(event.agentType()).isEqualTo(AGENT_TYPE);
        assertThat(event.durationMs()).isEqualTo(DURATION_MS);
        assertThat(event.timestamp()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Should reject null research ID")
    void shouldRejectNullResearchId() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentCompletedEvent(
                null,
                EXECUTION_ID,
                AGENT_TYPE,
                DURATION_MS,
                NOW
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Research ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null execution ID")
    void shouldRejectNullExecutionId() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentCompletedEvent(
                RESEARCH_ID,
                null,
                AGENT_TYPE,
                DURATION_MS,
                NOW
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Execution ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null agent type")
    void shouldRejectNullAgentType() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentCompletedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                null,
                DURATION_MS,
                NOW
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Agent type cannot be null");
    }

    @Test
    @DisplayName("Should reject null timestamp")
    void shouldRejectNullTimestamp() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentCompletedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                DURATION_MS,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Timestamp cannot be null");
    }

    @Test
    @DisplayName("Should reject negative duration")
    void shouldRejectNegativeDuration() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentCompletedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                -1L,
                NOW
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duration cannot be negative");
    }

    @Test
    @DisplayName("Should accept zero duration")
    void shouldAcceptZeroDuration() {
        // When
        ResearchAgentCompletedEvent event = new ResearchAgentCompletedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                0L,
                NOW
        );

        // Then
        assertThat(event.durationMs()).isZero();
    }

    @Test
    @DisplayName("Should work with all agent types")
    void shouldWorkWithAllAgentTypes() {
        // When/Then - verify all agent types can be used
        for (ResearchAgentType agentType : ResearchAgentType.values()) {
            ResearchAgentCompletedEvent event = ResearchAgentCompletedEvent.of(
                    RESEARCH_ID,
                    EXECUTION_ID,
                    agentType,
                    DURATION_MS
            );
            assertThat(event.agentType()).isEqualTo(agentType);
        }
    }

    @Test
    @DisplayName("Should be immutable (record type)")
    void shouldBeImmutable() {
        // Given
        ResearchAgentCompletedEvent event1 = new ResearchAgentCompletedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                DURATION_MS,
                NOW
        );

        ResearchAgentCompletedEvent event2 = new ResearchAgentCompletedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                DURATION_MS,
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
        ResearchAgentCompletedEvent event = new ResearchAgentCompletedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                DURATION_MS,
                NOW
        );

        // When
        String toString = event.toString();

        // Then - Records provide automatic toString with all fields
        assertThat(toString)
                .contains("ResearchAgentCompletedEvent")
                .contains(RESEARCH_ID.toString())
                .contains(EXECUTION_ID.toString())
                .contains(AGENT_TYPE.name())
                .contains(String.valueOf(DURATION_MS))
                .contains(NOW.toString());
    }
}
