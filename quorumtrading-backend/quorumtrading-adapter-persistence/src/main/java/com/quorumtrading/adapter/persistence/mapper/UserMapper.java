package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.UserJpaEntity;
import com.quorumtrading.adapter.persistence.entity.UserOAuthProviderJpaEntity;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.entity.UserOAuthProvider;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.enums.UserRole;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper between User domain model and UserJpaEntity.
 *
 * ARCHITECTURAL NOTE:
 * - Domain UserId (UUID wrapper) → Database id (UUID)
 * - Domain email → Database email field
 * - Domain role (enum) → Database role (string)
 * - Domain oauthProviders (list) → Database oauthProviders (OneToMany)
 *
 * The mapper handles the aggregate boundary, converting both User and nested UserOAuthProvider entities.
 */
@ApplicationScoped
public class UserMapper {

    /**
     * Convert domain User to JPA entity.
     *
     * Maps:
     * - UserId (UUID) → id field (direct mapping)
     * - email → email field
     * - displayName → displayName field
     * - avatarUrl → avatarUrl field
     * - role (enum) → role (string)
     * - oauthProviders (list) → oauthProviders (OneToMany)
     *
     * Timestamps are set to defaults by @PrePersist.
     */
    public UserJpaEntity toJpaEntity(User user) {
        UserJpaEntity entity = new UserJpaEntity();

        // Map UUID directly to entity ID
        if (user.getId() != null) {
            entity.id = user.getId().value();
        }

        entity.email = user.getEmail();
        entity.displayName = user.getDisplayName();
        entity.avatarUrl = user.getAvatarUrl();
        entity.role = user.getRole() != null ? user.getRole().name() : "USER";

        // Timestamps
        if (user.getCreatedAt() != null) {
            entity.createdAt = user.getCreatedAt();
        }
        if (user.getLastLoginAt() != null) {
            entity.lastLoginAt = user.getLastLoginAt();
        }

        entity.welcomeBudgetCredited = user.isWelcomeBudgetCredited();
        entity.returningUser = user.isReturningUser();

        // Map OAuth providers (child entities)
        entity.oauthProviders = user.getOauthProviders().stream()
            .map(this::toOAuthProviderJpaEntity)
            .collect(Collectors.toList());

        // Set bidirectional relationship
        entity.oauthProviders.forEach(provider -> provider.user = entity);

        return entity;
    }

    /**
     * Convert JPA entity to domain User.
     *
     * Maps:
     * - id field (UUID) → UserId
     * - email field → email
     * - displayName field → displayName
     * - avatarUrl field → avatarUrl
     * - role (string) → role (enum)
     * - oauthProviders (OneToMany) → oauthProviders (list)
     */
    public User toDomain(UserJpaEntity entity) {
        UserId id = new UserId(entity.id);

        // Parse role enum from string (defaults to USER if invalid/null)
        UserRole role;
        try {
            role = entity.role != null
                ? UserRole.valueOf(entity.role)
                : UserRole.USER;
        } catch (IllegalArgumentException e) {
            role = UserRole.USER;
        }

        // Map OAuth providers (child entities)
        List<UserOAuthProvider> providers = entity.oauthProviders.stream()
            .map(this::toOAuthProviderDomain)
            .collect(Collectors.toList());

        return User.reconstitute(
            id,
            entity.email,
            entity.displayName,
            entity.avatarUrl,
            role,
            entity.createdAt,
            entity.lastLoginAt,
            providers,
            entity.welcomeBudgetCredited,
            entity.returningUser
        );
    }

    /**
     * Convert domain UserOAuthProvider to JPA entity.
     */
    private UserOAuthProviderJpaEntity toOAuthProviderJpaEntity(UserOAuthProvider provider) {
        UserOAuthProviderJpaEntity entity = new UserOAuthProviderJpaEntity();
        entity.id = provider.getId();
        entity.provider = provider.getProvider().name();
        entity.providerUserId = provider.getProviderUserId();
        if (provider.getCreatedAt() != null) {
            entity.createdAt = provider.getCreatedAt();
        }
        return entity;
    }

    /**
     * Convert JPA UserOAuthProviderJpaEntity to domain UserOAuthProvider.
     */
    private UserOAuthProvider toOAuthProviderDomain(UserOAuthProviderJpaEntity entity) {
        OAuthProvider provider;
        try {
            provider = OAuthProvider.valueOf(entity.provider);
        } catch (IllegalArgumentException e) {
            // Fallback to GOOGLE if invalid
            provider = OAuthProvider.GOOGLE;
        }

        return new UserOAuthProvider(
            entity.id,
            provider,
            entity.providerUserId,
            entity.createdAt
        );
    }
}
