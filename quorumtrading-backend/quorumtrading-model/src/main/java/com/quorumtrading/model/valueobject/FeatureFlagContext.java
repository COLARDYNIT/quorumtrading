package com.quorumtrading.model.valueobject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who is asking, when a feature flag is evaluated.
 *
 * <p>This is the domain's shape of an Unleash evaluation context. {@link #userEmail()} doubles as
 * Unleash's {@code userId} so that the built-in {@code userWithId} strategy can target a single
 * person by email without a custom strategy. Extra {@link #properties()} are passed through as
 * Unleash context properties and can be matched with constraints.</p>
 *
 * <p>{@link #anonymous()} is the honest context for callers we cannot identify — an unauthenticated
 * request, or the MCP {@code tools/list} handshake, which has no per-request user at all. A flag
 * that is only on for specific emails is therefore off in an anonymous context.</p>
 */
public record FeatureFlagContext(UserId userId, String userEmail, Map<String, String> properties) {

    public FeatureFlagContext {
        properties = properties == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(properties));
    }

    /** No identified caller. Email-targeted flags evaluate to off. */
    public static FeatureFlagContext anonymous() {
        return new FeatureFlagContext(null, null, Map.of());
    }

    public static FeatureFlagContext forUser(UserId userId, String userEmail) {
        return new FeatureFlagContext(userId, userEmail, Map.of());
    }

    /**
     * Identifies the caller by email alone.
     *
     * <p>For request-path guards, where the email comes free off the security identity and
     * resolving the {@link UserId} would mean an extra database lookup on every gated request.
     * Email is all the built-in {@code userWithId} targeting needs.</p>
     */
    public static FeatureFlagContext forEmail(String userEmail) {
        return new FeatureFlagContext(null, userEmail, Map.of());
    }

    /** Returns a copy with one extra context property, for constraint matching. */
    public FeatureFlagContext with(String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return this;
        }
        Map<String, String> merged = new LinkedHashMap<>(properties);
        merged.put(key, value);
        return new FeatureFlagContext(userId, userEmail, merged);
    }

    public boolean isAnonymous() {
        return userEmail == null || userEmail.isBlank();
    }
}
