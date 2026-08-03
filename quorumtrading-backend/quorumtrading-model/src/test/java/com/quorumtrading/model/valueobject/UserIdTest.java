package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class UserIdTest {

    @Test
    void shouldCreateUserIdWithValidUUID() {
        UUID uuid = UUID.randomUUID();
        UserId userId = new UserId(uuid);
        assertThat(userId.value()).isEqualTo(uuid);
    }

    @Test
    void shouldThrowOnNullUUID() {
        assertThatThrownBy(() -> new UserId(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBeEqualForSameUUID() {
        UUID uuid = UUID.randomUUID();
        UserId id1 = new UserId(uuid);
        UserId id2 = new UserId(uuid);
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void shouldGenerateValidUserId() {
        UserId userId = UserId.generate();
        assertThat(userId).isNotNull();
        assertThat(userId.value()).isNotNull();
    }

    @Test
    void shouldCreateUserIdFromValidString() {
        String uuidString = "123e4567-e89b-12d3-a456-426614174000";
        UserId userId = UserId.fromString(uuidString);
        assertThat(userId).isNotNull();
        assertThat(userId.value()).isEqualTo(UUID.fromString(uuidString));
    }
}
