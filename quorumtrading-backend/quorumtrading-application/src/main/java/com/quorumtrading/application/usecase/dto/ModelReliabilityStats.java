package com.quorumtrading.application.usecase.dto;

import java.util.List;

public record ModelReliabilityStats(
    List<ModelReliabilityEntry> entries
) {
    public record ModelReliabilityEntry(
        String modelId,
        long successCount,
        long failureCount,
        double failureRate
    ) {}
}
