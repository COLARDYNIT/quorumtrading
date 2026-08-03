package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.application.usecase.result.ApiTokenCreateResult;

import java.time.Instant;

/**
 * Response returned when a token is created. Contains the raw token (shown once).
 */
public record ApiTokenCreatedResponse(
        String id,
        String rawToken,
        String name,
        String tokenPrefix,
        Instant createdAt,
        Instant expiresAt
) {
    public static ApiTokenCreatedResponse fromResult(ApiTokenCreateResult result) {
        return new ApiTokenCreatedResponse(
                result.tokenId().value().toString(),
                result.rawToken(),
                result.name(),
                result.tokenPrefix(),
                result.createdAt(),
                result.expiresAt()
        );
    }
}
