package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record McpBrokerAccountDto(
    String id,
    String name,
    String brokerType,
    String status,
    String totalBalance,
    String equity,
    String reservedBalance,
    String availableBalance,
    String currency,
    String createdAt,
    String updatedAt
) {}
