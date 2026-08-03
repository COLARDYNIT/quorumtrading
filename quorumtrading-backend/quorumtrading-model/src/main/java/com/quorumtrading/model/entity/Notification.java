package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationStatus;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.NotificationId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A single user-facing notification on a single delivery channel.
 *
 * <p><b>Aggregate Root.</b> One {@code Notification} row exists per
 * (user, type, channel) fan-out, so delivery and read state are tracked
 * independently per channel — an unread in-app notification doesn't get
 * dragged to read by an email being delivered, and a failed email doesn't
 * invalidate the in-app copy.</p>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>{@code title} is non-blank.</li>
 *   <li>{@code payload} is a defensive immutable copy.</li>
 *   <li>{@code READ} is only valid for {@link NotificationChannel#IN_APP}.</li>
 *   <li>State transitions are forward-only: {@code PENDING} → {@code DELIVERED}/{@code FAILED};
 *       {@code DELIVERED} → {@code READ} (in-app only).</li>
 * </ul>
 *
 * <p>The {@code payload} map carries channel-agnostic, type-specific context
 * (e.g. for {@code INVESTMENT_EXECUTED}: {@code planId}, {@code allocationId},
 * {@code amount}, {@code strategyId}). Frontend and email renderer use it to
 * build human-readable copy and deep-links.</p>
 */
public class Notification implements AggregateRoot {

    private final NotificationId id;
    private final UserId userId;
    private final NotificationType type;
    private final NotificationChannel channel;
    private final String title;
    private final String body;
    private final Map<String, Object> payload;
    private final UUID relatedEntityId;
    private NotificationStatus status;
    private final Instant createdAt;
    private Instant deliveredAt;
    private Instant readAt;
    private String failureReason;

    private Notification(NotificationId id, UserId userId, NotificationType type,
                         NotificationChannel channel, String title, String body,
                         Map<String, Object> payload, UUID relatedEntityId,
                         NotificationStatus status, Instant createdAt,
                         Instant deliveredAt, Instant readAt, String failureReason) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.channel = channel;
        this.title = title;
        this.body = body;
        this.payload = payload;
        this.relatedEntityId = relatedEntityId;
        this.status = status;
        this.createdAt = createdAt;
        this.deliveredAt = deliveredAt;
        this.readAt = readAt;
        this.failureReason = failureReason;
    }

    public static Notification create(NotificationId id, UserId userId, NotificationType type,
                                      NotificationChannel channel, String title, String body,
                                      Map<String, Object> payload, UUID relatedEntityId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(channel, "channel");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        Map<String, Object> safePayload = payload == null ? Map.of() : Map.copyOf(payload);
        return new Notification(id, userId, type, channel, title, body, safePayload,
                relatedEntityId, NotificationStatus.PENDING, Instant.now(),
                null, null, null);
    }

    public static Notification reconstitute(NotificationId id, UserId userId, NotificationType type,
                                            NotificationChannel channel, String title, String body,
                                            Map<String, Object> payload, UUID relatedEntityId,
                                            NotificationStatus status, Instant createdAt,
                                            Instant deliveredAt, Instant readAt,
                                            String failureReason) {
        Map<String, Object> safePayload = payload == null ? Map.of() : Map.copyOf(payload);
        return new Notification(id, userId, type, channel, title, body, safePayload,
                relatedEntityId, status, createdAt, deliveredAt, readAt, failureReason);
    }

    public void markDelivered() {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark delivered from status " + status);
        }
        this.status = NotificationStatus.DELIVERED;
        this.deliveredAt = Instant.now();
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark failed from status " + status);
        }
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }

    public void markRead() {
        if (channel != NotificationChannel.IN_APP) {
            throw new IllegalStateException(
                    "Only in-app notifications can be marked read; channel=" + channel);
        }
        if (status == NotificationStatus.READ) {
            return; // idempotent
        }
        if (status != NotificationStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Cannot mark read from status " + status);
        }
        this.status = NotificationStatus.READ;
        this.readAt = Instant.now();
    }

    public boolean isUnread() {
        return channel == NotificationChannel.IN_APP
                && status == NotificationStatus.DELIVERED;
    }

    public NotificationId getId() { return id; }
    public UserId getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public NotificationChannel getChannel() { return channel; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Map<String, Object> getPayload() { return payload; }
    public UUID getRelatedEntityId() { return relatedEntityId; }
    public NotificationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
    public String getFailureReason() { return failureReason; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notification that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
