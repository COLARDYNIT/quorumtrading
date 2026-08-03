package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object representing LLM model pricing information.
 * All costs are in USD.
 *
 * @param promptCostPerToken     Cost per input token (e.g., 0.000005 = $5 per million tokens)
 * @param completionCostPerToken Cost per output token (e.g., 0.000015 = $15 per million tokens)
 * @param requestCost            Fixed cost per request (usually 0)
 * @param imageCost              Cost per image processed (for multimodal models)
 */
public record LLMPricing(
    BigDecimal promptCostPerToken,
    BigDecimal completionCostPerToken,
    BigDecimal requestCost,
    BigDecimal imageCost
) {

    /**
     * Zero pricing for free models.
     */
    public static final LLMPricing FREE = new LLMPricing(
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO
    );

    public LLMPricing {
        if (promptCostPerToken == null) promptCostPerToken = BigDecimal.ZERO;
        if (completionCostPerToken == null) completionCostPerToken = BigDecimal.ZERO;
        if (requestCost == null) requestCost = BigDecimal.ZERO;
        if (imageCost == null) imageCost = BigDecimal.ZERO;
    }

    /**
     * Create pricing with just prompt and completion costs (most common case).
     */
    public static LLMPricing of(BigDecimal promptCost, BigDecimal completionCost) {
        return new LLMPricing(promptCost, completionCost, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Create pricing from string values (as returned by OpenRouter API).
     * Handles null/empty strings gracefully.
     */
    public static LLMPricing fromStrings(String prompt, String completion, String request, String image) {
        return new LLMPricing(
            parseOrZero(prompt),
            parseOrZero(completion),
            parseOrZero(request),
            parseOrZero(image)
        );
    }

    /**
     * Check if this model is free to use.
     */
    public boolean isFree() {
        return promptCostPerToken.compareTo(BigDecimal.ZERO) == 0
            && completionCostPerToken.compareTo(BigDecimal.ZERO) == 0
            && requestCost.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Estimate total cost for a given number of tokens.
     *
     * @param promptTokens     Number of input tokens
     * @param completionTokens Number of output tokens
     * @return Estimated total cost in USD
     */
    public BigDecimal estimateCost(int promptTokens, int completionTokens) {
        BigDecimal promptCost = promptCostPerToken.multiply(BigDecimal.valueOf(promptTokens));
        BigDecimal completionCost = completionCostPerToken.multiply(BigDecimal.valueOf(completionTokens));
        return promptCost.add(completionCost).add(requestCost);
    }

    /**
     * Get cost per 1 million prompt tokens (for display purposes).
     */
    public BigDecimal promptCostPerMillion() {
        return promptCostPerToken.multiply(BigDecimal.valueOf(1_000_000))
            .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Get cost per 1 million completion tokens (for display purposes).
     */
    public BigDecimal completionCostPerMillion() {
        return completionCostPerToken.multiply(BigDecimal.valueOf(1_000_000))
            .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Returns a new LLMPricing with all costs multiplied by the given rate.
     * Used to produce user-facing prices that include platform markup.
     *
     * @param rate The markup rate (e.g., 1.30 for 30% markup)
     * @return New LLMPricing with scaled costs
     */
    public LLMPricing withMarkup(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ONE) == 0) {
            return this;
        }
        return new LLMPricing(
            promptCostPerToken.multiply(rate),
            completionCostPerToken.multiply(rate),
            requestCost.multiply(rate),
            imageCost.multiply(rate)
        );
    }

    private static BigDecimal parseOrZero(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
