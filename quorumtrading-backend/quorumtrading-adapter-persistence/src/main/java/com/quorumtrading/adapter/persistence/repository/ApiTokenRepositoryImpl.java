package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.ApiTokenJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.ApiTokenMapper;
import com.quorumtrading.application.port.out.ApiTokenRepository;
import com.quorumtrading.model.entity.ApiToken;
import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class ApiTokenRepositoryImpl implements ApiTokenRepository {

    @Inject
    ApiTokenMapper mapper;

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public ApiToken save(ApiToken token) {
        ApiTokenJpaEntity jpaEntity = mapper.toJpaEntity(token);
        ApiTokenJpaEntity existing = entityManager.find(ApiTokenJpaEntity.class, jpaEntity.getId());
        if (existing != null) {
            entityManager.merge(jpaEntity);
        } else {
            entityManager.persist(jpaEntity);
        }
        return token;
    }

    @Override
    public Optional<ApiToken> findByTokenHash(String tokenHash) {
        TypedQuery<ApiTokenJpaEntity> query = entityManager.createQuery(
                "SELECT t FROM ApiTokenJpaEntity t WHERE t.tokenHash = :tokenHash",
                ApiTokenJpaEntity.class);
        query.setParameter("tokenHash", tokenHash);
        return query.getResultStream()
                .findFirst()
                .map(mapper::toDomain);
    }

    @Override
    public List<ApiToken> findByUserId(UserId userId) {
        TypedQuery<ApiTokenJpaEntity> query = entityManager.createQuery(
                "SELECT t FROM ApiTokenJpaEntity t WHERE t.userId = :userId ORDER BY t.createdAt DESC",
                ApiTokenJpaEntity.class);
        query.setParameter("userId", userId.value());
        return query.getResultList().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ApiToken> findById(ApiTokenId id) {
        return Optional.ofNullable(entityManager.find(ApiTokenJpaEntity.class, id.value()))
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(ApiTokenId id) {
        ApiTokenJpaEntity entity = entityManager.find(ApiTokenJpaEntity.class, id.value());
        if (entity != null) {
            entityManager.remove(entity);
        }
    }
}
