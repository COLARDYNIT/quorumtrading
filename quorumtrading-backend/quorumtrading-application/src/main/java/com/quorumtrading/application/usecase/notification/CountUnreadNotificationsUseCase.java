package com.quorumtrading.application.usecase.notification;

import com.quorumtrading.model.valueobject.UserId;

public interface CountUnreadNotificationsUseCase {
    long countUnread(UserId userId);
}
