package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BrokerAccountIdTest {

    @Test
    void shouldCreateBrokerAccountIdFromUUID() {
        UUID uuid = UUID.randomUUID();

        BrokerAccountId id = new BrokerAccountId(uuid);

        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void shouldGenerateRandomBrokerAccountId() {
        BrokerAccountId id1 = BrokerAccountId.generate();
        BrokerAccountId id2 = BrokerAccountId.generate();

        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1.value()).isNotNull();
    }

    @Test
    void shouldThrowWhenCreatingWithNullUUID() {
        assertThatThrownBy(() -> new BrokerAccountId(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("BrokerAccountId cannot be null");
    }

    @Test
    void shouldConvertToString() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        BrokerAccountId id = new BrokerAccountId(uuid);

        assertThat(id.toString()).contains("123e4567-e89b-12d3-a456-426614174000");
    }
}
