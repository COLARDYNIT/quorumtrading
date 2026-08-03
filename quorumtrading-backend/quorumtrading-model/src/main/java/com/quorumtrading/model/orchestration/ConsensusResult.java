package com.quorumtrading.model.orchestration;

import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.RecommendationType;

/**
 * Value object representing the result of a consensus evaluation.
 * Contains evaluation metadata that may not be persisted but is useful for decision making.
 */
public record ConsensusResult(
        RecommendationType finalAction,
        double aggregatedConfidence,
        ConsensusMethod methodUsed,
        String reasoning,
        boolean wasOverridden) {

    public ConsensusResult {
        if (finalAction == null) {
            throw new IllegalArgumentException("Final action cannot be null");
        }
        if (methodUsed == null) {
            throw new IllegalArgumentException("Consensus method cannot be null");
        }
        if (aggregatedConfidence < 0.0 || aggregatedConfidence > 1.0) {
            throw new IllegalArgumentException("Aggregated confidence must be between 0 and 1");
        }
    }

    /**
     * @deprecated Use {@link #finalAction()} instead (record accessor).
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public RecommendationType getFinalAction() {
        return finalAction;
    }

    /**
     * @deprecated Use {@link #aggregatedConfidence()} instead (record accessor).
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public double getAggregatedConfidence() {
        return aggregatedConfidence;
    }

    /**
     * @deprecated Use {@link #methodUsed()} instead (record accessor).
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public ConsensusMethod getMethodUsed() {
        return methodUsed;
    }

    /**
     * @deprecated Use {@link #reasoning()} instead (record accessor).
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public String getReasoning() {
        return reasoning;
    }

    @Override
    public String toString() {
        return String.format("ConsensusResult{action=%s, confidence=%.2f, method=%s, overridden=%s}",
                finalAction, aggregatedConfidence, methodUsed, wasOverridden);
    }
}
