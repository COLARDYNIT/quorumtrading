package com.quorumtrading.adapter.websocket;

import com.quorumtrading.adapter.websocket.event.SimulationProgressEvent;
import com.quorumtrading.adapter.websocket.mapper.WebSocketMessageMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MessageBroadcaster service.
 */
@QuarkusTest
class MessageBroadcasterTest {

    @Inject
    MessageBroadcaster broadcaster;

    @Inject
    WebSocketMessageMapper mapper;

    @Test
    void shouldInjectBroadcaster() {
        assertThat(broadcaster).isNotNull();
    }

    @Test
    void shouldInjectMapper() {
        assertThat(mapper).isNotNull();
    }

    @Test
    void shouldHandleEventGracefully() {
        // Arrange
        UUID simulationId = UUID.randomUUID();
        SimulationProgressEvent event = new SimulationProgressEvent(
            simulationId,
            "RUNNING",
            50,
            "Processing...",
            Instant.now()
        );

        // Act - should not throw exception even if no subscribers
        broadcaster.onSimulationProgress(event);

        // Assert - if we get here, no exception was thrown
        assertThat(true).isTrue();
    }
}
