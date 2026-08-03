package com.quorumtrading.adapter.rest.dto.request;

import java.util.List;
import java.util.Map;

/**
 * Change one feature flag.
 *
 * @param enabled    the master switch; required
 * @param strategies who an enabled flag applies to. Omit or leave empty for "everyone". To target
 *                   individuals, send one {@code userWithId} strategy whose {@code userIds}
 *                   parameter is a comma-separated list of email addresses, or use
 *                   {@link #enabledForEmails} which builds that for you.
 */
public record UpdateFeatureFlagRequest(
        Boolean enabled,
        List<StrategyPayload> strategies,
        List<String> enabledForEmails) {

    public record StrategyPayload(String name, Map<String, String> parameters) {}

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
