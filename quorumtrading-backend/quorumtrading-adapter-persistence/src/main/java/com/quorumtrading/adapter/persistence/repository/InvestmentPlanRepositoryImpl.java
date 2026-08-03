package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.InvestmentPlanJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.InvestmentPlanMapper;
import com.quorumtrading.application.port.out.InvestmentPlanRepository;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
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
public class InvestmentPlanRepositoryImpl implements InvestmentPlanRepository {

    @Inject EntityManager entityManager;
    @Inject InvestmentPlanMapper mapper;

    @Override
    public InvestmentPlan save(InvestmentPlan plan) {
        InvestmentPlanJpaEntity entity = mapper.toJpaEntity(plan);
        InvestmentPlanJpaEntity existing = entityManager.find(InvestmentPlanJpaEntity.class, entity.getId());
        if (existing != null) {
            entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
        }
        return plan;
    }

    @Override
    public Optional<InvestmentPlan> findById(InvestmentPlanId id) {
        return Optional.ofNullable(entityManager.find(InvestmentPlanJpaEntity.class, id.value()))
                .map(mapper::toDomain);
    }

    @Override
    public List<InvestmentPlan> findByUserId(UserId userId, Optional<InvestmentPlanStatus> statusFilter) {
        String jpql = "SELECT p FROM InvestmentPlanJpaEntity p WHERE p.userId = :userId";
        if (statusFilter.isPresent()) {
            jpql += " AND p.status = :status";
        }
        jpql += " ORDER BY p.createdAt DESC";

        TypedQuery<InvestmentPlanJpaEntity> query = entityManager.createQuery(jpql, InvestmentPlanJpaEntity.class);
        query.setParameter("userId", userId.value());
        statusFilter.ifPresent(s -> query.setParameter("status", s.name()));
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<InvestmentPlan> findDueForExecution(Instant asOf, int limit) {
        TypedQuery<InvestmentPlanJpaEntity> query = entityManager.createQuery(
                "SELECT p FROM InvestmentPlanJpaEntity p " +
                        "WHERE p.status = 'ACTIVE' AND p.nextExecutionAt <= :asOf " +
                        "ORDER BY p.nextExecutionAt ASC",
                InvestmentPlanJpaEntity.class);
        query.setParameter("asOf", asOf);
        query.setMaxResults(Math.max(1, Math.min(limit, 500)));
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean deleteById(InvestmentPlanId id) {
        InvestmentPlanJpaEntity entity = entityManager.find(InvestmentPlanJpaEntity.class, id.value());
        if (entity == null) return false;
        entityManager.remove(entity);
        return true;
    }
}
