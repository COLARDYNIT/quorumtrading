package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.ResearchStatus;
import com.quorumtrading.model.valueobject.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for MarketResearch aggregate roots.
 * Implemented by persistence adapters to provide data access.
 *
 * Follows hexagonal architecture: This is the PORT in quorumtrading-application module.
 * Implementations (ADAPTERS) live in quorumtrading-adapter-persistence module.
 */
public interface MarketResearchRepository {

    /**
     * Save or update a market research configuration.
     *
     * @param research the market research to save
     * @return the saved market research
     */
    MarketResearch save(MarketResearch research);

    /**
     * Find a market research by its ID.
     *
     * @param id the unique identifier
     * @return Optional containing the market research if found, empty otherwise
     */
    Optional<MarketResearch> findById(UUID id);

    /**
     * Find all market research configurations.
     *
     * @return list of all market research configurations
     */
    List<MarketResearch> findAll();

    /**
     * Find all market research configurations by status.
     *
     * @param status the research status to filter by
     * @return list of market research configurations matching the status
     */
    List<MarketResearch> findByStatus(ResearchStatus status);

    /**
     * Find all active market research configurations that are due for execution.
     *
     * @param now the current timestamp
     * @return list of market research configurations where status = ACTIVE and nextScheduledRun <= now
     */
    List<MarketResearch> findDueForExecution(Instant now);

    /**
     * Delete a market research by its ID.
     *
     * @param id the unique identifier
     */
    void delete(UUID id);

    /**
     * Check if a market research exists by its ID.
     *
     * @param id the unique identifier
     * @return true if the market research exists, false otherwise
     */
    boolean existsById(UUID id);

    /**
     * Find all active market research configurations owned by a user.
     *
     * @param ownerId the user's ID
     * @return list of active market research configurations owned by the user
     */
    List<MarketResearch> findActiveByOwnerId(UserId ownerId);

    /**
     * Find all market research configurations owned by a user that are paused for external reasons.
     *
     * @param ownerId the user's ID
     * @return list of market research configurations paused for external reasons
     */
    List<MarketResearch> findPausedForExternalReasonByOwnerId(UserId ownerId);

    /**
     * Find all market research configurations owned by a user.
     *
     * @param ownerId the user's ID
     * @return list of all market research configurations owned by the user
     */
    List<MarketResearch> findByOwnerId(UserId ownerId);

    /**
     * Count the total number of market research configurations.
     *
     * @return total number of market research configurations
     */
    long count();
}
