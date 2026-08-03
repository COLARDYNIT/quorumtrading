package com.quorumtrading.adapter.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.persistence.entity.NotificationJpaEntity;
import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationStatus;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class NotificationMapper {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE =
            new TypeReference<>() {};

    @Inject
    ObjectMapper objectMapper;

    public NotificationJpaEntity toJpaEntity(Notification n) {
        NotificationJpaEntity e = new NotificationJpaEntity();
        e.setId(n.getId().value());
        e.setUserId(n.getUserId().value());
        e.setType(n.getType().name());
        e.setChannel(n.getChannel().name());
        e.setTitle(n.getTitle());
        e.setBody(n.getBody());
        e.setPayload(serialize(n.getPayload()));
        e.setRelatedEntityId(n.getRelatedEntityId());
        e.setStatus(n.getStatus().name());
        e.setFailureReason(n.getFailureReason());
        e.setCreatedAt(n.getCreatedAt());
        e.setDeliveredAt(n.getDeliveredAt());
        e.setReadAt(n.getReadAt());
        return e;
    }

    public Notification toDomain(NotificationJpaEntity e) {
        return Notification.reconstitute(
                new NotificationId(e.getId()),
                new UserId(e.getUserId()),
                NotificationType.valueOf(e.getType()),
                NotificationChannel.valueOf(e.getChannel()),
                e.getTitle(),
                e.getBody(),
                deserialize(e.getPayload()),
                e.getRelatedEntityId(),
                NotificationStatus.valueOf(e.getStatus()),
                e.getCreatedAt(),
                e.getDeliveredAt(),
                e.getReadAt(),
                e.getFailureReason()
        );
    }

    private String serialize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize notification payload", ex);
        }
    }

    private Map<String, Object> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize notification payload", ex);
        }
    }
}
