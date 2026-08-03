package com.quorumtrading.application.usecase.result;

import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Result of resolving a user from an API token.
 * Contains both the user ID and the token ID (needed for last_used_at tracking).
 */
public record ResolvedApiToken(UserId userId, ApiTokenId tokenId) {
}
