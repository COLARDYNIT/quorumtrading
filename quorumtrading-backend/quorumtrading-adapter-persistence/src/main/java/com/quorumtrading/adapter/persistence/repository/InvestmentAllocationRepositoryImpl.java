package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.InvestmentAllocationJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.InvestmentAllocationMapper;
import com.quorumtrading.application.port.out.InvestmentAllocationRepository;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.enums.AllocationStatus;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
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
public class InvestmentAllocationRepositoryImpl implements InvestmentAllocationRepository {

    private static final int HARD_LIMIT = 500;

    /**
     * The statuses that mean money actually moved — mirrors
     * {@code InvestmentAllocation.wasExecuted()}. Stored as strings because the status column is
     * a varchar, not a Postgres enum.
     */
    private static final List<String> FUNDED_STATUSES = List.of(
            AllocationStatus.EXECUTED.name(),
            AllocationStatus.EXECUTED_PARTIAL_DEPLOY.name());

    @Inject EntityManager entityManager;
    @Inject InvestmentAllocationMapper mapper;

    @Override
    public InvestmentAllocation save(InvestmentAllocation allocation) {
        InvestmentAllocationJpaEntity entity = mapper.toJpaEntity(allocation);
        InvestmentAllocationJpaEntity existing =
                entityManager.find(InvestmentAllocationJpaEntity.class, entity.getId());
        if (existing != null) {
            entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
        }
        return allocation;
    }

    @Override
    public Optional<InvestmentAllocation> findById(InvestmentAllocationId id) {
        return Optional.ofNullable(entityManager.find(InvestmentAllocationJpaEntity.class, id.value()))
                .map(mapper::toDomain);
    }

    @Override
    public List<InvestmentAllocation> findByPlanId(InvestmentPlanId planId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, HARD_LIMIT));
        TypedQuery<InvestmentAllocationJpaEntity> query = entityManager.createQuery(
                "SELECT a FROM InvestmentAllocationJpaEntity a " +
                        "WHERE a.planId = :planId ORDER BY a.executionDate DESC",
                InvestmentAllocationJpaEntity.class);
        query.setParameter("planId", planId.value());
        query.setMaxResults(safeLimit);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<InvestmentAllocation> findFundedOnDay(InvestmentPlanId planId,
                                                         Instant fromInclusive,
                                                         Instant toExclusive) {
        TypedQuery<InvestmentAllocationJpaEntity> query = entityManager.createQuery(
                "SELECT a FROM InvestmentAllocationJpaEntity a " +
                        "WHERE a.planId = :planId " +
                        "AND a.status IN :fundedStatuses " +
                        "AND a.executionDate >= :fromInclusive " +
                        "AND a.executionDate < :toExclusive " +
                        "ORDER BY a.executionDate ASC",
                InvestmentAllocationJpaEntity.class);
        query.setParameter("planId", planId.value());
        query.setParameter("fundedStatuses", FUNDED_STATUSES);
        query.setParameter("fromInclusive", fromInclusive);
        query.setParameter("toExclusive", toExclusive);
        query.setMaxResults(1);
        return query.getResultList().stream().findFirst().map(mapper::toDomain);
    }

    @Override
    public List<InvestmentAllocation> findByUserId(UserId userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, HARD_LIMIT));
        TypedQuery<InvestmentAllocationJpaEntity> query = entityManager.createQuery(
                "SELECT a FROM InvestmentAllocationJpaEntity a " +
                        "WHERE a.userId = :userId ORDER BY a.executionDate DESC",
                InvestmentAllocationJpaEntity.class);
        query.setParameter("userId", userId.value());
        query.setMaxResults(safeLimit);
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }
}
