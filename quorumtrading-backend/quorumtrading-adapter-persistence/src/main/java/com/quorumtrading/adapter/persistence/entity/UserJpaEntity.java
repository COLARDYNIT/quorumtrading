package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserJpaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "email", length = 255, unique = true, nullable = false)
    public String email;

    @Column(name = "display_name", length = 255)
    public String displayName;

    @Column(name = "avatar_url", length = 500)
    public String avatarUrl;

    @Column(name = "role", length = 20, nullable = false)
    public String role = "USER";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    @Column(name = "welcome_budget_credited", nullable = false)
    public boolean welcomeBudgetCredited = false;

    @Column(name = "returning_user", nullable = false)
    public boolean returningUser = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<UserOAuthProviderJpaEntity> oauthProviders = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (role == null) {
            role = "USER";
        }
    }

    public void addOAuthProvider(UserOAuthProviderJpaEntity provider) {
        oauthProviders.add(provider);
        provider.user = this;
    }
}
