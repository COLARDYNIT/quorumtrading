package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.ModelTier;

/**
 * Value object representing the tier evaluation of an LLM model for a specific trading context.
 *
 * @param tier   The categorization tier (RECOMMENDED, PROVEN, BUDGET, EXPERIMENTAL, AVOID)
 * @param reason Human-readable explanation for the tier assignment
 */
public record ModelEvaluation(ModelTier tier, String reason) {

    public ModelEvaluation {
        if (tier == null) {
            throw new IllegalArgumentException("Tier cannot be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason cannot be null or blank");
        }
    }
}
