package com.quorumtrading.model.service;

import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.ModelEvaluation;
import com.quorumtrading.model.valueobject.TradingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Domain service for filtering LLM models based on trading context requirements.
 * <p>
 * Different trading contexts have different requirements:
 * <ul>
 *   <li>RESEARCH: Requires web search capability for market research</li>
 *   <li>BACKTESTING: All models sorted by cost (cheapest first)</li>
 *   <li>PAPER_TRADING: All models allowed</li>
 *   <li>LIVE_TRADING: Trusted providers, 32K+ context, 8B+ parameters</li>
 * </ul>
 */
public class ModelFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ModelFilter.class);

    // LIVE_TRADING requirements
    private static final int MIN_LIVE_TRADING_CONTEXT = 32768; // 32K tokens
    private static final int MIN_LIVE_TRADING_PARAMS = 8; // 8B parameters
    /**
     * Shared with {@link ModelTierRegistry} rather than restated. Two lists that disagreed meant
     * the platform's own shipped orchestrator default (deepseek) failed this LIVE_TRADING gate.
     */
    private static final Set<String> TRUSTED_PROVIDERS = ModelTierRegistry.TRUSTED_PROVIDERS;

    private final ModelTierRegistry tierRegistry = new ModelTierRegistry();

    /**
     * Filter models based on the requirements of a specific trading context.
     *
     * @param models  List of available models
     * @param context The trading context to filter for
     * @return Filtered list of models suitable for the context
     */
    public List<LLMModelInfo> filterForContext(List<LLMModelInfo> models, TradingContext context) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }

        List<LLMModelInfo> filtered = switch (context) {
            case RESEARCH -> models; // All models allowed - system appends :online suffix when needed

            case LIVE_TRADING -> models.stream()
                .filter(ModelFilter::hasSufficientContext)
                .filter(ModelFilter::isFromTrustedSource)
                .filter(m -> m.parameterSizeB() == null || m.parameterSizeB() >= MIN_LIVE_TRADING_PARAMS)
                .toList();

            case BACKTESTING -> models.stream()
                .sorted(Comparator.comparing(m -> m.pricing().promptCostPerMillion()))
                .toList();

            case PAPER_TRADING, CHAT -> models;
        };

        LOG.debug("Filtered {} models to {} for context {}", models.size(), filtered.size(), context);
        return filtered;
    }

    /**
     * Check if a specific model is valid for a trading context.
     *
     * @param model   The model to check
     * @param context The trading context
     * @return true if the model meets the context requirements
     */
    public boolean isValidForContext(LLMModelInfo model, TradingContext context) {
        if (model == null) {
            return false;
        }

        return switch (context) {
            case RESEARCH -> true; // All models allowed for research

            case LIVE_TRADING -> hasSufficientContext(model)
                && isFromTrustedSource(model)
                && (model.parameterSizeB() == null || model.parameterSizeB() >= MIN_LIVE_TRADING_PARAMS);

            case BACKTESTING, PAPER_TRADING, CHAT -> true;
        };
    }

    /**
     * Whether the context window is big enough — treating an unmeasured one as unknown, not small.
     *
     * <p>A context length of 0 means "nobody told us", which is exactly how this class already
     * treats a null parameter size: skip the check rather than fail it. An OpenAI-compatible server
     * that does not implement Ollama's {@code /api/show} reports 0 for every model, and refusing
     * every local model on the strength of a number nobody measured would be inventing a fact.</p>
     */
    private static boolean hasSufficientContext(LLMModelInfo model) {
        return model.contextLength() == 0 || model.contextLength() >= MIN_LIVE_TRADING_CONTEXT;
    }

    /**
     * Whether the model comes from somewhere trustworthy.
     *
     * <p>A model with no provider namespace is running on the user's own hardware — they pulled it
     * deliberately, and there is no third party to trust or distrust. Excluding local models here
     * meant the context-length and parameter-size gates never got to say anything about them: every
     * local model failed on the provider check first, whatever its actual capabilities.</p>
     */
    private static boolean isFromTrustedSource(LLMModelInfo model) {
        return model.provider() == null
            || TRUSTED_PROVIDERS.contains(model.provider().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Get a description of the requirements for a trading context.
     *
     * @param context The trading context
     * @return Human-readable description of requirements
     */
    public String getContextRequirements(TradingContext context) {
        return switch (context) {
            case RESEARCH -> "All models supported. Web search enabled via :online suffix. Not all models support web access.";
            case BACKTESTING -> "Sorted by cost (cheapest first). Consider free or low-cost models for high-volume analysis";
            case PAPER_TRADING -> "All models supported. Balanced quality and cost recommended";
            case LIVE_TRADING -> "Filtered to trusted providers (OpenAI, Anthropic, Google, Meta, Mistral, " +
                "Qwen, Cohere, DeepSeek, Xiaomi) plus models on your own hardware, with 32K+ context and " +
                "8B+ parameters. An unreported context window or parameter count is treated as unknown " +
                "rather than as a failure.";
            case CHAT -> "All models supported. Balanced quality and cost recommended for chat interactions";
        };
    }

    /**
     * Evaluate a model's tier for a specific trading context.
     * Delegates to the {@link ModelTierRegistry} for curated tier classification.
     *
     * @param model   The model to evaluate
     * @param context The trading context
     * @return The tier evaluation with reason
     */
    public ModelEvaluation evaluateModel(LLMModelInfo model, TradingContext context) {
        return tierRegistry.evaluate(model, context);
    }
}
