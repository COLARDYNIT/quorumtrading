package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.UserJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.UserMapper;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA EntityManager-based implementation of the UserRepository port.
 *
 * This adapter bridges the domain port interface to the JPA persistence layer.
 * Database uses UUID primary keys, mapping directly between domain and database.
 *
 * Uses EntityManager instead of Panache static methods to support cross-module entity discovery in Quarkus.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class UserRepositoryImpl implements UserRepository {

    @Inject
    UserMapper mapper;

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public User save(User user) {
        UserJpaEntity entity = mapper.toJpaEntity(user);

        // Ensure entity always has an ID
        if (entity.id == null) {
            entity.id = UUID.randomUUID();
        }

        // Check if this is an update (entity with this ID exists)
        UserJpaEntity existing = entityManager.find(UserJpaEntity.class, entity.id);

        if (existing != null) {
            // Update existing user - modify the existing entity directly
            existing.email = entity.email;
            existing.displayName = entity.displayName;
            existing.avatarUrl = entity.avatarUrl;
            existing.role = entity.role;
            existing.lastLoginAt = entity.lastLoginAt;
            existing.welcomeBudgetCredited = entity.welcomeBudgetCredited;
            existing.returningUser = entity.returningUser;

            // Update OAuth providers - remove old ones and add new ones
            existing.oauthProviders.clear();
            entity.oauthProviders.forEach(provider -> {
                provider.user = existing;
                existing.oauthProviders.add(provider);
            });

            entityManager.merge(existing);
            entity = existing;
        } else {
            // New user - use native SQL INSERT to bypass Hibernate's entity state checks
            // This is necessary because Hibernate considers entities with IDs as "detached"
            // even if they don't exist in the database yet
            entityManager.createNativeQuery(
                "INSERT INTO users (id, email, display_name, avatar_url, role, created_at, last_login_at, welcome_budget_credited, returning_user) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)"
            )
            .setParameter(1, entity.id)
            .setParameter(2, entity.email)
            .setParameter(3, entity.displayName)
            .setParameter(4, entity.avatarUrl)
            .setParameter(5, entity.role != null ? entity.role : "USER")
            .setParameter(6, entity.createdAt != null ? entity.createdAt : java.time.Instant.now())
            .setParameter(7, entity.lastLoginAt)
            .setParameter(8, entity.welcomeBudgetCredited)
            .setParameter(9, entity.returningUser)
            .executeUpdate();

            // Insert OAuth providers
            for (var provider : entity.oauthProviders) {
                entityManager.createNativeQuery(
                    "INSERT INTO user_oauth_providers (id, user_id, provider, provider_user_id, created_at) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5)"
                )
                .setParameter(1, provider.id)
                .setParameter(2, entity.id)
                .setParameter(3, provider.provider)
                .setParameter(4, provider.providerUserId)
                .setParameter(5, provider.createdAt != null ? provider.createdAt : java.time.Instant.now())
                .executeUpdate();
            }

            // Refresh entity to get managed state
            entity = entityManager.find(UserJpaEntity.class, entity.id);
        }

        return mapper.toDomain(entity);
    }

    @Override
    public Optional<User> findById(UserId id) {
        UserJpaEntity entity = entityManager.find(UserJpaEntity.class, id.value());
        return entity == null ? Optional.empty() : Optional.of(mapper.toDomain(entity));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        TypedQuery<UserJpaEntity> query = entityManager.createQuery(
            "SELECT u FROM UserJpaEntity u WHERE u.email = :email",
            UserJpaEntity.class
        );
        query.setParameter("email", email.toLowerCase().trim());

        return query.getResultList().stream()
            .findFirst()
            .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByProviderUserId(String provider, String providerUserId) {
        TypedQuery<UserJpaEntity> query = entityManager.createQuery(
            "SELECT u FROM UserJpaEntity u " +
            "JOIN u.oauthProviders p " +
            "WHERE p.provider = :provider AND p.providerUserId = :providerUserId",
            UserJpaEntity.class
        );
        query.setParameter("provider", provider);
        query.setParameter("providerUserId", providerUserId);

        return query.getResultList().stream()
            .findFirst()
            .map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(u) FROM UserJpaEntity u WHERE u.email = :email",
            Long.class
        );
        query.setParameter("email", email.toLowerCase().trim());

        return query.getSingleResult() > 0;
    }

    @Override
    public long count() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(u) FROM UserJpaEntity u",
            Long.class
        );
        return query.getSingleResult();
    }

    @Override
    public List<User> findRecent(int limit) {
        TypedQuery<UserJpaEntity> query = entityManager.createQuery(
            "SELECT u FROM UserJpaEntity u ORDER BY u.createdAt DESC",
            UserJpaEntity.class
        );
        query.setMaxResults(limit);

        return query.getResultList().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<User> search(String searchQuery, int limit) {
        TypedQuery<UserJpaEntity> query = entityManager.createQuery(
            "SELECT u FROM UserJpaEntity u WHERE LOWER(u.email) LIKE :q OR LOWER(u.displayName) LIKE :q ORDER BY u.createdAt DESC",
            UserJpaEntity.class
        );
        query.setParameter("q", "%" + searchQuery.toLowerCase().trim() + "%");
        query.setMaxResults(limit);

        return query.getResultList().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean deleteById(UserId id) {
        UserJpaEntity entity = entityManager.find(UserJpaEntity.class, id.value());
        if (entity == null) {
            return false;
        }
        entityManager.remove(entity);
        return true;
    }
}
