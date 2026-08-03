package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record McpContributionDto(
        String id,
        String amount,
        String currency,
        String contributionDate,
        String sourcePlanId,
        String sourceAllocationId,
        String createdAt
) {}
