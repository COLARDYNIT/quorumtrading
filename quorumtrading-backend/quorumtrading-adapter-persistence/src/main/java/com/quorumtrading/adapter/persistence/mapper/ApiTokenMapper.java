package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.ApiTokenJpaEntity;
import com.quorumtrading.model.entity.ApiToken;
import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ApiTokenMapper {

    public ApiTokenJpaEntity toJpaEntity(ApiToken token) {
        ApiTokenJpaEntity entity = new ApiTokenJpaEntity();
        entity.setId(token.getId().value());
        entity.setUserId(token.getUserId().value());
        entity.setName(token.getName());
        entity.setTokenHash(token.getTokenHash());
        entity.setTokenPrefix(token.getTokenPrefix());
        entity.setCreatedAt(token.getCreatedAt());
        entity.setLastUsedAt(token.getLastUsedAt());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setRevoked(token.isRevoked());
        entity.setRevokedAt(token.getRevokedAt());
        return entity;
    }

    public ApiToken toDomain(ApiTokenJpaEntity entity) {
        return ApiToken.reconstitute(
                new ApiTokenId(entity.getId()),
                new UserId(entity.getUserId()),
                entity.getName(),
                entity.getTokenHash(),
                entity.getTokenPrefix(),
                entity.getCreatedAt(),
                entity.getLastUsedAt(),
                entity.getExpiresAt(),
                entity.isRevoked(),
                entity.getRevokedAt()
        );
    }
}
