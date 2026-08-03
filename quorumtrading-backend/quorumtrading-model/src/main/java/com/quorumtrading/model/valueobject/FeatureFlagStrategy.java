package com.quorumtrading.model.valueobject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One activation strategy on a feature flag, in Unleash's own vocabulary.
 *
 * <p>A flag that is switched on is not necessarily on for everyone: Unleash evaluates the
 * flag's strategies against the request context and the flag is on when any of them matches.
 * {@code default} matches everyone; {@code userWithId} matches only the listed identities.
 * Since we set the Unleash context's {@code userId} to the caller's email address, targeting
 * an individual is {@link #forEmails(Collection)}.</p>
 *
 * <p>Parameter values are strings because that is what Unleash's wire format uses — the SDK
 * parses them per strategy.</p>
 */
public record FeatureFlagStrategy(String name, Map<String, String> parameters) {

    /** Unleash's built-in "everyone" strategy. */
    public static final String DEFAULT = "default";

    /** Unleash's built-in per-identity strategy; reads the {@code userIds} parameter. */
    public static final String USER_WITH_ID = "userWithId";

    /** Unleash's built-in percentage rollout strategy; reads {@code rollout}/{@code stickiness}. */
    public static final String FLEXIBLE_ROLLOUT = "flexibleRollout";

    private static final String USER_IDS_PARAMETER = "userIds";

    public FeatureFlagStrategy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Feature flag strategy name is required");
        }
        name = name.trim();
        parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
    }

    /** On for every caller. */
    public static FeatureFlagStrategy everyone() {
        return new FeatureFlagStrategy(DEFAULT, Map.of());
    }

    /**
     * On only for the given email addresses.
     *
     * <p>Unleash's {@code userWithId} strategy takes a single comma-separated parameter, so the
     * addresses are joined here rather than stored as a list.</p>
     */
    public static FeatureFlagStrategy forEmails(Collection<String> emails) {
        List<String> cleaned = new ArrayList<>();
        if (emails != null) {
            for (String email : emails) {
                if (email != null && !email.isBlank()) {
                    cleaned.add(email.trim());
                }
            }
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("At least one email address is required for a userWithId strategy");
        }
        return new FeatureFlagStrategy(USER_WITH_ID, Map.of(USER_IDS_PARAMETER, String.join(",", cleaned)));
    }

    /** True when this strategy makes the flag on for everyone. */
    public boolean isUnconditional() {
        return DEFAULT.equalsIgnoreCase(name);
    }
}
