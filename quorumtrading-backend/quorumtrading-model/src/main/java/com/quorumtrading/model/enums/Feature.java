package com.quorumtrading.model.enums;

import java.util.Optional;

/**
 * The registry of everything that can be switched off at runtime.
 *
 * <p>This enum — not the database — is the source of truth for which flags exist. A row in
 * {@code feature_flags} whose name matches nothing here is ignored, and a feature listed here
 * always shows up in the admin UI even before its row has been seeded. That asymmetry is
 * deliberate: a typo in a flag name should be a compile error, not a silently-always-off
 * feature.</p>
 *
 * <p>{@link #flagName()} is the identifier Unleash sees, so it follows Unleash's kebab-case
 * convention rather than the Java constant name.</p>
 */
public enum Feature {

    CHALLENGES("challenges", "Public trading challenges and their leaderboards"),
    MARKET_RESEARCH("market-research", "LLM-driven market research and strategy discovery");

    private final String flagName;
    private final String description;

    Feature(String flagName, String description) {
        this.flagName = flagName;
        this.description = description;
    }

    /** The Unleash toggle name, e.g. {@code "market-research"}. */
    public String flagName() {
        return flagName;
    }

    /** Human-readable summary shown in the admin UI. */
    public String description() {
        return description;
    }

    /** Resolves a flag name back to a feature, empty when the name is not in the registry. */
    public static Optional<Feature> fromFlagName(String flagName) {
        if (flagName == null) {
            return Optional.empty();
        }
        for (Feature feature : values()) {
            if (feature.flagName.equalsIgnoreCase(flagName)) {
                return Optional.of(feature);
            }
        }
        return Optional.empty();
    }
}
