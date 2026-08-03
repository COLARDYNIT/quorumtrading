package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Set;

public record UpdateNotificationPreferenceCommand(
        UserId userId,
        NotificationType type,
        Set<NotificationChannel> channels) {
    public UpdateNotificationPreferenceCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        channels = channels == null ? Set.of() : Set.copyOf(channels);
    }
}
