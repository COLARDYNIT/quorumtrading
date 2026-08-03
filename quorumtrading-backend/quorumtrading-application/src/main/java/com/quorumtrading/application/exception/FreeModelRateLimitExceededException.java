package com.quorumtrading.application.exception;

/**
 * Exception thrown when a user exceeds the daily rate limit for free model usage.
 */
public class FreeModelRateLimitExceededException extends RuntimeException {

    private final int dailyLimit;
    private final String modelId;

    public FreeModelRateLimitExceededException(String modelId, int dailyLimit) {
        super("Free model daily limit of " + dailyLimit + " calls exceeded for model: " + modelId);
        this.dailyLimit = dailyLimit;
        this.modelId = modelId;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public String getModelId() {
        return modelId;
    }
}
