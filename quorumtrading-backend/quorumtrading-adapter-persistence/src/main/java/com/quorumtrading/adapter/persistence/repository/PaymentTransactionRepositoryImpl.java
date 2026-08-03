package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.PaymentTransactionJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.PaymentTransactionMapper;
import com.quorumtrading.application.port.out.PaymentTransactionRepository;
import com.quorumtrading.model.entity.PaymentTransaction;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of PaymentTransactionRepository.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class PaymentTransactionRepositoryImpl implements PaymentTransactionRepository {

    private final EntityManager entityManager;
    private final PaymentTransactionMapper mapper;

    @Inject
    public PaymentTransactionRepositoryImpl(EntityManager entityManager, PaymentTransactionMapper mapper) {
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        PaymentTransactionJpaEntity jpa = mapper.toJpa(transaction);

        PaymentTransactionJpaEntity existing = entityManager.find(PaymentTransactionJpaEntity.class, jpa.getId());
        if (existing == null) {
            entityManager.persist(jpa);
        } else {
            jpa = entityManager.merge(jpa);
        }

        entityManager.flush();
        return mapper.toDomain(jpa);
    }

    @Override
    public Optional<PaymentTransaction> findById(UUID id) {
        PaymentTransactionJpaEntity jpa = entityManager.find(PaymentTransactionJpaEntity.class, id);
        return Optional.ofNullable(mapper.toDomain(jpa));
    }

    @Override
    public Optional<PaymentTransaction> findByProviderTransactionId(String providerTransactionId) {
        TypedQuery<PaymentTransactionJpaEntity> query = entityManager.createQuery(
                "SELECT p FROM PaymentTransactionJpaEntity p WHERE p.providerTransactionId = :providerTxId",
                PaymentTransactionJpaEntity.class
        );
        query.setParameter("providerTxId", providerTransactionId);

        return query.getResultStream()
                .findFirst()
                .map(mapper::toDomain);
    }

    @Override
    public List<PaymentTransaction> findByUserId(UserId userId) {
        TypedQuery<PaymentTransactionJpaEntity> query = entityManager.createQuery(
                "SELECT p FROM PaymentTransactionJpaEntity p WHERE p.userId = :userId ORDER BY p.createdAt DESC",
                PaymentTransactionJpaEntity.class
        );
        query.setParameter("userId", userId.value());

        return query.getResultList().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentTransaction> findSuccessfulByUserId(UserId userId) {
        TypedQuery<PaymentTransactionJpaEntity> query = entityManager.createQuery(
                "SELECT p FROM PaymentTransactionJpaEntity p WHERE p.userId = :userId AND p.status = :status ORDER BY p.createdAt DESC",
                PaymentTransactionJpaEntity.class
        );
        query.setParameter("userId", userId.value());
        query.setParameter("status", PaymentTransactionJpaEntity.Status.SUCCEEDED);

        return query.getResultList().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countSuccessful() {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(p) FROM PaymentTransactionJpaEntity p WHERE p.status = :status",
                Long.class
        );
        query.setParameter("status", PaymentTransactionJpaEntity.Status.SUCCEEDED);
        return query.getSingleResult();
    }

    @Override
    public int deleteByUserId(UserId userId) {
        return entityManager.createQuery(
                "DELETE FROM PaymentTransactionJpaEntity p WHERE p.userId = :userId"
        ).setParameter("userId", userId.value())
        .executeUpdate();
    }
}
