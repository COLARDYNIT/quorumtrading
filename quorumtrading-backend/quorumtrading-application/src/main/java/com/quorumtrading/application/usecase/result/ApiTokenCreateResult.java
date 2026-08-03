package com.quorumtrading.application.usecase.result;

import com.quorumtrading.model.valueobject.ApiTokenId;

import java.time.Instant;

/**
 * Result of creating an API token. Contains the raw token which is shown exactly once.
 */
public record ApiTokenCreateResult(
        ApiTokenId tokenId,
        String rawToken,
        String name,
        String tokenPrefix,
        Instant createdAt,
        Instant expiresAt
) {
}
