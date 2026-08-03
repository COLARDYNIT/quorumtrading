package com.quorumtrading.model.enums;

public enum AgentRole {
    ORCHESTRATOR("Orchestrator", "Strategy interpreter and final authority"),
    TECHNICAL("Technical Analyst", "Price patterns and technical indicators"),
    FUNDAMENTAL("Fundamental Analyst", "News and macroeconomic analysis"),
    SENTIMENT("Sentiment Analyst", "Social media and retail trends");

    private final String displayName;
    private final String description;

    AgentRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
