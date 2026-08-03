package com.quorumtrading.application.usecase.notification;

import com.quorumtrading.model.valueobject.UserId;

public interface MarkAllNotificationsReadUseCase {
    /**
     * @return number of notifications marked read
     */
    int execute(UserId userId);
}
