package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The set of delivery channels enabled for one user × {@link NotificationType}.
 *
 * <p><b>Aggregate Root</b>, identified by the composite {@code (userId, type)}.
 * Treated as one row per pair — there is no separate ID, because each pair
 * has at most one preference and the natural key is sufficient.</p>
 *
 * <p>An empty channel set means the user has opted out of this notification
 * type entirely.</p>
 *
 * <p><b>Defaults</b> are not stored — when a row is missing, the
 * dispatcher applies a hard-coded default per type (see
 * {@code NotificationService}). Storing only explicit user choices keeps the
 * preferences table sparse and lets defaults evolve without per-user backfills.</p>
 */
public class NotificationPreference implements AggregateRoot {

    private final UserId userId;
    private final NotificationType type;
    private Set<NotificationChannel> channels;

    private NotificationPreference(UserId userId, NotificationType type,
                                   Set<NotificationChannel> channels) {
        this.userId = userId;
        this.type = type;
        this.channels = channels;
    }

    public static NotificationPreference create(UserId userId, NotificationType type,
                                                Set<NotificationChannel> channels) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(type, "type");
        Set<NotificationChannel> safe = channels == null || channels.isEmpty()
                ? EnumSet.noneOf(NotificationChannel.class)
                : EnumSet.copyOf(channels);
        return new NotificationPreference(userId, type, safe);
    }

    public static NotificationPreference reconstitute(UserId userId, NotificationType type,
                                                      Set<NotificationChannel> channels) {
        return create(userId, type, channels);
    }

    /**
     * Replace the channel set wholesale. Passing an empty set opts out of this type.
     */
    public void setChannels(Set<NotificationChannel> channels) {
        this.channels = channels == null || channels.isEmpty()
                ? EnumSet.noneOf(NotificationChannel.class)
                : EnumSet.copyOf(channels);
    }

    public boolean isEnabled(NotificationChannel channel) {
        return channels.contains(channel);
    }

    public UserId getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public Set<NotificationChannel> getChannels() {
        return channels.isEmpty() ? Set.of() : EnumSet.copyOf(channels);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationPreference that)) return false;
        return userId.equals(that.userId) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, type);
    }
}
