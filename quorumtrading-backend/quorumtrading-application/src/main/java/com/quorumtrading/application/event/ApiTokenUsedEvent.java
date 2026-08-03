package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.ApiTokenId;

import java.time.Instant;

/**
 * CDI event fired when an API token is used for authentication.
 * Consumed asynchronously to update last_used_at without impacting request latency.
 */
public record ApiTokenUsedEvent(ApiTokenId tokenId, Instant usedAt) {
}
