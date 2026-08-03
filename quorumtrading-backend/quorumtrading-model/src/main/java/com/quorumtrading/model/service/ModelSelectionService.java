package com.quorumtrading.model.service;

import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.TradingContext;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Domain service for selecting the appropriate LLM model.
 * <p>
 * Selection priority:
 * <ol>
 *   <li>User preference for the context (if set)</li>
 *   <li>System default for the context</li>
 * </ol>
 * </p>
 */
public class ModelSelectionService {

    private final Map<TradingContext, ModelId> systemDefaults;

    /**
     * Create with system default models.
     */
    public ModelSelectionService(Map<TradingContext, ModelId> systemDefaults) {
        this.systemDefaults = new EnumMap<>(TradingContext.class);
        if (systemDefaults != null) {
            this.systemDefaults.putAll(systemDefaults);
        }
    }

    /**
     * Select the model to use for a given context.
     *
     * @param userBudget The user's budget with preferences (can be null)
     * @param context    The trading context
     * @return The selected model ID
     * @throws IllegalStateException if no model is available for the context
     */
    public ModelId selectModel(UserBudget userBudget, TradingContext context) {
        // First, try user preference
        if (userBudget != null) {
            Optional<ModelId> userPreference = userBudget.getPreferredModel(context);
            if (userPreference.isPresent()) {
                return userPreference.get();
            }
        }

        // Fall back to system default
        ModelId systemDefault = systemDefaults.get(context);
        if (systemDefault != null) {
            return systemDefault;
        }

        throw new IllegalStateException(
            "No model available for context: " + context + ". Configure a system default."
        );
    }

    /**
     * Select the model, returning the model ID as a string.
     */
    public String selectModelId(UserBudget userBudget, TradingContext context) {
        return selectModel(userBudget, context).value();
    }

    /**
     * Get the system default for a context.
     */
    public Optional<ModelId> getSystemDefault(TradingContext context) {
        return Optional.ofNullable(systemDefaults.get(context));
    }

    /**
     * Check if a system default is configured for a context.
     */
    public boolean hasSystemDefault(TradingContext context) {
        return systemDefaults.containsKey(context);
    }

    /**
     * Builder for creating ModelSelectionService with fluent API.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<TradingContext, ModelId> defaults = new EnumMap<>(TradingContext.class);

        public Builder withDefault(TradingContext context, String modelId) {
            defaults.put(context, ModelId.of(modelId));
            return this;
        }

        public Builder withDefault(TradingContext context, ModelId modelId) {
            defaults.put(context, modelId);
            return this;
        }

        public ModelSelectionService build() {
            return new ModelSelectionService(defaults);
        }
    }
}
