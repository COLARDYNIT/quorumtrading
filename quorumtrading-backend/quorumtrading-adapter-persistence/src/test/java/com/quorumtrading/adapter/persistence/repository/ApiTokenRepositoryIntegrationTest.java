package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.ApiTokenJpaEntity;
import com.quorumtrading.application.port.out.ApiTokenRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.ApiToken;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ApiTokenRepositoryIntegrationTest {

    @Inject
    ApiTokenRepository apiTokenRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    private UserId testUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        testUserId = UserId.generate();
        User user = User.create(testUserId, "apitoken-test@example.com", "Token Test", null, OAuthProvider.GOOGLE);
        userRepository.save(user);
    }

    @AfterEach
    @Transactional
    void cleanup() {
        entityManager.createQuery("DELETE FROM ApiTokenJpaEntity t WHERE t.userId = :userId")
                .setParameter("userId", testUserId.value())
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users WHERE email = 'apitoken-test@example.com'")
                .executeUpdate();
    }

    @Test
    void shouldSaveAndFindById() {
        ApiTokenId tokenId = ApiTokenId.generate();
        ApiToken token = ApiToken.create(tokenId, testUserId, "Test Token", "abc123hash", "qt_test1", null);

        apiTokenRepository.save(token);

        Optional<ApiToken> found = apiTokenRepository.findById(tokenId);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Token");
        assertThat(found.get().getTokenHash()).isEqualTo("abc123hash");
        assertThat(found.get().getTokenPrefix()).isEqualTo("qt_test1");
        assertThat(found.get().getUserId()).isEqualTo(testUserId);
        assertThat(found.get().isRevoked()).isFalse();
    }

    @Test
    void shouldFindByTokenHash() {
        String tokenHash = "unique_hash_value_for_lookup";
        ApiToken token = ApiToken.create(ApiTokenId.generate(), testUserId, "Hash Lookup", tokenHash, "qt_hash1", null);
        apiTokenRepository.save(token);

        Optional<ApiToken> found = apiTokenRepository.findByTokenHash(tokenHash);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Hash Lookup");
    }

    @Test
    void shouldReturnEmptyForUnknownHash() {
        Optional<ApiToken> found = apiTokenRepository.findByTokenHash("nonexistent_hash");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByUserId() {
        ApiToken token1 = ApiToken.create(ApiTokenId.generate(), testUserId, "Token A", "hash_a", "qt_tokna", null);
        ApiToken token2 = ApiToken.create(ApiTokenId.generate(), testUserId, "Token B", "hash_b", "qt_toknb", null);
        apiTokenRepository.save(token1);
        apiTokenRepository.save(token2);

        List<ApiToken> tokens = apiTokenRepository.findByUserId(testUserId);
        assertThat(tokens).hasSize(2);
    }

    @Test
    void shouldReturnEmptyListForUserWithNoTokens() {
        List<ApiToken> tokens = apiTokenRepository.findByUserId(UserId.generate());
        assertThat(tokens).isEmpty();
    }

    @Test
    void shouldUpdateExistingToken() {
        ApiTokenId tokenId = ApiTokenId.generate();
        ApiToken token = ApiToken.create(tokenId, testUserId, "Before Revoke", "hash_rev", "qt_befor", null);
        apiTokenRepository.save(token);

        // Re-fetch and revoke
        ApiToken toRevoke = apiTokenRepository.findById(tokenId).orElseThrow();
        toRevoke.revoke();
        apiTokenRepository.save(toRevoke);

        ApiToken found = apiTokenRepository.findById(tokenId).orElseThrow();
        assertThat(found.isRevoked()).isTrue();
        assertThat(found.getRevokedAt()).isNotNull();
    }

    @Test
    void shouldDeleteById() {
        ApiTokenId tokenId = ApiTokenId.generate();
        ApiToken token = ApiToken.create(tokenId, testUserId, "To Delete", "hash_del", "qt_todel", null);
        apiTokenRepository.save(token);

        apiTokenRepository.deleteById(tokenId);

        Optional<ApiToken> found = apiTokenRepository.findById(tokenId);
        assertThat(found).isEmpty();
    }

    @Test
    void shouldPersistExpiresAt() {
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        ApiToken token = ApiToken.create(ApiTokenId.generate(), testUserId, "Expiring", "hash_exp", "qt_expir", expiresAt);
        apiTokenRepository.save(token);

        ApiToken found = apiTokenRepository.findByTokenHash("hash_exp").orElseThrow();
        assertThat(found.getExpiresAt()).isNotNull();
    }

    @Test
    void shouldPersistLastUsedAt() {
        ApiTokenId tokenId = ApiTokenId.generate();
        ApiToken token = ApiToken.create(tokenId, testUserId, "Usage Tracking", "hash_use", "qt_usage", null);
        apiTokenRepository.save(token);

        ApiToken toUpdate = apiTokenRepository.findById(tokenId).orElseThrow();
        toUpdate.recordUsage();
        apiTokenRepository.save(toUpdate);

        ApiToken found = apiTokenRepository.findById(tokenId).orElseThrow();
        assertThat(found.getLastUsedAt()).isNotNull();
    }
}
