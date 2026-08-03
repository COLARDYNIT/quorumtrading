package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Output port for {@link NotificationPreference} persistence.
 *
 * <p>Composite key is {@code (userId, type)} — there is no surrogate id.</p>
 */
public interface NotificationPreferenceRepository {

    NotificationPreference save(NotificationPreference preference);

    Optional<NotificationPreference> find(UserId userId, NotificationType type);

    List<NotificationPreference> findByUser(UserId userId);
}
