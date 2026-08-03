package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.entity.ApiToken;

import java.time.Instant;

public record ApiTokenResponse(
        String id,
        String name,
        String tokenPrefix,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean revoked
) {
    public static ApiTokenResponse fromDomain(ApiToken token) {
        return new ApiTokenResponse(
                token.getId().value().toString(),
                token.getName(),
                token.getTokenPrefix(),
                token.getCreatedAt(),
                token.getLastUsedAt(),
                token.getExpiresAt(),
                token.isRevoked()
        );
    }
}
