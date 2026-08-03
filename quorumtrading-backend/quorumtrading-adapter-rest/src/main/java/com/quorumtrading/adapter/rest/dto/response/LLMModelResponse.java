package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.valueobject.CategorizedModel;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.service.PlatformMarkup;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for LLM model information.
 */
@Schema(description = "LLM model information with capabilities and pricing")
public record LLMModelResponse(
    @Schema(description = "Unique model identifier", example = "anthropic/claude-3.5-sonnet")
    String id,

    @Schema(description = "Human-readable display name", example = "Claude 3.5 Sonnet")
    String name,

    @Schema(description = "Model description", example = "Fast and intelligent model")
    String description,

    @Schema(description = "Provider name", example = "anthropic")
    String provider,

    @Schema(description = "Maximum context window in tokens", example = "200000")
    int contextLength,

    @Schema(description = "Cost per 1M input tokens in USD", example = "3.00")
    BigDecimal promptCostPerMillion,

    @Schema(description = "Cost per 1M output tokens in USD", example = "15.00")
    BigDecimal completionCostPerMillion,

    @Schema(description = "Supported modalities", example = "[\"text\", \"image\"]")
    List<String> modalities,

    @Schema(description = "Whether model supports web search", example = "false")
    boolean supportsWebSearch,

    @Schema(description = "Whether model is free to use", example = "false")
    boolean isFree,

    @Schema(description = "Model size in billions of parameters (null if unknown)", example = "70")
    Integer parameterSizeB,

    @Schema(description = "Model tier for the requested context (null when no context provided)",
            example = "RECOMMENDED", nullable = true)
    String tier,

    @Schema(description = "Reason for the tier assignment (null when no context provided)",
            example = "Best ROI: +13.34% returns at $0.00037/decision", nullable = true)
    String tierReason
) {
    /**
     * Create response from domain model (no tier info).
     * Prices include platform markup so users see the billed price.
     */
    public static LLMModelResponse fromDomain(LLMModelInfo model) {
        LLMPricing billedPricing = model.pricing().withMarkup(PlatformMarkup.rate());
        return new LLMModelResponse(
            model.id(),
            model.name(),
            model.description(),
            model.provider(),
            model.contextLength(),
            billedPricing.promptCostPerMillion(),
            billedPricing.completionCostPerMillion(),
            model.modalities(),
            model.supportsWebSearch(),
            model.isFree(),
            model.parameterSizeB(),
            null,
            null
        );
    }

    /**
     * Create response from categorized model (with tier info).
     * Prices include platform markup so users see the billed price.
     */
    public static LLMModelResponse fromCategorized(CategorizedModel categorized) {
        LLMModelInfo model = categorized.model();
        LLMPricing billedPricing = model.pricing().withMarkup(PlatformMarkup.rate());
        return new LLMModelResponse(
            model.id(),
            model.name(),
            model.description(),
            model.provider(),
            model.contextLength(),
            billedPricing.promptCostPerMillion(),
            billedPricing.completionCostPerMillion(),
            model.modalities(),
            model.supportsWebSearch(),
            model.isFree(),
            model.parameterSizeB(),
            categorized.evaluation().tier().name(),
            categorized.evaluation().reason()
        );
    }
}
