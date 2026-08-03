package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * MCP DTO for strategy summary. Includes full markdown content when fetched individually.
 */
@RegisterForReflection
public record McpStrategyDto(
    String id,
    String name,
    String description,
    String status,
    boolean enabled,
    List<String> stockTickers,
    List<String> cryptoTickers,
    String consensusMethod,
    /**
     * The agents that will actually vote, after the frontmatter is interpreted. Omitting the
     * {@code agents} key means all three, which is not obvious from the raw markdown — and a
     * three-agent list is not a promise of three votes: FUNDAMENTAL and SENTIMENT abstain when too
     * few news events are stored for the period, which is common on crypto backtests. See
     * agentAvailabilityNote.
     */
    List<String> effectiveAgents,
    /** Plain-language caveat about which of the effective agents may abstain, or null. */
    String agentAvailabilityNote,
    String tradingMode,
    Integer simulationCount,
    String content,
    String createdAt,
    String updatedAt,
    /** Web-app detail page for this strategy. Omitted on headless instances (frontend disabled). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String uiUrl
) {}
