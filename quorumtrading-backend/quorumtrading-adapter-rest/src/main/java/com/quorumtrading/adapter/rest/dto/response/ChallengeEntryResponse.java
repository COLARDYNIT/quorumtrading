package com.quorumtrading.adapter.rest.dto.response;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.math.BigDecimal;

@RegisterForReflection
public record ChallengeEntryResponse(
    int rank,
    String simulationId,
    String simulationName,
    String strategyName,
    String ownerDisplayName,
    BigDecimal currentValue,
    String returnPercent,
    String status,
    String llmCostUsd,
    Long executionTimeSeconds
) {}
