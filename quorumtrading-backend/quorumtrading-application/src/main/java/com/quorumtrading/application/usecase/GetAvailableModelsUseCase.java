package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.model.enums.ModelTier;
import com.quorumtrading.model.service.ModelFilter;
import com.quorumtrading.model.valueobject.CategorizedModel;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.TradingContext;
import org.jboss.logging.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Use case for retrieving available LLM models, optionally filtered by trading context.
 * <p>
 * Models are fetched from the LLM provider (e.g., OpenRouter) and filtered based on
 * the requirements of each trading context (e.g., RESEARCH requires web search capability).
 */
public class GetAvailableModelsUseCase {

    private static final Logger LOG = Logger.getLogger(GetAvailableModelsUseCase.class);

    private final LLMPort llmPort;
    private final ModelFilter modelFilter;

    public GetAvailableModelsUseCase(LLMPort llmPort, ModelFilter modelFilter) {
        this.llmPort = llmPort;
        this.modelFilter = modelFilter;
    }

    /**
     * Get all available models, sorted by provider and name.
     *
     * @return List of all available models
     */
    public List<LLMModelInfo> execute() {
        if (!llmPort.supportsModelListing()) {
            LOG.warn("LLM provider does not support model listing");
            return List.of();
        }

        List<LLMModelInfo> models = llmPort.getAvailableModels();
        LOG.debugf("Retrieved %d models from LLM provider", models.size());

        return sortModels(models);
    }

    /**
     * Get available models filtered for a specific trading context.
     *
     * @param context The trading context to filter for
     * @return Filtered list of models suitable for the context
     */
    public List<LLMModelInfo> execute(TradingContext context) {
        if (context == null) {
            return execute();
        }

        if (!llmPort.supportsModelListing()) {
            LOG.warn("LLM provider does not support model listing");
            return List.of();
        }

        List<LLMModelInfo> allModels = llmPort.getAvailableModels();
        List<LLMModelInfo> filtered = modelFilter.filterForContext(allModels, context);

        LOG.debugf("Retrieved %d models, %d after filtering for context %s",
            allModels.size(), filtered.size(), context);

        return sortModels(filtered);
    }

    /**
     * Get available models with tier evaluations for a specific trading context,
     * optionally filtered to only include specific tiers.
     *
     * @param context The trading context to evaluate for
     * @param tiers   If non-null and non-empty, only models in these tiers are returned
     * @return List of categorized models with tier and reason
     */
    public List<CategorizedModel> executeWithTiers(TradingContext context, Set<ModelTier> tiers) {
        List<CategorizedModel> all = executeWithTiers(context);
        if (tiers == null || tiers.isEmpty()) {
            return all;
        }
        return all.stream()
            .filter(cm -> tiers.contains(cm.evaluation().tier()))
            .toList();
    }

    /**
     * Get available models with tier evaluations for a specific trading context.
     * <p>
     * Models are filtered, evaluated for tier, and sorted by tier priority
     * (RECOMMENDED first → PROVEN → BUDGET → EXPERIMENTAL → AVOID),
     * then by provider and name within each tier.
     *
     * @param context The trading context to evaluate for
     * @return List of categorized models with tier and reason
     */
    public List<CategorizedModel> executeWithTiers(TradingContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required for tier evaluation");
        }

        if (!llmPort.supportsModelListing()) {
            LOG.warn("LLM provider does not support model listing");
            return List.of();
        }

        List<LLMModelInfo> allModels = llmPort.getAvailableModels();
        List<LLMModelInfo> filtered = modelFilter.filterForContext(allModels, context);

        List<CategorizedModel> categorized = filtered.stream()
            .map(model -> new CategorizedModel(model, modelFilter.evaluateModel(model, context)))
            .sorted(Comparator
                .comparingInt((CategorizedModel cm) -> cm.evaluation().tier().ordinal())
                .thenComparing(cm -> cm.model().provider() != null ? cm.model().provider() : "")
                .thenComparing(cm -> cm.model().name()))
            .toList();

        LOG.debugf("Retrieved %d models, %d after filtering, categorized for context %s",
            allModels.size(), categorized.size(), context);

        return categorized;
    }

    /**
     * Get the requirements description for a trading context.
     *
     * @param context The trading context
     * @return Human-readable description of model requirements
     */
    public String getContextRequirements(TradingContext context) {
        return modelFilter.getContextRequirements(context);
    }

    /**
     * Sort models by provider, then by name.
     */
    private List<LLMModelInfo> sortModels(List<LLMModelInfo> models) {
        return models.stream()
            .sorted(Comparator
                .comparing((LLMModelInfo m) -> m.provider() != null ? m.provider() : "")
                .thenComparing(LLMModelInfo::name))
            .toList();
    }
}
