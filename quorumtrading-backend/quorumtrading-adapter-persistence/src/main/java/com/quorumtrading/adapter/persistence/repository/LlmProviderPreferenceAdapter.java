package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.crypto.CredentialEncryptor;
import com.quorumtrading.adapter.persistence.entity.LlmProviderPreferenceJpaEntity;
import com.quorumtrading.application.port.out.LlmProviderPreferencePort;
import com.quorumtrading.model.enums.LlmProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

/**
 * Persists the deployment-wide LLM provider selection in the single-row
 * {@code llm_provider_preference} table; the OpenRouter key is encrypted at rest via
 * {@link CredentialEncryptor} (same AES-256-GCM scheme as broker credentials).
 *
 * <p>Reads are cached in memory and invalidated on every write, because the LLM router and the
 * OpenRouter adapter consult this port on the hot path of every LLM call. The cache assumes a
 * single application instance — fine for the self-host edition this feature serves; in the hosted
 * SaaS the selection feature is disabled and the table stays empty.</p>
 */
@ApplicationScoped
public class LlmProviderPreferenceAdapter implements LlmProviderPreferencePort {

    private static final Logger LOG = LoggerFactory.getLogger(LlmProviderPreferenceAdapter.class);

    @Inject
    EntityManager entityManager;

    @Inject
    CredentialEncryptor encryptor;

    /** Cached row snapshot. Null = not loaded; {@code Snapshot.EMPTY} = loaded, no row. */
    private volatile Snapshot cache;

    private record Snapshot(LlmProvider provider, byte[] keyEncrypted, Instant configuredAt) {
        static final Snapshot EMPTY = new Snapshot(null, null, null);
    }

    @Override
    public Optional<LlmProviderPreference> current() {
        Snapshot snapshot = loadSnapshot();
        if (snapshot.provider() == null) {
            return Optional.empty();
        }
        return Optional.of(new LlmProviderPreference(snapshot.provider(), snapshot.configuredAt()));
    }

    @Override
    @Transactional
    public void select(LlmProvider provider) {
        LlmProviderPreferenceJpaEntity entity = findOrCreateRow();
        entity.provider = provider.name();
        entity.configuredAt = Instant.now();
        entityManager.persist(entity);
        invalidate();
        LOG.info("Persisted LLM provider selection: {}", provider);
    }

    @Override
    @Transactional
    public void saveOpenRouterApiKey(String apiKey) {
        LlmProviderPreferenceJpaEntity entity = findOrCreateRow();
        entity.openrouterApiKeyEncrypted = encryptor.encryptString(apiKey);
        entityManager.persist(entity);
        invalidate();
        LOG.info("Stored OpenRouter API key (encrypted at rest)");
    }

    @Override
    public Optional<String> openRouterApiKey() {
        Snapshot snapshot = loadSnapshot();
        if (snapshot.keyEncrypted() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(encryptor.decryptString(snapshot.keyEncrypted()));
    }

    @Override
    @Transactional
    public void clear() {
        entityManager.createQuery("DELETE FROM LlmProviderPreferenceJpaEntity").executeUpdate();
        invalidate();
        LOG.info("Cleared LLM provider preference");
    }

    private Snapshot loadSnapshot() {
        Snapshot snapshot = cache;
        if (snapshot != null) {
            return snapshot;
        }
        LlmProviderPreferenceJpaEntity entity =
                entityManager.find(LlmProviderPreferenceJpaEntity.class, LlmProviderPreferenceJpaEntity.SINGLETON_ID);
        snapshot = entity == null
                ? Snapshot.EMPTY
                : new Snapshot(parseProvider(entity.provider), entity.openrouterApiKeyEncrypted, entity.configuredAt);
        cache = snapshot;
        return snapshot;
    }

    private LlmProviderPreferenceJpaEntity findOrCreateRow() {
        LlmProviderPreferenceJpaEntity entity =
                entityManager.find(LlmProviderPreferenceJpaEntity.class, LlmProviderPreferenceJpaEntity.SINGLETON_ID);
        if (entity == null) {
            entity = new LlmProviderPreferenceJpaEntity();
            entity.id = LlmProviderPreferenceJpaEntity.SINGLETON_ID;
            entity.configuredAt = Instant.now();
        }
        return entity;
    }

    private static LlmProvider parseProvider(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LlmProvider.valueOf(value);
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown LLM provider '{}' in llm_provider_preference — treating as unselected", value);
            return null;
        }
    }

    private void invalidate() {
        cache = null;
    }
}
