package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.ChallengeJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.ChallengeMapper;
import com.quorumtrading.application.port.out.ChallengeRepository;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.valueobject.ChallengeId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of the ChallengeRepository port.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class ChallengeRepositoryImpl implements ChallengeRepository {

    private static final Logger log = LoggerFactory.getLogger(ChallengeRepositoryImpl.class);

    @Inject
    ChallengeMapper mapper;

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public Challenge save(Challenge challenge) {
        log.info("save challenge: {}", challenge.getChallengeId());
        ChallengeJpaEntity entity = mapper.toJpaEntity(challenge);

        if (challenge.getChallengeId() != null) {
            UUID uuid = challenge.getChallengeId().value();
            ChallengeJpaEntity existing = entityManager.find(ChallengeJpaEntity.class, uuid);

            if (existing != null) {
                existing.setSlug(entity.getSlug());
                existing.setName(entity.getName());
                existing.setDescription(entity.getDescription());
                existing.setTicker(entity.getTicker());
                existing.setInitialCash(entity.getInitialCash());
                existing.setSimulationStartDate(entity.getSimulationStartDate());
                existing.setSimulationEndDate(entity.getSimulationEndDate());
                existing.setParticipationOpen(entity.getParticipationOpen());
                existing.setParticipationClose(entity.getParticipationClose());
                existing.setCreatedBy(entity.getCreatedBy());
                existing.setStrategiesPublic(entity.isStrategiesPublic());
                entity = entityManager.merge(existing);
            } else {
                entityManager.persist(entity);
            }
        } else {
            entityManager.persist(entity);
        }

        return mapper.toDomain(entity);
    }

    @Override
    public Optional<Challenge> findById(ChallengeId id) {
        return Optional.ofNullable(entityManager.find(ChallengeJpaEntity.class, id.value()))
            .map(mapper::toDomain);
    }

    @Override
    public List<Challenge> findAll() {
        TypedQuery<ChallengeJpaEntity> query = entityManager.createQuery(
            "SELECT c FROM ChallengeJpaEntity c ORDER BY c.createdAt DESC",
            ChallengeJpaEntity.class
        );
        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean deleteById(ChallengeId id) {
        ChallengeJpaEntity entity = entityManager.find(ChallengeJpaEntity.class, id.value());
        if (entity != null) {
            entityManager.remove(entity);
            return true;
        }
        return false;
    }

    @Override
    public List<Challenge> findActive() {
        Instant now = Instant.now();
        TypedQuery<ChallengeJpaEntity> query = entityManager.createQuery(
            "SELECT c FROM ChallengeJpaEntity c WHERE c.startDate <= :now AND c.endDate >= :now ORDER BY c.endDate ASC",
            ChallengeJpaEntity.class
        );
        query.setParameter("now", now);
        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Challenge> findBySlug(String slug) {
        TypedQuery<ChallengeJpaEntity> query = entityManager.createQuery(
            "SELECT c FROM ChallengeJpaEntity c WHERE c.slug = :slug",
            ChallengeJpaEntity.class
        );
        query.setParameter("slug", slug);
        return query.getResultList().stream().findFirst().map(mapper::toDomain);
    }

    @Override
    public List<Challenge> findAllSorted(int limit) {
        TypedQuery<ChallengeJpaEntity> query = entityManager.createQuery(
            "SELECT c FROM ChallengeJpaEntity c ORDER BY c.createdAt DESC",
            ChallengeJpaEntity.class
        );
        query.setMaxResults(limit);
        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}
