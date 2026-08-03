package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.NotificationPreferenceJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.NotificationPreferenceMapper;
import com.quorumtrading.application.port.out.NotificationPreferenceRepository;
import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class NotificationPreferenceRepositoryImpl implements NotificationPreferenceRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    NotificationPreferenceMapper mapper;

    @Override
    public NotificationPreference save(NotificationPreference preference) {
        NotificationPreferenceJpaEntity entity = mapper.toJpaEntity(preference);
        NotificationPreferenceJpaEntity.PreferenceKey key =
                new NotificationPreferenceJpaEntity.PreferenceKey(
                        entity.getUserId(), entity.getType());
        NotificationPreferenceJpaEntity existing = entityManager.find(
                NotificationPreferenceJpaEntity.class, key);
        if (existing != null) {
            existing.setChannels(entity.getChannels());
            entityManager.merge(existing);
        } else {
            entityManager.persist(entity);
        }
        return preference;
    }

    @Override
    public Optional<NotificationPreference> find(UserId userId, NotificationType type) {
        NotificationPreferenceJpaEntity.PreferenceKey key =
                new NotificationPreferenceJpaEntity.PreferenceKey(userId.value(), type.name());
        return Optional.ofNullable(entityManager.find(NotificationPreferenceJpaEntity.class, key))
                .map(mapper::toDomain);
    }

    @Override
    public List<NotificationPreference> findByUser(UserId userId) {
        TypedQuery<NotificationPreferenceJpaEntity> query = entityManager.createQuery(
                "SELECT p FROM NotificationPreferenceJpaEntity p WHERE p.userId = :userId",
                NotificationPreferenceJpaEntity.class);
        query.setParameter("userId", userId.value());
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }
}
