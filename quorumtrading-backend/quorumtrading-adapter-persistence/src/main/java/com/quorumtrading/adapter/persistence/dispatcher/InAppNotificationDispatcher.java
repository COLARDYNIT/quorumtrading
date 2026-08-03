package com.quorumtrading.adapter.persistence.dispatcher;

import com.quorumtrading.application.port.out.NotificationDispatcherPort;
import com.quorumtrading.application.port.out.NotificationRepository;
import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.enums.NotificationChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-app notification "delivery" — persist the notification row with
 * {@code DELIVERED} status. The frontend polls/loads it from there.
 *
 * <p>Lives in the persistence adapter because in-app delivery <em>is</em>
 * a write to the {@link NotificationRepository}; introducing a dedicated
 * adapter module would be pure ceremony.</p>
 *
 * <p>On persistence failure this dispatcher lets the exception propagate;
 * {@code NotificationService.execute(DispatchNotificationCommand)} catches it
 * and records the notification as FAILED in-memory for the caller's audit.</p>
 */
@ApplicationScoped
public class InAppNotificationDispatcher implements NotificationDispatcherPort {

    private static final Logger logger = LoggerFactory.getLogger(InAppNotificationDispatcher.class);

    @Inject
    NotificationRepository notificationRepository;

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    @Transactional
    public Notification dispatch(Notification notification) {
        if (notification.getChannel() != NotificationChannel.IN_APP) {
            throw new IllegalArgumentException(
                    "InAppNotificationDispatcher cannot dispatch channel " + notification.getChannel());
        }
        notification.markDelivered();
        notificationRepository.save(notification);
        logger.debug("Dispatched in-app notification {} (user={}, type={})",
                notification.getId(), notification.getUserId(), notification.getType());
        return notification;
    }
}
