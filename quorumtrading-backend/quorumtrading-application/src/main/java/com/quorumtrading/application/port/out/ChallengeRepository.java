package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.valueobject.ChallengeId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Challenge entities.
 * Implemented by persistence adapters to provide data access.
 */
public interface ChallengeRepository {
    Challenge save(Challenge challenge);
    Optional<Challenge> findById(ChallengeId id);
    Optional<Challenge> findBySlug(String slug);
    List<Challenge> findAll();
    boolean deleteById(ChallengeId id);

    /**
     * Find challenges that are currently active (startDate <= now <= endDate).
     *
     * @return list of active challenges
     */
    List<Challenge> findActive();

    /**
     * Find all challenges ordered by creation date (newest first).
     *
     * @param limit maximum number of challenges to return
     * @return list of challenges
     */
    List<Challenge> findAllSorted(int limit);
}
