package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Benchmark entities.
 * Implemented by persistence adapters to provide data access.
 */
public interface BenchmarkRepository {
    Benchmark save(Benchmark benchmark);
    Optional<Benchmark> findById(BenchmarkId id);
    List<Benchmark> findAll();
    boolean deleteById(BenchmarkId id);

    /**
     * Find all benchmarks ordered by creation date (newest first).
     * Limits result to a maximum number of benchmarks.
     *
     * @param limit Maximum number of benchmarks to return
     * @return List of benchmarks
     */
    List<Benchmark> findAllSorted(int limit);

    /**
     * Find benchmarks owned by a specific user, ordered by creation date (newest first).
     *
     * @param ownerId The owner's user ID
     * @param limit Maximum number of benchmarks to return
     * @return List of benchmarks owned by the user
     */
    List<Benchmark> findByOwnerId(UserId ownerId, int limit);
}
