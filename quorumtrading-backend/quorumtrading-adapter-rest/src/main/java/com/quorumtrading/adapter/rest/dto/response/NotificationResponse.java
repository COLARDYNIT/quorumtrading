package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.entity.Notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String channel,
        String title,
        String body,
        Map<String, Object> payload,
        UUID relatedEntityId,
        String status,
        Instant createdAt,
        Instant deliveredAt,
        Instant readAt,
        String failureReason) {

    public static NotificationResponse fromDomain(Notification n) {
        return new NotificationResponse(
                n.getId().value(),
                n.getType().name(),
                n.getChannel().name(),
                n.getTitle(),
                n.getBody(),
                n.getPayload(),
                n.getRelatedEntityId(),
                n.getStatus().name(),
                n.getCreatedAt(),
                n.getDeliveredAt(),
                n.getReadAt(),
                n.getFailureReason());
    }
}
