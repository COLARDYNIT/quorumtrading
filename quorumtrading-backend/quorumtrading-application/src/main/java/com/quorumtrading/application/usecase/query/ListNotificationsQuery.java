package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.UserId;

public record ListNotificationsQuery(UserId userId, boolean onlyUnread, int limit) {
    public ListNotificationsQuery {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (limit <= 0) {
            limit = 50;
        }
        if (limit > 200) {
            limit = 200;
        }
    }
}
