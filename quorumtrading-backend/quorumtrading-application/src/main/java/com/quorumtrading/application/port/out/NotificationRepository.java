package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.Notification;
import com.quorumtrading.model.enums.NotificationStatus;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Output port for {@link Notification} persistence.
 *
 * <p>Reads are user-scoped — implementations must NOT return notifications
 * belonging to other users when queried by id; pair {@link #findById} calls
 * with an ownership check at the use-case layer.</p>
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(NotificationId id);

    /**
     * List notifications for a user, newest first.
     *
     * @param userId    owner
     * @param onlyUnread if true, restrict to in-app DELIVERED rows
     * @param limit     maximum number of rows to return (clamped to a sane upper bound by the impl)
     */
    List<Notification> findByUser(UserId userId, boolean onlyUnread, int limit);

    /**
     * Count unread in-app notifications for a user. Used by the bell badge.
     */
    long countUnread(UserId userId);

    /**
     * Bulk-transition every unread in-app notification for the user to READ.
     *
     * @return number of rows updated
     */
    int markAllRead(UserId userId);

    /**
     * Update the status of a single notification, asserting ownership.
     * Used for retries and dispatcher feedback.
     */
    void updateStatus(NotificationId id, NotificationStatus status, String failureReason);
}
