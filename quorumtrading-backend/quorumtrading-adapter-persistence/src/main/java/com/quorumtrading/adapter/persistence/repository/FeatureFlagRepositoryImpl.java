package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.FeatureFlagJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.FeatureFlagMapper;
import com.quorumtrading.application.port.out.FeatureFlagRepository;
import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.enums.Feature;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class FeatureFlagRepositoryImpl implements FeatureFlagRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    FeatureFlagMapper mapper;

    @Override
    public List<FeatureFlag> findAll() {
        TypedQuery<FeatureFlagJpaEntity> query = entityManager.createQuery(
            "SELECT f FROM FeatureFlagJpaEntity f ORDER BY f.name", FeatureFlagJpaEntity.class);
        List<FeatureFlag> flags = new ArrayList<>();
        for (FeatureFlagJpaEntity entity : query.getResultList()) {
            FeatureFlag flag = mapper.toDomain(entity);
            // null means the row names a feature this build no longer knows about
            if (flag != null) {
                flags.add(flag);
            }
        }
        return flags;
    }

    @Override
    public Optional<FeatureFlag> find(Feature feature) {
        return Optional.ofNullable(entityManager.find(FeatureFlagJpaEntity.class, feature.flagName()))
            .map(mapper::toDomain);
    }

    @Override
    public FeatureFlag save(FeatureFlag featureFlag) {
        FeatureFlagJpaEntity incoming = mapper.toJpaEntity(featureFlag);
        FeatureFlagJpaEntity existing = entityManager.find(FeatureFlagJpaEntity.class, incoming.getName());
        if (existing == null) {
            entityManager.persist(incoming);
        } else {
            existing.setEnabled(incoming.isEnabled());
            existing.setStrategies(incoming.getStrategies());
            existing.setUpdatedAt(incoming.getUpdatedAt());
            existing.setUpdatedBy(incoming.getUpdatedBy());
            entityManager.merge(existing);
        }
        return featureFlag;
    }

    @Override
    public String versionStamp() {
        // One aggregate query, so the Unleash SDK's poll costs a single index probe when nothing
        // has changed. Count is part of the stamp so that a delete is noticed even if it does not
        // move MAX(updated_at).
        Object[] row = (Object[]) entityManager.createQuery(
                "SELECT COUNT(f), MAX(f.updatedAt) FROM FeatureFlagJpaEntity f")
            .getSingleResult();
        Long count = (Long) row[0];
        Instant latest = (Instant) row[1];
        return count + ":" + (latest == null ? "none" : Objects.toString(latest.toEpochMilli()));
    }
}
