package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.enums.NotificationChannel;

/**
 * Output port for delivering a {@link Notification} through a specific channel.
 *
 * <p>One implementation per {@link NotificationChannel}; the application
 * service routes by {@link #supportedChannel()}.</p>
 *
 * <p>Implementations are responsible for persisting their channel-specific
 * delivery state (e.g. the in-app dispatcher writes the notification row;
 * the email dispatcher updates {@code status} after the send attempt).</p>
 */
public interface NotificationDispatcherPort {

    /**
     * The channel this dispatcher handles. Multiple dispatchers may not share a channel.
     */
    NotificationChannel supportedChannel();

    /**
     * Deliver the notification synchronously. On success, the notification's
     * status MUST be {@code DELIVERED}; on failure, {@code FAILED} with a
     * non-null {@code failureReason}. Implementations should NOT throw — callers
     * inspect the returned notification's state.
     *
     * @param notification a freshly-created {@code Notification} in {@code PENDING} state
     * @return the same notification, mutated and persisted, in a terminal state
     */
    Notification dispatch(Notification notification);
}
