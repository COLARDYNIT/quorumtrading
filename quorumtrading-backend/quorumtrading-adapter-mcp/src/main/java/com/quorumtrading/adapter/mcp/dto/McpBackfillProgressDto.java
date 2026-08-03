package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record McpBackfillProgressDto(
    String jobId,
    String ticker,
    String assetType,
    String status,
    Integer progressPercentage,
    String startedAt,
    String completedAt,
    String errorMessage,
    Integer totalRecordsExpected,
    Integer recordsProcessed
) {}
