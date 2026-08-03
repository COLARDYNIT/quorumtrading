package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.valueobject.ResearchOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ResearchExecution entities.
 * Implemented by persistence adapters to provide data access.
 *
 * Follows hexagonal architecture: This is the PORT in quorumtrading-application module.
 * Implementations (ADAPTERS) live in quorumtrading-adapter-persistence module.
 */
public interface ResearchExecutionRepository {

    /**
     * Save or update a research execution.
     *
     * @param execution the research execution to save
     * @return the saved research execution
     */
    ResearchExecution save(ResearchExecution execution);

    /**
     * Find a research execution by its ID.
     *
     * @param id the unique identifier
     * @return Optional containing the research execution if found, empty otherwise
     */
    Optional<ResearchExecution> findById(UUID id);

    /**
     * Find all research executions for a specific market research.
     *
     * @param marketResearchId the market research identifier
     * @return list of research executions belonging to the market research
     */
    List<ResearchExecution> findByMarketResearchId(UUID marketResearchId);

    /**
     * Find all research executions by outcome.
     *
     * @param outcome the research outcome to filter by
     * @return list of research executions matching the outcome
     */
    List<ResearchExecution> findByOutcome(ResearchOutcome outcome);

    /**
     * Find the most recent research execution for a specific market research.
     *
     * @param marketResearchId the market research identifier
     * @return Optional containing the latest research execution if found, empty otherwise
     */
    Optional<ResearchExecution> findLatestByMarketResearchId(UUID marketResearchId);

    /**
     * Delete a research execution by its ID.
     *
     * @param id the unique identifier
     */
    void delete(UUID id);

    /**
     * Count how many strategies were created by a specific market research.
     * This counts executions where strategyId is not null.
     *
     * @param marketResearchId the market research identifier
     * @return the count of strategies created
     */
    int countStrategiesCreatedByResearch(UUID marketResearchId);
}
