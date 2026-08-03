package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class StrategyIdTest {

    @Test
    void shouldCreateStrategyIdFromUUID() {
        UUID uuid = UUID.randomUUID();

        StrategyId id = new StrategyId(uuid);

        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void shouldGenerateRandomStrategyId() {
        StrategyId id1 = StrategyId.generate();
        StrategyId id2 = StrategyId.generate();

        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1.value()).isNotNull();
    }

    @Test
    void shouldThrowWhenCreatingWithNullUUID() {
        assertThatThrownBy(() -> new StrategyId(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("StrategyId cannot be null");
    }

    @Test
    void shouldConvertToString() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        StrategyId id = new StrategyId(uuid);

        assertThat(id.toString()).contains("123e4567-e89b-12d3-a456-426614174000");
    }
}
