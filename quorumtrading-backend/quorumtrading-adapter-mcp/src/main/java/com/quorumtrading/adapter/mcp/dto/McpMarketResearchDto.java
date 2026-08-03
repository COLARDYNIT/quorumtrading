package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record McpMarketResearchDto(
    String id,
    String name,
    String status,
    List<String> targetAssets,
    int intervalAmount,
    String intervalUnit,
    boolean enableAutoBacktest,
    Integer backtestLengthDays,
    String backtestPeriodStart,
    String backtestPeriodEnd,
    String nextScheduledRun,
    String lastExecutedAt,
    String createdAt,
    int strategyCount,
    String instructions,
    Integer totalExecutions,
    String lastOutcome,
    /** Web-app detail page for this research. Omitted on headless instances (frontend disabled). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String uiUrl
) {}
