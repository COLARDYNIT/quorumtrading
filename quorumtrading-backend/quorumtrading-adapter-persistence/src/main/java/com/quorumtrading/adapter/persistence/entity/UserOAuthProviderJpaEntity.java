package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_oauth_providers")
public class UserOAuthProviderJpaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public UserJpaEntity user;

    @Column(name = "provider", length = 20, nullable = false)
    public String provider;

    @Column(name = "provider_user_id", length = 255, nullable = false)
    public String providerUserId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
