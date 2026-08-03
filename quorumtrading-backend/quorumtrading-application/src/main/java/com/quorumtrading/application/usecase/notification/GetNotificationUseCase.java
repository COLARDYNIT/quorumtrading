package com.quorumtrading.application.usecase.notification;

import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;

public interface GetNotificationUseCase {
    /**
     * @throws java.util.NoSuchElementException if not found
     * @throws SecurityException if found but owned by another user
     */
    Notification execute(NotificationId id, UserId userId);
}
