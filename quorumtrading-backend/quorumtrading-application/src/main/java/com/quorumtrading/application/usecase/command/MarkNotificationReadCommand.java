package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;

public record MarkNotificationReadCommand(NotificationId notificationId, UserId userId) {
    public MarkNotificationReadCommand {
        if (notificationId == null) {
            throw new IllegalArgumentException("notificationId is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
    }
}
