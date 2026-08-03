package com.quorumtrading.application.usecase.notification;

import com.quorumtrading.application.usecase.query.ListNotificationsQuery;
import com.quorumtrading.model.entity.Notification;

import java.util.List;

public interface ListNotificationsUseCase {
    List<Notification> execute(ListNotificationsQuery query);
}
