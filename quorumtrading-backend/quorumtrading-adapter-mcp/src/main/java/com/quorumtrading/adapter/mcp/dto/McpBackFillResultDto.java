package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record McpBackFillResultDto(
    String ticker,
    String assetType,
    String startDate,
    String endDate,
    String granularity,
    int recordsInserted,
    int recordsSkipped,
    int recordsFailed,
    int newsRecordsInserted,
    int newsRecordsSkipped,
    boolean success,
    String errorMessage
) {}
