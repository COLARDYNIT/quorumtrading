package com.quorumtrading.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@IdClass(NotificationPreferenceJpaEntity.PreferenceKey.class)
public class NotificationPreferenceJpaEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "type", length = 40)
    private String type;

    @Column(name = "channels", nullable = false, length = 120)
    private String channels;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationPreferenceJpaEntity() {}

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getChannels() { return channels; }
    public void setChannels(String channels) { this.channels = channels; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** Composite-key holder required by {@link IdClass}. */
    public static class PreferenceKey implements Serializable {
        private UUID userId;
        private String type;

        public PreferenceKey() {}
        public PreferenceKey(UUID userId, String type) {
            this.userId = userId;
            this.type = type;
        }
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PreferenceKey k)) return false;
            return Objects.equals(userId, k.userId) && Objects.equals(type, k.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, type);
        }
    }
}
