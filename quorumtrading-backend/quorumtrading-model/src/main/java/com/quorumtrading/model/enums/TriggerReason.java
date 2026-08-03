package com.quorumtrading.model.enums;

public enum TriggerReason {
    SCHEDULED("Scheduled interval trigger"),
    MARKET_EVENT("Market event detected"),
    MANUAL("Manual user trigger"),
    THRESHOLD_BREACH("Portfolio threshold breached");

    private final String description;

    TriggerReason(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
