package com.quorumtrading.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA view of a row in {@code feature_flags}. Keyed by the Unleash flag name, so there is no
 * surrogate id — the name is already the natural, stable identifier.
 */
@Entity
@Table(name = "feature_flags")
public class FeatureFlagJpaEntity {

    @Id
    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** JSON array of Unleash activation strategies. */
    @Column(name = "strategies", nullable = false, columnDefinition = "TEXT")
    private String strategies = "[]";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    @PreUpdate
    void touch() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (strategies == null) {
            strategies = "[]";
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStrategies() {
        return strategies;
    }

    public void setStrategies(String strategies) {
        this.strategies = strategies;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
