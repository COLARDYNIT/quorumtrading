package com.quorumtrading.model.enums;

/**
 * Enum representing the type of recommendation made by an agent.
 * Extracted from LLMDecision for standalone usage.
 */
public enum RecommendationType {
    BUY("Buy"),
    SELL("Sell"),
    HOLD("Hold"),
    SKIP("Skip");

    private final String displayName;

    RecommendationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
