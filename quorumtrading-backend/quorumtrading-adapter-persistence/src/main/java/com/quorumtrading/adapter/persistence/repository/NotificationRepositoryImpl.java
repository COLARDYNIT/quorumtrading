package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.NotificationJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.NotificationMapper;
import com.quorumtrading.application.port.out.NotificationRepository;
import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationStatus;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class NotificationRepositoryImpl implements NotificationRepository {

    private static final int HARD_LIMIT = 500;

    @Inject
    EntityManager entityManager;

    @Inject
    NotificationMapper mapper;

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = mapper.toJpaEntity(notification);
        NotificationJpaEntity existing = entityManager.find(NotificationJpaEntity.class, entity.getId());
        if (existing != null) {
            entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
        }
        return notification;
    }

    @Override
    public Optional<Notification> findById(NotificationId id) {
        return Optional.ofNullable(entityManager.find(NotificationJpaEntity.class, id.value()))
                .map(mapper::toDomain);
    }

    @Override
    public List<Notification> findByUser(UserId userId, boolean onlyUnread, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, HARD_LIMIT));
        String jpql = "SELECT n FROM NotificationJpaEntity n WHERE n.userId = :userId";
        if (onlyUnread) {
            jpql += " AND n.channel = :inApp AND n.status = :delivered";
        }
        jpql += " ORDER BY n.createdAt DESC";

        TypedQuery<NotificationJpaEntity> query = entityManager.createQuery(jpql, NotificationJpaEntity.class);
        query.setParameter("userId", userId.value());
        if (onlyUnread) {
            query.setParameter("inApp", NotificationChannel.IN_APP.name());
            query.setParameter("delivered", NotificationStatus.DELIVERED.name());
        }
        query.setMaxResults(safeLimit);

        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countUnread(UserId userId) {
        return entityManager.createQuery(
                "SELECT COUNT(n) FROM NotificationJpaEntity n " +
                        "WHERE n.userId = :userId AND n.channel = :inApp AND n.status = :delivered",
                Long.class)
                .setParameter("userId", userId.value())
                .setParameter("inApp", NotificationChannel.IN_APP.name())
                .setParameter("delivered", NotificationStatus.DELIVERED.name())
                .getSingleResult();
    }

    @Override
    public int markAllRead(UserId userId) {
        return entityManager.createQuery(
                "UPDATE NotificationJpaEntity n " +
                        "SET n.status = :read, n.readAt = :now " +
                        "WHERE n.userId = :userId AND n.channel = :inApp AND n.status = :delivered")
                .setParameter("read", NotificationStatus.READ.name())
                .setParameter("now", Instant.now())
                .setParameter("userId", userId.value())
                .setParameter("inApp", NotificationChannel.IN_APP.name())
                .setParameter("delivered", NotificationStatus.DELIVERED.name())
                .executeUpdate();
    }

    @Override
    public void updateStatus(NotificationId id, NotificationStatus status, String failureReason) {
        NotificationJpaEntity entity = entityManager.find(NotificationJpaEntity.class, id.value());
        if (entity == null) {
            return;
        }
        entity.setStatus(status.name());
        entity.setFailureReason(failureReason);
        if (status == NotificationStatus.DELIVERED && entity.getDeliveredAt() == null) {
            entity.setDeliveredAt(Instant.now());
        }
    }
}
