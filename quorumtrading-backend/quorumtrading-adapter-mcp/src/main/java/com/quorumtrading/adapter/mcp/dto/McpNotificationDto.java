package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Map;

@RegisterForReflection
public record McpNotificationDto(
        String id,
        String type,
        String channel,
        String title,
        String body,
        Map<String, Object> payload,
        String relatedEntityId,
        String status,
        String createdAt,
        String deliveredAt,
        String readAt,
        String failureReason
) {}
