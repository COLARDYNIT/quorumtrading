package com.quorumtrading.application.usecase.dto;

import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.ResearchExecution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * DTO containing comprehensive details about a market research configuration
 * including its execution history.
 */
public record MarketResearchDetails(
    MarketResearch research,
    List<ResearchExecution> executions,
    Optional<ResearchExecution> latestExecution
) {
    public MarketResearchDetails {
        Objects.requireNonNull(research, "Research cannot be null");
        Objects.requireNonNull(executions, "Executions cannot be null");
        Objects.requireNonNull(latestExecution, "Latest execution cannot be null");
    }

    /**
     * Get the total number of executions.
     *
     * @return total execution count
     */
    public int totalExecutions() {
        return executions.size();
    }

    /**
     * Get the count of successful executions (VIABLE outcome).
     *
     * @return successful execution count
     */
    public long successfulExecutions() {
        return executions.stream()
            .filter(e -> e.getOutcome().isSuccessful())
            .count();
    }
}
