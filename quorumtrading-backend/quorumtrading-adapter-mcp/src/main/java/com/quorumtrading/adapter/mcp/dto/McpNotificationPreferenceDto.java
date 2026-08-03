package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record McpNotificationPreferenceDto(
        String type,
        List<String> channels
) {}
