package com.quorumtrading.adapter.websocket;

import com.quorumtrading.adapter.websocket.dto.SimulationProgressMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StreamEndpoint WebSocket lifecycle.
 */
@QuarkusTest
class StreamEndpointTest {

    @Inject
    StreamEndpoint endpoint;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void shouldInjectEndpoint() {
        assertThat(endpoint).isNotNull();
    }

    @Test
    void shouldBroadcastToNoSessionsWithoutError() {
        // Should not throw exception when broadcasting to zero sessions
        SimulationProgressMessage message = new SimulationProgressMessage(
            "2025-01-28T12:00:00Z",
            "test-simulation-id",
            50.0,
            "2025-01-28T12:00:00Z",
            100
        );

        endpoint.broadcast(Collections.emptySet(), message);
        // No exception should be thrown
    }

    @Test
    void shouldSerializeMessageToJson() throws Exception {
        SimulationProgressMessage message = new SimulationProgressMessage(
            "2025-01-28T12:00:00Z",
            "test-simulation-id",
            50.0,
            "2025-01-28T12:00:00Z",
            100
        );

        String json = objectMapper.writeValueAsString(message);

        assertThat(json).contains("\"type\":\"SIMULATION_PROGRESS\"");
        assertThat(json).contains("\"simulationId\":\"test-simulation-id\"");
        assertThat(json).contains("\"progress\":50.0");
    }
}
