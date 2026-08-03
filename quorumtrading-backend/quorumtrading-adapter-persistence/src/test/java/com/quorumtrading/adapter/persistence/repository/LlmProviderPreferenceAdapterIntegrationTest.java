package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.LlmProviderPreferencePort;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort.LlmProviderPreference;
import com.quorumtrading.model.enums.LlmProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip integration test for {@link LlmProviderPreferenceAdapter} on {@code quorumtrading_test}
 * (V120). Covers the single-row upsert semantics, key encryption at rest, and the read cache's
 * invalidation on writes.
 */
@QuarkusTest
class LlmProviderPreferenceAdapterIntegrationTest {

    @Inject
    LlmProviderPreferenceAdapter adapter;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    @AfterEach
    void wipe() {
        adapter.clear();
    }

    @Test
    void unselectedByDefault() {
        assertThat(adapter.current()).isEmpty();
        assertThat(adapter.openRouterApiKey()).isEmpty();
    }

    @Test
    void selectPersistsProviderWithTimestamp() {
        adapter.select(LlmProvider.LOCAL);

        Optional<LlmProviderPreference> current = adapter.current();
        assertThat(current).isPresent();
        assertThat(current.get().provider()).isEqualTo(LlmProvider.LOCAL);
        assertThat(current.get().configuredAt()).isNotNull();
    }

    @Test
    void reselectOverwritesSingleRow() {
        adapter.select(LlmProvider.LOCAL);
        adapter.select(LlmProvider.OPENROUTER);

        assertThat(adapter.current()).isPresent();
        assertThat(adapter.current().get().provider()).isEqualTo(LlmProvider.OPENROUTER);

        Number rows = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM llm_provider_preference")
                .getSingleResult();
        assertThat(rows.longValue()).isEqualTo(1L);
    }

    @Test
    void apiKeyRoundTripsAndIsEncryptedAtRest() {
        adapter.saveOpenRouterApiKey("sk-or-v1-roundtrip-secret");

        assertThat(adapter.openRouterApiKey()).contains("sk-or-v1-roundtrip-secret");

        byte[] stored = (byte[]) entityManager
                .createNativeQuery("SELECT openrouter_api_key_encrypted FROM llm_provider_preference")
                .getSingleResult();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1))
                .as("column content must be ciphertext, not the plaintext key")
                .doesNotContain("sk-or-v1");
    }

    @Test
    void savingKeyDoesNotSelectAProvider() {
        adapter.saveOpenRouterApiKey("sk-or-v1-key-only");

        assertThat(adapter.current()).isEmpty();
        assertThat(adapter.openRouterApiKey()).contains("sk-or-v1-key-only");
    }

    @Test
    void keySurvivesSubsequentSelection() {
        adapter.saveOpenRouterApiKey("sk-or-v1-keep-me");
        adapter.select(LlmProvider.OPENROUTER);

        assertThat(adapter.openRouterApiKey()).contains("sk-or-v1-keep-me");
        assertThat(adapter.current().get().provider()).isEqualTo(LlmProvider.OPENROUTER);
    }

    @Test
    void clearRemovesSelectionAndKey() {
        adapter.saveOpenRouterApiKey("sk-or-v1-gone");
        adapter.select(LlmProvider.OPENROUTER);

        adapter.clear();

        assertThat(adapter.current()).isEmpty();
        assertThat(adapter.openRouterApiKey()).isEmpty();
    }

    @Test
    void portInterfaceIsSatisfiedByThisAdapter() {
        assertThat(adapter).isInstanceOf(LlmProviderPreferencePort.class);
    }
}
