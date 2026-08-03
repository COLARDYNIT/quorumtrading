package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Map;
import java.util.UUID;

/**
 * Request to fan out a single user-facing notification across every channel
 * the user has enabled for this {@link NotificationType}.
 *
 * <p>If the user has no preference row for this type, the dispatcher applies
 * the hard-coded default channel set. If the user has explicitly opted out
 * (empty channel set), no notifications are produced.</p>
 *
 * @param userId          recipient
 * @param type            classification, drives preference lookup
 * @param title           short headline used by both in-app and email
 * @param body            longer text; channels render at their discretion
 * @param payload         channel-agnostic structured context (planId, allocationId, etc.)
 * @param relatedEntityId optional foreign-key-ish pointer (e.g. allocationId) for deep-linking
 */
public record DispatchNotificationCommand(
        UserId userId,
        NotificationType type,
        String title,
        String body,
        Map<String, Object> payload,
        UUID relatedEntityId) {

    public DispatchNotificationCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
