package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;

/**
 * Domain entity representing an API token for programmatic access (e.g., MCP clients).
 *
 * <p><b>Aggregate Root</b> - Owns its own lifecycle (create, use, revoke) and is queried
 * independently by token hash. Not embedded in User to avoid loading all tokens on every
 * user fetch.</p>
 *
 * <p><b>Invariants Enforced:</b></p>
 * <ul>
 *   <li>Token hash and prefix are immutable after creation</li>
 *   <li>A revoked token cannot be un-revoked</li>
 *   <li>An expired token is never active</li>
 *   <li>Name must be non-blank</li>
 * </ul>
 *
 * <p><b>Security:</b> Only the SHA-256 hash of the token is stored. The raw token is
 * returned exactly once at creation time and never persisted.</p>
 */
public class ApiToken implements AggregateRoot {

    private final ApiTokenId id;
    private final UserId userId;
    private final String name;
    private final String tokenHash;
    private final String tokenPrefix;
    private final Instant createdAt;
    private Instant lastUsedAt;
    private final Instant expiresAt;
    private boolean revoked;
    private Instant revokedAt;

    private ApiToken(ApiTokenId id, UserId userId, String name, String tokenHash,
                     String tokenPrefix, Instant createdAt, Instant lastUsedAt,
                     Instant expiresAt, boolean revoked, Instant revokedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.tokenHash = tokenHash;
        this.tokenPrefix = tokenPrefix;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.revokedAt = revokedAt;
    }

    /**
     * Creates a new API token.
     *
     * @param id         unique token identifier
     * @param userId     the owning user
     * @param name       human-readable label
     * @param tokenHash  SHA-256 hex hash of the full raw token
     * @param tokenPrefix first 8 characters of the raw token for display
     * @param expiresAt  optional expiration (null = never expires)
     * @return a new ApiToken instance
     */
    public static ApiToken create(ApiTokenId id, UserId userId, String name,
                                   String tokenHash, String tokenPrefix, Instant expiresAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Token name cannot be blank");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("Token hash cannot be blank");
        }
        if (tokenPrefix == null || tokenPrefix.isBlank()) {
            throw new IllegalArgumentException("Token prefix cannot be blank");
        }
        return new ApiToken(id, userId, name, tokenHash, tokenPrefix,
                Instant.now(), null, expiresAt, false, null);
    }

    /**
     * Repository constructor for reconstituting from persistence.
     */
    public static ApiToken reconstitute(ApiTokenId id, UserId userId, String name,
                                         String tokenHash, String tokenPrefix,
                                         Instant createdAt, Instant lastUsedAt,
                                         Instant expiresAt, boolean revoked, Instant revokedAt) {
        return new ApiToken(id, userId, name, tokenHash, tokenPrefix,
                createdAt, lastUsedAt, expiresAt, revoked, revokedAt);
    }

    /**
     * Revokes this token, making it permanently unusable.
     *
     * @throws IllegalStateException if already revoked
     */
    public void revoke() {
        if (this.revoked) {
            throw new IllegalStateException("Token is already revoked");
        }
        this.revoked = true;
        this.revokedAt = Instant.now();
    }

    /**
     * Records a usage event, updating the last-used timestamp.
     */
    public void recordUsage() {
        this.lastUsedAt = Instant.now();
    }

    /**
     * Checks whether this token has passed its expiration date.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * A token is active if it is neither revoked nor expired.
     */
    public boolean isActive() {
        return !revoked && !isExpired();
    }

    // Getters

    public ApiTokenId getId() { return id; }
    public UserId getUserId() { return userId; }
    public String getName() { return name; }
    public String getTokenHash() { return tokenHash; }
    public String getTokenPrefix() { return tokenPrefix; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public Instant getRevokedAt() { return revokedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiToken that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ApiToken{" +
                "id=" + id +
                ", userId=" + userId +
                ", name='" + name + '\'' +
                ", tokenPrefix='" + tokenPrefix + '\'' +
                ", revoked=" + revoked +
                '}';
    }
}
