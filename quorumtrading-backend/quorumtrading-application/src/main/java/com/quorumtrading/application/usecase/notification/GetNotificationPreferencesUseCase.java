package com.quorumtrading.application.usecase.notification;

import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

public interface GetNotificationPreferencesUseCase {
    /**
     * Returns the effective preferences for every {@code NotificationType}, filling in
     * the system default when no row exists for the user.
     */
    List<NotificationPreference> listForUser(UserId userId);
}
