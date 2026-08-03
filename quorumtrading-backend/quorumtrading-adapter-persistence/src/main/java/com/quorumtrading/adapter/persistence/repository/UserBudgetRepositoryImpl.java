package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.UserBudgetJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.UserBudgetMapper;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.UserBudgetId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of the UserBudgetRepository port.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class UserBudgetRepositoryImpl implements UserBudgetRepository {

    @Inject
    UserBudgetMapper mapper;

    @Inject
    EntityManager entityManager;

    @Override
    public Optional<UserBudget> findByUserId(UserId userId) {
        TypedQuery<UserBudgetJpaEntity> query = entityManager.createQuery(
            "SELECT b FROM UserBudgetJpaEntity b WHERE b.userId = :userId",
            UserBudgetJpaEntity.class
        );
        query.setParameter("userId", userId.value());

        return query.getResultList().stream()
            .findFirst()
            .map(mapper::toDomain);
    }

    @Override
    public Optional<UserBudget> findById(UserBudgetId id) {
        UserBudgetJpaEntity entity = entityManager.find(UserBudgetJpaEntity.class, id.value());
        return entity == null ? Optional.empty() : Optional.of(mapper.toDomain(entity));
    }

    @Override
    @Transactional
    public UserBudget save(UserBudget userBudget) {
        UserBudgetJpaEntity entity = mapper.toJpaEntity(userBudget);

        // Ensure entity always has an ID
        if (entity.id == null) {
            entity.id = UUID.randomUUID();
        }

        // Check if this is an update (entity with this ID exists)
        UserBudgetJpaEntity existing = entityManager.find(UserBudgetJpaEntity.class, entity.id);

        if (existing != null) {
            // Update existing budget
            existing.budgetLimitAmount = entity.budgetLimitAmount;
            existing.budgetLimitCurrency = entity.budgetLimitCurrency;
            existing.currentSpendAmount = entity.currentSpendAmount;
            existing.lifetimeSpendAmount = entity.lifetimeSpendAmount;
            existing.currentPeriod = entity.currentPeriod;
            existing.modelPreferences = entity.modelPreferences;
            existing.dailySpendLimit = entity.dailySpendLimit;
            existing.lastTopUpAt = entity.lastTopUpAt;
            existing.updatedAt = entity.updatedAt;

            entityManager.merge(existing);
            entity = existing;
        } else {
            // New budget - use native SQL INSERT with named parameters
            // Note: CAST is used instead of :: to avoid parameter name parsing issues
            entityManager.createNativeQuery(
                "INSERT INTO user_budgets (id, user_id, budget_limit_amount, budget_limit_currency, " +
                "current_spend_amount, lifetime_spend_amount, current_period, model_preferences, daily_spend_limit, last_top_up_at, created_at, updated_at) " +
                "VALUES (:id, :userId, :limitAmount, :limitCurrency, :spendAmount, :lifetimeSpend, :period, CAST(:prefs AS jsonb), :dailyLimit, :lastTopUp, :createdAt, :updatedAt)"
            )
            .setParameter("id", entity.id)
            .setParameter("userId", entity.userId)
            .setParameter("limitAmount", entity.budgetLimitAmount)
            .setParameter("limitCurrency", entity.budgetLimitCurrency)
            .setParameter("spendAmount", entity.currentSpendAmount)
            .setParameter("lifetimeSpend", entity.lifetimeSpendAmount)
            .setParameter("period", entity.currentPeriod)
            .setParameter("prefs", toJsonString(entity.modelPreferences))
            .setParameter("dailyLimit", entity.dailySpendLimit)
            .setParameter("lastTopUp", entity.lastTopUpAt)
            .setParameter("createdAt", entity.createdAt)
            .setParameter("updatedAt", entity.updatedAt)
            .executeUpdate();

            // Refresh entity to get managed state
            entity = entityManager.find(UserBudgetJpaEntity.class, entity.id);
        }

        return mapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UserBudgetId id) {
        UserBudgetJpaEntity entity = entityManager.find(UserBudgetJpaEntity.class, id.value());
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    public List<UserBudget> findAll() {
        TypedQuery<UserBudgetJpaEntity> query = entityManager.createQuery(
            "SELECT b FROM UserBudgetJpaEntity b",
            UserBudgetJpaEntity.class
        );

        return query.getResultList().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public boolean existsByUserId(UserId userId) {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(b) FROM UserBudgetJpaEntity b WHERE b.userId = :userId",
            Long.class
        );
        query.setParameter("userId", userId.value());

        return query.getSingleResult() > 0;
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private String toJsonString(java.util.Map<String, String> map) {
        try {
            return JSON_MAPPER.writeValueAsString(map != null ? map : Collections.emptyMap());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
