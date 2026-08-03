package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.application.usecase.dto.MarketResearchDetails;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for comprehensive market research details.
 * Includes research configuration, execution history, and statistics.
 */
@Schema(description = "Comprehensive market research details with execution history")
public record MarketResearchDetailsResponse(
    @Schema(description = "Market research configuration")
    MarketResearchResponse research,

    @Schema(description = "All execution history (newest first)")
    List<ResearchExecutionResponse> executions,

    @Schema(description = "Latest execution (null if never executed)")
    ResearchExecutionResponse latestExecution,

    @Schema(description = "Total number of executions", example = "42")
    int totalExecutions,

    @Schema(description = "Number of successful executions (STRATEGY_CREATED or NO_OPPORTUNITY)", example = "38")
    long successfulExecutions
) {
    /**
     * Convert application layer MarketResearchDetails DTO to REST DTO.
     *
     * @param details the application DTO
     * @return the response DTO
     */
    public static MarketResearchDetailsResponse fromDomain(MarketResearchDetails details) {
        MarketResearchResponse researchResponse = MarketResearchResponse.fromDomain(details.research());

        List<ResearchExecutionResponse> executionResponses = details.executions().stream()
            .map(ResearchExecutionResponse::fromDomain)
            .toList();

        ResearchExecutionResponse latestExecutionResponse = details.latestExecution()
            .map(ResearchExecutionResponse::fromDomain)
            .orElse(null);

        return new MarketResearchDetailsResponse(
            researchResponse,
            executionResponses,
            latestExecutionResponse,
            details.totalExecutions(),
            details.successfulExecutions()
        );
    }
}
