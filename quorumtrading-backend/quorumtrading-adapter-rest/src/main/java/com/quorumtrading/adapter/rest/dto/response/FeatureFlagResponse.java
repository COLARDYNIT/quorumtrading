package com.quorumtrading.adapter.rest.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Admin view of one feature flag.
 *
 * @param name        the flag name, e.g. {@code "market-research"}
 * @param description what the flag governs
 * @param enabled     the master switch
 * @param strategies  who an enabled flag applies to; empty means everyone
 * @param updatedAt   when it last changed
 * @param updatedBy   the administrator who last changed it, null for a seeded flag
 */
public record FeatureFlagResponse(
        String name,
        String description,
        boolean enabled,
        List<FeatureFlagStrategyDto> strategies,
        Instant updatedAt,
        String updatedBy) {

    /**
     * One Unleash activation strategy.
     *
     * @param name       {@code default} (everyone), {@code userWithId} (listed identities),
     *                   {@code flexibleRollout} (percentage)
     * @param parameters strategy parameters, e.g. {@code {"userIds": "a@example.com,b@example.com"}}
     */
    public record FeatureFlagStrategyDto(String name, java.util.Map<String, String> parameters) {}
}
