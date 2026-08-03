package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class SimulationIdTest {

    @Test
    void shouldCreateSimulationIdFromUUID() {
        UUID uuid = UUID.randomUUID();

        SimulationId id = new SimulationId(uuid);

        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void shouldGenerateRandomSimulationId() {
        SimulationId id1 = SimulationId.generate();
        SimulationId id2 = SimulationId.generate();

        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1.value()).isNotNull();
    }

    @Test
    void shouldThrowWhenCreatingWithNullUUID() {
        assertThatThrownBy(() -> new SimulationId(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SimulationId cannot be null");
    }

    @Test
    void shouldConvertToString() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        SimulationId id = new SimulationId(uuid);

        assertThat(id.toString()).contains("123e4567-e89b-12d3-a456-426614174000");
    }
}
