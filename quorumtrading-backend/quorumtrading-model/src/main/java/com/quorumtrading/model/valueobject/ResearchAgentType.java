package com.quorumtrading.model.valueobject;

/**
 * Value object representing the type of research agent.
 */
public enum ResearchAgentType {
    NEWS_SENTIMENT_ANALYST("News & Sentiment Analyst"),
    STRATEGY_DESIGNER("Strategy Designer"),
    VIABILITY_ASSESSOR("Viability Assessor");

    private final String displayName;

    ResearchAgentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
