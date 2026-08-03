package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.SimulationContributionJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.SimulationContributionMapper;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.SimulationId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class SimulationContributionRepositoryImpl implements SimulationContributionRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    SimulationContributionMapper mapper;

    @Override
    public Contribution save(Contribution contribution) {
        SimulationContributionJpaEntity entity = mapper.toJpaEntity(contribution);
        SimulationContributionJpaEntity existing =
                entityManager.find(SimulationContributionJpaEntity.class, entity.getId());
        if (existing != null) {
            entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
        }
        return contribution;
    }

    @Override
    public Optional<Contribution> findById(ContributionId id) {
        return Optional.ofNullable(
                entityManager.find(SimulationContributionJpaEntity.class, id.value()))
                .map(mapper::toDomain);
    }

    @Override
    public List<Contribution> findBySimulationId(SimulationId simulationId) {
        TypedQuery<SimulationContributionJpaEntity> query = entityManager.createQuery(
                "SELECT c FROM SimulationContributionJpaEntity c " +
                        "WHERE c.simulationId = :simulationId " +
                        "ORDER BY c.contributionDate ASC",
                SimulationContributionJpaEntity.class);
        query.setParameter("simulationId", simulationId.value());
        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Map<SimulationId, List<Contribution>> findBySimulationIds(Collection<SimulationId> simulationIds) {
        if (simulationIds == null || simulationIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = simulationIds.stream().map(SimulationId::value).distinct().toList();
        TypedQuery<SimulationContributionJpaEntity> query = entityManager.createQuery(
                "SELECT c FROM SimulationContributionJpaEntity c " +
                        "WHERE c.simulationId IN :ids " +
                        "ORDER BY c.contributionDate ASC",
                SimulationContributionJpaEntity.class);
        query.setParameter("ids", ids);
        return query.getResultList().stream()
                .map(mapper::toDomain)
                .collect(Collectors.groupingBy(Contribution::simulationId));
    }

    @Override
    public Optional<Contribution> findByPlanAndDate(UUID sourcePlanId, Instant contributionDate) {
        if (sourcePlanId == null) {
            return Optional.empty();
        }
        TypedQuery<SimulationContributionJpaEntity> query = entityManager.createQuery(
                "SELECT c FROM SimulationContributionJpaEntity c " +
                        "WHERE c.sourcePlanId = :planId AND c.contributionDate = :date",
                SimulationContributionJpaEntity.class);
        query.setParameter("planId", sourcePlanId);
        query.setParameter("date", contributionDate);
        return query.getResultStream().findFirst().map(mapper::toDomain);
    }
}
