package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.OAuthProvider;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing an OAuth provider linked to a user account.
 *
 * <p>Users can authenticate via multiple OAuth providers (Google, Apple).
 * This entity tracks the provider type and the provider-specific user ID.</p>
 *
 * <p><b>Child Entity</b> - Part of the User aggregate.</p>
 */
public class UserOAuthProvider {
    private final UUID id;
    private final OAuthProvider provider;
    private final String providerUserId;
    private final Instant createdAt;

    /**
     * Constructor for creating a new provider link.
     *
     * @param id The unique identifier for this provider link
     * @param provider The OAuth provider (GOOGLE, APPLE)
     * @param providerUserId The user ID from the provider
     */
    public UserOAuthProvider(UUID id, OAuthProvider provider, String providerUserId) {
        this.id = id;
        this.provider = Objects.requireNonNull(provider, "Provider cannot be null");
        this.providerUserId = Objects.requireNonNull(providerUserId, "Provider user ID cannot be null");
        this.createdAt = Instant.now();
    }

    /**
     * Repository constructor for reconstituting from persistence.
     *
     * @param id The unique identifier
     * @param provider The OAuth provider
     * @param providerUserId The provider-specific user ID
     * @param createdAt The timestamp when this link was created
     */
    public UserOAuthProvider(UUID id, OAuthProvider provider, String providerUserId, Instant createdAt) {
        this.id = id;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public OAuthProvider getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserOAuthProvider that)) return false;
        return provider == that.provider && providerUserId.equals(that.providerUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, providerUserId);
    }

    @Override
    public String toString() {
        return "UserOAuthProvider{" +
                "provider=" + provider +
                ", providerUserId='" + providerUserId + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
