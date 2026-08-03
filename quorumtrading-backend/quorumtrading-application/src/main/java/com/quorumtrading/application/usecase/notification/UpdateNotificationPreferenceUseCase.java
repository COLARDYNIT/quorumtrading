package com.quorumtrading.application.usecase.notification;

import com.quorumtrading.application.usecase.command.UpdateNotificationPreferenceCommand;
import com.quorumtrading.model.entity.NotificationPreference;

public interface UpdateNotificationPreferenceUseCase {
    NotificationPreference execute(UpdateNotificationPreferenceCommand command);
}
