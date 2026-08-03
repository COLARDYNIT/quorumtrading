package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.BenchmarkJpaEntity;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapper between Benchmark domain entity and BenchmarkJpaEntity.
 */
@ApplicationScoped
public class BenchmarkMapper {

    public BenchmarkJpaEntity toJpaEntity(Benchmark benchmark) {
        BenchmarkJpaEntity entity = new BenchmarkJpaEntity();

        if (benchmark.getBenchmarkId() != null) {
            entity.setId(benchmark.getBenchmarkId().value());
        }

        entity.setName(benchmark.getName());
        entity.setStartDate(benchmark.getStartDate());
        entity.setEndDate(benchmark.getEndDate());
        entity.setCreatedAt(benchmark.getCreatedAt());
        if (benchmark.getOwnerId() != null) {
            entity.setOwnerId(benchmark.getOwnerId().value());
        }

        return entity;
    }

    public Benchmark toDomain(BenchmarkJpaEntity entity) {
        BenchmarkId id = new BenchmarkId(entity.getId());
        UserId ownerId = entity.getOwnerId() != null ? new UserId(entity.getOwnerId()) : null;

        return Benchmark.reconstitute(
            id,
            entity.getName(),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.getCreatedAt(),
            ownerId
        );
    }
}
