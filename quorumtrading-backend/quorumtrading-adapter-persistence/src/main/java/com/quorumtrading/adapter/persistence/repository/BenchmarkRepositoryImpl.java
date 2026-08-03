package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.BenchmarkJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.BenchmarkMapper;
import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA EntityManager-based implementation of the BenchmarkRepository port.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class BenchmarkRepositoryImpl implements BenchmarkRepository {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRepositoryImpl.class);

    @Inject
    BenchmarkMapper mapper;

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public Benchmark save(Benchmark benchmark) {
        log.info("save benchmark: {}", benchmark);
        BenchmarkJpaEntity entity = mapper.toJpaEntity(benchmark);

        if (benchmark.getBenchmarkId() != null) {
            UUID uuid = benchmark.getBenchmarkId().value();
            BenchmarkJpaEntity existing = entityManager.find(BenchmarkJpaEntity.class, uuid);

            if (existing != null) {
                existing.setName(entity.getName());
                existing.setStartDate(entity.getStartDate());
                existing.setEndDate(entity.getEndDate());
                existing.setCreatedAt(entity.getCreatedAt());
                existing.setOwnerId(entity.getOwnerId());
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
    public Optional<Benchmark> findById(BenchmarkId id) {
        UUID uuid = id.value();

        return Optional.ofNullable(entityManager.find(BenchmarkJpaEntity.class, uuid))
            .map(mapper::toDomain);
    }

    @Override
    public List<Benchmark> findAll() {
        TypedQuery<BenchmarkJpaEntity> query = entityManager.createQuery(
            "SELECT b FROM BenchmarkJpaEntity b ORDER BY b.createdAt DESC",
            BenchmarkJpaEntity.class
        );

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean deleteById(BenchmarkId id) {
        UUID uuid = id.value();

        BenchmarkJpaEntity entity = entityManager.find(BenchmarkJpaEntity.class, uuid);
        if (entity != null) {
            entityManager.remove(entity);
            return true;
        }
        return false;
    }

    @Override
    public List<Benchmark> findAllSorted(int limit) {
        TypedQuery<BenchmarkJpaEntity> query = entityManager.createQuery(
            "SELECT b FROM BenchmarkJpaEntity b ORDER BY b.createdAt DESC",
            BenchmarkJpaEntity.class
        );
        query.setMaxResults(limit);

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Benchmark> findByOwnerId(UserId ownerId, int limit) {
        TypedQuery<BenchmarkJpaEntity> query = entityManager.createQuery(
            "SELECT b FROM BenchmarkJpaEntity b WHERE b.ownerId = :ownerId ORDER BY b.createdAt DESC",
            BenchmarkJpaEntity.class
        );
        query.setParameter("ownerId", ownerId.value());
        query.setMaxResults(limit);

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}
