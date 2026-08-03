package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record McpMarketEventDto(
    Long id,
    String timestamp,
    String eventType,
    String source,
    String symbol,
    String data,
    String closePrice,
    String headline,
    String ingestedAt
) {}
