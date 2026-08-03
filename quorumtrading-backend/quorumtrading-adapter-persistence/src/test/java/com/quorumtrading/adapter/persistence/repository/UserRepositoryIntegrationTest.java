package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.StrategyJpaEntity;
import com.quorumtrading.adapter.persistence.entity.UserJpaEntity;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@QuarkusTest
class UserRepositoryIntegrationTest {

    @Inject
    UserRepository userRepository;

    @AfterEach
    @Transactional
    void cleanup() {
        // Delete strategies owned by test users first (FK constraint on owner_id -> users)
        // Get IDs of users we're about to delete (all except system users)
        StrategyJpaEntity.delete("ownerId IN (SELECT id FROM UserJpaEntity WHERE email NOT IN (?1, ?2))",
            "system@quorumtrading.com", "system@yoloquant.ai");
        // Then delete test users (but not system user)
        UserJpaEntity.delete("email NOT IN (?1, ?2)", "system@quorumtrading.com", "system@yoloquant.ai");
    }

    @Test
    void shouldSaveAndFindUser() {
        UserId id = UserId.generate();
        User user = User.create(id, "test@example.com", "Test User", null, OAuthProvider.GOOGLE);

        User saved = userRepository.save(user);

        Optional<User> found = userRepository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getDisplayName()).isEqualTo("Test User");
        assertThat(found.get().hasProvider(OAuthProvider.GOOGLE)).isTrue();
    }

    @Test
    void shouldFindByEmail() {
        UserId id = UserId.generate();
        User user = User.create(id, "find@example.com", "Find User", null, OAuthProvider.APPLE);
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("find@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(id);
    }

    @Test
    void shouldUpdateExistingUser() {
        UserId id = UserId.generate();
        User user = User.create(id, "update@example.com", "Original", null, OAuthProvider.GOOGLE);
        userRepository.save(user);

        User updated = userRepository.findById(id).orElseThrow();
        updated.updateProfile("Updated Name", "https://new-avatar.url");
        updated.addProvider(OAuthProvider.APPLE, "apple-id");
        userRepository.save(updated);

        User found = userRepository.findById(id).orElseThrow();
        assertThat(found.getDisplayName()).isEqualTo("Updated Name");
        assertThat(found.getAvatarUrl()).isEqualTo("https://new-avatar.url");
        assertThat(found.hasProvider(OAuthProvider.APPLE)).isTrue();
    }

    @Test
    void shouldCheckExistsByEmail() {
        UserId id = UserId.generate();
        User user = User.create(id, "exists@example.com", "Exists", null, OAuthProvider.GOOGLE);
        userRepository.save(user);

        assertThat(userRepository.existsByEmail("exists@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("notexists@example.com")).isFalse();
    }

    @Test
    void shouldFindByProviderUserId() {
        UserId id = UserId.generate();
        String appleUserId = "apple-user-123";
        User user = User.create(id, "provider@example.com", "Provider User", null, OAuthProvider.GOOGLE);
        user.addProvider(OAuthProvider.APPLE, appleUserId);
        userRepository.save(user);

        Optional<User> found = userRepository.findByProviderUserId("APPLE", appleUserId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(id);
    }

    @Test
    void shouldNormalizeEmailToLowercase() {
        UserId id = UserId.generate();
        User user = User.create(id, "UPPERCASE@EXAMPLE.COM", "Upper", null, OAuthProvider.GOOGLE);
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("uppercase@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("uppercase@example.com");
    }

    @Test
    void shouldSaveUserWithMultipleProviders() {
        UserId id = UserId.generate();
        User user = User.create(id, "multi@example.com", "Multi Provider", null, OAuthProvider.GOOGLE);
        user.addProvider(OAuthProvider.APPLE, "apple-123");
        userRepository.save(user);

        User found = userRepository.findById(id).orElseThrow();
        assertThat(found.hasProvider(OAuthProvider.GOOGLE)).isTrue();
        assertThat(found.hasProvider(OAuthProvider.APPLE)).isTrue();
    }

    @Test
    void shouldHandleNullAvatarUrl() {
        UserId id = UserId.generate();
        User user = User.create(id, "noavatar@example.com", "No Avatar", null, OAuthProvider.GOOGLE);
        userRepository.save(user);

        User found = userRepository.findById(id).orElseThrow();
        assertThat(found.getAvatarUrl()).isNull();
    }

    @Test
    void shouldRecordLoginTimestamp() {
        UserId id = UserId.generate();
        User user = User.create(id, "login@example.com", "Login User", null, OAuthProvider.GOOGLE);
        userRepository.save(user);

        User found = userRepository.findById(id).orElseThrow();
        assertThat(found.getLastLoginAt()).isNull();

        found.recordLogin();
        userRepository.save(found);

        User updated = userRepository.findById(id).orElseThrow();
        assertThat(updated.getLastLoginAt()).isNotNull();
    }
}
