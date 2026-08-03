package com.quorumtrading.application.service;

import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationServiceTest {

    private AuthorizationService authService;
    private UserId ownerId;
    private UserId otherId;

    @BeforeEach
    void setUp() {
        authService = new AuthorizationService();
        ownerId = UserId.generate();
        otherId = UserId.generate();
    }

    @Test
    void shouldAllowOwnerToViewOwnStrategy() {
        Strategy strategy = createStrategy(ownerId);
        assertThat(authService.canView(ownerId, strategy)).isTrue();
    }

    @Test
    void shouldDenyOtherUserFromViewingStrategy() {
        Strategy strategy = createStrategy(ownerId);
        assertThat(authService.canView(otherId, strategy)).isFalse();
    }

    @Test
    void shouldDenyAnonymousFromViewingStrategy() {
        Strategy strategy = createStrategy(ownerId);
        assertThat(authService.canView(null, strategy)).isFalse();
    }

    @Test
    void shouldOnlyAllowOwnerToEdit() {
        Strategy strategy = createStrategy(ownerId);
        assertThat(authService.canEdit(ownerId, strategy)).isTrue();
        assertThat(authService.canEdit(otherId, strategy)).isFalse();
        assertThat(authService.canEdit(null, strategy)).isFalse();
    }

    @Test
    void shouldOnlyAllowOwnerToDelete() {
        Strategy strategy = createStrategy(ownerId);
        assertThat(authService.canDelete(ownerId, strategy)).isTrue();
        assertThat(authService.canDelete(otherId, strategy)).isFalse();
        assertThat(authService.canDelete(null, strategy)).isFalse();
    }

    private Strategy createStrategy(UserId owner) {
        return new Strategy(StrategyId.generate(), "Test", "content", owner);
    }
}
