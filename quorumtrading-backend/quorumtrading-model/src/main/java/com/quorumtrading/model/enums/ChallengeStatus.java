package com.quorumtrading.model.enums;

/**
 * Status of a challenge, derived from dates (not persisted).
 */
public enum ChallengeStatus {
    UPCOMING("Upcoming", "Challenge has not started yet"),
    ACTIVE("Active", "Challenge is currently running and accepting entries"),
    COMPLETED("Completed", "Challenge has ended");

    private final String displayName;
    private final String description;

    ChallengeStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
