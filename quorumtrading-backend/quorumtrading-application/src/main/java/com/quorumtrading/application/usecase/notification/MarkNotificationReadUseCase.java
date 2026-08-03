package com.quorumtrading.application.usecase.notification;

import com.quorumtrading.application.usecase.command.MarkNotificationReadCommand;

public interface MarkNotificationReadUseCase {
    void execute(MarkNotificationReadCommand command);
}
