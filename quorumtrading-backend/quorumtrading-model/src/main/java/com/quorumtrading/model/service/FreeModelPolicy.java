package com.quorumtrading.model.service;

import java.util.List;
import java.util.Set;

/**
 * Domain service that determines which models are free for users
 * and enforces a daily usage limit. The free model list is configuration-driven
 * so models can be added/removed without code changes.
 */
public class FreeModelPolicy {

    private final Set<String> freeModelIds;
    private final int dailyLimit;

    public FreeModelPolicy(List<String> freeModelIds, int dailyLimit) {
        this.freeModelIds = Set.copyOf(freeModelIds);
        this.dailyLimit = dailyLimit;
    }

    /**
     * Check if a model is in the configured free-for-user list.
     */
    public boolean isFreeForUser(String modelId) {
        return freeModelIds.contains(modelId);
    }

    /**
     * Get the daily usage limit for free models.
     */
    public int getDailyLimit() {
        return dailyLimit;
    }

    /**
     * Get the set of free model IDs.
     */
    public Set<String> getFreeModelIds() {
        return freeModelIds;
    }
}
