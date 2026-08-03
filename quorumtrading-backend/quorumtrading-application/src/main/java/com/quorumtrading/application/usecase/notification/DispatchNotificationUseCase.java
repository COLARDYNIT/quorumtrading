package com.quorumtrading.application.usecase.notification;

import com.quorumtrading.application.usecase.command.DispatchNotificationCommand;
import com.quorumtrading.model.entity.Notification;

import java.util.List;

/**
 * Internal use case: fans out a notification across every channel the user has
 * enabled for the given type. Called by feature-specific services (e.g. the
 * recurring-investment executor) — not exposed through REST or MCP.
 */
public interface DispatchNotificationUseCase {
    /**
     * @return the per-channel notifications that were produced, in their terminal state
     *         (DELIVERED or FAILED). Empty if the user opted out of this type.
     */
    List<Notification> execute(DispatchNotificationCommand command);
}
