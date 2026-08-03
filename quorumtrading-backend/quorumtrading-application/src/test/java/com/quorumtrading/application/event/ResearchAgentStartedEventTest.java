package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.ResearchAgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ResearchAgentStartedEvent Tests")
class ResearchAgentStartedEventTest {

    private static final UUID RESEARCH_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();
    private static final ResearchAgentType AGENT_TYPE = ResearchAgentType.NEWS_SENTIMENT_ANALYST;
    private static final Instant NOW = Instant.now();

    @Test
    @DisplayName("Should create event with factory method")
    void shouldCreateEventWithFactoryMethod() {
        // When
        ResearchAgentStartedEvent event = ResearchAgentStartedEvent.of(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE
        );

        // Then
        assertThat(event).isNotNull();
        assertThat(event.researchId()).isEqualTo(RESEARCH_ID);
        assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(event.agentType()).isEqualTo(AGENT_TYPE);
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should create event with constructor")
    void shouldCreateEventWithConstructor() {
        // When
        ResearchAgentStartedEvent event = new ResearchAgentStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                NOW
        );

        // Then
        assertThat(event).isNotNull();
        assertThat(event.researchId()).isEqualTo(RESEARCH_ID);
        assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(event.agentType()).isEqualTo(AGENT_TYPE);
        assertThat(event.timestamp()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Should reject null research ID")
    void shouldRejectNullResearchId() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentStartedEvent(
                null,
                EXECUTION_ID,
                AGENT_TYPE,
                NOW
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Research ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null execution ID")
    void shouldRejectNullExecutionId() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentStartedEvent(
                RESEARCH_ID,
                null,
                AGENT_TYPE,
                NOW
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Execution ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null agent type")
    void shouldRejectNullAgentType() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                null,
                NOW
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Agent type cannot be null");
    }

    @Test
    @DisplayName("Should reject null timestamp")
    void shouldRejectNullTimestamp() {
        // When/Then
        assertThatThrownBy(() -> new ResearchAgentStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Timestamp cannot be null");
    }

    @Test
    @DisplayName("Should work with all agent types")
    void shouldWorkWithAllAgentTypes() {
        // When/Then - verify all agent types can be used
        for (ResearchAgentType agentType : ResearchAgentType.values()) {
            ResearchAgentStartedEvent event = ResearchAgentStartedEvent.of(
                    RESEARCH_ID,
                    EXECUTION_ID,
                    agentType
            );
            assertThat(event.agentType()).isEqualTo(agentType);
        }
    }

    @Test
    @DisplayName("Should be immutable (record type)")
    void shouldBeImmutable() {
        // Given
        ResearchAgentStartedEvent event1 = new ResearchAgentStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                NOW
        );

        ResearchAgentStartedEvent event2 = new ResearchAgentStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
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
        ResearchAgentStartedEvent event = new ResearchAgentStartedEvent(
                RESEARCH_ID,
                EXECUTION_ID,
                AGENT_TYPE,
                NOW
        );

        // When
        String toString = event.toString();

        // Then - Records provide automatic toString with all fields
        assertThat(toString)
                .contains("ResearchAgentStartedEvent")
                .contains(RESEARCH_ID.toString())
                .contains(EXECUTION_ID.toString())
                .contains(AGENT_TYPE.name())
                .contains(NOW.toString());
    }
}
