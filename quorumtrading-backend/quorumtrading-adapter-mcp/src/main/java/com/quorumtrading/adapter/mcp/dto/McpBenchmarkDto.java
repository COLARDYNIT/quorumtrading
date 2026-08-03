package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * MCP DTO for benchmark details.
 * Flattened representation of a Benchmark entity with associated simulation IDs.
 */
@RegisterForReflection
public record McpBenchmarkDto(
    String id,
    String name,
    String startDate,
    String endDate,
    boolean isLive,
    List<String> simulationIds,
    List<String> strategyNames,
    int totalSimulations,
    int completedSimulations,
    String createdAt,
    /** Web-app detail page for this benchmark. Omitted on headless instances (frontend disabled). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String uiUrl
) {
}
