package com.quorumtrading.integration;

import com.quorumtrading.application.event.ConsensusStartedEvent;
import com.quorumtrading.application.event.LLMDecisionCreatedEvent;
import com.quorumtrading.application.event.TradeExecutionEvent;
import com.quorumtrading.model.valueobject.SimulationId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for CDI event publishing chain.
 *
 * Verifies that domain events are correctly fired through CDI and
 * can be observed by beans (like MessageBroadcaster). Uses a test
 * observer bean to capture fired events without requiring actual
 * WebSocket connections.
 */
@QuarkusTest
class WebSocketEventIntegrationTest {

    @Inject
    Event<LLMDecisionCreatedEvent> llmDecisionEvent;

    @Inject
    Event<ConsensusStartedEvent> consensusStartedEvent;

    @Inject
    Event<TradeExecutionEvent> tradeExecutionEvent;

    @Inject
    TestEventObserver testObserver;

    @BeforeEach
    void setUp() {
        testObserver.clear();
    }

    @Test
    @DisplayName("should fire LLM decision created event through CDI")
    void shouldFireLlmDecisionEvent() {
        // Arrange
        SimulationId simId = new SimulationId(UUID.randomUUID());
        LLMDecisionCreatedEvent event = new LLMDecisionCreatedEvent(
            simId, 1L, "TECHNICAL", "BUY",
            "Strong momentum detected", "{\"action\":\"BUY\"}",
            0.85, Instant.now(), null, null
        );

        // Act
        llmDecisionEvent.fire(event);

        // Assert — test observer should have received it
        assertThat(testObserver.getLlmDecisionEvents()).hasSize(1);
        assertThat(testObserver.getLlmDecisionEvents().get(0).getAgentRole()).isEqualTo("TECHNICAL");
    }

    @Test
    @DisplayName("should fire consensus started event through CDI")
    void shouldFireConsensusStartedEvent() {
        // Arrange
        SimulationId simId = new SimulationId(UUID.randomUUID());
        ConsensusStartedEvent event = new ConsensusStartedEvent(
            simId, UUID.randomUUID().toString(), Instant.now()
        );

        // Act
        consensusStartedEvent.fire(event);

        // Assert
        assertThat(testObserver.getConsensusStartedEvents()).hasSize(1);
        assertThat(testObserver.getConsensusStartedEvents().get(0).getSimulationId()).isEqualTo(simId);
    }

    @Test
    @DisplayName("should fire trade execution event through CDI")
    void shouldFireTradeExecutionEvent() {
        // Arrange
        SimulationId simId = new SimulationId(UUID.randomUUID());
        TradeExecutionEvent event = new TradeExecutionEvent(
            simId, 1L, "BUY", "BTC",
            BigDecimal.valueOf(0.5), BigDecimal.valueOf(45000),
            true, "Trade executed successfully",
            100L, Instant.now()
        );

        // Act
        tradeExecutionEvent.fire(event);

        // Assert
        assertThat(testObserver.getTradeExecutionEvents()).hasSize(1);
        assertThat(testObserver.getTradeExecutionEvents().get(0).getAction()).isEqualTo("BUY");
        assertThat(testObserver.getTradeExecutionEvents().get(0).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("should not fail when no WebSocket subscribers exist")
    void shouldNotFailWhenNoSubscribers() {
        // Events should fire without error even when no WebSocket sessions are connected.
        // MessageBroadcaster observes these events but should gracefully handle zero subscribers.
        SimulationId simId = new SimulationId(UUID.randomUUID());

        // Fire multiple events rapidly — none should throw
        assertThatCode(() -> {
            llmDecisionEvent.fire(new LLMDecisionCreatedEvent(
                simId, 1L, "TECHNICAL", "HOLD", "No clear signal", "{}", 0.5, Instant.now(), null, null
            ));
            consensusStartedEvent.fire(new ConsensusStartedEvent(
                simId, UUID.randomUUID().toString(), Instant.now()
            ));
            tradeExecutionEvent.fire(new TradeExecutionEvent(
                simId, 1L, "HOLD", "BTC", BigDecimal.ZERO, BigDecimal.ZERO,
                false, "No trade needed", null, Instant.now()
            ));
        }).doesNotThrowAnyException();
    }

    /**
     * CDI test observer bean that captures fired events for assertion.
     */
    @ApplicationScoped
    public static class TestEventObserver {

        private final CopyOnWriteArrayList<LLMDecisionCreatedEvent> llmDecisionEvents = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<ConsensusStartedEvent> consensusStartedEvents = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<TradeExecutionEvent> tradeExecutionEvents = new CopyOnWriteArrayList<>();

        void onLlmDecision(@Observes LLMDecisionCreatedEvent event) {
            llmDecisionEvents.add(event);
        }

        void onConsensusStarted(@Observes ConsensusStartedEvent event) {
            consensusStartedEvents.add(event);
        }

        void onTradeExecution(@Observes TradeExecutionEvent event) {
            tradeExecutionEvents.add(event);
        }

        public CopyOnWriteArrayList<LLMDecisionCreatedEvent> getLlmDecisionEvents() {
            return llmDecisionEvents;
        }

        public CopyOnWriteArrayList<ConsensusStartedEvent> getConsensusStartedEvents() {
            return consensusStartedEvents;
        }

        public CopyOnWriteArrayList<TradeExecutionEvent> getTradeExecutionEvents() {
            return tradeExecutionEvents;
        }

        public void clear() {
            llmDecisionEvents.clear();
            consensusStartedEvents.clear();
            tradeExecutionEvents.clear();
        }
    }
}
