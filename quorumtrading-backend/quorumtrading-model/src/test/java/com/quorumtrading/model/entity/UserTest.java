package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.enums.UserRole;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    void shouldCreateUserWithRequiredFields() {
        UserId id = UserId.generate();
        User user = User.create(id, "test@example.com", "Test User", "https://avatar.url", OAuthProvider.GOOGLE);

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Test User");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.hasProvider(OAuthProvider.GOOGLE)).isTrue();
    }

    @Test
    void shouldLinkAdditionalProvider() {
        UserId id = UserId.generate();
        User user = User.create(id, "test@example.com", "Test User", null, OAuthProvider.GOOGLE);

        assertThat(user.hasProvider(OAuthProvider.APPLE)).isFalse();
        user.addProvider(OAuthProvider.APPLE, "apple-sub-123");
        assertThat(user.hasProvider(OAuthProvider.APPLE)).isTrue();
    }

    @Test
    void shouldUpdateLastLogin() {
        UserId id = UserId.generate();
        User user = User.create(id, "test@example.com", "Test User", null, OAuthProvider.GOOGLE);

        assertThat(user.getLastLoginAt()).isNull();
        user.recordLogin();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void shouldThrowOnBlankEmail() {
        assertThatThrownBy(() -> User.create(UserId.generate(), "", "Name", null, OAuthProvider.GOOGLE))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
