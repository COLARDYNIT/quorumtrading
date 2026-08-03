package com.quorumtrading.application.usecase.apitoken;

import com.quorumtrading.application.usecase.result.ResolvedApiToken;

public interface ResolveUserByApiTokenUseCase {
    /**
     * Resolves a user from a raw API token string.
     * Hashes the token and looks up the corresponding active, non-expired token.
     *
     * @param rawToken the full raw token (e.g., "qt_a1b2c3d4...")
     * @return the resolved token info containing user ID and token ID
     * @throws SecurityException if the token is invalid, expired, or revoked
     */
    ResolvedApiToken execute(String rawToken);
}
