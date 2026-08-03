package com.quorumtrading.model.valueobject;

/**
 * Value object combining an LLM model with its tier evaluation for a specific trading context.
 *
 * @param model      The LLM model information
 * @param evaluation The tier evaluation (tier + reason)
 */
public record CategorizedModel(LLMModelInfo model, ModelEvaluation evaluation) {

    public CategorizedModel {
        if (model == null) {
            throw new IllegalArgumentException("Model cannot be null");
        }
        if (evaluation == null) {
            throw new IllegalArgumentException("Evaluation cannot be null");
        }
    }
}
