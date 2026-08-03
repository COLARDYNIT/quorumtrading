package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record McpInvestmentAllocationDto(
        String id,
        String planId,
        String executionDate,
        String status,
        String intendedAmount,
        String actualAmount,
        String currency,
        String chosenStrategyId,
        String pickerReasoning,
        String pickerLlmCostUsd,
        String action,
        String targetSimulationId,
        String contributionId,
        List<String> brokerOrderRefs,
        String failureReason,
        String createdAt
) {}
